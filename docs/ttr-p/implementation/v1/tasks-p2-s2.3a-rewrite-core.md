# Tasks · P2 · Stage 2.3a — Rewrite engine core (T8): strata, sugar, lowering

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

> **Split note:** plan Stage 2.3 is delivered as **2.3a** (this file — rewrite engine, termination/fission spec, sugar expansion, capability lowering, property tests) + **2.3b** (`tasks-p2-s2.3b-movement-collapse.md` — T5-b escalation, movement synthesis, container-collapse, waves, `ttrp explain` golden). The plan's Stage-2.3 DONE bar is split accordingly; both halves must close before Phase 2 closes.

## Stage deliverable

The T8 normalizer's engine and its first two strata. Plan stage line (this half): *"sugar expansion stratum (Select/Calc/HAVING/Distinct); capability lowering (Branch→Filter etc.); **termination measure + node-fission rules specified and tested** (the named B work items)"* — plus the plan DONE-bar half: *"rewrite engine property-tested for termination and determinism."* The engine is stratified and fixpoint-iterating (B-T9: rewrite less-physical → more-physical until "can this engine process this graph?" holds), joins the Stage-2.2 manifests with compiler-owned rules (T6-d α), logs every applied rewrite with provenance (feeding `ttrp explain` in 2.3b), and is proven terminating + deterministic by `io.kotest.property` tests. The **termination measure and node-fission rules are design-note-first**: `notes-t8-termination.md` is written and reviewed BEFORE the fixpoint is coded — they are the named open compiler work items from B (B-T10 sweep / T6-d), not settled design; coding ahead of the note is improvising.

## Pre-flight (all must pass before T2.3a.1)

- [ ] Stages 2.1 + 2.2 DONE bars checked; `./gradlew :packages:kotlin:ttrp-graph:test` green.
- [ ] Read `~/Dev/ai-platform/EXAMPLES.md` §7d (proto-level walker pattern: **post-order `rewriteChildren` with reference-equality idempotency**) — the recommended shape for the T8 rewriter. If the file is unavailable, note it under §Blockers observations but proceed: the pattern is fully restated in T2.3a.3.
- [ ] (Optional, inspiration only) Calcite clone at `~/Dev/view-only/calcite` (graphify-out available): how `RelOptRule`/`HepPlanner` structure match+apply+registry. **The T8 engine is our own** — Calcite arrives only in P3 via ttr-translator (B-T3 two-tier split); do not import Calcite here.
- [ ] Confirm the Phase-1 expression IR types (T5 twin: `Expression`, `AggregateCall` arm, `Cast`, catalogue ids): `grep -rn "AggregateCall" packages/kotlin/ttrp-frontend/src/main --include=*.kt | head`. Record FQNs — sugar/lowering rewrites build expression trees.

## Tasks

### T2.3a.1 · Test corpus + spec skeletons (TEST-FIRST)

- [ ] Fixture tree `packages/kotlin/ttrp-graph/src/test/resources/fixtures/rewrite/`:
  - `sugar.ttrp` — one polars container exercising all four sugars:
    ```pl
    container prep(in t, out o) target polars {
        s = select(t, account_id, region as reg, amount)      // Select → Project
        c = calc(s, vat = amount * 0.21)                       // Calc → Project
        d = distinct(c)                                        // Distinct → Aggregate
        o = d -> aggregate {
                   group by reg
                   total = sum(vat)
                   having total > 1000                          // HAVING → Aggregate + Filter
                 }
    }
    ```
    (Spellings follow the Stage-1.1/1.2 grammar as shipped; if the grammar has no `having` clause or no `select`/`calc`/`distinct` op forms, STOP — §Blockers: that is a Phase-1 gap, not something to invent here.)
  - `lowering/branch-null.ttrp` — polars container: `values`-or-`load` of rows where the branch predicate is TRUE / FALSE / NULL for distinct rows, `b = branch(x, amount > 100)`, both ports consumed. Purpose: pin 3VL routing (NULL row lands on the **false** port).
  - `lowering/switch.ttrp` — Switch with two conditions + `else`, non-overlapping mode.
  - `lowering/setops.ttrp` — Intersect + Except on polars (join-pattern lowering) and on erp_pg (native, no rewrite fires).
  - `lowering/right-join.ttrp` — `type: right` join on polars (input-swap lowering per the 2.2 manifest gap).
  - `hero.ttrp` — symlink-free copy or shared loader from Stage 2.1 fixtures (Branch@polars is the hero's one lowering).
- [ ] Spec skeletons in `.../org/tatrman/ttrp/graph/rewrite/`: `RewriteEngineSpec.kt`, `SugarExpansionSpec.kt`, `CapabilityLoweringSpec.kt`, `RewritePropertySpec.kt` (property tests — cases named in T2.3a.6), plus `ArbGraphs.kt` (generators, stub now).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test` — compiles; new specs red, prior stages green.

### T2.3a.2 · Termination measure + node-fission design note — REVIEW GATE

- [ ] Write `docs/ttr-p/implementation/v1/notes-t8-termination.md` covering, minimally:
  1. **Strata order (fixed by prior decisions — restate, don't reinvent):** authoring-sugar expansion ≺ function capability-lowering ≺ node capability-lowering ≺ node-fission ≺ whole-node re-placement (T5-b order + T6-d "sugar ≺ function-lowering ≺ node-fission ≺ re-placement") ≺ movement synthesis ≺ container-collapse (2.3b strata).
  2. **The measure** — propose and defend a strictly-decreasing lexicographic tuple, strawman: `M(G) = (sugarNodeCount, functionMissCount, nodeMissCount, unsynthesizedCrossEngineEdgeCount)`; every rule strictly decreases its own component and never increases an earlier one; fixpoint iteration bound = sum of initial components; the engine ASSERTS the decrease per applied rule at runtime (cheap, catches rule bugs forever).
  3. **Node-fission rules** (T6-d work item): when a Project/Calc carries expressions where only some functions miss on the target engine, split the node so ONLY the unsupported-function slice re-places (a Project fissions into Project(supported)∘Project(missing-slice) preserving column dependencies and the SSA label on the final node, `#`-suffixed intermediates); define when fission is legal (no intra-node dependency from a missing-slice output into a supported-slice input… or handle by topological column ordering), when it degenerates (everything misses ⇒ no fission, whole node escalates), and its measure effect (functionMissCount strictly drops per fission because the supported slice becomes miss-free).
  4. **Determinism rules:** stable node ids (document order), rules applied in (stratum, node-id) order, insertion-ordered collections only, no reliance on hash iteration; same input graph + same manifests ⇒ byte-identical output graph and rewrite log.
  5. **Confluence stance:** v1 does not prove confluence; determinism (fixed application order) substitutes for it (T8-b: v1 rewriting is cost-free/deterministic; Z is v2).
- [ ] Open a review: commit the note, request review from Bora (the `/review` cadence — record the request in the note header). **Do not start T2.3a.3 until the note is approved**; if review stalls, that is a legitimate §Blockers entry.
  - **Verify:** note exists at `docs/ttr-p/implementation/v1/notes-t8-termination.md`; header carries `Status: approved <date> by <reviewer>` before the next task's first commit.

### T2.3a.3 · Rewrite engine skeleton: strata, walker, rewrite log

- [ ] `org.tatrman.ttrp.graph.rewrite`:
  - `interface RewriteRule { val stratum: Stratum; fun apply(node: Node, ctx: RewriteContext): RewriteResult }` where `RewriteResult` = `Unchanged` (return the **same reference** — reference equality is the idempotency signal, the EXAMPLES.md §7d pattern) or `Replaced(subgraph, appliedRewrite)`.
  - `RewriteEngine(rules, manifests, world)`: per stratum, **post-order walk** (children/inputs before consumers) over nodes in (stratum, node-id) order; a node whose rule returns `Unchanged` by reference is never re-visited within the stratum pass; strata run to local fixpoint before the next stratum starts; the engine asserts the T2.3a.2 measure strictly decreases on every `Replaced` and hard-fails (internal error, not a diagnostic) otherwise; global iteration guard = the measure-derived bound, exceeded ⇒ internal error naming the last rule.
  - `AppliedRewrite { rule, stratum, before: labels+ids, after: labels+ids, sourceLocation, reason }` accumulated in an ordered `RewriteLog` — this is `ttrp explain`'s "applied rewrites" section (S4) and each entry doubles as a T6-e info diagnostic ("X lowered to Y for engine E").
  - Control-edge preservation invariant checked after every stratum: every FS/SS edge of the input graph is still satisfied by the output (endpoints may have been replaced — the log maps old→new ids; B-T2 hard-on-effect).
- [ ] `RewriteEngineSpec` cases: "unchanged node is returned by reference", "engine reaches fixpoint on an already-normal graph in one pass", "measure violation by a deliberately broken test rule fails hard", "rewrite log records before after and reason", "control edges survive replacement via id mapping", "strata run in declared order".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*RewriteEngineSpec'` — green.

### T2.3a.4 · Authoring-sugar stratum (engine-independent, expanded early)

- [ ] Rules (B-T10: sugar is engine-independent — these fire regardless of manifests):
  - `Select(cols, renames)` → `Project` (choose/rename only).
  - `Calc(assignments)` → `Project` (pass-through + computed columns).
  - `Distinct` → `Aggregate` (group-by all input columns, no aggregate calls) — B-T10 sweep; native-DISTINCT emit is E/Calcite's later job.
  - `Aggregate.having != null` → `Aggregate` (having stripped) + downstream `Filter(having)` — the canonical "express HAVING without HAVING".
  - SSA labels: the sugar node's label lands on the **last** node of its expansion (the one whose out-edge the label named); intermediates get anonymous `~n` labels (C1-c-i / E-b naming discipline).
- [ ] `SugarExpansionSpec` cases (over `sugar.ttrp`): "select becomes project preserving rename", "calc becomes project with computed column", "distinct becomes group-by-all aggregate", "having splits into aggregate plus filter", "labels survive on expansion tails", "expansion is engine-independent (fires for pg target too)", "expanded graph contains no SugarNode instances".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*SugarExpansionSpec'` — green.

### T2.3a.5 · Capability-lowering stratum (engine-relative)

- [ ] Rules fire ONLY when the container's engine manifest lacks the node/parameter (T6-d α join: native? no → rewrite exists? yes → apply):
  - **Branch → Filter × 2.** true port ⇒ `Filter(pred)`; false port ⇒ `Filter(not(coalesce(pred, false)))` — 3VL-correct complement: a NULL predicate row goes to **false** (Branch splits into passing/failing partitions, B-T2 routing table; SQL `WHERE` keeps only TRUE). Multicast input feeds both filters. If only one out-port is consumed, still lower only the consumed side(s).
  - **Switch → Filter chain.** Non-overlapping: branch *i* ⇒ `Filter(cond_i AND not(coalesce(cond_1,false)) AND … AND not(coalesce(cond_{i-1},false)))`; `else` ⇒ conjunction of all-negated. Overlapping mode: plain `Filter(cond_i)` each, no `else`.
  - **Intersect / Except → Join(semi) / Join(anti)** on all columns (B-T10 sweep: join-pattern lowering for dataframes) — set semantics: wrap sides in the Distinct-expansion (group-by-all Aggregate) first.
  - **Join{type: right} → Join{type: left}** with swapped inputs + a `Project` restoring column order (covers the 2.2 polars manifest gap).
  - **Pivot{native: false} → Aggregate + CASE-bearing Project** over the statically declared pivot values (T10: declared values ⇒ static schema; one aggregate call per declared value via `case when key = <v> then val end` catalogue expressions).
  - **Function capability-lowering** (T5-b α, same stratum boundary as T6-d's "function-lowering"): a small compiler-owned table of expression rewrites keyed by catalogue id (v1 seed: `between` → `>= and <=`, `coalesce` → nested `case` **only** for engines whose manifest lacks `coalesce`); a missing function with no table entry produces a `FunctionMiss` that SURVIVES to 2.3b escalation — this stratum never errors on it (T5-b: escalation is node-granular, next stage).
- [ ] `CapabilityLoweringSpec` cases: "branch lowers on polars", "branch does not lower on a hypothetical engine declaring Branch" (test-only manifest fixture), "null predicate row routes to false port" (assert filter expressions literally — `not(coalesce(pred, false))`), "switch non-overlapping chain excludes earlier conditions", "intersect lowers to distinct semi join on polars and stays native on pg", "right join swaps inputs and restores column order", "pivot expands to case aggregate", "unrewritable function miss survives as data not diagnostic", "rewrite log carries one entry per lowering with engine name".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*CapabilityLoweringSpec'` — green; hero fixture normalizes with exactly one lowering applied (Branch@polars → 2×Filter).

### T2.3a.6 · Property tests: termination + determinism (`io.kotest.property`)

- [ ] `ArbGraphs.kt`: generators for well-formed small graphs — `Arb` of node kinds weighted toward the T10 transform set, chained into random DAGs (respect single-in by construction; random container split with targets drawn from {erp_pg, polars}; random sugar nodes; expressions drawn from a fixed pool incl. functions present/absent per manifest). Size bound ≤ 30 nodes. Shape:
  ```kotlin
  import io.kotest.property.Arb
  import io.kotest.property.arbitrary.arbitrary
  import io.kotest.property.arbitrary.int
  import io.kotest.property.checkAll

  fun arbGraph(): Arb<TtrpGraph> = arbitrary { rs ->
      val n = Arb.int(1..30).bind()
      GraphGen(rs.random).chain(n)   // deterministic from the RandomSource
  }
  ```
- [ ] `RewritePropertySpec` (StringSpec) cases:
  - `"normalization terminates within the measure bound"` — `checkAll(500, arbGraph()) { g -> engine.normalize(g).iterations shouldBeLessThanOrEqual measureBound(g) }` (the engine's internal assertion also guards every intermediate step).
  - `"normalization is deterministic"` — normalize the same graph twice through two fresh engine instances; structural equality of graphs AND equality of rewrite logs.
  - `"normalization is idempotent"` — `normalize(normalize(g).graph).graph` structurally equals `normalize(g).graph` and applies zero rewrites (fixed point is fixed).
  - `"no sugar nodes survive"` — post-normalize graph contains no `SugarNode`.
  - `"control edges are preserved"` — every input FS/SS constraint maps to a satisfied constraint in the output.
  - Failures must print the generated graph's textual dump (implement `TtrpGraph.debugRender()`) — property shrinking without a readable counterexample is useless.
- [ ] Register the rewrite-log info diagnostics format (T6-e rows "lowered"/"expanded") in the diagnostics catalogue as `TTRP-CAP-101` ("<node> lowered to <nodes> for <engine>", info) and `TTRP-CAP-102` ("<function> expanded for <engine>", info) — ids ≥ 100 to keep the error/warn block contiguous below.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*RewritePropertySpec'` — green at 500 iterations, runtime < 60s (tune size bound, not iteration count, if slow). Then full module: `./gradlew :packages:kotlin:ttrp-graph:test` green.

## Definition of DONE (stage)

- [ ] `notes-t8-termination.md` exists, is review-approved, and the coded measure/fission logic cites it (KDoc link).
- [ ] Engine: stratified fixpoint, post-order reference-equality walker, per-rule measure assertion, ordered rewrite log with old→new id mapping.
- [ ] Sugar stratum: Select/Calc/Distinct/HAVING all expand; no SugarNode survives normalization; labels land per the SSA rule.
- [ ] Lowering stratum: Branch/Switch/Intersect/Except/right-Join/Pivot + function table; 3VL null-routing pinned by test; unrewritable function misses survive as data for 2.3b.
- [ ] Property tests green: termination (bounded), determinism (double-run equality), idempotency, sugar-free, control-preservation — 500 iterations each.
- [ ] Hero normalizes: exactly one applied rewrite (Branch→Filter×2 on polars), log entry names rule + engine + source location.
- [ ] `./gradlew build` green repo-wide; ktlint clean.

## Blockers

_(empty — coder records here)_

## References

- **Decisions:** B-T8 (node set + rewrite rules; normalization fixpoint), B-T9 (physicality spectrum, one graph), B-T10 + sweep (two rewrite kinds; engine-relative primitive/macro; Distinct/Intersect/Except/semi-anti rulings; **termination measure + node-fission = named work items** — this stage's T2.3a.2), B-T5/T5-b (function misses escalate at node granularity, order sugar → function-lowering → re-placement), B-T6/T6-d (manifest strictly separate from rules; stratification sugar ≺ function-lowering ≺ node-fission ≺ re-placement), B-T2 (control hard-on-effect — preservation invariant), B-T3 (two-tier: T8 here, Calcite only behind ttr-translator in P3), Q7-γ/C1-c-i/E-b (label discipline through expansions).
- **Docs:** `architecture.md` §3–4 · `02-internal-model-options.md` §T8, §T5-b, §T6-d (termination work-item paragraph) · `contracts.md` §8.
- **External (coder-readable):** `~/Dev/ai-platform/EXAMPLES.md` §7d — post-order `rewriteChildren` + reference-equality idempotency walker (recommended shape) · `~/Dev/view-only/calcite` (graphify-out) — `RelOptRule`/`HepPlanner` structure, **inspiration only, no dependency**.
- **Kotest property testing:** `io.kotest.property.checkAll`, `Arb`, `arbitrary { }` builder (bundle `libs.bundles.kotest` already includes `kotest-property`; precedent: `ttr-semantics/.../ResolverPropertySpec.kt`).
