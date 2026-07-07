# TTR-P Optimizer (Z 1.0) — Phased Implementation Plan

> **Status:** consolidated 2026-07-07, per the planning skill. Companions: [`architecture.md`](./architecture.md), [`contracts.md`](./contracts.md). Design ground truth: control-room decision log (2026-07-07 Z entries) + [`../design/13-optimizer-options.md`](../design/13-optimizer-options.md) + [`../design/14-optimizer-worksheets.md`](../design/14-optimizer-worksheets.md).
>
> Per the planning skill: this document = overall plan + phases with deliverables, pre-flight conditions, and definitions of DONE. **Per-phase task lists (6–8 tasks each, TDD-ordered, with an overall task-management document) are generated separately, phase by phase, when implementation starts.**

---

## 0. Overall plan

Seven phases. The spine: **cost model first** (it's testable against the W3 hand-run numbers before any solver exists), **solver floor second** (min-cut — dependency-free, exact on the hero world), **CP-SAT third**, then **choice variables**, **language surface**, and **integration**. Each phase lands behind the `[ttrp] optimize` knob (default `off`), so trunk stays releasable throughout; nothing downstream of Z changes by design (architecture §2, invariant 3).

```
Z-P0 scaffold ─► Z-P1 cost model ─► Z-P2 solver floor ─► Z-P3 CP-SAT ─► Z-P4 choice vars ─► Z-P5 surface ─► Z-P6 integration
                    (W3 numbers)      (hero: plan C)      (k engines,     (hero: plan D/E)   (grammar,       (explain, bundle,
                                                           makespan)                          B-T9 amend)     conform, Designer)
```

**Global pre-flight (gates the whole plan):**

1. TTR-P v1 complete through its P5 (A4 exit): compiler front-half, T6 capability manifests, movement synthesis, bundle, `ttrp-conform` all real.
2. `ttr-metadata` published with world resolution (its own arc; serverless/repo backing is sufficient for Z-P0…P5 — the served backing may arrive in parallel).
3. The B-T9/B-T6 amendments are recorded (done — control room 2026-07-07); TTR-M `schema manifest` + world `calibration`/`prices`/`transfer-rates`/`stats` grammar additions are scheduled into the TTR-M side (Z-P1 pre-flight below).

**Global definition of DONE (Z 1.0):** the hero scenario, authored with free placement (no containers), compiles with `optimize=on` into the W3-shaped plan (eager-agg variant, ≈5.3 s-class on the calibrated dev world), `ttrp-conform` proves Q9-equivalence against the pinned all-directive variant, `ttrp explain` shows placements/cuts/rewrites/gap, and a fully-pinned v1 program byte-reproduces its v1 bundle.

---

## Z-P0 · Module scaffold & problem model

**Pre-flight:** global pre-flight 1–2; module-cut approval (`ttr-optimizer`, `ttr-optimizer-cpsat` per contracts §10).

**Deliverables:** Gradle modules wired into the build + publish (tag pattern per PUBLISHING.md); the data model of contracts §6 (`PlacementProblem`, `PlacementSolution`, `RewriteChoice`, `SolverBudget`, `Objective`-as-data); `PlacementSolver` interface + registry with selection rule; `MetadataSource`/`StatsSnapshot` consumer interfaces (contracts §5) with a repo-read fake; `[ttrp]` knob parsing (contracts §2); diagnostic ids `TTRP-OPT-001…040` registered with fixture stubs.

**DONE when:** modules build/test/publish-local; the registry selects backends by `supports()` order under test; a `NoopSolver` returns the pinned assignment for a fully-pinned problem; all diagnostics render with suggested alternatives from fixtures.

## Z-P1 · Cost model (the W3-verifiable core)

**Pre-flight:** TTR-M grammar work for `schema manifest` cost-shapes + world `calibration`/`prices`/`transfer-rates`/inline `stats` + sibling `def stats` doc (contracts §3–4) — this is a **TTR-M-side dependency**, plan it as a small TTR-M change with its own tests.

**Deliverables:** manifest cost-shape parsing/validation (unit grammar `/row`, `/byte`, fixed; reserved units rejected `TTRP-OPT-030`; missing-shape default + `TTRP-OPT-031`); resolved-world merge (type × instance calibration/prices); size estimator (declared/served stats → per-edge rows/bytes; default selectivities per node kind); duration fold (resource vector × calibration → ms); transfer estimator (bytes / pair rate); makespan evaluator over a *given* assignment (critical path incl. FS/SS + concurrency); `mem` working-set estimator per island.

**DONE when:** the W3 table reproduces as a golden test — plans A/B/C/D/E on the hero graph + dev-world fixture evaluate to ≈20 110 / 6 850 / 6 700 / 5 250 / 5 300 ms; snapshot-fingerprint changes propagate into plan identity; stats precedence chain (served > sibling > inline > default) proven by tests.

## Z-P2 · Solver floor — min-cut + HEFT + feasibility

**Pre-flight:** Z-P1 DONE.

**Deliverables:** Stone-construction `MinCutSolver` (2 engines, COST_SUM tier; exact); `HeftSeeder` (list scheduling on the makespan model — warm start + upper bound + `fast`-tier fallback for k>2); capacity pre-filter (`mem` vs engine memory → domain pruning; `TTRP-OPT-011` with binding-constraint report); ceiling check (`TTRP-OPT-010` at ≥100 movable nodes); pins/`together` handling in the problem builder.

**DONE when:** hero (no choice vars) solves to plan C by min-cut, proven optimal by exhaustive enumeration on the ≤12-node fixture; capacity tests exclude oversized placements and produce OPT-011 with the correct binding constraint; determinism: repeated solves bit-identical.

## Z-P3 · CP-SAT backend (makespan, k engines, capacities)

**Pre-flight:** Z-P2 DONE; OR-Tools JVM packaging decision executed (per-platform natives in `ttr-optimizer-cpsat` only — the floor stays dependency-free).

**Deliverables:** CP-SAT model builder (assignment vars, interval/cumulative scheduling for makespan, capacity constraints, hint-penalty objective terms); budget tiers → solver params (fixed seed; `fast` delegates to min-cut/HEFT, `balanced` time-capped, `thorough` optimality-or-generous-cap); gap reporting (`TTRP-OPT-040` on cap); 3-engine world fixture.

**DONE when:** CP-SAT agrees with min-cut on every 2-engine COST_SUM fixture; the 3-engine fixture solves with proven-optimal makespan ≤ HEFT's bound; parallel-vs-serial fixture demonstrates makespan ≠ cost-sum ranking (the Z-b argument, as a test); determinism per (problem, tier, seed).

## Z-P4 · Boundary rewrites as choice variables

**Pre-flight:** Z-P3 DONE (choice variables need the CP-SAT model; the min-cut path handles them by enumerate×solve — implement both per W3 finding 3).

**Deliverables:** `RewriteEnumerator` with the four Z-a kinds — FILTER_PUSHDOWN, PROJECT_PRUNE, EAGER_AGG (decomposable-aggregate legality: SUM/COUNT/MIN/MAX; AVG via SUM+COUNT), MATERIALIZE_REUSE (+ MATERIALIZE_INDEX macro variant per GI-5) — each with a legality predicate over the graph + a pre-computed `ChoiceEffect`; `PlanApplier` extension: taken choices → graph rewrites (T8 vocabulary: node insertion, Materialize macro expansion) with provenance for `explain`.

**DONE when:** hero with choice vars finds D or E (≈5 250/5 300 ms — the makespan objective must break the tie toward D, as a test); every rewrite kind has legality-accept and legality-reject unit fixtures; applied plans re-evaluate (Z-P1 evaluator) to the solver's predicted objective within rounding; `ttrp-conform` (dev harness run) proves D ≡ A on results.

## Z-P5 · Language surface & B-T9 amendment implementation

**Pre-flight:** Z-P4 DONE; TTR-P grammar change window (spec-version bump per S6/grammar-master).

**Deliverables:** `TTRP.g4` delta (`prefer`, `together` — contracts §1); model fields + v1-ingest rule (`target` ⇒ DIRECTIVE); Z-off degradation (`prefer`=`target`; grouping ⇒ defaults else `TTRP-OPT-020`); problem-builder mapping (pins/hints/cohesion/grouping); formatter + LSP surface updates (hover shows strength; rename-safe); C1 canvas metadata for container roles (render hints distinctly — Designer-side ticket, not this repo's blocker).

**DONE when:** the full v1 golden corpus parses/compiles byte-identically (all-directive special case — regression gate); property tests: any program's Z-off bundle is independent of `prefer`↔`target` swaps; grammar conformance suite green across generated parsers.

## Z-P6 · Pipeline integration, explain, bundle, conform

**Pre-flight:** Z-P5 DONE; served `ttr-metadata` backing available (for the ladder tests; repo backing covers the rest).

**Deliverables:** the optimize stage wired between placement-check and movement synthesis behind `[ttrp] optimize`; snapshot-at-pass-start + ladder (`TTRP-OPT-001/002`) against both backings; bundle `manifest.json` `stats` + `optimizer` blocks (contracts §7); `ttrp explain`/`ttrp/explain` payload (contracts §8) with derived-island rendering data; Designer budget user-setting plumbed to `SolverBudget`; `ttrp-conform` mode `--against-unoptimized`; golden-plan suite (hero + er-variant, pinned snapshot fixtures).

**DONE when:** the **global Z 1.0 definition of DONE** holds end-to-end; ladder behaviors demonstrated against a killed server (hard error at start; stale warning mid-session); explain output snapshot-stable; docs updated (TTR-P architecture §10 deferred-register prunes Z; CLAUDE.md pointer to `docs/ttr-p/optimizer/`).

---

## Risks & watch items

- **OR-Tools native packaging** (Z-P3): per-platform natives are the one heavy dependency — confined to the optional module by design; fallback = floor solvers, so a packaging slip degrades tiers, not correctness.
- **TTR-M grammar dependency** (Z-P1/P5 pre-flights): two small cross-cutting grammar changes ride the grammar-master process — schedule early, they gate their phases.
- **Makespan model fidelity:** island duration = Σ nodes is a v1 proxy (GI-1 forbids modeling engine internals); watch W3-style golden numbers against real runs once F-lite executes optimized bundles — feeds the stats/calibration refresh story, never plan correctness.
- **Tripwires recorded in design:** coupled-rewrite list growth → Z 3.0 memo-as-generator; priced engines → Z 2.0 profiles (both pre-paid: objective-as-data, resource vectors).

## Task lists

Generated 2026-07-07 per the planning skill → [`tasks/00-task-management.md`](./tasks/00-task-management.md) (overall tracker: rules, pre-flight gate, phase/stage checkbox table, phase-exit reviews, library reference card) + per-phase files `tasks/tasks-z-p0.md` … `tasks-z-p6.md` — 15 stage-level mini task lists of 6–8 checkboxed tasks, TDD-ordered (tests defined concretely, W3 numbers as golden expectations), verify commands per stage.
