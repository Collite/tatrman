# PL Design — Next Steps (pick-up point, written 2026-07-02)

> Where to resume the TTR Processing Language design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Option catalogues: [`01-design-space-map.md`](./01-design-space-map.md), workstream B → [`02-internal-model-options.md`](./02-internal-model-options.md), workstream G → [`03-tooling-delivery-options.md`](./03-tooling-delivery-options.md).
> Supersedes [`next-steps-260701.md`](./next-steps-260701.md).

## Where we are

- **Workstream A (vision/scope) — CONVERGED** (unchanged).
- **Workstream B (internal model) — IN PROGRESS, most heavy forks closed.** T2/T9/T10/T8 (structural heart) + T5 (expressions) + T3 (IR relationship) all decided. T4 seeded this session. Remaining: **T6**, finish **T4**, then B-leftover sweep.
- **Workstream G (tooling & delivery) — mostly CONVERGED this session.** G-b decided (the crux); component roster set. Only light leans left to ratify.

### Decisions banked 2026-07-01/02 (see decision log for full text)
- **B-T3 = γ** — PL owns its model (+ own optimizer later); **delegate relational islands to Calcite** (via Proteus). ⇒ **T5-a = adapt** (PL owns its `Expression` twin, lowers to `plan.v1.Expression` for SQL islands). Calcite reuse = two-tier.
- **B-T5 NULL = canonical SQL three-valued** (`NULL = NULL` → NULL). Forced by A4; hard constraint on codegen (E).
- **B-T5 expression capability misses resolve at NODE granularity** — no-rewrite function ⇒ the **whole node** re-places to a capable engine (never mid-expression); policy = split-with-warning, configurable (auto vs refuse). Closed T5-b-fork.
- **G-b = WS-LSP + repo-attached Designer server; Kotlin-only, no KMP.** Designer is a thin frontend; the **Designer server** attaches to the model repo and is the backend. Kills the KMP/JS/Python/conformance-harness complexity: **single Kotlin parser.** Restores "one LSP across hosts" (stdio for IDEs + WS for the Designer server).

### Concepts introduced this session (in the docs, not yet all formalized)
- **PL is a COMPILER; Kantheon is a RUNTIME.** ⇒ PL owns a **compile-time world/reference**; it *imports* Kantheon workspace *metadata* but does not reuse Kantheon's runtime refs. Runtime binding is a later additive layer.
- **The compile-time WORLD** (reuse Tatrman's `com.tatrman.world.World` **as ideas, not code**): Storage vs Compute environments; capability relations (`canRead/canWrite/canQuery/canMove`, `getEnvForTransfer`) that *are* T6 + Charon-transfer feasibility; per-program internal-world copy = the compile-time sandbox where instances are minted. Rename Tatrman's `TableContainer` (storage grouping) so it doesn't clash with PL's execution `Container` node (T9) — use *schema*/*namespace*.
- **Table = envelope; Table instance = a data-version in it. This is SSA-for-storage.** Needed because a graph can `Store` and `Load` the same table; the optimizer preserves read/write ordering on the *same* envelope, reorders freely across *different* instances. Anonymous instances on intermediate dataflow edges; explicit tracked instances on named world tables. An instance dependency is a **hard** ordering edge (generalizes T2-e).

## Immediate next — resume order (agreed with Bora)

### 1. T6 — Engine capability manifests (DO FIRST; mostly handed to us by T4's world)
Formalize: each environment/engine declares its supported **node subset** (T10) **+ function subset** (T5-b) **+ read/write/move relations**. This is the richer-than-Tatrman version of the world's capability maps. Drives T8 rewriting + the node-level re-placement decision (T5-b). Feeds author-time capability *checking/preview* in the Designer server.

### 2. T4 — finish multi-statement / variables / reuse
- **Q7:** are surface variables (RAE `X = filter(Y,…)`) just **sugar for named instances/edges**, or a real **binding construct**? (↔ C surfaces.)
- Multi-statement program structure; containers-as-functions (T9) already give reuse.
- `WorkspaceRef` reuse at **runtime** only (compile-time equivalent = a table-instance in the internal world).
- Reconcile PlanNode's `Scan`→`TableScan` step with PL's `Load` resolving against the world.

### 3. B-leftover sweep
- **T5 still-open:** aggregate expressions as a distinct arm (`AggregateCall`) vs unified `FunctionCall`; subquery-valued expressions in v1?; parameters/bind values in v1 or F/v2.
- **T7:** what counts as "final step" for the dynamic-schema exception; schema sourcing (T7-b, ↔ D); pivot-as-final-only (reconcile with T10 static-pivot).
- **T10:** Distinct/Intersect/Except (sugar vs primitive); Join semi/anti (native vs rewrite); Explode/Unnest in v1?; does pandas rewrite Branch or use boolean masks.

### 4. Ratify the cheap G leans (housekeeping)
- **G-a** same monorepo · **G-e** text canonical (layout/ports serialized into PL text, v1.1 layout-block precedent) · **G-g** kantheon consumes compiled plans (`ttr-metadata` is the one published+consumed artifact).

## After B + G
Per the design-space map: **C** (surfaces — start C0 relationship, then per-surface; hero scenario authorable in ≥2), **D** (TTR model binding + MD sugar; note the MD-catalog absorption question from T5-c lands here), **E** (transpilation/codegen per target; canonical NULL is a hard constraint here), **F** (orchestration — emerges from container-collapse, T9), then **H**, with **Z** (optimizer — now explicitly "our own", B-T3) parked to v2.

## Open questions to hold (rolling)
- Does **TTR's own designer** converge onto the same JVM Designer server (embedding `ttr-semantics`), or keep its TS browser-worker LSP? (One backend vs two.)
- **Designer server** multi-user/auth — local single-user v1, or shared/hosted (→ auth/RLS)?
- **`ttr-metadata` vs `md-catalog`** — does the new JVM metadata component subsume the (TS, data-only) `md-catalog`, or sit beside it? (↔ D.)
- Q1 (A): is the AI agent a first-class surface user? (drives G-g-β runtime compilation.)

## Key mental model to reload (one paragraph)
One graph of operation **nodes** connected via typed **ports** (data/control-bearing; named + default; multicast; no implicit union). Nodes sit on a **physicality spectrum**; before running on an engine we **iteratively rewrite** (T8 fixpoint) until that engine can process the graph — **primitive/macro is engine-relative**, two rewrite kinds (authoring sugar vs capability lowering). **Containers** group nodes, act as functions, bear the execution target; collapsing them yields the **orchestrator graph** (F). Movement is explicit: **Load/Store** cross the engine-memory boundary, **Transfer** is a Charon call, **Materialize** is an optimization macro → Store+(Index)+Load. **Expressions** are a structured tree (PL's own, adapted from `plan.v1.Expression`, lowering to it for SQL islands); NULL is canonical SQL three-valued; a function an engine can't do forces **whole-node** re-placement, never a mid-expression split. **PL is a compiler** over a **compile-time world** of Storage/Compute environments with capability manifests (T6); the graph's data model is **(table envelope, table instance)** where instances are SSA-style data-versions. The **internal model is PL's own (γ)**; the **relational island** lowers to Kantheon `PlanNode` → Calcite → SQL, while dataframe islands emit separately. **Text is canonical**; the toolchain is **Kotlin/JVM** (parser→semantics→normalizer→Calcite back-half), served to a thin **Designer frontend** by a repo-attached **Designer server** over WS, and to the IDEs over stdio — **one LSP across hosts**.
