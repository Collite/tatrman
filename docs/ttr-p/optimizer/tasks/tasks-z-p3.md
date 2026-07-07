# Z-P3 — CP-SAT backend

> Pre-flight: Z-P2 done; OR-Tools packaging decision executed (natives confined to `ttr-optimizer-cpsat`). All tests in this phase carry `@RequiresOrTools` (Z-P0.S1-T6) so native-less CI skips. DoD: [`../plan.md`](../plan.md) §Z-P3.
>
> **API card (verified against OR-Tools docs 2026-07-07):**
> ```java
> Loader.loadNativeLibraries();                       // once, lazily, in CpSatSolver companion
> CpModel m = new CpModel();
> BoolVar x = m.newBoolVar("assign[n,e]");
> IntVar start = m.newIntVar(0, horizon, "start[n]");
> IntervalVar iv = m.newOptionalIntervalVar(start, dur, end, x, "iv[n,e]");
> m.addCumulative(capacity).addDemand(iv, 1);          // per-engine concurrency
> m.addLessOrEqual(a, b).onlyEnforceIf(lit);
> IntVar makespan = m.newIntVar(0, horizon, "makespan");
> m.minimize(LinearExpr.newBuilder().add(makespan).addTerm(devLit, penalty).build());
> CpSolver s = new CpSolver();
> s.getParameters().setMaxTimeInSeconds(cap).setRandomSeed(seed).setNumWorkers(1); // 1 worker ⇒ determinism
> CpSolverStatus st = s.solve(m);                      // OPTIMAL | FEASIBLE | INFEASIBLE
> s.value(v); s.booleanValue(x); s.objectiveValue(); s.bestObjectiveBound();
> ```
> Scale all durations to integer **milliseconds** (CP-SAT is integer-only); horizon = Σ all max durations.

## S1 · Assignment model — cost-sum parity {#s1}

Verify: `./gradlew :packages:kotlin:ttr-optimizer-cpsat:test`.

- [ ] **T1 (tests first).** `CpSatCostSumParityTest`: hero COST_SUM → assignment identical to `MinCutSolver`, `objectiveValue == 6702.0`, status OPTIMAL (gap 0.0). Plus the 20 seeded perturbed fixtures from Z-P2 (`ExhaustiveCrossCheckTest` fixtures extracted into a shared object) — CP-SAT == exhaustive minimum on every one.
- [ ] **T2 (tests first).** `CpSatConstraintTest`: `"pin fixes assignment"` · `"together group co-assigned"` (exactly-one engine var per group) · `"capacity: 1GB polars excludes join island"` (per-engine Σ mem demands of assigned nodes ≤ memoryBytes) · `"infeasible → InfeasibilityReport, not exception"`.
- [ ] **T3.** Implement `CpSatModelBuilder` (assignment part): `assign[n][e]` BoolVars + exactly-one per node; pins as `addEquality(assign[n][pin], 1)`; cohesion via shared group var; cost-sum objective = Σ node-cost terms + Σ cut-edge terms (cut literal per edge×engine-pair: `cut[uv] ≥ assign[u][e] − assign[v][e]` linearization) + hint-deviation penalty terms; capacity as linear Σ mem ≤ cap per engine.
- [ ] **T4.** Implement `CpSatSolver` (`id="cpsat"`; `supports` = everything the model builder covers; translates `SolverBudget` → parameters per the tier table: FAST = delegate refusal (`supports=false` under FAST — registry falls to mincut/heft), BALANCED = `maxTimeInSeconds` from budget.wallMillis, THOROUGH = generous cap; `setNumWorkers(1)` + seed always).
- [ ] **T5.** Gap + status mapping: OPTIMAL → gap 0.0; FEASIBLE → gap from `bestObjectiveBound()`, attach `TTRP-OPT-040` warning payload; INFEASIBLE → `InfeasibilityReport` (echo binding constraint from the capacity pre-analysis).
- [ ] **T6.** Green; commit `Z-P3.S1: CP-SAT assignment model, min-cut parity`.

## S2 · Makespan scheduling, budgets, 3-engine fixture {#s2}

Verify: `./gradlew :packages:kotlin:ttr-optimizer-cpsat:test`.

- [ ] **T1 (tests first).** `CpSatMakespanTest`: hero MAKESPAN (no choices) → `objectiveValue == 6530` (plan C's makespan, Z-P1.S3-T3), assignment == plan C; `"parallel beats serial"` fixture: two independent 1000 ms chains + 2 equal engines — MAKESPAN splits them (1000 + ε transfer=0), COST_SUM is indifferent; assert the two objectives rank plans differently (the Z-b decision, as a test).
- [ ] **T2 (tests first).** `ThreeEngineTest`: fixture = dev world + `snow` engine (contracts §4) with cpu-factor 0.5 but transfer 25 ms/MB both ways; expected optimum computed by exhaustive search in the test itself (3^k, k ≤ 9) — CP-SAT must match; HEFT's makespan must be ≥ CP-SAT's (bound sanity).
- [ ] **T3 (tests first).** `BudgetDeterminismTest`: BALANCED with 200 ms cap on the 3-engine fixture, run twice — identical solutions (seed + 1 worker); THOROUGH reaches OPTIMAL; artificial 1 ms cap → FEASIBLE-or-worse path exercises OPT-040 payload.
- [ ] **T4.** Extend `CpSatModelBuilder` with scheduling: per node×engine `newOptionalIntervalVar(start, durMs[n][e], end, assign[n][e])`; precedence `end(u) + transferMs(uv)·cut(uv) ≤ start(v)` via `onlyEnforceIf`; per-engine `addCumulative(concurrency)` over its optional intervals; `makespan ≥ end(n)` ∀ sinks; objective = makespan + penalties.
- [ ] **T5.** Wire `Objective` dispatch (CostSum → S1 model, Makespan → S2 model) and registry default order finalization: `mincut` (fast 2-engine cost-sum) → `cpsat` → `heft`.
- [ ] **T6.** Component test `RegistryEndToEndTest` (in `ttr-optimizer-cpsat`): hero, MAKESPAN, BALANCED → cpsat chosen, plan C, gap 0.0; hero, COST_SUM, FAST → mincut chosen. Green; tracker; commit `Z-P3.S2: makespan scheduling + budget tiers`.
