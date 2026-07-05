# Tasks · M1 · Stage 1.1 — Module scaffold + typed model + sources

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Feature decisions MD1–MD8 → `../../architecture/architecture.md` §8 · TTR-P decision IDs → `../../../ttr-p/design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

Two new Gradle modules — `:packages:kotlin:ttr-metadata` and `:packages:kotlin:ttr-metadata-git` — with Ariadne's `model/` + `source/` (minus `GitArchiveStorage`, which lands in `-git`) + `reconcile/` ported under `org.tatrman.ttr.metadata.*`, their nine core Kotest specs green against the **in-repo** ttr-parser/writer/semantics, a dependency-rules gate proving the core module carries no ktor/grpc/otel/jgit, and `LocalFsStorage` proven against a tatrman-style model-repo fixture (modeler.toml + packaged `.ttrm` files + the M0 world doc) alongside a minimal Ariadne `model-ttr` seed fixture.

**Extraction ground rules (apply to every task):**
- Copy from `/Users/bora/Dev/collite-gh/kantheon/services/ariadne/...` — the coder has both repos checked out. **Never edit kantheon in M1** (kantheon changes are M4; the Ariadne core freeze is in force — plan §Cross-cutting).
- PR description carries a provenance note: source repo/paths + the kantheon commit hash copied from (`git -C ~/Dev/collite-gh/kantheon rev-parse HEAD` at copy time).
- Package rename applied on copy (mapping table in T1.1.3); spec files move with their code and keep their class names.
- **Known verbatim-blocker (recon 2026-07-05, record deviations in the PR):** Ariadne builds against published `org.tatrman:ttr-*:0.8.4` *and* kantheon's `:shared:proto`. The moved code therefore needs two mechanical adaptations that architecture §3's "seams are clean" undersells: (a) `org.tatrman.plan.v1.QualifiedName` / `SchemaCode` / `schemaCodeToToken` (proto types) are replaced by library-owned types (T1.1.3); (b) the in-repo ttr-parser is at grammar 4.x — `ParseResult.schemaDirective` is now `modelDirective`, `.ttr` files are `.ttrm`, and `Mapping*`→`Binding*` AST names apply (T1.1.5). Behavior stays identical; the moved specs are the referee.

## Pre-flight (all must pass before T1.1.1)

- [ ] **KANTHEON BASELINE GREEN (added 2026-07-05, RM15): `just test-kt ariadne` passes in the kantheon repo at the pinned modeler 0.8.4.** As of 2026-07-05 it does NOT: the 0.8.4 re-point (kantheon `1eaaac8`) compiles after the `modelDirective` fix but the suite is red — the test fixtures/specs still speak pre-4.0 TTR (12 fixture files under `src/test/resources/{fixture-model, fixture-packages, fixture-packages-noimport, v2-1-samples}` use `schema <code>` directives that grammar 4.0 no longer parses; ~9 specs embed inline pre-4.0 TTR snippets; qname-form drift from the qname-redesign needs review). **That review-and-fix is kantheon-side work and must land BEFORE this stage starts** — see the arc doc's "Pre-arc baseline" checklist (`kantheon/docs/architecture/fork/ttr-metadata-adoption.md`). Rationale: T1.1.1 ports specs "with assertions unchanged"; a red baseline would port unknown-good assertions and launder kantheon failures into the library. If red, STOP → §Blockers.
- [ ] **M0 DONE** (phase pre-flight): `tests/conformance/fixtures/31-world.ttrm` exists and `./gradlew :packages:kotlin:ttr-parser:test --tests '*WorldParseSpec*'` is green — M1 fixtures include world docs even though resolution lands in M2 (plan M1 note).
- [ ] tatrman baseline green: `./gradlew build` and `pnpm -r test`.
- [ ] kantheon readable at `/Users/bora/Dev/collite-gh/kantheon`; `ls /Users/bora/Dev/collite-gh/kantheon/services/ariadne/src/main/kotlin/org/tatrman/kantheon/ariadne/` shows `model source reconcile resolve graph search registry refresh export grpc parse`.
- [ ] Announce the Ariadne core freeze in the kantheon repo channel/arc doc (plan §Cross-cutting: no new core features kantheon-side during M1–M4; bugfixes land twice with a tracking note).

## Tasks

### T1.1.1 · Ported-spec roster, new-spec skeletons, fixtures (TEST-FIRST)

- [ ] Copy the stage's spec files (paths relative to `services/ariadne/src/test/kotlin/org/tatrman/kantheon/ariadne/` → `packages/kotlin/ttr-metadata/src/test/kotlin/org/tatrman/ttr/metadata/`); apply the T1.1.3 package rename in headers/imports but change **no assertions**:

  | kantheon spec | tatrman target | notes |
  |---|---|---|
  | `ModelTtrLoadSpec.kt` | `…/metadata/ModelTtrLoadSpec.kt` | scope narrowed to the seed subset (see fixtures below); provenance header added |
  | `A5DiagnosticsSpec.kt` | `…/metadata/A5DiagnosticsSpec.kt` | package/import diagnostics through reconcile |
  | `source/FallbackSourceSpec.kt` | `…/metadata/source/FallbackSourceSpec.kt` | |
  | `source/InlineMappingEquivalenceSpec.kt` | `…/metadata/source/InlineMappingEquivalenceSpec.kt` | |
  | `source/InlineMappingSynthesisSpec.kt` | `…/metadata/source/InlineMappingSynthesisSpec.kt` | |
  | `source/PackageResolutionSpec.kt` | `…/metadata/source/PackageResolutionSpec.kt` | |
  | `source/StockRoleResolutionSpec.kt` | `…/metadata/source/StockRoleResolutionSpec.kt` | |
  | `source/V21SamplesSpec.kt` | `…/metadata/source/V21SamplesSpec.kt` | |
  | `source/GitArchiveStorageSpec.kt` | `packages/kotlin/ttr-metadata-git/src/test/kotlin/org/tatrman/ttr/metadata/git/GitArchiveStorageSpec.kt` | -git module (MD3) |

  Staying in kantheon (do NOT copy): `MetadataQuerySpec`, `MetadataServiceFixtureSpec`, `Phase2_2ExpressivenessSpec`, `QueryParseWorkerSpec`, all of `grpc/`, `refresh/RefreshSchedulerSpec`, `search/SearchRpcSpec` (grpc-layer / wrapper suite — contracts §7). The remaining core specs (resolve/graph/search/refresh/export) move in **Stage M1.2**, not here.
- [ ] Copy the fixture trees these specs reference, verbatim: `services/ariadne/src/test/resources/{fixture-model, fixture-packages, fixture-packages-noimport, v2-1-samples, model-ttr-areas}` → `packages/kotlin/ttr-metadata/src/test/resources/` (same names). (`fixture-fuzzy` moves in M1.2 with search; `fixture-yaml` is legacy and stays.)
- [ ] **Fixture A — Ariadne model-ttr seed subset:** copy ONE package tree from `services/ariadne/src/main/resources/model-ttr/` (pick `obchodni_doklady/` plus whatever cross-referenced package it imports — inspect its `import` lines and include the transitive minimum) → `packages/kotlin/ttr-metadata/src/test/resources/model-ttr-seed/`, with a `README.md` provenance note (source path + kantheon commit). Adapt the copied `ModelTtrLoadSpec` to load this subset (assertions on "no resolution errors" + object counts recomputed for the subset — document the recount in the spec header).
- [ ] **Fixture B — tatrman-style model repo** at `packages/kotlin/ttr-metadata/src/test/resources/tatrman-repo/`: `modeler.toml` (minimal `[project]`-style stub matching architecture-doc §5 conventions), `models/erp/db.ttrm` (`model db` — tables `accounts`, `SALES_TXN` with the s1.3 column rosters), `models/erp/er.ttrm` (`model er` + binding defs per s1.3), `models/acme/worlds.ttrm` (= copy of `tests/conformance/fixtures/31-world.ttrm`, path-adjusted package). This is the seed the M2 shared fixture home (contracts §8) will grow from — keep qnames aligned with s1.3's roster.
- [ ] New spec skeletons (red): `…/metadata/source/TatrmanRepoFixtureSpec.kt` — `LocalFsStorage("repo", <tatrman-repo path>)` → `FileBasedSource` → snapshot: lists exactly the 3 `.ttrm` files, loads tables/entities/mappings, **world file parses with zero errors** (its defs may land as unmodeled kinds until M2 — assert no *errors*, not full typing); `…/metadata/DependencyRulesSpec.kt` — see T1.1.2.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test` fails to **compile** (main sources don't exist yet) — that is the expected red; commit fixtures + specs first.

### T1.1.2 · Gradle scaffold: two modules, catalog pins, dependency-rules gate

- [ ] `gradle/libs.versions.toml` additions (mirror kantheon's pins for behavior parity — `kantheon/gradle/libs.versions.toml` lines 114–117; do not chase newer versions in this stage):

  ```toml
  # [versions]
  jgrapht = "1.5.2"
  jgit = "6.8.0.202311291450-r"
  commons-compress = "1.24.0"
  # [libraries]
  jgrapht-core            = { module = "org.jgrapht:jgrapht-core",             version.ref = "jgrapht" }
  jgit                    = { module = "org.eclipse.jgit:org.eclipse.jgit",    version.ref = "jgit" }
  apache-commons-compress = { module = "org.apache.commons:commons-compress",  version.ref = "commons-compress" }
  ```
- [ ] `settings.gradle.kts`: append `include(":packages:kotlin:ttr-metadata")` and `include(":packages:kotlin:ttr-metadata-git")`.
- [ ] `packages/kotlin/ttr-metadata/build.gradle.kts` — full content (POM/publishing block copied from `ttr-parser/build.gradle.kts` lines 72–112 with name/description swapped to "TTR Metadata"):

  ```kotlin
  plugins {
      base
      alias(libs.plugins.kotlin.jvm)
      alias(libs.plugins.ktlint)
      `java-library`
      `maven-publish`
  }

  kotlin { jvmToolchain(21) }
  tasks.test { useJUnitPlatform() }

  dependencies {
      // Re-exported: consumers get one coherent ttr-* set (contracts §1).
      api(project(":packages:kotlin:ttr-parser"))
      api(project(":packages:kotlin:ttr-writer"))
      api(project(":packages:kotlin:ttr-semantics"))
      implementation(libs.jgrapht.core)
      implementation(libs.slf4j.api)
      implementation(libs.kotlinx.coroutines.core)   // MetadataRefresher (M1.2) — add the catalog entry if absent

      testImplementation(libs.bundles.kotest)
      testImplementation(libs.mockk)
  }

  // MD3 / architecture §2.1: the core stays off heavy classpaths. Gate, not convention.
  val bannedDependencyGroups = setOf(
      "io.ktor", "io.grpc", "io.opentelemetry", "org.eclipse.jgit",
      "org.apache.commons" /* commons-compress rides -git only */, "com.google.protobuf",
  )
  val dependencyRules = tasks.register("dependencyRules") {
      val runtime = configurations.runtimeClasspath
      doLast {
          val offenders = runtime.get().resolvedConfiguration.resolvedArtifacts
              .map { it.moduleVersion.id.group }
              .filter { g -> bannedDependencyGroups.any { g == it || g.startsWith("$it.") } }
              .distinct()
          check(offenders.isEmpty()) { "Banned groups on ttr-metadata runtimeClasspath: $offenders (MD3 / architecture §2.1)" }
      }
  }
  tasks.named("check") { dependsOn(dependencyRules) }
  ```

  (`kotlinx-coroutines-core` needs a catalog entry — add `kotlinx-coroutines = "1.9.0"` + library alias, matching kantheon's pin.)
- [ ] `DependencyRulesSpec.kt` (belt to the task's braces): Kotest spec asserting the *test* runtime also carries none of the banned groups — scan `System.getProperty("java.class.path")` entries for `/ktor`, `/grpc`, `/opentelemetry`, `/org.eclipse.jgit`, `/protobuf-java` path fragments and expect none. (The Gradle task guards the published POM; the spec guards the suite's own classpath.)
- [ ] `packages/kotlin/ttr-metadata-git/build.gradle.kts`: same plugin/publishing skeleton; deps:

  ```kotlin
  dependencies {
      api(project(":packages:kotlin:ttr-metadata"))   // implements the core ModelStorage SPI
      implementation(libs.jgit)
      implementation(libs.apache.commons.compress)
      implementation(libs.slf4j.api)
      testImplementation(libs.bundles.kotest)
  }
  ```
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:dependencies --configuration runtimeClasspath` shows only ttr-*, antlr-runtime, jgrapht, slf4j, coroutines, kotlin-stdlib; `./gradlew :packages:kotlin:ttr-metadata:dependencyRules` passes; both modules `compileKotlin` (empty `src/main` OK).

### T1.1.3 · Library-owned qname layer (the de-proto shim)

Everything downstream keys objects on kantheon's proto `org.tatrman.plan.v1.QualifiedName`. The library owns its replacements (contracts §2 presumes a library `QualifiedName`; consumers convert at their edges — Ariadne's grpc layer does proto↔library conversion in M4).

- [ ] `packages/kotlin/ttr-metadata/src/main/kotlin/org/tatrman/ttr/metadata/model/QualifiedName.kt`:
  - `enum class SchemaCode { UNSPECIFIED, DB, ER, MAP, QUERY, CNC /* + WORLD reserved, wired in M2 */ }` — mirror the value set of kantheon `shared/proto/.../plan/v1/SchemaCodes.kt` so `schemaCodeToToken`/`parseSchemaCode` port 1:1 (copy those two functions' bodies into this file, provenance-commented).
  - `data class QualifiedName(val schemaCode: SchemaCode, val namespace: String, val name: String)` + `fun dotted(): String` (the GH-#53 lowercase-token rendering, moved from `MetadataServiceImpl.dotted()` lines 149–154 — it belongs to the model, not the wire).
- [ ] Rename mapping table (apply with `sed`-style discipline on every copied file, this stage and M1.2):

  | from | to |
  |---|---|
  | `org.tatrman.kantheon.ariadne.model` | `org.tatrman.ttr.metadata.model` |
  | `org.tatrman.kantheon.ariadne.source` | `org.tatrman.ttr.metadata.source` |
  | `org.tatrman.kantheon.ariadne.reconcile` | `org.tatrman.ttr.metadata.reconcile` |
  | `org.tatrman.kantheon.ariadne.resolve` | `org.tatrman.ttr.metadata.resolve` (M1.2) |
  | `org.tatrman.kantheon.ariadne.graph` | `org.tatrman.ttr.metadata.graph` (M1.2) |
  | `org.tatrman.kantheon.ariadne.search` | `org.tatrman.ttr.metadata.search` (M1.2) |
  | `org.tatrman.kantheon.ariadne.registry` | `org.tatrman.ttr.metadata.registry` (M1.2) |
  | `org.tatrman.kantheon.ariadne.refresh` | `org.tatrman.ttr.metadata.refresh` (M1.2, `MetadataRefresher` only) |
  | `org.tatrman.kantheon.ariadne.export` | `org.tatrman.ttr.metadata.export` (M1.2) |
  | `import org.tatrman.plan.v1.QualifiedName` | `import org.tatrman.ttr.metadata.model.QualifiedName` |
  | `import org.tatrman.plan.v1.schemaCodeToToken` | `import org.tatrman.ttr.metadata.model.schemaCodeToToken` |
  - **Verify:** module compiles with a placeholder usage; `grep -rn "kantheon\|plan\.v1" packages/kotlin/ttr-metadata/src/main` → empty.

### T1.1.4 · Port `model/` (the typed model)

- [ ] Copy `services/ariadne/src/main/kotlin/org/tatrman/kantheon/ariadne/model/Model.kt` (564 lines: `Model`, `AreaRecord`, `ModelDescriptor`, `ModelVersion`, `ModelObject`, `Binding`, `SchemaContents`, `DbSchema/ErSchema/CncSchema`, `DbTable/DbView/DbColumn/DbProcedure/DbForeignKey`, `Entity/Attribute/Relation`, `Mapping` family, `Role`, `Er2CncRoleMapping`, `Query/ParseStatus`, `DrillMap`, `SearchHints`, `LocalizedText(List)`) → `…/ttr/metadata/model/Model.kt`; apply the rename table.
- [ ] Keep `ParseStatus` and `DrillMap` even though QueryParseWorker stays kantheon-side — Ariadne's worker mutates parse status *around* the library model (architecture §1.2); the data types are model, the worker is wrapper.
- [ ] Do NOT add `WorldSchema` to `SchemaContents` yet — that is M2 surface (contracts §2 marks it NEW); leave a `// M2: WorldSchema joins SchemaContents (contracts §2)` marker on the sealed interface.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:compileKotlin` green.

### T1.1.5 · Port `source/` (SPI + fs/classpath storages + file source + stock/fallback)

- [ ] Copy, rename, and adapt: `source/Source.kt` (1,136 lines — `ModelSource`, `SourceSnapshot`, `LoadWarning`, `ModelStorage`, `StorageFile`, `LoadedFile`, `LocalFsStorage`, `ClasspathStorage`, `FileBasedSource`, the `Reference` helper object), `source/BuiltinStockSource.kt`, `source/FallbackSource.kt`, `source/LoadIssueLogging.kt` → `…/ttr/metadata/source/`. **`source/GitArchiveStorage.kt` is NOT copied here** — T1.1.6.
- [ ] Grammar-4.x adaptations (each gets a `// M1 adaptation:` comment naming the kantheon original):
  - `TtrLoader.ParseResult.schemaDirective` → `modelDirective` (in-repo `TtrLoader.kt` line ~177); the directive's code/namespace field names moved with the 4.0 `schemaDirective→modelDirective` rename — align call sites in `FileBasedSource.load()`.
  - File extensions: Ariadne walks `listOf("ttr", "ttrm")` and `LocalFsStorage.fetchVersion()` hashes only `.ttr` (kantheon `Source.kt` line 172) — in tatrman the model extension is `.ttrm` only (CLAUDE.md v3.0 rename). Walk `listOf("ttrm")` and fix `fetchVersion()` to hash `.ttrm` (this is a live bug-in-waiting in the original — fetchVersion misses `.ttrm`-only changes; note it in the PR as a behavior fix, spec-cover it in `TatrmanRepoFixtureSpec`).
  - AST names already `Binding*` at 0.8.4 — expect no `Mapping*` residue, but `grep -n "MappingProperty\|BindingProperty" ` after copy to confirm against the in-repo parser API.
  - Contracts §2 spells the SPI `listFiles(glob: String)`; the real moved surface is `listFiles(extensions: List<String>, prefixes: List<String>)`. **Keep the moved surface** (contracts: "exact Kotlin spelling may drift; shape drift needs a changelog entry") and record a contracts-changelog entry in the PR: "§2 ModelStorage.listFiles — extensions/prefixes form (moved verbatim)".
- [ ] `BuiltinStockSource`: keep it pre-loading the stock CNC vocab via in-repo `ttr-semantics` `StockLoader` (same six roles: `fact`, `dimension`, `structural`, `master`, `transaction`, `bridge`).
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*FallbackSourceSpec*' --tests '*StockRoleResolutionSpec*' --tests '*V21SamplesSpec*' --tests '*InlineMapping*'` green (these exercise source/ without reconcile-heavy paths; the rest go green in T1.1.7).

### T1.1.6 · `ttr-metadata-git`: GitArchiveStorage

- [ ] Copy `source/GitArchiveStorage.kt` (127 lines; jgit clone/fetch + archive extraction behind `ModelStorage`) → `packages/kotlin/ttr-metadata-git/src/main/kotlin/org/tatrman/ttr/metadata/git/GitArchiveStorage.kt`; package `org.tatrman.ttr.metadata.git`; it implements `org.tatrman.ttr.metadata.source.ModelStorage` from the core artifact (MD3 — jgit/commons-compress stay off the compiler/Designer-server classpath; Ariadne is the only consumer).
- [ ] Apply the same `.ttrm` extension adaptation as T1.1.5 if the class hardcodes extensions; keep the `METADATA_GIT_*`-shaped constructor knobs exactly as copied (kantheon's env contract is frozen — MD7/contracts §7; the *library* takes plain constructor params, env reading stays in Ariadne's `Application.kt`).
- [ ] `GitArchiveStorageSpec.kt` (copied in T1.1.1) green — it builds a local temp git repo; no network.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata-git:test` green; `./gradlew :packages:kotlin:ttr-metadata:dependencyRules` still passes (proves jgit didn't leak into core).

### T1.1.7 · Port `reconcile/` + stage-green sweep

- [ ] Copy `reconcile/ModelReconciler.kt` (305 lines: `ModelReconciler`, `ReconciliationPolicy` with `OnMissing/OnMismatch/OnDuplicate/OnCycle`, `ReconciliationResult`) → `…/ttr/metadata/reconcile/`; adaptations: rename table + `schemaCodeToToken` from the T1.1.3 shim. Reconciler behavior (priority merge, protected qnames, post-load reference-resolution pass hookup) changes **not at all** — `A5DiagnosticsSpec`, `PackageResolutionSpec`, `ModelTtrLoadSpec` are the referee.
- [ ] Fill `TatrmanRepoFixtureSpec` (T1.1.1 skeleton): fixture-B repo loads end-to-end (`LocalFsStorage` → `FileBasedSource` → `ModelReconciler`), zero errors, `accounts`/`SALES_TXN`/entities/bindings present by qname, world file contributes zero errors, and `fetchVersion()` changes when a `.ttrm` is touched (the T1.1.5 fix).
- [ ] Stage sweep: full module suites + repo gates.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test :packages:kotlin:ttr-metadata-git:test` fully green (all 9 ported specs + 2 new) · `./gradlew build` green · `git -C /Users/bora/Dev/collite-gh/kantheon status --porcelain` is empty (kantheon untouched).

## Definition of DONE (stage)

- [ ] Both modules build in tatrman; `settings.gradle.kts` includes them; catalog pins for jgrapht/jgit/commons-compress/coroutines added.
- [ ] All 9 ported specs green under `org.tatrman.ttr.metadata.*` with unchanged assertions (8 in core, `GitArchiveStorageSpec` in `-git`).
- [ ] `dependencyRules` task + `DependencyRulesSpec` prove no ktor/grpc/otel/jgit/protobuf on the core module.
- [ ] `TatrmanRepoFixtureSpec` green: tatrman-style repo (incl. M0 world doc) loads offline via `LocalFsStorage`; model-ttr seed subset green via `ModelTtrLoadSpec`, provenance noted.
- [ ] `grep -rn "kantheon\|plan\.v1\|ariadne\.v1" packages/kotlin/ttr-metadata packages/kotlin/ttr-metadata-git --include='*.kt'` → empty.
- [ ] Kantheon repo byte-untouched; PR carries the provenance note + the two recorded deviations (de-proto shim, listFiles surface).

## Blockers

_(empty — coder records here)_

## References

- **MD1** extract to tatrman, Ariadne wraps · **MD3** `-git` split (jgit off core classpath) · **MD5** mechanism only — no manifest reading/diagnostic ids enter with the port · architecture §3 (extraction verdicts table: `model/`/`source/`/`reconcile/` = CORE; `GitArchiveStorage` = CORE, separate artifact).
- Contracts §1 (artifact deps: "ttr-parser, ttr-writer, ttr-semantics, jgrapht-core, slf4j-api. No Ktor, no gRPC, no OTel, no jgit."), §2 (storage SPI shapes — with the listFiles drift note this stage records).
- Kantheon sources: `services/ariadne/src/main/kotlin/org/tatrman/kantheon/ariadne/{model/Model.kt, source/Source.kt, source/{BuiltinStockSource,FallbackSource,LoadIssueLogging,GitArchiveStorage}.kt, reconcile/ModelReconciler.kt}`; dep pins `kantheon/gradle/libs.versions.toml` (jgrapht 1.5.2 · jgit 6.8.0.202311291450-r · commons-compress 1.24.0 · coroutines 1.9.0); proto qname helpers `kantheon/shared/proto/src/main/kotlin/org/tatrman/plan/v1/SchemaCodes.kt`.
- tatrman precedents: `packages/kotlin/ttr-parser/build.gradle.kts` (plugins/publishing/toolchain 21), `PUBLISHING.md` (no publishing wiring this stage — M1.2 owns it), CLAUDE.md §Kotlin artifacts.
- Fixture rosters: `docs/ttr-p/implementation/v1/tasks-p1-s1.3-resolution.md` T1.3.1 (db/er content of fixture B); M0 world fixture `tests/conformance/fixtures/31-world.ttrm`. The shared java-test-fixtures home is **M2.1 scope** (contracts §8) — fixture B seeds it.
- Grammar-4.x API deltas the port must absorb: `packages/kotlin/ttr-parser/src/main/kotlin/org/tatrman/ttr/parser/loader/TtrLoader.kt` (`modelDirective`), CLAUDE.md v3.0/v4.0 rename notes, kantheon catalog comment at `tatrman-modeler = "0.8.4"` (the `Mapping*→Binding*` migration already landed there).
