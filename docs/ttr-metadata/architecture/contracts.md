# ttr-metadata — Contracts (v1)

> **Status:** designed 2026-07-05. Source of truth for the library API surface, the Designer-server protocol, artifact coordinates, and compatibility rules. Companion: [`architecture.md`](./architecture.md). Changes here require a changelog entry (bottom).
>
> Signatures below are normative in *shape*; exact Kotlin spelling may drift during extraction — any drift that changes a shape needs a changelog entry.

---

## 1. Artifacts & coordinates

| Artifact | Module | Content | Consumers |
|---|---|---|---|
| `org.tatrman:ttr-metadata` | `:packages:kotlin:ttr-metadata` | typed model, storage SPI (+ fs/classpath), reconciler, resolver, graph, search, registry, refresher mechanism, export, **world resolution** | ttrp-frontend (P1.3), ttr-designer-server, Ariadne |
| `org.tatrman:ttr-metadata-git` | `:packages:kotlin:ttr-metadata-git` | `GitArchiveStorage` (jgit) implementing the core `ModelStorage` SPI | Ariadne |

Publishing: tag-driven per `PUBLISHING.md` — new tags **`kotlin-metadata/v<x.y.z>`** (publishes both artifacts together; they version in lockstep). Semver: minor = additive API, major = breaking; ttr-metadata pins the ttr-parser/semantics versions it builds against and re-exports them as `api` deps (consumers get one coherent set). JVM toolchain 21, Kotlin per `gradle/libs.versions.toml` (matches ttr-semantics).

## 2. Library API — core

Package root `org.tatrman.ttr.metadata`. All types immutable; snapshot semantics throughout.

```kotlin
// ── storage SPI (moved verbatim from Ariadne source/) ─────────────────
fun interface ModelSource { fun load(): SourceSnapshot }
interface ModelStorage {
    fun fetchVersion(): String
    fun listFiles(extensions: Set<String>, prefixes: List<String> = emptyList()): List<StorageFile>   // moved surface (v1.1 correction)
    fun read(file: StorageFile): String
}
class LocalFsStorage(root: Path) : ModelStorage          // repo-attached (Designer server, compiler)
class ClasspathStorage(prefix: String) : ModelStorage    // bundled seed models
// GitArchiveStorage lives in ttr-metadata-git

// ── load & lifecycle ──────────────────────────────────────────────────
class MetadataLoader(source: ModelSource) {
    fun load(): LoadResult                               // parse (ttr-parser) → reconcile → resolve → Model
}
data class LoadResult(val model: Model?, val issues: List<LoadIssue>)   // never throws on model errors

class MetadataRegistry {                                  // AtomicReference snapshot + listeners (moved)
    fun read(): RegistrySnapshot?
    fun readOrError(): RegistrySnapshot
    fun swap(snapshot: RegistrySnapshot)
    fun addListener(listener: (RegistrySnapshot) -> Unit)
}
class MetadataRefresher(loader: MetadataLoader, registry: MetadataRegistry) {
    suspend fun tryRefresh(): RefreshOutcome              // mutex-guarded; scheduling = host concern
    suspend fun forceRefresh(): RefreshOutcome
}

// ── typed model (moved from Ariadne model/, package-renamed) ──────────
data class Model(/* schemas, packages, areas, versions */) {
    fun objectByQname(): Map<QualifiedName, ModelObject>
    fun areaByName(name: String): AreaRecord?
}
sealed interface ModelObject                              // DbTable, DbView, DbColumn, DbProcedure,
                                                          // Entity, Attribute, RelationDef, Binding, …
sealed interface SchemaContents { fun objects(): Sequence<ModelObject> }  // DbSchema, ErSchema, CncSchema, WorldSchema(NEW)

// ── query facade (MD2: pulled down from Ariadne's gRPC bodies) ───────
class MetadataQuery(snapshot: RegistrySnapshot) {
    fun listObjects(filter: ObjectFilter, page: PageRequest): Page<ModelObject>
    fun getObject(qname: QualifiedName): ModelObject?
    fun resolve(qname: QualifiedName, expected: ObjectKind): ResolveOutcome   // §3 kind-typed lookup
    fun search(query: SearchQuery): List<SearchHit>       // keyword|regex|substring|all + post-processing
    fun graph(): ModelGraph                               // DEFINES/REFERENCES/MAPS_TO/USES; traverse/topo/cycles
    fun resolveArea(name: String): AreaResolution?
    fun erToDb(erQname: QualifiedName): ErBindingResult   // §3
}
```

`ResolveOutcome` and every error surface are **structured, id-free** (MD5): the library reports *what* (`NotFound`, `KindMismatch(expected, found)`, `Ambiguous(candidates)`, `BindingMissing(erQname, searchedPackages)`), consumers mint their own diagnostic ids (`TTRP-RES-003` etc.).

## 3. Library API — world resolution (new)

```kotlin
class WorldResolver(snapshot: RegistrySnapshot) {
    fun listWorlds(): List<QualifiedName>
    fun resolve(worldQname: QualifiedName): WorldResolution   // Ok(ResolvedWorld) | structured failure
}
data class ResolvedWorld(
    val qname: QualifiedName,
    val engines: List<ResolvedEngine>,        // instance ⊕ extends-type overlay applied
    val executors: List<ResolvedExecutor>,
    val storages: List<ResolvedStorage>,      // incl. hosts: [pkg] mapping, schema defs (D-c world tier)
    val staging: ResolvedStorage?,            // exactly-one validated; >1 → StagingConflict failure (D-f)
    val fingerprint: String,                  // "sha256:<semantic-hash>" — §5
)
data class ErBindingResult(                   // E-d support: provenance data, consumer renders it
    val dbQname: QualifiedName?,
    val chain: List<BindingStep>,             // each: erQname, dbQname, definitionLocation
)
```

Failure shapes: `WorldNotFound`, `NotAWorld(foundKind)`, `StagingConflict(storages)`, `HostsUnknownPackage(pkg)`, `ExtendsUnresolved(typeRef)`. Selection precedence (`uses world` pin > `[ttrp] world` > error) is the **caller's** contract (TTR-P contracts §2) — `WorldResolver` takes the already-chosen qname.

**Capability manifests:** engine/executor manifest *content* (T6 β entries) rides on `ResolvedEngine`/`ResolvedExecutor` as data (`manifest: JsonObject`-shaped, format owned by TTR-P Stage 2.2). ttr-metadata transports and overlays it; it never interprets it (MD5).

## 4. Designer-server protocol — `ttrm/*`

Transport: WebSocket, JSON-RPC 2.0, one message per text frame, endpoint `ws://127.0.0.1:<port>/ttrm` (S24: loopback bind, no auth; default port **7270**, `--port` to override). Read-only in v1 — no method mutates the repo.

| Method | Params → Result | Notes |
|---|---|---|
| `ttrm/getModelIndex` | `{}` → `{packages[], schemas[], areas[], counts, modelVersion}` | the browse tree |
| `ttrm/getModelGraph` | `{scope?: {package? schema? qnames[]?}, edgeTypes?}` → `{nodes[], edges[]}` | node = `{qname, kind, label, schema, pkg}`; edge = `{from, to, type}` (DEFINES\|REFERENCES\|MAPS_TO\|USES); shaped after the payload the Designer already renders (MD6) |
| `ttrm/getObject` | `{qname}` → `{object, sourceLocation, references[]}` | full typed detail incl. er↔db bindings |
| `ttrm/search` | `{query, algorithm?: keyword\|regex\|substring\|all, limit?}` → `{hits[]}` | |
| `ttrm/getWorld` | `{qname?}` → `ResolvedWorld` JSON (or `{worlds[]}` when no qname) | serves TTR-P P5 `ttrp/getWorld` needs |
| `ttrm/getStatus` | `{}` → `{modelVersion, loadedAt, issues[], repoRoot}` | |
| `ttrm/refresh` | `{force?}` → `{outcome, modelVersion}` | re-load from disk |
| `ttrm/modelChanged` | **notification →client** `{modelVersion}` | fired via registry listener on file-watch reload |

Errors: JSON-RPC error objects `{code, message, data:{kind, …structured fields…}}`; codes: `-32000` model-not-loaded, `-32001` not-found, `-32002` bad-scope. Protocol version handshake: `ttrm/getStatus` carries `protocolVersion: 1`; additive changes bump nothing, breaking changes bump it (and this doc).

**Future mounts on the same host (informative):** TTR-P WS-LSP at `/lsp` (P5.1, mounts into this server per MD8) · TTR-M editing + `.ttrl` (C1-f, post-v1). `ttrm/*` is reserved for TTR-M model serving; `ttrp/*` stays with the TTR-P LSP.

## 5. Semantic world fingerprint

One implementation, exported for the TTR-P bundle (`manifest.json.world.fingerprint`, TTR-P contracts §5) and conform harness. Canonicalization: resolved (post-overlay) world → canonical JSON (sorted keys, qname-sorted arrays, defaults elided, source locations and doc-comments excluded) → sha256. Property tests pin: insensitive to declaration order/whitespace/comments; sensitive to any semantic field (engine version, hosts, staging flag, manifest entries). Spelled `sha256:<hex>`.

## 6. Frontend data-source contract (packages/designer)

```ts
interface ModelDataSource {                       // implemented by: WorkerLspDataSource (existing path),
  getModelIndex(): Promise<ModelIndex>            //                 WsDesignerServerDataSource (new)
  getModelGraph(scope?: GraphScope): Promise<ModelGraphPayload>
  getObject(qname: string): Promise<ObjectDetail>
  search(q: SearchParams): Promise<SearchHit[]>
  onModelChanged(cb: (v: string) => void): Disposable
  readonly capabilities: { edit: boolean }        // WS source: edit=false → designer hides edit affordances
}
```

Selection: explicit configuration (URL param `?server=ws://127.0.0.1:7270` or config file) — never sniffed (P2).

## 7. Kantheon adoption contract

- Ariadne consumes `org.tatrman:ttr-metadata` + `:ttr-metadata-git` from GitHub Packages; pins in `gradle/libs.versions.toml`.
- **Frozen through the swap (MD7):** the `org.tatrman.ariadne.v1` proto, all 14 RPC behaviors, ports 7260/7261/7262, `METADATA_GIT_*` env contract, ariadne-mcp.
- Deleted from Ariadne after the swap: `model/ source/ reconcile/ resolve/ graph/ search/ registry/ export/`(minus routes)`, refresh/MetadataRefresher` — anything re-appearing there is drift (guard: review rule in the kantheon arc doc).
- Stays in Ariadne: `grpc/`, `PageTokenCodec`, proto conversions, `parse/` (QueryParseWorker + query-translator), `RefreshScheduler`, Application/Ktor/OTel/k8s, `MetadataExportRoutes`.
- Compatibility gate: Ariadne's remaining Kotest suite green against the published artifact = the adoption DONE bar; the moved core specs (24: 23 core + 1 git; 14 stay) live in tatrman from M1 on.

## 8. Fixture sharing with TTR-P

The world/model fixture project designed in TTR-P `tasks-p1-s1.3-resolution.md` (erp db/er/binding models + `acme.worlds` world doc + WLD/RES/SCH negative roster) gets its **single home in ttr-metadata** (`packages/kotlin/ttr-metadata/src/testFixtures/resources/fixtures/` — the `java-test-fixtures` source set, required for cross-project consumption; v1.1 correction); ttrp-frontend's Stage 1.3 consumes it via `testFixtures(project(":packages:kotlin:ttr-metadata"))` instead of duplicating. TTR-P task list s1.3 gets a pre-flight amendment (plan §6).

---

## Changelog

- **v1.2 · 2026-07-05 (M2)** — implementation-driven shape confirmations (see
  `../implementation/v1/notes-api-review.md`): §8 fixture home is
  **`src/testFixtures/resources/fixtures/`** (the `test` source set is not
  cross-project consumable; only `testFixtures` is) — consumers wire
  `testFixtures(project(":packages:kotlin:ttr-metadata"))`. §3 `manifest` is
  transported as `Map<String, PropertyValue>` (the parser value model), not a JSON
  tree — TTR-P Stage 2.2 owns the JSON projection/interpretation (MD5); the 1:1
  JSON mapping is shown by `WorldFingerprint.canonValue`. §3 overlay merge rule
  (shipped, reviewable): instance wins, type fills, **lists/manifest replaced
  wholesale** (not element-merged); **dotted `extends` resolves in-model else
  `ExtendsUnresolved`, bare ids pass through on `extendsRef`** (RM6). §5 fingerprint
  implemented (`WorldFingerprint`): canonical JSON (sorted keys, qname-sorted arrays,
  defaults elided, locations/comments excluded) → sha256; world qname excluded from
  the hash (F-f-ii, reviewable). §2 `LoadResult.issues` is the finalized sealed
  `LoadIssue` taxonomy (id-free enum categories, MD5); `MetadataRefresher` keeps the
  moved Ariadne surface (RM9). SchemaCode gains library-only `WORLD` (proto frozen).
- **v1.1 · 2026-07-05** — task-cutting corrections: §2 `ModelStorage.listFiles(extensions, prefixes)` (actual moved surface); §7 moved-spec count 24 (was ≈19); §8 fixture home `src/testFixtures/resources/` under `java-test-fixtures`. Known queued entries (land with their stages): §2 registry/refresher exact signatures (RM9), §4 getModelGraph canvas-DTO note + getStatus handshake + per-element field pins (RM8), §3 `extendsRef` pass-through rule (RM6).
- **v1 · 2026-07-05** — initial design (MD1–MD8).
