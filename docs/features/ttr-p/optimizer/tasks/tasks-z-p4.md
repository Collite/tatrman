# Z-P4 — Boundary rewrites as choice variables

> Pre-flight: Z-P3 done. DoD: [`../plan.md`](../plan.md) §Z-P4. The four kinds (Z-a): FILTER_PUSHDOWN, PROJECT_PRUNE, EAGER_AGG, MATERIALIZE_REUSE (+ MATERIALIZE_INDEX variant, GI-5).

## S1 · Rewrite enumerator + legality {#s1}

Verify: `./gradlew :packages:kotlin:ttr-optimizer:test`.

- [ ] **T1 (tests first).** `EagerAggLegalityTest`: accept — hero `aggregate(sum(amount) by branch)` above `join` on key `account_id` ⇒ pre-agg `sum(amount) by account_id` legal (SUM decomposes; group-by extends by join key) · reject — aggregate containing `avg` **without** rewrite-to-SUM+COUNT marker; aggregate over non-decomposable fn (`median`); join type `full` (null-extension breaks partial groups — reject in Z 1.0, note in KDoc).
- [ ] **T2 (tests first).** `FilterPushdownLegalityTest`: accept — filter above join referencing only one side's columns; reject — predicate spanning both sides; reject — pushing across an Aggregate that drops the filtered column. `ProjectPruneLegalityTest`: unused-column sets computed transitively from sink requirements; accept/reject fixtures.
- [ ] **T3 (tests first).** `MaterializeReuseTest`: fan-out node with 2 consumers on the same engine ⇒ MATERIALIZE_REUSE opportunity (effect: producer cost once + store/load vs twice); with consumers on different engines ⇒ opportunity attaches to the cut side; MATERIALIZE_INDEX variant only when the consumer is a Join on stored input (GI-5), effect includes index build cost + reduced join cost per the cost-shape table.
- [ ] **T4 (tests first).** `ChoiceEffectTest`: EAGER_AGG on `sales_f → join` replaces edge volume 400 000 000 B/5 000 000 rows with 9 000 000 B/100 000 rows **and** adds the pre-agg node cost (1 000 ms polars / 2 000 ms pg — computed via `DurationModel`, engine-dependent); effects are *functions of the assignment side*, represented per-engine.
- [ ] **T5.** Implement `RewriteEnumerator(opGraph, estimates) → List<RewriteChoice>`: pattern-match the four kinds over the graph; legality predicates as separate pure functions (`Legality.kt`) — every predicate has both accept and reject fixtures by T1–T3; pre-compute `ChoiceEffect` per engine side via the Z-P1 models.
- [ ] **T6.** Determinism + bound: enumerator output ordered by (kind, edge id); component test `EnumeratorHeroTest` — hero yields exactly: 1× EAGER_AGG (sales edge), 1× FILTER_PUSHDOWN? (none — hero filters are already at sources; assert **absent**), 1× MATERIALIZE_REUSE? (none — no fan-out; assert absent). The hero's enumeration is exactly one choice — assert `size == 1`.
- [ ] **T7.** Green; commit `Z-P4.S1: rewrite enumerator + legality predicates`.

## S2 · Choices in solvers + plan applier {#s2}

Verify: both modules' tests.

- [ ] **T1 (tests first).** `CpSatChoicesTest` (`@RequiresOrTools`): hero + choices, MAKESPAN, THOROUGH → **plan D** (eager-agg taken, join in polars), `objectiveValue == 5080`; COST_SUM variant → D at `5252`; pin join to erp_pg → **plan E** shape (choice still taken, transfer 9 MB), makespan `5250`.
- [ ] **T2 (tests first).** `MinCutChoicesTest`: with `choices.size ≤ 4`, `EnumeratingMinCut` (2^choices × min-cut) matches CP-SAT's COST_SUM result on the hero (both 5252); `supports()` still false for MAKESPAN.
- [ ] **T3 (tests first).** `PlanApplierTest`: applying plan D's solution to the hero graph yields — a new `aggregate` node (pre-agg) between `filter_amount` and `join` with provenance `choice:EAGER_AGG@…`; derived containers: `<derived-pg>` {load_accounts, filter_active} target erp_pg, `<derived-polars>` {rest} target polars_w, `derived=true`; MATERIALIZE choices expand to the Materialize macro (Store+[Index]+Load, B-T9) — separate fixture with a fan-out graph; **re-evaluating the applied graph with Z-P1 evaluators reproduces the solver's objective within ±1 ms** (the applier-honesty test).
- [ ] **T4.** Implement choice variables in `CpSatModelBuilder`: BoolVar per choice; edge volume/cost terms switch on it (big-M-free: two optional linear terms with `onlyEnforceIf(choice)`/`onlyEnforceIf(choice.not())`); pre-agg node cost joins the taken side's engine terms.
- [ ] **T5.** Implement `EnumeratingMinCut` wrapper (`id="mincut"` extended: iterate choice subsets ≤ 2^4, min-cut each, keep best — W3 finding 3); update `supports()` accordingly.
- [ ] **T6.** Implement `PlanApplier`: solution → graph edits in T8 vocabulary (insert nodes, expand Materialize macros, form derived containers by same-engine reachability respecting `together`), all with provenance; output = ordinary graph, ready for movement synthesis.
- [ ] **T7.** Green; tracker; commit `Z-P4.S2: choice variables end-to-end; hero reaches 5 080 ms`.
