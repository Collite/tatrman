# Z-P2 — Solver floor: problem builder, min-cut, HEFT

> Pre-flight: Z-P1 done (evaluators + hero fixture exist). DoD: [`../plan.md`](../plan.md) §Z-P2.

## S1 · Problem builder {#s1}

Verify: `./gradlew :packages:kotlin:ttr-optimizer:test`.

- [ ] **T1 (tests first).** `ProblemBuilderTest`: `"directive containers become pins"` (hero with `target` on both prep containers → pins map has their nodes) · `"prefer becomes hint with profile penalty"` · `"together yields one cohesion group"` · `"grouping-only container contributes nothing"` · `"no containers → all nodes movable"` · `"FS/SS edges land in precedence"` .
- [ ] **T2 (tests first).** `CapacityFilterTest`: with polars memory 1 GB, all-Polars domains prune polars for the join island; `"no feasible placement → TTRP-OPT-011 with binding constraint text containing island id, needed bytes, available bytes"` (fixture: both engines too small).
- [ ] **T3 (tests first).** `CeilingTest`: generated chain of 100 movable nodes → `TTRP-OPT-010`; 99 → passes. Pinned nodes don't count (95 movable + 20 pinned → passes).
- [ ] **T4.** Implement `ProblemBuilder(opGraph: OpGraphSource, world: ResolvedWorld, estimates, config, profile) → PlacementProblem`: container-annotation mapping (Z-P5 delivers real syntax; until then `FixtureGraph` carries annotations), transfer-byte estimates onto edges, precedence assembly, hint penalties from the profile (Z 1.0 `makespan` profile: single uniform penalty constant, declared in the profile object).
- [ ] **T5.** Implement `CapacityFilter` (domain pruning vs `MemEstimator`; emits OPT-011) and wire `checkCeiling` (Z-P0) into the builder entry.
- [ ] **T6.** Green; commit `Z-P2.S1: problem builder + capacity + ceiling`.

## S2 · MinCutSolver + HeftSeeder {#s2}

Verify: `./gradlew :packages:kotlin:ttr-optimizer:test`.

- [ ] **T1 (tests first).** `MinCutSolverTest`: `"hero (no choices, COST_SUM) → plan C assignment"` (assert full node→engine map: accounts chain on erp_pg through filter_active, everything else polars_w) · `"objectiveValue == 6702.0, gap == 0.0"` · `"supports() false for 3 engines / for MAKESPAN / for problems with choices"` · `"pin on join to erp_pg forces cut migration"` (join+downstream pg; recompute expected by hand: transfer sales_f 400 MB → plan cost 10 000 + …; assert solver == exhaustive).
- [ ] **T2 (tests first).** `ExhaustiveCrossCheckTest` (component test): brute-force all 2^k assignments of the ≤12-node hero (k = movable count) via `CostSumEvaluator`; assert min-cut's solution cost equals the exhaustive minimum — on the base fixture AND on 20 randomized cost-perturbed variants (fixed RNG seed 42).
- [ ] **T3 (tests first).** `HeftSeederTest`: `"produces a feasible full assignment"` (respects pins + capacity domains) · `"hero makespan bound ≤ all-PG makespan"` · `"deterministic"` (two runs bit-equal).
- [ ] **T4.** Implement `StoneNetworkBuilder`: terminals `S`=engine1, `T`=engine2; per movable node `n`: edge `S→n` cap = cost(n on engine2), `n→T` cap = cost(n on engine1) (Stone's construction — cost of the side you *don't* join); per data edge undirected cap = transfer cost; pinned nodes get `∞` (Long.MAX/4) on the opposing edge. Use JGraphT `SimpleDirectedWeightedGraph` + `PushRelabelMFImpl.calculateMinCut(s, t)`; read `getSourcePartition()` → assignment.
- [ ] **T5.** Implement `MinCutSolver` (`id="mincut"`; `supports` = 2 engines ∧ `Objective.CostSum` ∧ choices empty ∧ no cohesion group spanning a pin conflict; cohesion groups contracted into super-nodes before the network build).
- [ ] **T6.** Implement `HeftSeeder` (`id="heft"`): upward-rank list scheduling over `DurationModel` (rank = node duration avg across engines + max(child rank + transfer)); assign greedily to earliest-finish engine respecting pins/domains; returns `PlacementSolution` with `gap = Double.NaN` (bound-only semantics documented in KDoc).
- [ ] **T7.** Register both in the default `SolverRegistry` order (`mincut`, `cpsat` placeholder, `heft`); registry component test: hero COST_SUM routes to mincut, hero MAKESPAN (no cpsat yet) routes to heft under `FAST`, errors `TTRP-OPT-011`-adjacent "no exact backend" under `THOROUGH` (define diagnostic reuse: OPT-040 wording).
- [ ] **T8.** Green; tracker boxes; commit `Z-P2.S2: min-cut (Stone) + HEFT seed`.
