# Tasks · M4 · Stage 4.1 — Kantheon swap + delete

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Feature decisions MD1–MD8 → `../../architecture/architecture.md` §8 · TTR-P decision IDs → `../../../ttr-p/design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.
>
> ⚠️ **This stage is EXECUTED IN THE KANTHEON REPO** (`Collite/kantheon`). All file paths below are kantheon paths unless prefixed `tatrman:`. This list maps **1:1 onto the kantheon arc-doc checklist** `docs/architecture/fork/ttr-metadata-adoption.md` §"Execution checklist (Stage M4.1)" — each task names its arc-checklist item(s); **tick the arc doc's box in the same commit as this list's box**. The gRPC contract is FROZEN throughout (MD7): `org.tatrman.ariadne.v1` proto, all 14 RPC behaviors, ports 7260/7261/7262, `METADATA_GIT_*` env, ariadne-mcp — the swap must be invisible outside the service.

## Stage deliverable

Ariadne runs on `org.tatrman:ttr-metadata` + `:ttr-metadata-git` (v0.1.x from GitHub Packages); the moved core packages and their ≈24 moved specs are **deleted** from `services/ariadne`; `MetadataServiceImpl` bodies are proto conversion + `MetadataQuery`/library delegation (MD2); `RefreshScheduler` drives the library `MetadataRefresher`; the remaining grpc-layer Kotest suite is green **unchanged**; the Jib image runs on local K3s and ariadne-mcp smoke passes; a `git grep` drift guard proves no core copy remains (contracts §7 drift rule).

## Pre-flight (all must pass before T4.1.1)

- [ ] **Arc-checklist item 1:** `kotlin-metadata/v0.1.0` visible on GitHub Packages (github.com/Collite/tatrman → Packages: `ttr-metadata`, `ttr-metadata-git`) — tatrman M2.2 T2.2.6 done. Consumer PAT configured per kantheon conventions: `gpr.user`/`gpr.token` (`read:packages`) in `~/.gradle/gradle.properties` — the same `gpr.*` pair `settings.gradle.kts` already uses for `org.tatrman:ttr-parser` (verify: `./gradlew :services:ariadne:dependencies --configuration compileClasspath | grep ttr-parser` resolves today).
- [ ] **Core freeze active since M1** (arc doc §"Core freeze"): `git log --oneline --since=<M1-start-date> -- services/ariadne/src/main/kotlin/org/tatrman/kantheon/ariadne/{model,source,reconcile,resolve,graph,search,registry,export,refresh}` — any commits found must carry `// dual-landed: <tatrman commit>` notes. List violations under §Blockers before proceeding.
- [ ] Kantheon baseline green: `just build-kt ariadne && just test-kt ariadne`.
- [ ] tatrman M2 DONE: `notes-api-review.md` seed row 6 (refresher signature) has a disposition — read it; T4.1.6 depends on the pinned shape.

## Tasks

### T4.1.1 · Core-freeze reconciliation — diff frozen packages between repos BEFORE deleting

- [ ] For each to-be-deleted package, diff kantheon's tree against the tatrman library source it became (accounting for the package rename `org.tatrman.kantheon.ariadne.*` → `org.tatrman.ttr.metadata.*`):
  ```bash
  # from a dir containing both checkouts; repeat per package: model source reconcile resolve graph search registry export refresh
  diff -r --ignore-matching-lines='^package \|^import ' \
    kantheon/services/ariadne/src/main/kotlin/org/tatrman/kantheon/ariadne/model \
    tatrman/packages/kotlin/ttr-metadata/src/main/kotlin/org/tatrman/ttr/metadata/model
  ```
- [ ] Every hunk must be explainable as (a) the M1 rename/reshape, (b) a tatrman-side M2 improvement, or (c) a **dual-landed bugfix** (`// dual-landed:` note present on the kantheon side). Any kantheon-side change NOT present in the library = unreconciled drift → land it as a tatrman fix and wait for a `kotlin-metadata/v0.1.x` patch (drift rule: fixes ship as library releases, never re-fork) — record under §Blockers if this forces a version bump.
- [ ] Record the reconciliation outcome (clean / list of dual-landed commits absorbed) in the arc doc under a new "Freeze reconciliation" note line.
  - **Verify:** diff output reviewed and archived in the PR description; §Blockers empty or the patch release pinned instead of v0.1.0 in T4.1.2.

### T4.1.2 · Pin artifacts — `gradle/libs.versions.toml` + Ariadne build (arc-checklist item 2)

- [ ] `gradle/libs.versions.toml`: add
  ```toml
  # [versions]
  tatrman-ttr-metadata = "0.1.0"        # or the T4.1.1 patch version
  # [libraries]
  tatrman-ttr-metadata     = { module = "org.tatrman:ttr-metadata",     version.ref = "tatrman-ttr-metadata" }
  tatrman-ttr-metadata-git = { module = "org.tatrman:ttr-metadata-git", version.ref = "tatrman-ttr-metadata" }
  ```
  (beside the existing `tatrman-ttr-parser/writer/semantics` rows; same GitHub Packages feed — no `settings.gradle.kts` change needed.)
- [ ] `services/ariadne/build.gradle.kts`: add `implementation(libs.tatrman.ttr.metadata)` + `implementation(libs.tatrman.ttr.metadata.git)`. Remove `implementation(libs.jgrapht.core)`, `implementation(libs.jgit)`, `implementation(libs.apache.commons.compress)` — jgrapht rides `ttr-metadata` transitively, jgit/commons-compress ride `-git` (MD3); Ariadne must not re-declare them (that would mask a library packaging bug). Keep ttr-parser/writer/semantics lines only if grpc-layer code still imports them directly (check with `git grep`, drop if not — ttr-metadata re-exports them as `api`).
- [ ] Tick arc-checklist item 2.
  - **Verify:** `./gradlew :services:ariadne:dependencies --configuration runtimeClasspath | grep -E "ttr-metadata|jgit|jgrapht"` shows both artifacts resolving from the feed and jgit/jgrapht appearing only transitively. (Build will not compile yet — that is T4.1.3.)

### T4.1.3 · Import rewrite + delete moved packages and specs (arc-checklist item 3)

- [ ] Rewrite imports across `services/ariadne/src` (BSD sed shown; GNU sed drops the `''`):
  ```bash
  find services/ariadne/src -name '*.kt' -exec sed -i '' -E \
    's/org\.tatrman\.kantheon\.ariadne\.(model|source|reconcile|resolve|graph|search|registry|export)\./org.tatrman.ttr.metadata.\1./g; s/org\.tatrman\.kantheon\.ariadne\.refresh\.MetadataRefresher/org.tatrman.ttr.metadata.refresh.MetadataRefresher/g' {} +
  # MetadataExportRoutes STAYS kantheon — revert its rewritten references:
  find services/ariadne/src -name '*.kt' -exec sed -i '' \
    's/org\.tatrman\.ttr\.metadata\.export\.MetadataExportRoutes/org.tatrman.kantheon.ariadne.export.MetadataExportRoutes/g' {} +
  ```
  ⚠️ Before running, confirm the library's actual package layout against the artifact (`unzip -l ~/.gradle/caches/**/ttr-metadata-0.1.*.jar | grep -o 'org/tatrman/ttr/metadata/[a-z]*' | sort -u`) — adjust the mapping if M1 reshaped sub-packages (e.g. `query/MetadataQuery`).
- [ ] Delete moved **main** code (keep-list in parentheses is exhaustive — everything else in the dir goes):
  - `services/ariadne/src/main/kotlin/org/tatrman/kantheon/ariadne/model/` — all
  - `.../source/` — all (`Source.kt`, `BuiltinStockSource.kt`, `FallbackSource.kt`, `GitArchiveStorage.kt`, `LoadIssueLogging.kt`)
  - `.../reconcile/` — all (`ModelReconciler.kt`)
  - `.../resolve/` — all (`ReferenceResolutionPass.kt`, `Resolution.kt`, `DrillMapValidator.kt`, `PublishedResolverAdapter.kt`)
  - `.../graph/` — all (`ModelGraph.kt`, `TraverseEdgesHandler.kt` — core moved; if a proto-conversion sliver of TraverseEdgesHandler must survive, it moves INTO `grpc/MetadataServiceImpl.kt` in T4.1.5, not stay here)
  - `.../search/` — all (`SearchAlgorithm.kt`, `SearchIndexHolder.kt`, `SearchPostProcessor.kt`, `IndexableObjects.kt`, `all/`, `keyword/`, `regex/`, `substring/`)
  - `.../registry/` — all (`MetadataRegistry.kt`)
  - `.../export/` — `GraphDotExporter.kt`, `ModelToDefinitions.kt`, `TtrWriter.kt` (**KEEP `MetadataExportRoutes.kt`** — Ktor stays, contracts §7)
  - `.../refresh/MetadataRefresher.kt` (**KEEP `RefreshScheduler.kt`**)
- [ ] Delete moved **specs** under `services/ariadne/src/test/kotlin/org/tatrman/kantheon/ariadne/` — they run in tatrman since M1 (the 24 files; the arc doc's "≈19" undercounted — reconciled roster below, cross-check against what tatrman's ttr-metadata test tree actually contains before deleting, `ls tatrman:packages/kotlin/ttr-metadata*/src/test`):
  `A5DiagnosticsSpec.kt`, `ModelTtrLoadSpec.kt`, `export/DbErCncSplitSpec.kt`, `export/ExportRoundTripSpec.kt`, `export/GraphDotExporterSpec.kt`, `export/InlineMappingExportSpec.kt`, `export/MetadataExportPipelineSpec.kt`, `export/ModelToDefinitionsSpec.kt`, `graph/TraverseEdgesHandlerSpec.kt` (†), `refresh/MetadataRefresherSpec.kt`, `resolve/DrillMapValidatorSpec.kt`, `resolve/ResolutionIntegrationSpec.kt`, `search/AllAlgorithmAndPostProcessSpec.kt`, `search/KeywordAlgorithmSpec.kt`, `search/RegexAlgorithmSpec.kt`, `search/SearchScaffoldingSpec.kt`, `search/SubstringAlgorithmSpec.kt`, `source/FallbackSourceSpec.kt`, `source/GitArchiveStorageSpec.kt` (→ lives in `ttr-metadata-git`), `source/InlineMappingEquivalenceSpec.kt`, `source/InlineMappingSynthesisSpec.kt`, `source/PackageResolutionSpec.kt`, `source/StockRoleResolutionSpec.kt`, `source/V21SamplesSpec.kt`.
  († `TraverseEdgesHandlerSpec` is proto-typed: delete only if tatrman ported its cases; otherwise keep a slimmed RPC-level `grpc/TraverseEdgesSpec` — decide from the tatrman tree, note the outcome in the PR.)
- [ ] **STAYING specs — the grpc-layer suite (do NOT touch):** `MetadataQuerySpec.kt` (ListQueries/GetQuery RPC), `MetadataServiceFixtureSpec.kt`, `Phase2_2ExpressivenessSpec.kt`, `QueryParseWorkerSpec.kt`, `grpc/GetModelSpec.kt`, `grpc/GetObjectColumnSearchHintsSpec.kt`, `grpc/ListObjectsFuzzyAttributeMappingSpec.kt`, `grpc/ListObjectsFuzzyOnlyFilterSpec.kt`, `grpc/ListObjectsFuzzyOnlyFixtureSpec.kt`, `grpc/ListObjectsPackageFilterSpec.kt`, `grpc/PageTokenCodecSpec.kt`, `grpc/ResolveAreaSpec.kt`, `search/SearchRpcSpec.kt`, `refresh/RefreshSchedulerSpec.kt` (14 files — this is the adoption gate suite, contracts §7).
- [ ] Fix straggler references (`Application.kt` wiring, `parse/` worker imports, `MetadataExportRoutes.kt` imports of the now-library export pipeline) until the module compiles. Tick arc-checklist item 3.
  - **Verify:** `just build-kt ariadne` compiles; `git status` shows only deletions + import rewrites; staying-spec list untouched (`git diff --stat -- '*Spec.kt' | grep -v deleted` empty for the 14).

### T4.1.4 · `MetadataServiceImpl` slimming, group A — GetModel / GetObject / GetSnapshot / ListObjects / ListRoles / GetRolesForEntity (arc-checklist item 4, part 1)

- [ ] Incremental, NOT big-bang: rewrite RPC bodies group by group with the grpc suite green after each group. Group A = the pure snapshot-read RPCs: `getModel`, `getObject`, `getSnapshot`, `listObjects` (filtering/paging/fuzzy-attribute logic now lives in library `MetadataQuery` per MD2 — the M1.2 pull-down; the RPC keeps only proto decode → `MetadataQuery.listObjects(filter, page)` → proto encode + `PageTokenCodec` wire tokens), `listRoles` + `getRolesForEntity` (grouped here because they touch only snapshot cnc-roles + mappings — plain reads, same shape).
- [ ] Rules: proto types never cross into library calls (convert at the boundary); `PageTokenCodec` stays wire-level in `grpc/`; response messages (`notReadyMessage`, `object_not_found` codes) byte-identical — the specs enforce MD7.
  - **Verify (gate A):** `./gradlew :services:ariadne:test --tests '*GetModelSpec' --tests '*GetObjectColumnSearchHintsSpec' --tests '*ListObjects*' --tests '*Phase2_2ExpressivenessSpec' --tests '*MetadataServiceFixtureSpec' --no-daemon` — green, zero spec edits.

### T4.1.5 · Slimming, group B — Search, then TraverseEdges / ResolveArea (arc-checklist item 4, part 2)

- [ ] `search`: body → `MetadataQuery.search(query)` (algorithm selection keyword|regex|substring|all + post-processing are library-side since M1) + proto conversion; the service keeps only the `SearchAlgorithmRegistry`/`SearchIndexHolder` **construction wiring** if the library exposes them as injectables — prefer the library's own defaults, delete kantheon wiring that duplicates them. Gate: `./gradlew :services:ariadne:test --tests '*SearchRpcSpec' --no-daemon` green.
- [ ] `traverseEdges`: body → library `MetadataQuery.graph()` traversal (the moved TraverseEdgesHandler core) + proto edge/direction conversion (absorb any surviving conversion sliver from T4.1.3 here). `resolveArea`: body → `MetadataQuery.resolveArea(name)` + conversion. Gate: `./gradlew :services:ariadne:test --tests '*ResolveAreaSpec' --no-daemon` green (plus the TraverseEdges spec per the T4.1.3 † outcome).
  - **Verify (gate B):** both gates above green; cumulative re-run of gate A still green.

### T4.1.6 · Slimming, group C — GetStatus / Refresh / ValidateModel / ListQueries / GetQuery + RefreshScheduler → library refresher (arc-checklist items 4 part 3 + 5)

- [ ] `getStatus`, `validateModel`: read `RegistrySnapshot` + the library's finalized `LoadIssue` taxonomy (tatrman M2.2 T2.2.3) — map categories to the existing `ResponseMessage` codes (`source_load_warning`, …) so responses stay byte-compatible (MD7).
- [ ] `listQueries` / `getQuery`: stay thin over `snapshot.model.queries` + the kantheon-side `QueryParseState` overlay (`liveParseStatus`) — QueryParseWorker/`parse/` is the ttr-translator arc, untouched here.
- [ ] `refresh` RPC + `refresh/RefreshScheduler.kt`: drive the **library** `MetadataRefresher` per the shape pinned in tatrman `notes-api-review.md` seed row 6 (contracts §2 spells `tryRefresh()/forceRefresh(): RefreshOutcome`; the pre-swap kantheon signature was `refresh(sourceId, force): List<…>` with `snapshotSwapped`/`success` fields — adapt `RefreshScheduler`'s loop and `RefreshSchedulerSpec`'s stubs to the library shape; scheduling policy, interval config `metadata.refresh-scheduler.interval-seconds`, and the survive-a-bad-tick catch stay kantheon, mechanism is library). Tick arc-checklist items 4 and 5.
  - **Verify (gate C):** `./gradlew :services:ariadne:test --tests '*MetadataQuerySpec' --tests '*RefreshSchedulerSpec' --tests '*QueryParseWorkerSpec' --no-daemon` green; then the FULL suite: `just test-kt ariadne` — all 14 staying specs green, zero behavioral edits to specs (adapter-stub edits from the refresher shape are the only permitted diff — call them out in the PR).

### T4.1.7 · Build, deploy to local K3s, smoke via ariadne-mcp (arc-checklist items 6 + 7)

- [ ] `just build-kt ariadne && just test-kt ariadne` — green from clean (`--no-daemon` is baked into the recipes).
- [ ] `just deploy-kt ariadne` (Jib image `ariadne:dev`, temurin-21-jre, ports 7260/7261 — frozen) onto local K3s; pod healthy; logs show the model loading via the library (`MetadataRefresher`/registry swap lines) and `METADATA_GIT_*` env still honored (GitArchiveStorage now from `ttr-metadata-git`).
- [ ] Smoke through `tools/ariadne-mcp` (deployed via `just deploy-kt ariadne-mcp`, or run locally against the pod) — **name the calls, check each returns non-error content backed by the frozen proto**:
  - `get_model` (→ GetModel)
  - `get_tables` + `get_entities` (→ ListObjects, db + er kinds)
  - `get_table_details` on `erp`-side table (→ GetObject)
  - `resolve_area` (→ ResolveArea)
  - `list_roles` + `get_roles_for_entity` (→ ListRoles / GetRolesForEntity)
  - **Search + Refresh have no ariadne-mcp tool** (arc-doc gap — recorded in §Blockers-adjacent note, not a blocker): smoke them via grpcurl against port 7260 with reflection enabled (`grpc.reflection-enabled=true` in the pod config): `grpcurl -plaintext localhost:7260 org.tatrman.ariadne.v1.AriadneService/Search` (keyword query) and `.../Refresh` (`force: false`) — both return OK with the post-swap model.
- [ ] Tick arc-checklist items 6 and 7.
  - **Verify:** all smoke calls listed above pasted into the PR with their (truncated) responses; pod restart count 0.

### T4.1.8 · Drift guard + docs close-out (arc-checklist items 8 + 9)

- [ ] Drift guard — all THREE greps must come back empty, wire them into the PR description and the arc doc's review rule:
  ```bash
  git grep -nE "org\.tatrman\.kantheon\.ariadne\.(model|source|reconcile|resolve|graph|search|registry)\b" -- services/ariadne
  git grep -nE "class (ModelReconciler|MetadataRegistry|ModelGraph|SearchIndexHolder|LocalFsStorage|ClasspathStorage|GitArchiveStorage|MetadataRefresher|ReferenceResolutionPass)\b" -- services/ariadne
  git grep -n "org.tatrman.kantheon.ariadne.export" -- services/ariadne | grep -v MetadataExportRoutes
  ```
- [ ] `services/ariadne/README.md`: rewrite the component description — Ariadne = gRPC facade + conversions + PageTokenCodec + QueryParseWorker + RefreshScheduler + Ktor/OTel/k8s over `org.tatrman:ttr-metadata(+-git)`; document the drift rule ("core fixes = library release, never re-fork"; PRs adding model/graph/search/resolution logic under `services/ariadne` get bounced to the library).
- [ ] `docs/architecture/fork/contracts.md`: record the library boundary — consumed artifacts + pinned version key `tatrman-ttr-metadata`, the frozen surface list (MD7), the deleted-package list, the staying-spec list (the 14), and the drift-guard greps as a review rule.
- [ ] Close the arc: tick items 8 + 9 in `docs/architecture/fork/ttr-metadata-adoption.md`; all nine checklist boxes now `[x]`; add a close-out line (date + `kotlin-metadata` version). Report DONE back to the tatrman plan (progress-phase doc, tatrman side).
  - **Verify:** three greps empty; `just test-kt ariadne` green one final time; arc-doc checklist fully ticked; `git log --oneline` shows the incremental gate structure (groups A/B/C as separate commits, kantheon commit conventions).

## Definition of DONE (stage)

- [ ] Ariadne builds and its full remaining Kotest suite (the 14 grpc-layer specs) is green against published `org.tatrman:ttr-metadata:0.1.x` + `:ttr-metadata-git:0.1.x` — zero spec behavior edits (MD7 byte-compatibility gate; refresher-stub adaptation only).
- [ ] All moved main packages + the 24-spec deletion roster gone; `MetadataExportRoutes.kt`, `RefreshScheduler.kt`, `grpc/`, `parse/`, `Application.kt` remain.
- [ ] `MetadataServiceImpl` = conversion + delegation for all 14 RPCs; each group landed behind its own green gate.
- [ ] Jib image runs on local K3s (ports/env frozen); ariadne-mcp + grpcurl smoke roster passes.
- [ ] Drift-guard greps empty; README + `docs/architecture/fork/contracts.md` record the boundary; arc-doc checklist 9/9 ticked.
- [ ] Plan.md M4 DONE bar satisfied (tatrman side updated).

## Blockers

_(empty — coder records here)_

## References

- **Arc doc (the 1:1 checklist):** `kantheon:docs/architecture/fork/ttr-metadata-adoption.md` — items 1→pre-flight, 2→T4.1.2, 3→T4.1.3, 4→T4.1.4/5/6, 5→T4.1.6, 6/7→T4.1.7, 8/9→T4.1.8.
- **Contracts:** tatrman `docs/ttr-metadata/architecture/contracts.md` §7 (adoption contract: frozen surface, delete list, stay list, gate) · §1 (artifact coordinates/lockstep) · §2 (`MetadataQuery`, `MetadataRefresher` shapes).
- **MD decisions:** MD1 (arrow stays one-way), MD2 (facade = conversion + delegation), MD3 (`-git` split; jgit off other classpaths), MD7 (frozen gRPC contract — Golem/Shem/ariadne-mcp untouched).
- **Kantheon mechanics:** `justfile` (`build-kt`/`test-kt` → `./gradlew :services:ariadne:{build,test} --no-daemon` via `_resolve`; `deploy-kt ariadne` → Jib → K3s) · `services/ariadne/build.gradle.kts` (dep baseline this stage edits) · `gradle/libs.versions.toml` rows 293–295 (the ttr-parser consumption pattern being cloned) · `settings.gradle.kts` GitHub Packages block (`gpr.*`).
- **Smoke surface:** `tools/ariadne-mcp/.../Tools.kt` tool names (`get_model`, `get_tables`, `get_entities`, `get_table_details`, `resolve_area`, `list_roles`, `get_roles_for_entity`, …); Search/Refresh via grpc reflection (`Application.kt` `grpc.reflection-enabled`).
- **tatrman-side inputs:** `tasks-m2-s2.2-fingerprint-publish.md` T2.2.4 seed row 6 (refresher shape) + T2.2.6 (the artifact this stage pins).
