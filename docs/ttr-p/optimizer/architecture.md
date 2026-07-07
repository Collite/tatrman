# TTR-P Optimizer (Z) — Architecture

> **Status:** consolidated 2026-07-07 from the converged workstream Z design ([`../design/13-optimizer-options.md`](../design/13-optimizer-options.md) GI-1…GI-22, [`../design/14-optimizer-worksheets.md`](../design/14-optimizer-worksheets.md), control-room decision log 2026-07-07). Companions: [`contracts.md`](./contracts.md), [`plan.md`](./plan.md).
>
> The optimizer is a **TTR-P v2** feature. This document describes **Z 1.0**; the version roadmap (§9) names what 2.0/3.0 add and which present choices they constrain.

---

## 1. What the optimizer is (and is not)

Z takes one normalized TTR-P graph, a declared **world** (engines, executors, storages, staging), per-engine **manifests** (capabilities + cost shapes), and **stats**, and decides the **execution plan**: which engine runs each node, where island boundaries fall, what crosses them, and what gets materialized (and indexed) at them — minimizing **makespan** under the F-a wave-execution semantics.

**Z optimizes boundary economics only (Z-a).** Intra-island optimization — join ordering, predicate ordering, access paths — belongs to the target engine's own optimizer. Z performs only the rewrites that change boundary economics: filter/project pushdown across a prospective cut, eager (partial) aggregation before a cut, materialize-for-reuse at fan-out, index-after-materialize. Emit stays preserved-shape (E-b); Z's output is a *graph*, not emitted code.

**Z is optional and author-overridable.** v1 programs (all containers `target`-annotated) are the fully-pinned special case: Z is a no-op on them. Authors release freedom gradually: `prefer` hints, `together` cohesion, grouping-only containers, or no containers at all (Z-e).

Scale contract: **< 100 nodes**. Larger graphs get the named diagnostic `TTRP-OPT-010` ("un-optimizable") — never a silent heuristic downgrade (GI-3).

## 2. Position in the compile pipeline

```
parse → resolve → build graph (SSA) → sugar expansion → capability normalize (T8)
      → author-placement check (pins/hints legality, capability vs manifests)
      → ┌───────────────────────────────────────────────┐
        │  Z · OPTIMIZE  (this document; skipped = v1)   │
        │  in:  normalized graph + container annotations │
        │  out: same graph vocabulary — engine-assigned  │
        │       derived containers, selected rewrites    │
        │       applied, Materialize macros inserted     │
        └───────────────────────────────────────────────┘
      → movement synthesis (Store+Transfer+Load, staging D-f)
      → emit islands + bundle (E, F-lite)
```

Z's output stays inside the existing model — (derived) containers with engine targets, materialize macros, ordinary nodes — so **nothing downstream of Z changes**: movement synthesis, emit, bundle, and `ttrp-conform` operate exactly as in v1. `ttrp-conform` doubles as Z's correctness harness: an optimized plan must conform (Q9) against the unoptimized plan.

## 3. Component architecture

```
                       ┌─────────────────────────────────────────────┐
                       │        org.tatrman:ttr-optimizer            │
                       │                                             │
 normalized graph ───▶ │  RewriteEnumerator                          │
 container annotations │    boundary-relevant opportunities →        │
                       │    choice variables (legality pre-checked)  │
 MetadataSource ─────▶ │  CostModel                                  │
   (world, manifests,  │    resource vectors × calibration → time    │
    stats snapshot)    │    sizes: declared/served stats + defaults  │
                       │  ProblemBuilder                             │
 profile + budget ───▶ │    graph+costs+pins+hints+choices+capacity  │
                       │    → PlacementProblem                       │
                       │  PlacementSolver  (interface, backends:)    │
                       │    ├─ MinCutSolver   (2-engine, cost-sum)   │
                       │    ├─ CpSatSolver    (k-engine, makespan,   │
                       │    │                  capacities; optional) │
                       │    └─ HeftSeeder     (warm start / bound)   │
                       │  PlanApplier                                │
                       │    solution → graph edits (derived          │
                       │    containers, rewrites, Materialize)       │
                       │  Explainer                                  │
                       │    decisions + costs + gap → explain payload│
                       └─────────────────────────────────────────────┘
```

Dependencies: the TTR-P front-half graph model, `ttr-metadata` (the `MetadataSource` client interface). **No Calcite dependency** — Calcite stays in `ttr-translator` at the emit boundary. The CP-SAT backend is an **optional module** (`ttr-optimizer-cpsat`, carries the OR-Tools native dependency); the floor (min-cut + HEFT) is dependency-free JVM (Z-g).

## 4. The optimization model

**Decision variables.** Per movable node: engine assignment. Per enumerated rewrite opportunity: apply/don't (choice variable, Z-f-β). Per fan-out feeding ≥2 consumers, and per cut edge: materialize (+index) or not. Derived: island boundaries = maximal same-engine connected subgraphs (respecting `together`), cut edges = engine-crossing data edges.

**Constraints.** Directive pins (`target`) fix assignments; `together` forces one island; hint deviations are free variables with penalty terms; **capacity**: per-island working-set `mem` ≤ engine memory calibration — infeasible placements are excluded; if *no* feasible placement exists → `TTRP-OPT-011` (GI-21). Control edges (FS/SS) are hard on their effect (B-T2): the wave/precedence structure they induce is a scheduling constraint, never dropped.

**Objective (Z 1.0): makespan.** Island duration = Σ node durations (engine internals not modeled — GI-1); node duration = fold of its manifest resource vector over instance calibration; transfer duration = bytes / pair rate; makespan = critical path over the precedence structure (data deps + FS/SS + wave semantics), with per-engine concurrency from calibration. Hint penalties join the objective as declared profile terms (GI-16-e).

**Budget tiers (GI-4).** `fast` = cost-sum assignment (min-cut exact for 2 engines; HEFT else) · `balanced` = CP-SAT with time cap, report best + gap · `thorough` = CP-SAT to optimality or generous cap. Anytime semantics: at cap, best-found-so-far + optimality gap in `explain`. Deterministic per tier (fixed seeds/params): same (graph, world, snapshot, profile, tier) ⇒ same plan (GI-15).

**Size estimation is deliberately dumb (Z-d):** declared/served base-table stats; fixed default selectivities per node kind for intermediates. Z's wins come from boundary volumes, not selectivity precision (evidenced by worksheet W3).

## 5. Metadata & statistics

Z is a client of **`MetadataSource`** (contracts §5) with two backings: **server-connected** (metadata served; fresh stats) and **serverless** (repo attached and read; declared floor). The interface has no method to reach an engine instance — Z-d-β is unrepresentable. One immutable **stats snapshot** per optimize pass; its **fingerprint** is recorded in the bundle manifest beside the world fingerprint. Degradation ladder (GI-19): no metadata source at pass start ⇒ `TTRP-OPT-001` hard error; source lost mid-session ⇒ continue on the held snapshot + `TTRP-OPT-002` stale warning. Correctness-compile remains fully offline (GI-15's D-g refinement).

## 6. Cost model: vectors, calibration, prices

Engine-type manifests are **TTR-M documents** (GI-21) carrying per-node-kind **resource vectors**: `cpu` (µs terms), `io` (bytes), `mem` (bytes; **feasibility dimension**, not cost), with unit grammar `/row` (v1; `/row-in`/`/row-out` reserved). Edges carry `xfer` (bytes). World **instances** carry `calibration` (rates, memory, concurrency) and `prices` (per-resource; empty in Z 1.0 worlds). Objectives are **folds** over resources — time in Z 1.0; $ in Z 2.0 with zero manifest changes (Z-b γ-direction). **Profiles** (GI-10) = named (objective, constraints, weights) tuples: shapes in the toolchain, instances in the world; Z 1.0 ships the single `makespan` profile.

## 7. Surfaces

- **Language (Z-e, amends B-T9):** `container [together] [target <e> | prefer <e>]`. Bare container = grouping only. No containers = Z draws islands. Z off ⇒ `prefer` = `target` (GI-22).
- **CLI:** `ttrp build --optimize [--profile <p>] [--budget fast|balanced|thorough]`; `[ttrp]` keys as project defaults (contracts §2).
- **`ttrp explain` / `ttrp/explain`:** placements (with pin/hint provenance), cut edges + volumes, rewrites applied, materializations, hint deviations + penalties paid, objective breakdown, optimality gap, snapshot fingerprints. The Designer renders derived containers per C1 derived-canvas rules (dashed, read-only, auto-layout) and exposes the budget as a user setting.
- **Bundle:** `manifest.json` gains the `optimizer` block + `stats` fingerprint (contracts §7).

## 8. Testing strategy

- **Unit:** cost-model folds (vector × calibration arithmetic against worksheet-W3 hand numbers); rewrite-legality predicates; ζ of the problem builder (same graph ⇒ same problem).
- **Component:** solver backends against known-optimal small instances (min-cut vs CP-SAT agreement on 2-engine cost-sum; capacity-infeasibility cases; budget determinism).
- **Golden plans:** the hero scenario (and an er-flavored variant) with pinned stats → snapshot-tested `explain` output; the W3 table is the first golden fixture (A ≈ 20.1 s … D ≈ 5.25 s).
- **Conformance:** `ttrp-conform` runs optimized vs unoptimized bundles — results must be Q9-identical for every plan Z produces.
- **Diagnostics table:** every `TTRP-OPT-*` id is a fixture with a triggering case (house convention).

## 9. Version roadmap (trigger-driven)

| Version | Adds | Trigger | Constrains now |
|---|---|---|---|
| **Z 1.0** | everything above | TTR-P v2 | — |
| **Z 2.0** | multi-objective profiles ($, deadlines; lexicographic/weighted/ε-constraint) | priced engines; night-batch profile | resource vectors + prices vocabulary (§6); objective-as-data in `PlacementSolver` |
| **Z 3.0** | δ memo **as generator**: rewrite-chain discovery emitting choice variables into the same solver | the Z-f tripwire (coupled-rewrite list outgrows pre-enumeration) | choice-variable representation is the generator's output format |
| long-term | elastic worlds (capacity as decision variable; stochastic objectives) | K8s/pod-spawning worlds | world passed as data, never ambient (GI-6) |

Plan caching (keyed by graph + world + snapshot fingerprints) deferred (GI-17): Z 1.0 optimizes per build.

## 10. Invariants (Z-specific, additive to TTR-P's)

1. **Z never changes results** — only placement/shape within Q9 equivalence; `ttrp-conform` enforces it.
2. **Z never calls an engine instance** — metadata interface only, enforced by the type system.
3. **Z's output is ordinary graph vocabulary** — downstream stages are Z-agnostic.
4. **Determinism:** same (graph, resolved world, stats snapshot, profile, budget tier) ⇒ same plan; the snapshot fingerprint makes this auditable.
5. **Pins are sacred:** a `target` directive is never overridden; a hint deviation is always visible in `explain` with its penalty.
6. **Explicit failure:** >100 nodes, no feasible placement, no metadata — named diagnostics, never silent degradation (P2).
