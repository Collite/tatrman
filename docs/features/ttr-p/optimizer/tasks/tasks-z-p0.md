# Z-P0 — Scaffold & problem model

> Pre-flight: tracker gate checked. DoD: [`../plan.md`](../plan.md) §Z-P0. Check each box the moment its task is done.

## S1 · Modules & build {#s1}

Verify: `./gradlew :packages:kotlin:ttr-optimizer:test :packages:kotlin:ttr-optimizer-cpsat:test` green; `./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttr-optimizer:publishToMavenLocal` succeeds.

- [ ] **T1 (test first).** Create `packages/kotlin/ttr-optimizer/src/test/kotlin/org/tatrman/optimizer/SmokeTest.kt` — Kotest `FunSpec` with one test `"module wires"` asserting `PlacementStrength.DIRECTIVE.name == "DIRECTIVE"` (compile-level smoke; will fail until T3/T5). Same for `ttr-optimizer-cpsat` (`CpSatSmokeTest`, asserts `Loader` class is loadable — guard with `@EnabledIf` OS check helper, see S1-T6).
- [ ] **T2.** Create `packages/kotlin/ttr-optimizer/build.gradle.kts` by copying `packages/kotlin/ttr-parser/build.gradle.kts` and adapting: no antlr codegen block; deps = `kotlin-stdlib`, `org.jgrapht:jgrapht-core` (pin the version already used by the repo's version catalog, else latest 1.5.x), Kotest per parser module. Publishing block: artifact `ttr-optimizer`, POM name "TTR-P Optimizer", same repo URLs as siblings.
- [ ] **T3.** Create `packages/kotlin/ttr-optimizer/src/main/kotlin/org/tatrman/optimizer/Model.kt` with the **verbatim contracts §6 types**: `PlacementProblem`, `OpNode`, `DataEdge`, `PrecedenceEdge`, `EngineModel`, `Hint`, `RewriteChoice` (+ `RewriteKind` enum: `FILTER_PUSHDOWN, PROJECT_PRUNE, EAGER_AGG, MATERIALIZE_REUSE, MATERIALIZE_INDEX`), `ChoiceEffect`, `PlacementSolution`, `InfeasibilityReport`, `SolverBudget` (+ `Tier` enum `FAST, BALANCED, THOROUGH`), `Objective` (sealed: `Makespan`, `CostSum`), `PlacementStrength` enum. All ids = value classes over `String` (`NodeId`, `EdgeId`, `EngineId`, `ChoiceId`).
- [ ] **T4.** Create `packages/kotlin/ttr-optimizer-cpsat/build.gradle.kts`: depends on project `ttr-optimizer` + `com.google.ortools:ortools-java` (latest stable; single dependency — it pulls platform natives). No publishing until Z-P3 (add `publish = false` marker comment).
- [ ] **T5.** Register both modules in the root `settings.gradle.kts` (alphabetical, beside `ttr-parser`/`ttr-writer`); add both to the CI Kotlin test workflow where the sibling modules are listed.
- [ ] **T6.** Add `OrToolsSupport.kt` in `ttr-optimizer-cpsat` test sources: helper `orToolsAvailable(): Boolean` (try `Loader.loadNativeLibraries()`, catch `UnsatisfiedLinkError` → false) + Kotest `EnabledIf` annotation class `RequiresOrTools`. All cpsat tests use it — CI without natives skips, never fails.
- [ ] **T7.** Run the verify commands; fix until green; commit `Z-P0.S1: optimizer module scaffold`.

## S2 · Solver registry, knobs, diagnostics {#s2}

Verify: `./gradlew :packages:kotlin:ttr-optimizer:test` green.

- [ ] **T1 (tests first).** `RegistryTest.kt` (FunSpec), cases: `"picks first supporting backend in configured order"` (two fakes, first `supports()=false`) · `"noop solver returns pinned assignment unchanged"` (fully-pinned 3-node problem → solution.assignment == pins, gap 0.0) · `"registry with no supporting backend throws OptDiagnosticException(TTRP_OPT_010_ADJACENT)"` — write against not-yet-existing classes.
- [ ] **T2 (tests first).** `KnobsTest.kt`: parse a `[ttrp]` TOML fragment (fixture string) → `OptimizeConfig(optimize=OFF|ON|PINS_ONLY, profile="makespan", budget=BALANCED)`; cases for defaults (contracts §2), bad enum value → named error, CLI-flag override precedence (`fromCli(overrides, base)`).
- [ ] **T3 (tests first).** `DiagnosticsTest.kt`: every id in contracts §9 (`TTRP-OPT-001, 002, 010, 011, 020, 030, 031, 040`) exists in `OptDiagnostics`, has non-blank `message` template and non-blank `suggestion`, and severities match the table. Table-driven test — the fixture IS contracts §9.
- [ ] **T4.** Implement `PlacementSolver` interface + `SolverRegistry(backends: List<PlacementSolver>)` with the selection rule (contracts §6); implement `NoopSolver` (`id="noop"`, supports only fully-pinned problems).
- [ ] **T5.** Implement `OptimizeConfig` + TOML-fragment parsing (reuse the project-manifest parsing utility from the TTR-P front-half if merged; **if not yet available**, parse from a passed-in `Map<String,String>` and leave a `// Z-P6: wire to real [ttrp] loader` marker — the test uses the map form either way).
- [ ] **T6.** Implement `OptDiagnostics` (id, severity, template, suggestion) + `OptDiagnosticException`; wire `TTRP-OPT-010` ceiling check as a pure function `checkCeiling(problem)` (movable-node count ≥ 100 → diagnostic) with its own test case in `RegistryTest`.
- [ ] **T7.** Define the **anti-corruption seam**: interface `OpGraphSource` (in `org.tatrman.optimizer.input`) — the minimal read view Z needs from the front-half graph (nodes w/ kind + container annotation, data edges w/ schema ref, control edges, provenance ids). Provide `FixtureGraph` test implementation (hand-built hero graph skeleton, 9 nodes — see Z-P1.S3 fixture). Z compiles standalone against this seam until Z-P6 wires the real graph.
- [ ] **T8.** All tests green; check S1/S2 boxes in the tracker; commit `Z-P0.S2: problem model, registry, knobs, diagnostics`.
