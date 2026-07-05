# Tasks · M1 · Stage 1.2 — Resolve + graph + search + registry + refresher + export, `MetadataQuery` pull-down, publish wiring

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Feature decisions MD1–MD8 → `../../architecture/architecture.md` §8 · TTR-P decision IDs → `../../../ttr-p/design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The remaining Ariadne core lands in `org.tatrman.ttr.metadata.*`: `resolve/`, `graph/`, `search/`, `registry/`, `refresh/MetadataRefresher` (mechanism only — `RefreshScheduler` stays kantheon), `export/` (minus the Ktor `MetadataExportRoutes`), plus the **MD2 pull-down**: the ListObjects filtering/paging/fuzzy logic extracted from kantheon's `MetadataServiceImpl` into a library `MetadataQuery` facade with component tests pinning the moved behaviors. `PUBLISHING.md` gains the two artifact rows; `.github/workflows/publish.yml` gains `kotlin-metadata/v*`; `-Pversion=0.0.1-LOCAL publishToMavenLocal` resolves from a scratch consumer. **Phase M1 DONE bar:** both modules build/test green in tatrman CI; the scratch consumer resolves `org.tatrman:ttr-metadata:0.0.1-LOCAL` from Maven Local.

Extraction ground rules from [tasks-m1-s1.1-model-sources.md](./tasks-m1-s1.1-model-sources.md) apply unchanged: copy from `/Users/bora/Dev/collite-gh/kantheon/services/ariadne/...`, provenance note in the PR, package-rename table (s1.1 T1.1.3), **kantheon stays byte-untouched** (M4 does the swap; core freeze in force). Additional de-proto scope for this stage (recon 2026-07-05): `search/SearchAlgorithm.kt`, `search/SearchPostProcessor.kt`, `search/keyword/KeywordAlgorithm.kt` import proto `org.tatrman.ariadne.v1.SearchRequest`, and `graph/TraverseEdgesHandler.kt` imports proto `EdgeType`/`Direction` — the library replaces these with owned types (contracts §2 `SearchQuery`; `ModelGraph`'s own `EdgeType`/`Direction` enums already exist at `graph/ModelGraph.kt` lines 224–226).

## Pre-flight (all must pass before T1.2.1)

- [x] Stage M1.1 DONE bar green: `./gradlew :packages:kotlin:ttr-metadata:test :packages:kotlin:ttr-metadata-git:test` and `:packages:kotlin:ttr-metadata:dependencyRules`.
- [x] `grep -rn "kantheon\|plan\.v1\|ariadne\.v1" packages/kotlin/ttr-metadata/src --include='*.kt'` → empty (s1.1 exit condition holds).
- [x] Read `.github/workflows/publish.yml` + `PUBLISHING.md` in full (T1.2.7 edits both) and kantheon `grpc/MetadataServiceImpl.kt` lines 129–428 + 582–716 + 883–920 (the pull-down source).

## Tasks

### T1.2.1 · Ported-spec roster + `MetadataQuery` component-spec skeletons (TEST-FIRST)

- [x] Copy this stage's spec files (`services/ariadne/src/test/kotlin/org/tatrman/kantheon/ariadne/` → `packages/kotlin/ttr-metadata/src/test/kotlin/org/tatrman/ttr/metadata/`; rename table applied, assertions unchanged unless a row says "adapted"):

  | kantheon spec | tatrman target | notes |
  |---|---|---|
  | `resolve/DrillMapValidatorSpec.kt` | `…/metadata/resolve/DrillMapValidatorSpec.kt` | |
  | `resolve/ResolutionIntegrationSpec.kt` | `…/metadata/resolve/ResolutionIntegrationSpec.kt` | |
  | `graph/TraverseEdgesHandlerSpec.kt` | `…/metadata/graph/TraverseEdgesHandlerSpec.kt` | **adapted**: proto `EdgeType`/`Direction` → library enums (T1.2.3) |
  | `search/KeywordAlgorithmSpec.kt` | `…/metadata/search/KeywordAlgorithmSpec.kt` | **adapted**: proto `SearchRequest` builder → `SearchQuery(…)` (T1.2.4) |
  | `search/RegexAlgorithmSpec.kt` | `…/metadata/search/RegexAlgorithmSpec.kt` | adapted (same) |
  | `search/SubstringAlgorithmSpec.kt` | `…/metadata/search/SubstringAlgorithmSpec.kt` | adapted (same) |
  | `search/AllAlgorithmAndPostProcessSpec.kt` | `…/metadata/search/AllAlgorithmAndPostProcessSpec.kt` | adapted (same) |
  | `search/SearchScaffoldingSpec.kt` | `…/metadata/search/SearchScaffoldingSpec.kt` | |
  | `refresh/MetadataRefresherSpec.kt` | `…/metadata/refresh/MetadataRefresherSpec.kt` | `RefreshSchedulerSpec` stays kantheon |
  | `export/DbErCncSplitSpec.kt` | `…/metadata/export/DbErCncSplitSpec.kt` | |
  | `export/ExportRoundTripSpec.kt` | `…/metadata/export/ExportRoundTripSpec.kt` | |
  | `export/GraphDotExporterSpec.kt` | `…/metadata/export/GraphDotExporterSpec.kt` | |
  | `export/InlineMappingExportSpec.kt` | `…/metadata/export/InlineMappingExportSpec.kt` | |
  | `export/MetadataExportPipelineSpec.kt` | `…/metadata/export/MetadataExportPipelineSpec.kt` | drop/replace any `MetadataExportRoutes` (Ktor) cases — routes stay kantheon; keep the pipeline cases |
  | `export/ModelToDefinitionsSpec.kt` | `…/metadata/export/ModelToDefinitionsSpec.kt` | |

  With s1.1's nine, the moved-core-spec total is **24** (23 in core + `GitArchiveStorageSpec` in `-git`) — architecture §3's "≈19" undercounted; record the true count in the progress doc. Staying kantheon (final roster, for the M4 list): `MetadataQuerySpec`, `MetadataServiceFixtureSpec`, `Phase2_2ExpressivenessSpec`, `QueryParseWorkerSpec`, `grpc/{GetModelSpec, GetObjectColumnSearchHintsSpec, ListObjectsFuzzyAttributeMappingSpec, ListObjectsFuzzyOnlyFilterSpec, ListObjectsFuzzyOnlyFixtureSpec, ListObjectsPackageFilterSpec, PageTokenCodecSpec, ResolveAreaSpec}`, `refresh/RefreshSchedulerSpec`, `search/SearchRpcSpec` — 14 specs.
- [x] Copy `services/ariadne/src/test/resources/fixture-fuzzy/` → `packages/kotlin/ttr-metadata/src/test/resources/fixture-fuzzy/` (feeds the MD2 component tests).
- [x] New spec skeletons (red), package `org.tatrman.ttr.metadata.query` — these pin the MD2 moved behaviors as **library** cases, mirroring the kantheon grpc specs case-for-case (translate proto requests to `ObjectFilter`/`PageRequest`; keep the kantheon spec name in a header comment for the M4 cross-check):
  - `MetadataQueryListObjectsSpec.kt` — mirrors `ListObjectsPackageFilterSpec` (package filter = `sourceFile.contains("/<pkg>/")`) + the schema/kind/tags/sourceFilePrefix filter cases embedded in `MetadataServiceImpl.listObjects` (lines 355–369).
  - `MetadataQueryFuzzySpec.kt` — mirrors `ListObjectsFuzzyOnlyFilterSpec`, `ListObjectsFuzzyOnlyFixtureSpec` (over `fixture-fuzzy/`), `ListObjectsFuzzyAttributeMappingSpec` (fuzzy attribute → er2db column target; Expression/unmapped attributes skipped with warning; memoised per model version — lines 168–209).
  - `MetadataQueryPagingSpec.kt` — sort-key ordering (`schemaCode.namespace.name`, line 369), default page size 100, cap 1000 (line 372), stable windows across identical snapshots, `afterKey` resume semantics, `totalCount` (see T1.2.5 paging design — the base64 wire token itself stays kantheon in `PageTokenCodec`).
  - `MetadataQueryResolveAreaSpec.kt` — mirrors `ResolveAreaSpec`'s found/not-found/packages-verbatim cases as `resolveArea(name)` returning `AreaResolution?` (null = unknown; no warning-message minting — MD5).
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*MetadataQuery*'` red (compile failure over the missing facade is the expected red); ported specs compile-red pending T1.2.2–T1.2.6.

### T1.2.2 · Port `resolve/` + `registry/` + `refresh/MetadataRefresher` + the `MetadataLoader` facade

- [x] Copy → rename: `resolve/ReferenceResolutionPass.kt`, `resolve/Resolution.kt`, `resolve/DrillMapValidator.kt`, `resolve/PublishedResolverAdapter.kt` (bridges to in-repo `ttr-semantics` `Resolver`/`SymbolTable`/`StockLoader` — verify the 0.8.4-era `org.tatrman.ttr.semantics` surface still matches the in-repo one; adapt call sites, never the semantics module), `registry/MetadataRegistry.kt` (54 lines — `AtomicReference` snapshot + `CopyOnWriteArrayList` listeners + `RegistrySnapshot(model, graph, swappedAt, warnings)`), `refresh/MetadataRefresher.kt` (183 lines — mutex-guarded try/force refresh, `SourceResult`). **`refresh/RefreshScheduler.kt` is NOT copied** (periodic policy = host concern; architecture §3).
- [x] New file `…/ttr/metadata/MetadataLoader.kt` (contracts §2 — thin composition, no new logic): `class MetadataLoader(source: ModelSource, policy: ReconciliationPolicy = default)` with `fun load(): LoadResult`; `data class LoadResult(val model: Model?, val issues: List<LoadWarning>)` — never throws on model errors. Body = `source.load()` → `ModelReconciler` → resolution pass, exactly the sequence Ariadne's `Application.kt` composes today (read it for the order; do not copy its Ktor/env parts). `LoadWarning` stands in for contracts' `LoadIssue` until the M2.2 taxonomy pass — leave a `// M2.2: LoadIssue taxonomy (plan)` marker.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*Resolution*' --tests '*DrillMapValidator*' --tests '*MetadataRefresher*'` green.

### T1.2.3 · Port `graph/` (de-proto TraverseEdgesHandler)

- [x] Copy → rename: `graph/ModelGraph.kt` (240 lines — JGraphT `DefaultDirectedGraph`, `EdgeType { DEFINES, REFERENCES, MAPS_TO, USES }`, `Direction`, cycle/topo/connectivity helpers) verbatim; `graph/TraverseEdgesHandler.kt` (174 lines) **adapted**: replace `import org.tatrman.ariadne.v1.Direction as ProtoDirection` / `EdgeType as ProtoEdgeType` with the library enums from `ModelGraph.kt`; `traverse(from, edgeTypes: Set<EdgeType>, direction: Direction, maxDepth)` — keep `MAX_DEPTH_CAP` and step semantics identical (proto↔enum mapping becomes Ariadne grpc-layer code in M4).
- [x] Adapt the copied `TraverseEdgesHandlerSpec` to the enum surface — same fixtures, same expected edge sequences.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*TraverseEdges*'` green; `grep -rn "ariadne\.v1" packages/kotlin/ttr-metadata/src/main` → empty.

### T1.2.4 · Port `search/` (de-proto SearchRequest → `SearchQuery`)

- [x] Copy → rename: `search/SearchAlgorithm.kt` (SPI + `SearchIndex` + `RebuildOutcome`/`CompileError`/`SearchHit` + `SearchAlgorithmRegistry`), `search/SearchIndexHolder.kt`, `search/IndexableObjects.kt`, `search/SearchPostProcessor.kt`, `search/keyword/{KeywordAlgorithm,StopWords,Tokenizer}.kt`, `search/regex/RegexAlgorithm.kt`, `search/substring/SubstringAlgorithm.kt`, `search/all/AllAlgorithm.kt`.
- [x] De-proto: new `data class SearchQuery(val query: String, val algorithm: String = "all", val language: String = "cs", val limit: Int = 0, val resultThreshold: Float = 0f /* + any further SearchRequest fields the algorithms/postProcess actually read — enumerate them from the copied code, field-for-field */)` in `…/metadata/search/`; `SearchAlgorithm.search(query: SearchQuery, index: SearchIndex)` and `postProcess(hits, query)` take it. Defaults mirror the grpc defaults (`DEFAULT_SEARCH_ALGORITHM = "all"`, language fallback "cs" — `MetadataServiceImpl` lines 639–640) so M4's facade is a field-copy.
- [x] Adapt the four algorithm specs + `AllAlgorithmAndPostProcessSpec` to build `SearchQuery` instead of the proto builder — assertion values unchanged.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*Algorithm*' --tests '*SearchScaffolding*'` green.

### T1.2.5 · MD2 pull-down — `MetadataQuery` facade + component tests

The gRPC bodies in kantheon `grpc/MetadataServiceImpl.kt` embed reusable query logic; it moves here so M4 can shrink those bodies to proto-conversion + delegation. Exact sources (kantheon file/lines → library home `…/ttr/metadata/query/MetadataQuery.kt`):

  | moves | from `MetadataServiceImpl.kt` | becomes |
  |---|---|---|
  | ListObjects filter pipeline: schema / kind / tags / sourceFilePrefix / fuzzyOnly / package filters + sort key | `listObjects`, lines 342–388 (filters 355–369, sort 369) | `listObjects(filter: ObjectFilter, page: PageRequest): Page<ModelObject>` |
  | fuzzy-attribute → backing-column set, memoised per model version | `attributeBackedFuzzyColumns` + cache, lines 168–209 | private in `MetadataQuery` (same memoisation) |
  | per-kind `SearchHints` accessor | `searchHintsOrNull`, lines 211–220 | `ModelObject.searchHintsOrNull()` extension in `model/` |
  | paging window: default 100, cap 1000, sort-key slice | lines 372–376 (calls `PageTokenCodec.paginate`) | library windowing over `PageRequest(afterKey: String?, pageSize: Int)` → `Page(items, nextAfterKey: String?, totalCount)`. **`PageTokenCodec` (base64 wire tokens, legacy-offset decoding) stays kantheon** (architecture §3: "paging codec is wire-level"); M4 maps token↔afterKey at the facade |
  | object lookup | `getObject` lookup, lines 394–395 | `getObject(qname: QualifiedName): ModelObject?` (not-found = null; message minting stays kantheon — MD5) |
  | search orchestration: algorithm select + index fetch + postProcess | `search`, lines 639–694 (minus OTel spans 633–707 and proto mapping) | `search(query: SearchQuery): List<SearchHit>` |
  | area resolution | `resolveArea`, lines 892–920 | `resolveArea(name: String): AreaResolution?` (`AreaResolution(packages, description, tags)`) |

- [x] Implement `class MetadataQuery(private val snapshot: RegistrySnapshot, private val searchRegistry: SearchAlgorithmRegistry = SearchAlgorithmRegistry(emptyMap()), private val indexHolder: SearchIndexHolder? = null)` with the methods above plus `graph(): ModelGraph` (returns `snapshot.graph`) and `data class ObjectFilter(schema: SchemaCode?, kind: String?, tags: List<String>, sourceFilePrefix: String?, pkg: String?, fuzzyOnly: Boolean)` — field-per-field the ListObjectsRequest surface, proto-free. `resolve(qname, expected)` (kind-typed lookup) is **M2.1 scope** (contracts §2 note) — leave a marker, don't stub behavior.
- [x] NOT pulled down (record in the class KDoc so M4 doesn't go looking): `getModel` package-bundle assembly + PackageVersion sha256 (lines 222–340 — proto-bundle-shaped, Golem contract), `getSnapshot` etag walk (430–477), `listQueries`/`getQuery` live-parse-status plumbing (479–580 — depends on kantheon's `QueryParseState`), `listRoles`/`getRolesForEntity` (723–785 — thin over model, stays), `validateModel`, `getStatus`, `refresh` RPC shells, all `to*Detail()`/`to*Proto()` builders (933–1382).
- [x] Fill the four T1.2.1 component specs; green them against `fixture-fuzzy/` and the s1.1 fixtures.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*MetadataQuery*'` green — filter semantics, page windows, and fuzzy-attribute mapping each pinned by at least one case that names its kantheon grpc-spec twin.

### T1.2.6 · Port `export/` (minus Ktor routes)

- [x] Copy → rename: `export/ModelToDefinitions.kt` (608 lines — typed model → ttr-parser `Definition` trees; heavy ttr-parser API surface: apply the s1.1 grammar-4.x adaptations — `modelDirective`, `Binding*` names, `.ttrm`), `export/GraphDotExporter.kt`, `export/TtrWriter.kt` (21-line wrapper over in-repo `org.tatrman.ttr.writer.TtrRenderer`). **`export/MetadataExportRoutes.kt` (Ktor) is NOT copied** — stays kantheon (architecture §3).
- [x] The six export specs (T1.2.1) green — `ExportRoundTripSpec` is the strongest referee for the 4.x adaptation (model → definitions → rendered TTR → reparse → same model).
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*Export*' --tests '*ModelToDefinitions*' --tests '*GraphDot*' --tests '*DbErCncSplit*' --tests '*InlineMappingExport*'` green; `dependencyRules` still passes (no ktor arrived with export).

### T1.2.7 · Publishing wiring + Maven Local smoke + phase DONE sweep

- [x] `.github/workflows/publish.yml` (current tag→module mechanics at lines 13–18 + 37–51): add `'kotlin-metadata/v*'` to `on.push.tags` and a resolver branch **before** the `kotlin/v*` fallback:

  ```bash
  elif [[ "$TAG" == kotlin-metadata/v* ]]; then
    MODULES=":packages:kotlin:ttr-metadata:publish :packages:kotlin:ttr-metadata-git:publish"
  ```

  (Both artifacts publish together, versions in lockstep — contracts §1. The `kotlin/v*` bundle branch is NOT extended — ttr-metadata versions independently of the parser bundle.)
- [x] `PUBLISHING.md`: two rows in "What is published" (`:packages:kotlin:ttr-metadata` → `org.tatrman:ttr-metadata`, "typed model, storage SPI, reconciler, resolver, graph, search, registry, refresher mechanism, export, world resolution (M2)"; `:packages:kotlin:ttr-metadata-git` → `org.tatrman:ttr-metadata-git`, "GitArchiveStorage behind the core ModelStorage SPI — Ariadne only"), one row in the tag table (`kotlin-metadata/v<x.y.z>` → both artifacts), and a semver note: "ttr-metadata pins the in-repo ttr-parser/semantics and re-exports them as `api` deps (contracts §1)". First real tag `kotlin-metadata/v0.1.0` is cut at **M2.2**, not here — say so in the row.
- [x] Maven Local smoke: `./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttr-metadata:publishToMavenLocal :packages:kotlin:ttr-metadata-git:publishToMavenLocal` (note: the ttr-* `api` project deps publish POM refs to `org.tatrman:ttr-parser` etc. at `0.0.1-LOCAL` — publish those three to Maven Local at the same `-Pversion` in the same invocation so the consumer resolves a closed set; if the POM emits unresolvable project coordinates instead, STOP and record in §Blockers).
- [ ] Scratch consumer (throwaway, e.g. `/tmp/ttr-metadata-consumer/` — not committed): `settings.gradle.kts` + `build.gradle.kts` with `repositories { mavenLocal(); mavenCentral() }`, `dependencies { implementation("org.tatrman:ttr-metadata:0.0.1-LOCAL") }`, kotlin-jvm plugin; `Main.kt` = `LocalFsStorage` over a copy of the s1.1 `tatrman-repo` fixture → `MetadataLoader.load()` → print object count + `MetadataQuery(…).listObjects(ObjectFilter(...), PageRequest(...)).totalCount`. Paste the run output into the PR.
- [x] Phase DONE sweep: `./gradlew build` (whole repo) · `pnpm -r test` untouched-green · kantheon still byte-untouched (`git -C /Users/bora/Dev/collite-gh/kantheon status --porcelain` empty) · update `progress-phase-M1.md` with the moved-spec ledger (24 moved / 14 stayed) for M4's delete list.
  - **Verify:** scratch consumer `./gradlew run` prints the fixture counts; `git log --oneline -1` on the tag-wiring commit follows repo commit style (`Section M1.2: …` per CLAUDE.md convention).

## Definition of DONE (stage = Phase M1 DONE bar)

- [x] All 15 stage-ported specs green under `org.tatrman.ttr.metadata.*` (with s1.1: 23 core + 1 `-git` moved specs total, ledger recorded).
- [x] `MetadataQuery` implemented with the seven pulled-down behaviors; the four component specs pin filter semantics, page windows (100 default / 1000 cap / sort-key ordering), fuzzy-attribute mapping (incl. Expression/unmapped skip + memoisation), and area resolution — each traceable to its kantheon grpc-spec twin.
- [x] Zero proto/ktor/grpc/otel/jgit on the core module (`dependencyRules` + `grep -rn "ariadne\.v1\|plan\.v1\|kantheon" src/main` empty); `RefreshScheduler`, `PageTokenCodec`, `MetadataExportRoutes`, `QueryParseWorker` demonstrably NOT present in tatrman.
- [x] `publish.yml` handles `kotlin-metadata/v*` (both artifacts, lockstep); `PUBLISHING.md` rows landed.
- [x] `-Pversion=0.0.1-LOCAL` publishToMavenLocal + scratch consumer resolve-and-run succeeds with a closed dependency set.
- [x] `./gradlew build` green repo-wide; kantheon untouched.

## Blockers

_(empty — coder records here)_

## References

- **MD2** query logic pulled down into `MetadataQuery`; facade = conversion + delegation (kantheon side lands in M4) · **MD3** `-git` isolation holds through this stage · **MD5** library returns structured results/nulls — no message/id minting moved with the pull-down · **MD7** the grpc surface this stage carves from is frozen — behavior parity is the contract.
- Architecture §3 (verdicts: `resolve/ graph/ search/ registry/ refresh(mechanism) export/` = CORE; `grpc/`+`PageTokenCodec`+`parse/`+`RefreshScheduler` = KANTHEON) · contracts §1 (artifacts/tag), §2 (core API shapes: `MetadataLoader`/`LoadResult`, `MetadataRegistry`, `MetadataRefresher`, `MetadataQuery`, `SearchQuery`), §7 (kantheon adoption ledger this stage's spec roster feeds).
- Pull-down source: `kantheon/services/ariadne/src/main/kotlin/org/tatrman/kantheon/ariadne/grpc/MetadataServiceImpl.kt` — `listObjects` 342–388, fuzzy machinery 168–209, `searchHintsOrNull` 211–220, `search` 627–716, `resolveArea` 892–920, `getObject` 390–428; `grpc/PageTokenCodec.kt` (stays; `paginate`/`encodeAfter`/`decode` incl. legacy-offset tokens).
- De-proto findings (recon 2026-07-05, contradicts architecture §3's "core imports only ttr-*, jgrapht, jgit, slf4j"): `search/SearchAlgorithm.kt`/`SearchPostProcessor.kt`/`keyword/KeywordAlgorithm.kt` → `ariadne.v1.SearchRequest`; `graph/TraverseEdgesHandler.kt` → `ariadne.v1.EdgeType/Direction`; pervasive `plan.v1.QualifiedName` (handled s1.1 T1.1.3).
- Publish mechanics: `.github/workflows/publish.yml` (tag-pattern branch at lines 37–51), `PUBLISHING.md` (tag table, semver discipline, no-SNAPSHOT rule, `0.0.1-LOCAL` convention).
- Downstream consumers of this stage: M2.1 `WorldResolver` (over `RegistrySnapshot`), M3.1 designer server (`MetadataLoader`/`Registry`/`MetadataQuery` over `LocalFsStorage`), TTR-P s1.3 T1.3.3 (`implementation(project(":packages:kotlin:ttr-metadata"))`), M4 swap (kantheon `MetadataServiceImpl` bodies → `MetadataQuery` delegation).
