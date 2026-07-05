# M2.2 · API-shape review — ttr-metadata vs TTR-P consumers

> Walks every step that consumes ttr-metadata in TTR-P `tasks-p1-s1.3-resolution.md`
> and `tasks-p2-s2.2-manifests-world.md` against the real M2 Kotlin surface.
> Disposition per row: **FIX-HERE** (done in M2.2 T2.2.5) or **TTR-P-AMENDMENT**
> (drafted here, applied per plan §6 when the TTR-P lists are next touched).

## Seed rows (found during M2.1)

| # | Expectation | Real API element | ✓/✗ | Disposition |
|---|---|---|---|---|
| 1 | contracts §8 fixture home `src/test/resources/fixtures/` | shipped at **`src/testFixtures/resources/fixtures/`** (`test` source set is not cross-project consumable; only `testFixtures` is) | ✗→✓ | **FIX-HERE**: contracts §8 changelog entry (v1.2). Consumer wire is `testImplementation(testFixtures(project(":packages:kotlin:ttr-metadata")))`. |
| 2 | s1.3 T1.3.3 `ResolvedWorld(engines, executors, storages(+hosts, staging, schemas), worldQname, fingerprint)` | `world.ResolvedWorld(qname, engines, executors, storages, staging, fingerprint)`; `ResolvedStorage(hosts, staging, schemas, …)` | ✓ | Matches contracts §3. **TTR-P-AMENDMENT**: s1.3's local wrapper must not be named `WorldResolver` (collides with the library's `org.tatrman.ttr.metadata.world.WorldResolver`) — rename the ttrp wrapper (e.g. `TtrpWorldBinder`). |
| 3 | s2.2 T2.2.4 `extends: postgres-16` (compiler-manifest id) + `+functions` additive delta | M2.1 rule: **dotted `extends` resolves in-model else `ExtendsUnresolved`; bare id passes through on `ResolvedEngine.extendsRef`** (unresolved) for the compiler's ManifestSource join. List/manifest overlay is **replace-not-merge** (instance wins wholesale). | ✓ (library layer) | **TTR-P-AMENDMENT**: the two overlays are different layers — the library does world-def↔world-def overlay; TTR-P's WorldBinder does `extendsRef`↔compiler-manifest overlay with `+functions` additive semantics on top. s2.2 fixture keeps `postgres-16` as a **bare** id (passes through) OR moves base types into a world-level `def engine` (in-model overlay). Record both options; s2.2 chooses. |
| 4 | s2.2 rides manifest *content* on `ResolvedEngine/Executor.manifest` | `manifest: Map<String, PropertyValue>` (parser value model), transported opaque (MD5) | ✓ (shape drift) | **FIX-HERE (doc)**: contracts §3 says "`JsonObject`-shaped". The library transports parser `PropertyValue`s, not a JSON tree. `WorldFingerprint.canonValue` shows the 1:1 JSON projection. Changelog note: manifest is `Map<String,PropertyValue>`; TTR-P Stage 2.2 owns the JSON interpretation. Consumable by kotlinx-serialization after that projection. |
| 5 | s1.3 WLD-004 vs s2.2 WLD-002 both = two-staging (id collision) | library has no ids (MD5) | ✓ | **TTR-P-AMENDMENT**: renumber on the TTR-P side; library unaffected (`StagingConflict` carries fields only). |
| 6 | `MetadataRefresher.tryRefresh()/forceRefresh(): RefreshOutcome` (contracts §2) | ported Ariadne `MetadataRefresher` — moved surface (see RM9). M4.1 drives it from `RefreshScheduler` (kantheon). | ✓ (moved surface) | **FIX-HERE (doc)**: pin the moved surface as final; contracts §2 changelog notes "signatures per the moved Ariadne surface (RM9)". No cross-repo break: M4 consumes exactly the ported shape. |
| 7 | `staging = null` legality vs s2.2 T2.2.7 deferred TTRP-WLD-003 | `ResolvedWorld.staging: ResolvedStorage?` — null when zero staging (legal; caller decides if absence is an error) | ✓ | OK. Confirmed the caller-side deferral works with a null field (MD5 — the library never errors on zero staging). |

## Consumer walk (s1.3 / s2.2 ttr-metadata steps)

| step | expectation | real API | ✓ |
|---|---|---|---|
| s1.3 pre-flight #2 | model-graph load from a `.ttrm` dir, qname queries, er2db traversal, world resolution, offline | `MetadataLoader`+`LocalFsStorage`; `MetadataQuery.{listObjects,getObject,resolve,search,graph,resolveArea,erToDb}`; `WorldResolver` | ✓ |
| s1.3 T1.3.3 | `WorldResolver.resolve(worldQname)` → engines/executors/storages/staging + hosts mapping | `world.WorldResolver` (see row 2) | ✓ |
| s1.3 T1.3.4 | kind-typed resolve → wrong-kind/not-found | `MetadataQuery.resolve(qname, expected): ResolveOutcome` (`Found/NotFound/KindMismatch/Ambiguous`) | ✓ |
| s1.3 T1.3.5 | er2db provenance for the ErRewriter | `MetadataQuery.erToDb(qname): ErBindingResult` (chain of `BindingStep{erQname,dbQname,definitionLocation}`) | ✓ |
| s2.2 T2.2.4 | WorldBinder rides `ResolvedWorld` + `extendsRef` | `ResolvedEngine/Storage.extendsRef` (see row 3) | ✓ |
| s2.2 T2.2.7 | staging inputs | `ResolvedWorld.staging` + `StagingConflict` (see row 7) | ✓ |

**Result:** no unadjudicated `✗`. FIX-HERE items (1, 4-doc, 6-doc) land in T2.2.5 as contracts
changelog entries; TTR-P-AMENDMENTs (2, 3, 5) are drafted above and applied per plan §6.
