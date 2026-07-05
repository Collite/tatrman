# E — Transpilation & Emit: Option Catalogue (DIVERGENCE)

> **Naming note (consolidation sweep, 2026-07-04):** this doc predates the H rename + S-sweep. Read `PL_CONN_*` as `TTR_CONN_*`, `pl-conform` as `ttrp-conform` (S3), `pl explain` as `ttrp explain` (S4), `[pl]` as `[ttrp]` (S5). Standing contracts live in `../architecture/contracts.md`.

> **Mode: divergence.** Enumerate alternatives + trade-offs. **No decisions here** — those go to the control-room decision log.
> Control surface: [`00-control-room.md`](./00-control-room.md). Internal model: [`02-internal-model-options.md`](./02-internal-model-options.md) §T3/§T5/§T6. Model binding: [`06-model-binding-options.md`](./06-model-binding-options.md). Review findings: [`review-260702.md`](./review-260702.md) §4.2/§4.1/§5.
> Opened 2026-07-03.

**The question E must answer.** How does the normalized graph become *runnable things* — per-island payloads (SQL, Polars/pandas scripts) inside a compiled artifact — such that A4's "identical results across engines" is checkable, and without coupling the compiler to anything it can't reach offline.

## What's already banked (constraints, not options)

- **T3-γ**: PL owns its model; the **relational island** lowers to `plan.v1.PlanNode` at the E boundary for SQL-family engines; dataframe islands emit separately.
- **T5**: canonical **SQL three-valued NULL** everywhere — engines that don't match get *enforcing* codegen; one expression grammar; catalogue functions; capability misses re-place whole nodes (T5-b).
- **T6/D-g**: the compiler works **offline** against the declared world; invocation bindings choose per-container delivery; **v1 execution engine = bash**.
- **G-g**: kantheon consumes **compiled plans**, never the PL compiler.
- **C3-d-iv / D-f**: movement synthesized through declared staging.
- **Preserved-shape principle** (adjacent Calcite-translator effort, review §5): *prefer custom preserved-shape operators over Calcite's lossy semantic rewrites.*

---

## E-a · What does the compiled artifact contain — and the Proteus coupling problem

T3's arrow "PlanNode → Proteus → Calcite → SQL" hides a contradiction: **Proteus is a kantheon runtime service; the PL compiler compiles offline** (D-g), and modeler is *upstream* of kantheon (kantheon consumes `org.tatrman:*`; the reverse would be circular). Who turns the relational island into SQL, and what lands in the artifact?

- **E-a-α · Compiler emits CONCRETE payloads; PL embeds Calcite itself.** The PL compiler (JVM, per Q5) owns graph→RelNode→dialect-SQL directly — same *machinery* as Proteus (Calcite), zero *dependency* on it. The artifact contains final SQL text + dataframe scripts. `plan.v1.PlanNode` remains an **export format** (below), not a compile-time dependency.
  - *Buys:* offline compile holds; coupling direction stays modeler→(nothing); bash artifact is self-contained; the preserved-shape principle is *ours to implement* (Proteus's unparse choices don't constrain us).
  - *Costs:* two Calcite-unparse codebases in the org (PL's and Proteus's) — drift risk between "what PL emits standalone" and "what Kantheon runs from PlanNode"; mitigated only by the Q9 conformance harness.
- **E-a-β · Compiler emits PlanNode protos; SQL is produced at runtime** (by Proteus in Kantheon, by "something" in bash-land).
  - *Buys:* one SQL emitter org-wide (Proteus).
  - *Costs:* bash-standalone would need a runtime translator — i.e., shipping half the compiler in the artifact; offline "what SQL will run?" becomes unanswerable at compile time (bad for review/debug); contradicts the artifact-as-reviewable-text instinct.
- **E-a-γ · Both from v1:** artifact carries concrete payloads **and** exported PlanNode protos per relational island (the Kantheon path consumes plans per G-g; bash ignores them).
  - *Buys:* the Kantheon integration is contractual from day one.
  - *Costs:* exporting = *supporting*: plan.v1 version pinning, conformance between the two forms — a standing contract before any consumer exists.

*Lean: α, with γ's export as a **later, additive** artifact flag once a Kantheon consumer is real. PlanNode stays the internal boundary shape (T3) — exporting it is cheap when needed.*

## E-b · SQL emit shape (the preserved-shape principle applied)

Given graph→RelNode→SQL in-compiler, what does the SQL *look like*?

- **E-b-α · CTE-per-node.** Each graph node in the island becomes a named CTE (`WITH sales AS (…), j AS (…) SELECT …`); **SSA variable names survive as CTE names**.
  - *Buys:* the SQL mirrors the authored graph — debuggable, diffable, lineage-readable; preserved-shape at the structural level; a DBA reads the artifact and sees the program.
  - *Costs:* verbose SQL; some optimizers handle deep CTE chains worse than nested subqueries (PG12+ inlines CTEs, mostly moot); trivial single-node islands get ceremonial WITH.
- **E-b-β · Nested subqueries** (Calcite default unparse). *Buys:* zero custom unparse work. *Costs:* authored structure destroyed; names lost; exactly the "lossy rewrite" the principle warns against.
- **E-b-γ · Collapse/optimize at emit** (let Calcite's optimizer restructure). *Costs:* that's Z's job (parked v2); non-deterministic-looking output; violates preserved-shape outright.
- Sub-point: trivial islands (one Scan+Filter) emit flat SELECT without WITH — a deterministic formatting rule, not an optimizer.

*Lean: α with the flat-trivial rule.*

## E-c · Dataframe emit — structure, NULL enforcement, and v1 engines

**Structure:**
- **E-c-α · Straight-line script.** One statement per node, SSA variable names carried (`sales = sales.filter(…)` — the emitted Polars mirrors the canonical text almost 1:1).
- **E-c-β · Runtime-harness library.** Artifact scripts call a shipped `pl-runtime` package (node functions, NULL helpers, error-port capture); scripts become thin op-lists.
- **E-c-γ · Hybrid: straight-line + minimal inline prelude.** Generated code is plain Polars, with a small *generated* (not imported) prelude for the few enforcement helpers a given program needs — artifact stays dependency-free and readable.
  - α maximizes readability but scatters enforcement patterns; β centralizes semantics but adds a versioned runtime dependency to every artifact (and a "what's in the runtime" contract); γ keeps artifacts self-contained at the cost of duplicated helper text.

**NULL/semantics enforcement (T5-d debt):** Polars is near-SQL natively (null propagation, joins); pandas is not (NaN≠null, `groupby` drops NaN keys unless `dropna=False`, NaN join-key behavior). Enforcement = emit patterns + the Q9 harness proving them.

**v1 engine set (review §3.6 "decide pandas-in-v1"):** the hero needs **Postgres-family SQL + Polars**. Note the decoupling C0 already gives us: **TTR-pandas the *dialect* ≠ pandas the *engine*** — a TTR-pandas fragment compiles to whichever engine its container targets (Polars in v1). So deferring the pandas *engine* costs no surface promise.
- *Lean:* v1 data engines = **{Postgres, Polars}**; pandas engine = v1.x (its emitter + enforcement patterns are the α/γ machinery re-instantiated); Kotlin-DF later.

## E-d · Where does the er tier get rewritten? (attribute→column, relation-joins)

- **E-d-α · Early — normalization sugar stratum.** er refs desugar to db refs (+ expanded join conditions) right after resolution, in T8's first stratum (sugar ≺ …). The rest of the pipeline sees only db-tier.
  - *Buys:* one tier through normalize/fission/placement/emit; er is *exactly* authoring sugar (consistent with Select/Calc/HAVING); emit stays dumb.
  - *Costs:* diagnostics after the rewrite would speak column names unless provenance is kept.
- **E-d-β · Late — at emit.** Logical names flow through the whole graph; each emitter maps them.
  - *Buys:* every intermediate artifact (graphical view, diagnostics) speaks the author's language natively.
  - *Costs:* every emitter + the capability checker must understand two tiers; manifest matching on logical names is fiction (engines know columns).
- **E-d-γ · α + mandatory provenance:** early rewrite, but nodes/expressions carry origin (`customerType ← er erp.er.customer.customerType`); diagnostics and the graphical view render through provenance; lineage queries use it.

*Lean: γ — T8-consistent, and provenance was already the lineage answer (C0: lineage derived, never authored).*

## E-e · Q9 — the A4 "identical results" equivalence procedure (proposal to confirm)

Not a fork — a procedure to pin. Proposal:

1. **Schema equivalence:** canonical PL type set maps to an **Arrow schema**; compare via Arrow schema fingerprint (Kantheon prior art). Engine-native type quirks must normalize to the canonical type or the program doesn't compile (T7 static schemas).
2. **Row equivalence:** result = **multiset** (order-insensitive) unless the island's terminal node is `Sort` — then order-sensitive *on the sorted prefix of columns only*. Comparison implementation: canonical-sort both sides by all columns, compare streams.
3. **NULL ordering:** irrelevant under (2) except terminal Sort → canonical **NULLS LAST** in emitted Sort on every engine (enforced in codegen, not assumed).
4. **Numerics:** `decimal` exact (the default for money/hero); `float64` compared within declared tolerance — **per-program conformance annotation, default exact**; if exact fails and no tolerance declared, the conformance run fails (P2: no silent epsilon).
5. **Datetime:** canonical UTC, microsecond precision; sub-µs engines truncate at Load/Store boundaries (emitted, not assumed).
6. **Strings/collation:** v1 comparisons and sorts are **binary/UTF-8 codepoint**; locale collation is out of v1 (emitted `COLLATE "C"`-equivalent where engines default otherwise). Locale-sensitive sort = declared v1 non-goal.
7. **Delivery:** both sides export **Arrow IPC**; a `pl-conform` harness tool does (1)–(6). This doubles as the E regression suite (and the PL↔PlanNode drift check from E-a).

## E-f · Q8 — cross-engine RLS stance (record, don't design)

- **E-f-α · v1 = trusted principal, declared.** The compiled artifact runs under the credentials the world's named connections carry; RLS enforcement belongs to each engine at runtime; PL adds **no** policy propagation. Plus a cheap tripwire: world storage may declare `rls: true` → any synthesized/explicit Store/Transfer *out* of it emits a **compile warning** ("data leaves an RLS-governed engine under the executing principal").
- **E-f-β · Compile-time refusal:** same flag, but crossing is an **error** unless the program carries an explicit override. Safer, more friction.
- **E-f-γ · Row-filter propagation** into every island (real policy compilation) — v2/Kantheon territory (Argos exists there).

*Lean: α for v1 (with the flag + warning), β available as a project-defaults strictness knob (`[pl] rls-egress = warn|error`), γ explicitly v2. Either way the decision is finally recorded.*

## E-g · Transfer is engine-relative too (amends B-T9's wording)

B-T9 says "Transfer = a Charon call." Under T6's invocation bindings that's too narrow: in the **Kantheon world**, Transfer binds to Charon; in the **bash-standalone world**, Transfer must bind to native tools (`psql \copy` out to staging, file copy, etc.) chosen from the (storage, executor) manifest pair — the same fractal as everything else.

- **E-g-α · Generalize:** Transfer is an abstract movement node; its delivery is an invocation binding; Charon is its Kantheon-world binding. (Amends B-T9 wording, preserves its intent: Transfer still only moves, never persists beyond staging semantics.)
- **E-g-β · Keep Charon-only:** every world must run Charon (bash artifacts shell out to a Charon CLI?). Honest cost: a hard runtime dependency for standalone artifacts; Charon isn't shipped that way.

*Lean: α — arguably already implied by T6, but B-T9's text should be amended consciously.*

## E-h · Artifact bundle shape (sketch — finish in F-lite)

Working sketch, to be finished with F-lite's executor design: a **directory bundle, all reviewable text** — `run.sh` (orchestrator, v1 bash), `islands/<container>.<sql|py>` (payloads, preserved-shape), `transfers/` (movement commands per binding), `plan.ttrp?`/manifest (what runs where, in what order — format TBD), plus declared schemas for staging. Committed to the repo like any build output the team wants reviewed. Open: single-file variant? checksums/world-fingerprint pinning (artifact records *which world* it was compiled against — T6 verification hook)?

---

## RESOLVED (2026-07-03) — E converged

All threads decided in-session (full text in the control-room decision log):

- **E-a = α′ extended**: the **Proteus translation core moves to modeler** as a published `org.tatrman` library (name → H); kantheon's Proteus becomes a thin wrapper — the metadata/Ariadne pattern. PL compiler embeds it, compiles offline. **Artifacts carry concrete payloads; PlanNode emission is world-driven** — a Kantheon execution target in the world makes the invocation binding deliver `plan.v1` plans; any other target gets dialect SQL. No standing export contract. *Cross-repo consequence: a kantheon extraction arc, planned there.*
- **E-b = CTE-per-node** preserved-shape SQL; SSA names survive as CTE names; flat-SELECT rule for trivial islands.
- **E-c = γ**: straight-line dataframe scripts + generated inline prelude (dependency-free artifacts); **v1 engines = {Postgres, Polars}**, pandas engine v1.x (dialect ≠ engine).
- **E-d = γ**: er rewrite early (T8 sugar stratum) with mandatory provenance.
- **E-e / Q9 = confirmed** seven-point equivalence procedure; `pl-conform` over Arrow IPC; doubles as emit regression + standalone-vs-Kantheon drift guard.
- **E-f / Q8 = trusted principal + tripwire** (`rls: true` storage flag → egress warning; `[pl] rls-egress = warn|error`; propagation v2).
- **E-g**: Transfer generalized to an abstract movement node with per-world invocation bindings (Charon = the Kantheon binding) — **amends B-T9's wording**.
- **E-h**: bundle shape deliberately deferred to F-lite (it is the executor's artifact).

## Open questions (E-local)

- Dialect coverage v1: Postgres only, or Postgres + MSSQL (Brontes exists)? (Hero needs one SQL dialect.)
- Where do emitted-SQL golden tests live (per-dialect snapshot corpus)?
- ~~Does the artifact embed the world fingerprint (T6), and what's the fingerprint?~~ — **answered in F-lite (`08` F-f-ii): yes; semantic fingerprint of the resolved world model + qname in clear; artifact records, capable invoker verifies.**
- `EXPLAIN`-style tooling: `pl explain` showing island → payload mapping (G/LSP method later)?

## Cross-links

E → T3/T5 (PlanNode boundary, expression lowering) · E → T6 (invocation bindings; world fingerprint) · E → D (named connections carry credentials; staging) · E → F-lite (artifact + executor; FF/Q10) · E → Q9 (harness = A4's teeth) · E → Q8 (recorded stance) · E → G (conformance CI; `pl explain`) · E → Z (emit-time optimization explicitly out — preserved shape).
