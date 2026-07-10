# Z 1.0 — Task Management (overall tracker)

> Master document for the optimizer implementation. Structure: **Plan → Phase → Stage**; each stage is a mini task list of 6–8 checkboxed tasks in its phase file. Source design: [`../architecture.md`](../architecture.md) · [`../contracts.md`](../contracts.md) · [`../plan.md`](../plan.md).

## Rules for the coder (read before every session)

1. **Check every checkbox immediately after finishing its task.** Both here (stage level) and in the phase file (task level). Never batch checkbox updates.
2. **TDD is mandatory.** Each stage lists test tasks *first* — write them, watch them fail, then implement. Do not reorder.
3. **Run the stage's verify command** before marking the stage done here.
4. Unit tests = single class/function; component tests = inter-class within the module. **No full E2E integration tests** — integration testing has a separate flow (`ttrp-conform` arrives in Z-P6 as a harness mode, not as tests you write earlier).
5. Conventions: Kotlin modules under `packages/kotlin/`, Kotest (mirror `packages/kotlin/ttr-parser`'s test style), package root `org.tatrman.optimizer.*`, ESM/TS rules don't apply here. Commit style `Z-P<n>.S<m>: <description>`.
6. If a task references a not-yet-existing TTR-P v1 component, the task says so and names the stub/adapter to use — do not invent couplings.
7. Numbers in golden tests (the W3 fixture family) are **exact expected values from the fixture**; if you change the fixture, recompute by hand first, then update the test — never the other way round.

## Pre-flight gate (verify before Z-P0)

- [ ] TTR-P v1 through P5 (A4 exit) is merged: front-half, capability manifests, movement synthesis, bundle, `ttrp-conform`.
- [ ] `org.tatrman:ttr-metadata` published with world resolution (repo/serverless backing is enough until Z-P6).
- [ ] Grammar-master window scheduled for the two TTR-M deltas (Z-P1.S1, Z-P5.S1).

## Phase & stage tracker

| Phase | Stage | Mini task list | Done |
|---|---|---|---|
| **Z-P0** scaffold | S1 modules & build | [`tasks-z-p0.md#s1`](./tasks-z-p0.md) | [ ] |
| | S2 problem model, registry, knobs, diagnostics | [`tasks-z-p0.md#s2`](./tasks-z-p0.md) | [ ] |
| **Z-P1** cost model | S1 TTR-M grammar: manifests, world, stats | [`tasks-z-p1.md#s1`](./tasks-z-p1.md) | [ ] |
| | S2 cost-shape & world model | [`tasks-z-p1.md#s2`](./tasks-z-p1.md) | [ ] |
| | S3 estimator & makespan evaluator (W3 golden) | [`tasks-z-p1.md#s3`](./tasks-z-p1.md) | [ ] |
| **Z-P2** solver floor | S1 problem builder (pins, together, capacity, ceiling) | [`tasks-z-p2.md#s1`](./tasks-z-p2.md) | [ ] |
| | S2 min-cut + HEFT | [`tasks-z-p2.md#s2`](./tasks-z-p2.md) | [ ] |
| **Z-P3** CP-SAT | S1 assignment model (cost-sum parity) | [`tasks-z-p3.md#s1`](./tasks-z-p3.md) | [ ] |
| | S2 makespan scheduling, budgets, gap | [`tasks-z-p3.md#s2`](./tasks-z-p3.md) | [ ] |
| **Z-P4** choice vars | S1 rewrite enumerator + legality | [`tasks-z-p4.md#s1`](./tasks-z-p4.md) | [ ] |
| | S2 choices in solvers + plan applier | [`tasks-z-p4.md#s2`](./tasks-z-p4.md) | [ ] |
| **Z-P5** surface | S1 grammar delta + model + Z-off degradation | [`tasks-z-p5.md#s1`](./tasks-z-p5.md) | [ ] |
| | S2 formatter, LSP, builder mapping | [`tasks-z-p5.md#s2`](./tasks-z-p5.md) | [ ] |
| **Z-P6** integration | S1 pipeline stage, snapshot ladder, bundle | [`tasks-z-p6.md#s1`](./tasks-z-p6.md) | [ ] |
| | S2 explain, conform mode, golden plans, docs | [`tasks-z-p6.md#s2`](./tasks-z-p6.md) | [ ] |

## Phase exit reviews

House cadence: after each phase, a `/review` pass verifies the phase's Definition of DONE ([`../plan.md`](../plan.md)) against runtime — progress-doc `[x]` marks are intent, not truth.

- [ ] Z-P0 review · [ ] Z-P1 review · [ ] Z-P2 review · [ ] Z-P3 review · [ ] Z-P4 review · [ ] Z-P5 review · [ ] Z-P6 review (= Z 1.0 global DONE)

## Library reference card

- **OR-Tools CP-SAT (Java)** — dependency `com.google.ortools:ortools-java` (pulls per-platform natives; call `Loader.loadNativeLibraries()` once, in the backend's companion init). Key APIs (verified 2026-07-07): `CpModel.newIntVar/newBoolVar/newOptionalIntervalVar`, `addCumulative`, `addLessOrEqual(...).onlyEnforceIf(lit)`, `LinearExpr.newBuilder().addTerm(v, c)`, `model.minimize(expr)`, `CpSolver.getParameters().setMaxTimeInSeconds(s)/.setRandomSeed(n)`, `solver.solve(model)`, `solver.value(v)/booleanValue(lit)/objectiveValue()/bestObjectiveBound()` (gap = `|obj − bound| / max(1,|obj|)`). Snippets in `tasks-z-p3.md`.
- **JGraphT** (min-cut floor) — `org.jgrapht:jgrapht-core` (pure JVM). `PushRelabelMFImpl<V,E>` implements both `MaximumFlowAlgorithm` and `MinimumSTCutAlgorithm`: `calculateMinCut(source, sink)`, then `getSourcePartition()/getSinkPartition()/getCutEdges()`. Precedent: the old PoC (`~/Dev/tatrman-poc`) already ships JGraphT.
- **Calcite clone** (`~/Dev/view-only/calcite`, graphified) — **reference reading only**; Z has no Calcite dependency (architecture §3). Consult for the ttr-translator boundary if a cost question touches SQL emit.
- **Kotest + Gradle conventions** — mirror `packages/kotlin/ttr-parser` (build.gradle.kts shape, test layout, FunSpec style); examples table in the planning skill's `EXAMPLES.md` (`~/Dev/ai-platform`).
