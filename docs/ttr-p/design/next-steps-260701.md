# PL Design — Next Steps (pick-up point, written 2026-06-30 for 2026-07-01)

> Where to resume the TTR Processing Language design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Full option catalogues: [`01-design-space-map.md`](./01-design-space-map.md), [`02-internal-model-options.md`](./02-internal-model-options.md).

## Where we are

- **Workstream A (vision/scope) — CONVERGED.** PL = separate TTR-family language referencing TTR models; multi-engine from v1; all 5 surfaces in v1; personas DE + analyst (data scientist later); no writes beyond materialize+Charon; success criteria set; principle **P1** (small core, rich edges).
- **Workstream B (internal model) — IN PROGRESS, structural heart done.** Deep-dive running in `02-internal-model-options.md`. Closed: ports/edges/control/error (T2), movement+materialize+container (T9), transform node set + engine-relative primitivity (T10), node-set/rewrite framing (T1/T8). The model is coherent and mostly settled.

## Immediate next — resume B in this order (agreed with Bora)

### 1. T5 — Expression sublanguage (DO FIRST)
The language *inside* Filter predicates, Project/Calc computed columns, Join conditions, Switch/Branch conditions, Aggregate calls. Foundational — workstream C (surfaces) can't proceed without it. Questions to open:
- Reuse Kantheon `plan.v1.Expression` (function-call tree: `call{function, operands}`, `field_ref`, `literal`) as the IR, adapt it, or design fresh?
- **Expressions are engine-relative too** — which functions each engine supports parallels node capability (T8); an expression may need lowering (e.g. a function missing on an engine). Same two-rewrite-kinds logic?
- Function catalogue — relationship to TTR's MD **calc catalog** (`monthOfDate`, `sum`, …)? One catalogue or two?
- Typing: static per T7; type-checking + coercion rules; NULL semantics.
- Literals, operators, precedence.
- How expressions surface in each language (opaque strings vs structured) — cf. RAE/Kyx used string expressions; TransDSL/DFDSL used structured trees.

### 2. T3 — Relationship to Kantheon `PlanNode` + Calcite reuse
- Adopt / adapt `PlanNode`, or a sibling that lowers to it?
- Use Calcite's `RelOptRule` / `HepPlanner` to *implement* T8's rewriting for the relational subset (T8-a)? Serialization / canonical form (protobuf vs text, ↔ G "text is canonical").

### 3. T4 — Multi-statement / variables / reuse
How a program is structured; containers already give us "functions" (T9). Variables (RAE-style binding) vs pure graph.

### 4. T6 — Engine capability manifests
Mostly framed already (engines declare supported node + function subsets; containers bear targets; drives T8 rewriting). Needs formalizing.

## Smaller open items to sweep (don't forget)
- **T7** details: what counts as "final step" for the dynamic-schema exception; schema sourcing (T7-b); pivot-as-final-only? (↔ T10 pivot with static/declared values — reconcile.)
- **T10** leftovers: Distinct / Intersect / Except (sugar vs per-engine primitive); Join semi/anti (native vs rewrite); Explode/Unnest in v1?; does pandas rewrite Branch or use boolean masks.
- **T2-c** error retry/compensation → belongs to workstream F.

## After B
Per the design-space map: **C** (surface languages — start with C0 relationship, then per-surface; the hero scenario should be authorable in ≥2), **D** (data objects / TTR model binding + MD sugar), **E** (transpilation/codegen per target), **F** (orchestration — note it *emerges from container-collapse*, T9), then **G/H**, with **Z** (optimizer) parked to v2.

## Key mental model to reload (one paragraph)
One graph of operation **nodes** connected via typed **ports** (data/control-bearing; named + default; multicast yes, no implicit union). Control edges carry **events** (v2; v1 = static FS/SS/FF, acyclic). Nodes sit on a **physicality spectrum**; before running on an engine we **iteratively rewrite** the graph until that engine can process it (**primitive/macro is engine-relative**). Two rewrite kinds: authoring sugar (engine-independent) and capability lowering (engine-driven). **Containers** group nodes, act as functions, and **bear the execution target**; collapsing containers yields the **orchestrator graph**. Movement is explicit: **Load/Store** cross the engine-memory boundary, **Transfer** is a Charon call (physical↔physical, may convert); **Materialize** is an optimization-only macro → Store+(Index)+Load. Charon **moves only**, never persists.
