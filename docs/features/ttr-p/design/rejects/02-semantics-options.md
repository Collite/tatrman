# R-A / R-B — Reject Semantics & Stream Contract: Option Catalogue (DIVERGENCE)

> **Mode: divergence.** Enumerate alternatives + trade-offs. **No decisions here** — those go to the
> control-room decision log. Control surface: [`00-control-room.md`](./00-control-room.md).
> Opened 2026-07-15.

**The question this doc must answer.** Before any engine can *produce* rejects, the model must say
what a reject *is* (R-A) and what the stream *carries* (R-B) — statically typed (T2-a), 3VL-clean
(T5), and conform-comparable (A4/Q9).

## What's already banked

- C3-f: `rejects` is data-shaped, per-node, reserved; unconnected = fail-fast.
- T2-c: both error modes (signal + erroneous-rows) exist in the model.
- `cast(NULL) = NULL` is a **success** — 3VL is never a failure. Only genuine evaluation
  failure rejects.
- The hero fixtures give two concrete sites: RH-1 (cast in a cleaning island) and RH-2 (`j.rejects`
  on a join — currently semantically undefined; see R-Q1 on its `err rejects` header oddity).

---

## R-A1 · The trigger set — what turns a row into a reject?

- **R-A1-α · Evaluation failure only.** A reject is a row on which the node's row-level function is
  *partial*: the engine would raise. Concretely: cast/parse failure, division by zero, arithmetic
  overflow, invalid date arithmetic, malformed source record at `load`.
  - *Buys:* smallest coherent semantics; exactly the thing fail-fast already means (connecting the
    port converts "abort" into "divert" — one concept, two routings); no new syntax.
  - *Costs:* real pipelines also want *policy* rejects ("negative quantity is bad data") — under α
    those stay `filter`+wiring, authored by hand (arguably fine: it *is* ordinary dataflow).
  - *Prior art:* SQL Server SSIS error outputs (truncation/conversion errors), Polars strict modes.
- **R-A1-β · α + declared expectations.** Nodes (or a dedicated `expect` node/clause) carry
  author-declared predicates; violators divert to `rejects` alongside evaluation failures.
  - *Buys:* the data-quality story becomes first-class (dbt tests, AWS Deequ, Databricks DLT
    `EXPECT … ON VIOLATION DROP ROW` are all this); one port carries both kinds of "bad row".
  - *Costs:* scope bloat — expectations are *declarative sugar over filter+branch*, which the
    language already expresses; conflates "the engine could not compute" with "the author disliked
    the value" in one stream (metadata can distinguish, R-B1-β); new grammar in every surface.
  - *Note:* β can be layered on α later without breaking anything — an `expect` desugars to a
    Branch whose false-side unions into `rejects`. That is an argument to *decide α now, park β*.
- **R-A1-γ · α + structural misses.** Join-unmatched rows, duplicate-key hits, etc. divert.
  - *Buys:* matches the graphical-ETL mental model (Alteryx J/L/R anchors, SSIS lookup error output);
    makes RH-2's `j.rejects` mean something obvious.
  - *Costs:* unmatched ≠ erroneous — an inner join *by definition* drops non-matches; calling them
    rejects overloads the port (and `join(type: left)` already expresses "keep them"). Duplicate
    "misses" are engine-relative. Muddies the partition invariant (R-B2): for a join, `out ⊎ rejects`
    is *not* the input.
  - *Prior art for rejecting γ:* the language already has `.true/.false` Branch anchors and join
    types for structural routing — γ's use cases are expressible today.

*Lean: α for the core semantics, with β parked as a later desugar (parking lot) and γ rejected as
port overloading — but RH-2 then needs re-reading (see R-A2 join row).*

## R-A2 · The node-kind matrix — who can reject, and what does it mean?

Walked against R-A1-α (evaluation failure). The matrix is the *contract* R-C/R-D implement.

| Node | Can reject? | The failing thing | Notes / forks |
|---|---|---|---|
| `load` | **yes** | source record does not deserialize to the declared schema (malformed CSV line, unparsable field) | the classic ETL reject; overlaps R-C1-ζ native capture. Under T7 (schema-on-read banned, declared schemas) a load reject = "record violates declared schema". |
| `calc` | **yes** | any expression arm raises: cast, `/0`, overflow, date arithmetic | *the* workhorse (RH-1). Which functions are reject-capable is a **catalogue property**, not a node property → R-A3. |
| `filter` | **yes (derived)** | the *predicate* raises while evaluating (cast inside predicate) | the predicate's boolean result routing stays `out`; only evaluation *failure* rejects. |
| `join` | **restricted** | the ON-expression raises during evaluation | the failing unit is a *candidate pair*, not a row → R-B3. Unmatched rows are **not** rejects under α (γ rejected). RH-2's `j.rejects` is then either (i) legitimately "ON-eval failures" (rare: ON is usually equality over clean keys) or (ii) a hero-authoring artifact to fix. |
| `aggregate` | **problematic** | `sum` overflow, avg of empty… — failure is *group-level* | attribution options: reject the whole group's **input rows** (semantically honest, expensive to produce), reject a **group row** (schema = output schema — breaks "rejects carry inputs"), or **aggregates are `err`-only** (failure aborts; no row rejects). → R-Q5. |
| `branch` | **yes (derived)** | its predicate raises | same as filter; the Branch lowering already routes `rejects` onto the true-side filter (`Rules.kt`) — consistent. |
| `store` | **parked** | write-side constraint violations (UNIQUE/CHECK/FK) | real (Oracle `LOG ERRORS INTO`, MSSQL `IGNORE_DUP_KEY`), but touches A3's write-scope boundary → parking lot. |
| `union`/`select`/`sort`/`limit` | **no** | total functions — nothing row-level can fail | (sort comparisons are total under canonical collation, E-e-6.) |

- **R-A2-α · Uniform port, sparse capability:** every node keeps the reserved port (C3-f stands);
  *which* nodes can actually emit rows on it is derived per-node-kind + per-expression from the
  catalogue ("this node's expressions contain ≥1 reject-capable call"). Wiring `rejects` on a node
  that provably cannot reject = author-time **warning** (dead wire), not error.
- **R-A2-β · Port only where rejectable:** the model drops `rejects` from non-rejectable node kinds.
  *Costs:* breaks C3-f's uniformity ("two reserved ports on every node"), complicates the Designer
  and the builder; saves nothing (an unwired port is free).

*Lean: α — keep C3-f uniform, derive rejectability, warn on dead wires.*

## R-A3 · Failure identity — who defines "this row fails"?

The A4 crux. `' 12 '`, `'+12'`, `'1e3'`, `'  '`, locale decimals, `2**63`, `'0000-00-00'` — PG's
`int4in`, MSSQL's `TRY_CAST(… AS int)` and Polars' `cast(pl.Int64)` each accept a *different*
lexical space. If failure is engine-defined, the same program produces **different rejects streams**
per engine and A4 is dead on arrival.

- **R-A3-α · Engine-defined failure.** A reject is whatever the executing engine raises on.
  - *Buys:* zero spec work; native forms usable directly; fastest possible emit.
  - *Costs:* rejects streams diverge across engines → the rejects port must be **excluded from
    conform** (a declared A4 exception) — which quietly makes rejects second-class results and
    poisons the "placement is a free choice" story (moving a container changes *which rows are bad*).
- **R-A3-β · Catalogue-defined failure (canonical validity).** For every reject-capable catalogue
  function, the catalogue specifies the **validity domain** (e.g. `cast(text→int)`: optional sign +
  digits, optional surrounding ASCII whitespace, value within int64 — *exact spec per type pair*).
  Engines *implement* it: native try-forms are used where provably equivalent, otherwise **enforcing
  emit** (regex/bounds guard) coerces the engine to the canonical domain — the same move as
  canonical-3VL-everywhere.
  - *Buys:* A4 holds on rejects streams; placement stays semantics-free; the validity spec doubles
    as documentation and as the conform fixture generator.
  - *Costs:* someone must *write* the validity specs (per function × type pair — the reject-capable
    set is small in v1.x: casts, `/`, maybe datetime parse); enforcing emit costs runtime cycles on
    engines whose native domain is wider/narrower.
- **R-A3-γ · Catalogue-defined, engine-widened.** Canonical spec as β, but engines may *accept more*
  (rows canonical-invalid but engine-parsable go to `out`, not rejects).
  - *Buys:* cheaper emit (only reject what the engine rejects, guard only the gap).
  - *Costs:* still breaks A4 (a `' 12 '` row lands in `out` on PG and in `rejects` on an engine
    with a stricter parser); "widened" is just α with extra steps. Recorded to map the space.

*Lean: β — it is candidate principle R-P2 verbatim. This is the fork to converge first; nearly
everything in `03`/`04` instantiates it.*

---

## R-B1 · Rejects-stream schema

- **R-B1-α · Input row verbatim.** `rejects` schema = the node's input port schema, rows as the
  node saw them.
  - *Buys:* trivially static (T2-a: it *is* the in-port schema); the row is re-processable (fix and
    replay); zero invention.
  - *Costs:* no "why" — the consumer can't tell which expression failed, needed the moment a node
    has two reject-capable exprs (R-Q7).
- **R-B1-β · Input row + canonical error metadata.** Verbatim row plus reserved columns, e.g.
  `_reject_code` (canonical diagnostic id, `TTRP-RJ-…`), `_reject_expr` (SSA/expression id),
  optionally `_reject_node`. **Canonical codes, never engine message text** (R-Q6 — engine text
  breaks A4 and leaks dialects).
  - *Buys:* debuggable + routable (filter rejects by code); still statically typed (schema =
    in-schema ⊕ fixed metadata columns, derivable at build time); codes join the diagnostics-table
    regime the toolchain already runs (C2-g named diagnostics).
  - *Costs:* reserved column names can collide with user columns (needs a reserved prefix rule à la
    S10); the metadata is *per-error*, forcing the R-B4 dedupe question; movement/conform must treat
    metadata columns as ordinary data (they are).
- **R-B1-γ · Error envelope.** Fixed schema (`code`, `node`, `payload`), the offending row
  serialized into `payload` (JSON/text).
  - *Buys:* one universal rejects schema program-wide — rejects from different nodes union freely.
  - *Costs:* destroys typing (payload is a blob — T7's spirit violated); un-replayable without a
    parser; engine JSON-serialization of arbitrary rows is its own conformance nightmare.
- **R-B1-δ · Author-declared projection.** The port declaration picks columns (`rejects(code, id,
  raw_qty)`). *Buys:* lean streams. *Costs:* new grammar; static checking is fine but the default
  still must exist — δ is a *refinement* of α/β, not an alternative.

*Lean: β with a reserved prefix; α is the degenerate no-metadata case and could be the v1.x step
with β's columns added compatibly. Union-of-rejects across nodes stays explicit (B-T2: no implicit
union) and requires schema compatibility as with any union — envelope-γ's "free union" is explicitly
not a goal.*

## R-B2 · The partition invariant

- **R-B2-α · Exact disjoint partition.** For a rejects-capable single-in node:
  `multiset(in) = multiset(out-as-inputs) ⊎ multiset(rejects)` — i.e. every input row lands in
  exactly one of {processed, rejected}. (For `filter`: processed = predicate-evaluated, whichever
  way it routed.)
  - *Buys:* crisp, testable (conform can count); the mental model "divert, don't duplicate, don't
    drop"; makes guard-and-branch (R-C1-α) *provably correct* by construction.
  - *Costs:* constrains producers — e.g. a native load-capture (R-C1-ζ) that double-reports a row
    violates it; volatile expressions (R-Q4) can flip V between evaluations and break disjointness
    unless fenced.
- **R-B2-β · Weak containment.** `rejects ⊆ in`, `out = op(in ∖ rejects)` up to engine slack.
  - *Buys:* room for engine-native mechanisms with fuzzy edges.
  - *Costs:* "up to slack" is exactly what A4 exists to forbid; unverifiable.

*Lean: α, stated per node kind (join/aggregate get their own phrasing via R-B3/R-Q5).*

## R-B3 · Multi-in nodes — whose row does a join reject?

The ON-expression evaluates over a *candidate pair*; a pair is not an input row of either port.

- **R-B3-α · Pair schema.** `j.rejects` carries left⊕right (prefixed/renamed columns, the join's
  internal evaluation schema).
  - *Buys:* honest — that is the failing unit; schema statically derivable.
  - *Costs:* a *new* schema nobody declared; under nested-loop semantics one bad left row rejects
    N pairs (multiplicity explosion — which pairs exist is engine/plan-dependent → A4 hazard!).
- **R-B3-β · Decompose-to-evaluate.** Normalization pulls reject-capable subexpressions out of ON
  into upstream per-side `calc` nodes (a capability-lowering-style rewrite); the join's ON becomes
  total; `join.rejects` becomes *provably dead* (R-A2-α warning) and the real rejects happen on
  single-in nodes with clean R-B2-α semantics.
  - *Buys:* dissolves the pair problem *and* the multiplicity hazard; keeps the partition invariant
    universal; consistent with "expressions escalate at node granularity" thinking (T5-b).
  - *Costs:* rewrite complexity (correlated subexpressions referencing both sides genuinely can't
    move — e.g. `cast(l.a) = cast(r.b)` moves fine, `f(l.a, r.b)` failing doesn't); those residual
    both-sides expressions need α or δ anyway as a fallback.
  - *Consequence for RH-2:* the hero's `rejects = j.rejects` would be re-authored (or re-read) as
    the *cast-carrying calc's* rejects — likely what it always morally meant. → feeds R-Q1 fixture fix.
- **R-B3-γ · Per-side ports** (`j.rejects_left`, `j.rejects_right`). *Buys:* input-schema purity.
  *Costs:* new reserved names (S10 churn); still can't place a both-sides failure; port explosion.

*Lean: β with α as the residual fallback for both-sides expressions (and a warning when it kicks in).*

## R-B4 · Multi-error rows (R-Q7)

- **R-B4-α · First-error-wins, one reject row.** Deterministic expression order (document order);
  a row rejects once with the first failing expr's code.
  - *Buys:* preserves R-B2-α cardinality exactly; cheap.
  - *Costs:* under-reports (fix one error, re-run, hit the next); "first" requires a canonical
    evaluation order engines don't naturally share — the guard must *impose* it (CASE ladder).
- **R-B4-β · One row per error.** *Buys:* complete diagnosis. *Costs:* breaks R-B2-α's multiset
  arithmetic (rejects ⊇ input rows); consumers must dedupe to replay.
- **R-B4-γ · One row, error-set column** (array/bitmask of codes). *Buys:* complete + cardinality-
  preserving. *Costs:* array-typed metadata column (v1 type system is flat scalars — collides with
  the "no nested types until v2" boundary).

*Lean: α for v1.x (document-order-deterministic), γ noted as the v2 upgrade once nested types land.*

---

## Open questions raised here

R-Q1 (fixture header), R-Q3 (overflow spec), R-Q5 (aggregates), R-Q6 (canonical codes — effectively
answered by R-B1-β's framing, ratify at convergence), R-Q7 (→ R-B4).

## Cross-links

R-A3 ↔ R-D2 (same fork, two ends) · R-A2 matrix → R-C/R-D producers implement exactly these rows ·
R-B1-β codes → C2-g diagnostics tables · R-B3-β rewrite → R-E1 locus (it *is* a graph rewrite —
deciding R-E1-α makes it natural) · R-B2-α → Q9 conform test design.
