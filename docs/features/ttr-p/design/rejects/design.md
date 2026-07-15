# Rejects — Design (planning input)

> **Audience: the `/planning` session.** Compact statement of everything decided in the rejects
> design effort (2026-07-15, sessions 1–5). Ground truth for rationale + rejected alternatives:
> [`00-control-room.md`](./00-control-room.md) §6. Exhaustive prose:
> [`detailed-design.md`](./detailed-design.md). Standing parent contracts:
> [`../../architecture/contracts.md`](../../architecture/contracts.md).

## 1. Result in one paragraph

A **reject** is a row on which a node's row-level function fails to evaluate (R-A1 = α) — and
*failure* is defined by the **canonical catalogue**, not the executing engine (R-A3 = β, principle
R-P2). The compiler implements rejects as a **graph-rewrite elaboration stratum** (R-E1 = α):
every rejects-*wired* node rewrites into a guard-and-branch subgraph over a **total validity
predicate V** drawn from the catalogue's per-function validity spec; emitters then see plain
dataflow. On SQL engines the rejects port becomes one more terminal SELECT over the shared CTE
chain (R-C1 = α); on Polars, a mask-and-split (R-D1 = α). Unwired ports are never elaborated —
fail-fast stays zero-cost (R-P3). The rejects stream is a first-class conform result with an
eighth partition check added to Q9 (R-D3 = α). No grammar changes in v1.x; the C2-e fragment
graduation boundary stands (R-F1 = α).

## 2. Semantics contract

- **Trigger (R-A1 = α):** evaluation failure only. Declared expectations = parked (desugar path
  exists later); structural misses (join-unmatched etc.) are **not** rejects.
- **Reject-capable set v1.x (RS-1):** `cast` (type pairs), division (`/0`), datetime parse.
  **Not** reject-capable: arithmetic overflow (fail-fast/`err`), aggregates (`err`-only, RS-2),
  volatile expressions (forbidden in reject-capable positions by catalogue purity flag, R-C2-b),
  total functions (select/sort/union/limit).
- **Port model (R-A2 = α):** the reserved `rejects` port stays on every node (C3-f);
  rejectability is catalogue-derived; wiring a provably-dead port = author-time warning
  (`TTRP-RJ-1xx` range).
- **3VL:** `cast(NULL) = NULL` is success, never a reject.
- **Failure identity (R-A3 = β / R-D2 = β / R-P2):** each reject-capable catalogue function
  carries a **canonical validity domain** per type pair (spec deliverable, lean near-PG
  semantics — write it in the first implementation phase) **plus an accept/reject example
  corpus** that doubles as conform fixtures. Engines implement the domain: native try/non-strict
  forms only where the manifest asserts domain equality, otherwise enforcing guard emit.
- **Multi-in nodes (R-B3 = β + α fallback):** normalization decomposes reject-capable
  subexpressions out of join ON into per-side `calc`s; residual both-sides expressions fall back
  to pair-schema rejects with a compile warning. Consequence: the v1 hero's `j.rejects` becomes
  the cast-calc's rejects; **re-author the hero fixture** (also fixes its `err rejects` header —
  ex-R-Q1).

## 3. Stream contract

- **Schema (R-B1 = β):** input row verbatim ⊕ `_ttrp_reject_code` (canonical `TTRP-RJ-0xx` id —
  never engine message text) ⊕ `_ttrp_reject_expr`. Reserved prefix **`_ttrp_`** (RS-5); user
  columns starting `_ttrp_` = compile error.
- **Partition invariant (R-B2 = α):** per reject site,
  `multiset(in) = multiset(processed) ⊎ multiset(rejects)` — exact, disjoint.
- **Multi-error rows (R-B4 = α):** first-error-wins in document order (imposed by the guard CASE
  ladder); error-set column = v2 (needs nested types).
- Rejects edges are ordinary data: cross-engine movement synthesizes as usual; downstream
  consumption (store/display/filter) is unrestricted; unions of reject streams are explicit and
  schema-checked like any union.

## 4. Compiler contract (elaboration)

- **Locus (R-E1 = α):** one deterministic rewrite stratum; position (RS-3):
  **sugar ≺ reject-elaboration ≺ capability-check/placement ≺ movement-synthesis.**
- **Shape:** rejects-wired node → `guard-calc` (adds `_ttrp_v<n>` validity columns via internal
  catalogue functions, e.g. `is_castable(x, T)`, `is_nonzero(y)`) → `branch(V)` → {op on
  true side → `out`, project + code on false side → `rejects`}. Internal validity functions are
  **not authorable surface syntax**.
- **Trigger:** the wire, not the capability — unwired ports are never elaborated (R-P3).
- **Escalation:** V-capability miss ⇒ the **whole elaborated cluster escalates as one
  provenance-tagged unit** (authored-node granularity, T5-b-consistent) or errors, per the knob
  `[ttrp] rejects-in-sql = produce | escalate | error` (R-E2-γ).
- **Identity gate:** the stratum is uniform across all three surfaces — byte-identity preserved.
- **Provenance:** mandatory (E-d-γ pattern); every synthetic node points at its authored node.

## 5. Emit contracts

- **SQL (R-C1 = α, R-C2-a = α):** guard columns in a dedicated `<ssa>_guard` CTE; `out` and
  `rejects` are terminal SELECTs over the shared chain (`WHERE _ttrp_v…` / `WHERE NOT _ttrp_v…` +
  code CASE ladder). Bundle model unchanged (statement-per-OUT-port; the multi-output island
  machinery gains one port — includes ADBC/Arrow export). Procedural row-at-a-time capture is
  **excluded**. Load rejects: portable floor = declare-raw columns + cast rejects; PG17
  `COPY … ON_ERROR` / MSSQL `ERRORFILE` are per-(engine, version) manifest **accelerators**
  (R-C1-ζ).
- **Polars (R-D1 = α):** mask-and-split — non-strict op + sentinel mask
  (`result.is_null() & raw.is_not_null()`), two-frame split; prelude helpers per E-c-γ.
- **Fail-fast (R-D4 = α):** unwired = today's plain emit; any failure = nonzero exit +
  diagnostic; no cross-engine first-failure promise.

## 6. Manifests (R-E2 = β + γ)

Per reject-capable (function, type pair) per engine:
`{native_form?, domain: canonical | wider | narrower | unknown, min_version}` — T10-parameterized.
`domain: unknown` + no guard implementation ⇒ not placeable on that engine (→ knob). Native forms
are used **only** under `domain: canonical` claims backed by the R-Q9 corpus. Emitter *fusion* of
guard subgraphs into native forms is parked (R-E1-γ; revisit with profiling + corpus data).

## 7. Conform (R-D3 = α) — parent amendment

Rejects ports export Arrow and enter the Q9 compare as first-class results. Q9 grows an **eighth
check**: the R-B2 partition count per reject site. **Amend** `contracts.md` §9 (+ E-e text in the
parent design set) — seven points → eight; changelog entry required. The validity corpus per
catalogue function supplies reject-triggering fixtures.

## 8. Surfaces & tooling

- **No grammar changes in v1.x.** Canonical `.rejects` taps already parse; fragments keep `err`
  only (C2-e stands, R-F1 = α).
- **Designer (R-F2):** authored-shape rendering, elaboration collapsed via provenance, drill-in
  available; synthetic nodes mint no `.ttrl` view-state keys.
- **`ttrp/getGraph` (RS-4):** authored shape by default; `elaborated: true` flag for tooling.
- **Diagnostics:** row-level codes `TTRP-RJ-0xx`; authoring diagnostics `TTRP-RJ-1xx` (dead wire,
  forced escalation, `_ttrp_` collision, volatile-in-reject-capable, pair-schema fallback
  warning). All join the diagnostics tables (fixtures + assist repair vocabulary).

## 9. Deferred / parked (with revisit conditions)

- **Fragment reject taps** — δ shape (TTR-B verb via S20; TTR-SQL/TTR-pandas keep graduation);
  β-(i) single-site header tap to be priced alongside. *Revisit:* producers landed + RH-1 seal.
- **R-E1-γ emitter fusion.** *Revisit:* R-Q9 data + profiled guard overhead.
- **Expectations (`expect` clauses)** as declared rejects. *Revisit:* post-v1.x, own effort.
- **Store-node rejects** (write-side constraint violations). *Revisit:* with the writes-scope
  question (A3).
- **Error-set metadata column** (multi-error). *Revisit:* v2 nested types.
- **Overflow as reject-capable.** *Revisit:* on demand (RS-1).

## 10. Plan-shaped notes

- **Spikes first:** R-Q9 (domain-divergence corpus — feeds every manifest `domain:` claim),
  R-Q10 (MSSQL multi-statement island executor check), R-Q11/R-Q12 (Polars version + Decimal
  non-strict behavior).
- **Deliverables the plan must schedule:** canonical validity spec + corpus (per RS-1 set);
  internal validity functions in the catalogue; the elaboration stratum + provenance; guard-CTE
  SQL emit + Polars mask-split emit; manifest schema extension + PG/MSSQL/Polars entries;
  conform eighth check + fixture wiring; diagnostics table entries; hero fixture re-author;
  parent contracts amendment (Q9 → 8 points; deferred-register update in `architecture.md` §10 —
  the item moves from deferred to designed/implementing).
- **Explicitly not in scope:** fragment grammar work, orchestration reactions to rejects,
  pandas engine, optimizer interaction (Z sees elaborated dataflow as hints per T2-e — nothing
  new to build).
