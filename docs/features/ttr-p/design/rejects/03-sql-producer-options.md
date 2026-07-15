# R-C — SQL Producer Mechanism: Option Catalogue (DIVERGENCE)

> **Mode: divergence.** No decisions here. Control surface: [`00-control-room.md`](./00-control-room.md).
> Semantics contract being implemented: [`02-semantics-options.md`](./02-semantics-options.md).
> Opened 2026-07-15. Targets (R-GI2): **PG + MSSQL**.

**The problem, precisely.** A SQL statement is atomic: one row hitting `22P02` (PG,
`invalid text representation`) or Msg 245 (MSSQL, conversion failure) aborts the whole statement.
There is no per-row error channel in a SELECT. Yet the bundle contract wants, for RH-1:

```sql
-- islands/returns_pg.sql — what we need to be able to emit
WITH returns_raw AS (…),                       -- load
     parsed      AS (…cast…),                  -- calc: the reject site
     clean       AS (SELECT … FROM parsed WHERE returned_qty > 0)
SELECT … FROM clean;          -- → out
SELECT … FROM ???;            -- → rejects   ← this statement is the design question
```

Fail-fast (unconnected `rejects`) is the one case SQL gives us for free: the plain `CAST` aborts —
exactly C3-f's default. **The whole question is the connected case.**

## What's already banked

- E-b: CTE-per-node, SSA names as CTE names, preserved shape.
- `SqlIslandEmitter`: one statement per (non-rejects) OUT port over a shared CTE chain — N terminal
  SELECTs is *already the model*; a rejects port slots in as one more terminal SELECT if (and only
  if) its rows are expressible relationally.
- Canonical 3VL; enforcing codegen where engines differ (the precedent R-C1-α leans on).
- T5-b escalation + T10 parameterized manifests (the machinery ε and β both need).

---

## R-C1 · The mechanism forks

### R-C1-α · Guard-and-branch (validity-predicate rewrite)

For each reject-capable expression `e` over row `r`, derive a **total** validity predicate `V_e(r)`
— true iff evaluating `e(r)` would succeed *per the canonical validity spec* (R-A3-β). The node
splits relationally:

```
rejects = σ(¬V_e)(in)            out = op(σ(V_e)(in))
```

Emitted (PG, RH-1 — `pg_input_is_valid` flavor):

```sql
parsed_guard AS (
  SELECT *, pg_input_is_valid(returned_qty_raw, 'integer') AS _v1 FROM returns_raw
),
parsed AS (
  SELECT …, CAST(returned_qty_raw AS integer) AS returned_qty
  FROM parsed_guard WHERE _v1
),
parsed_rejects AS (
  SELECT …, 'TTRP-RJ-001' AS _reject_code FROM parsed_guard WHERE NOT _v1
)
```

- *Buys:* pure relational — the rejects port becomes an ordinary terminal SELECT (the bundle model
  needs *nothing new*); preserved-shape holds (guard CTEs are named, readable, diffable); R-B2-α
  partition is correct **by construction** (`V` total, `WHERE _v1` / `WHERE NOT _v1` exhaustive
  under 2VL — note `V` must return true/false, never NULL, part of totality); works on *every*
  SQL engine ever (the mechanism itself is dialect-free; only V's *implementation* is dialect).
- *Costs:* **V must exist and be faithful** — per catalogue function × type pair × dialect
  (manageable: the v1.x reject-capable set is small — casts, `/`, datetime parse; R-Q3 overflow is
  the hard one); rows are scanned twice conceptually (once for guard, once per terminal — engines
  usually CSE it, PG may need `MATERIALIZED` fencing for volatile exprs, R-Q4); the "would succeed"
  claim is a *proof obligation per dialect* (fixture-tested via conform, not hand-waved).
- *Prior art:* every hand-written "clean + errors" CTE pair in production SQL; dbt's
  `safe_cast`-then-split macros; BigQuery `SAFE_CAST` idioms.

### R-C1-β · Engine-native try forms

Use the dialect's non-raising forms directly as the split predicate:
MSSQL `TRY_CAST(x AS int)` / `TRY_CONVERT`; PG ≥ 16 `pg_input_is_valid(x, 'type')`; (BigQuery
`SAFE_CAST`, DuckDB `TRY_CAST` — future targets).

- The sentinel problem: `TRY_CAST(x) IS NULL` conflates "failed" with "x was NULL"; the guard is
  `TRY_CAST(x) IS NULL AND x IS NOT NULL` (3VL-correct: NULL input succeeds to NULL).
- *Buys:* single evaluation, engine-optimized, no regex maintenance; MSSQL story is *clean* —
  `TRY_CAST` is exactly the primitive.
- *Costs:* **β is not a mechanism — it is an *implementation of α's V*** whose acceptance domain is
  the engine's, i.e. R-A3-α by the back door. `TRY_CAST` and `pg_input_is_valid` accept different
  lexical spaces; using them raw re-diverges the engines. Under R-A3-β they are usable **only where
  provably equal to the canonical domain, else wrapped** (canonical guard first, native try inside).
  PG floor: `pg_input_is_valid` needs PG16 (R-Q2) — older PG falls back to regex/CASE V.
- *Verdict shape:* fold β into α as the *preferred V-implementation tier*, manifest-selected
  (R-E2): `V_impl = native-try | canonical-guard`, per (engine, function, version).

### R-C1-γ · Two-pass / error-table

Materialize the rejects first (temp/staging table), then compute `out` against the anti-join;
ancestry: Oracle `DML … LOG ERRORS INTO`, classic ETL error tables.

- *Buys:* rejects exist as a durable artifact mid-run (ops-friendly); natural if a *procedural*
  producer (δ) writes them.
- *Costs:* needs DDL rights + staging in-engine (the bundle's staging is *cross*-engine, D-f — a
  new in-engine staging concept); two statements with state between them (executor complexity);
  and — decisive — **given α, γ is redundant**: the rejects terminal SELECT already shares the
  guard CTE; materialization adds nothing semantically. Keep only as the pairing for δ.

### R-C1-δ · Procedural row-at-a-time

PL/pgSQL loop with `BEGIN … EXCEPTION WHEN others THEN` per row; MSSQL cursor + `TRY/CATCH`.

- *Buys:* captures *exactly* what the engine raises — the only mechanism that is semantically exact
  under R-A3-α, and the only one that works for expressions with **no derivable V** (arbitrary
  UDF-ish calls, R-Q3 overflow if unspecifiable).
- *Costs:* performance catastrophe (row-at-a-time × round-trip parse); abandons preserved shape
  (emits a DO block, not CTEs); PG requires a function/DO context; per-row subtransactions in PG
  are XID-burners; under R-A3-β it is also *wrong* (engine domain ≠ canonical domain).
- *Verdict shape:* at most a named **fallback tier** with a compile *warning* ("expression X forced
  procedural reject capture — consider placement"), or excluded entirely (a V-less reject-capable
  expression is simply *not SQL-placeable*, → ε).

### R-C1-ε · Refuse-and-escalate (placement answer)

SQL engine manifests declare rejects-production unsupported (wholesale, or per-function where V is
unimplementable); a connected `rejects` port on a SQL-placed node = capability miss → **T5-b node
escalation**: the reject-site node is split out of the island and placed on a rejects-capable
engine (Polars), with synthesized movement (C3-d-iv) around it.

- *Buys:* zero SQL machinery; maximally honest about engine limits; reuses escalation machinery
  that *must exist anyway* (T5-b split-with-warning is already the expression-miss policy); the
  `[ttrp]` policy knob precedent applies (`rejects-in-sql = produce | escalate | error`).
- *Costs:* as *the* answer it is a product-story failure — "TTR-P can't clean data in the database"
  (the deferred register explicitly promises the opposite); the data round-trip for one cast is
  absurd at warehouse scale; the graph's placement stops being the author's (B-T9 v1: placement is
  explicit and the author's).
- *Verdict shape:* not the mechanism — but the **degradation path** when α has no V for an
  expression and δ is excluded: escalate that node (or error, per knob).

### R-C1-ζ · Load-boundary native capture

PG 17 `COPY … ON_ERROR ignore` (+ `LOG_VERBOSITY`), PG 18 `REJECT LIMIT`; MSSQL
`BULK INSERT … ERRORFILE` / OPENROWSET. Captures **load-node** rejects (malformed records) natively.

- *Buys:* the *only* way to catch "row does not deserialize at all" in-engine (α's V needs the raw
  text to already be *in* a column — a truly malformed CSV line never gets that far); huge ingest
  speedups vs staging-then-guarding.
- *Costs:* ingest-only (calc/filter/join rejects untouched); version floors (PG17+ — steeper than
  R-Q2's PG16); ERRORFILE lands rejects in a *file*, not a rowset (movement synthesis needed to
  re-enter dataflow); `COPY … ON_ERROR` drops rows without yielding them queryably until PG18's
  logging — reconstructing the *stream* per R-B1 takes an anti-join against the source file, which
  may be outside the engine.
  - Alternative for the v1.x floor: declare-raw-load pattern — load *all* columns as `text` (the
    hero already does this: schema `returns_raw` with `returned_qty_raw text`), making every field
    failure a **cast reject** (α handles it) and only line-level malformation a load reject.
- *Verdict shape:* a per-(engine, version) **manifest accelerator** for load rejects; the portable
  floor is declare-raw + α.

## R-C2 · Cross-cutting sub-forks

- **R-C2-a · Where the guard sits in the emitted SQL.** (α) guard columns in a dedicated
  `<ssa>_guard` CTE (shown above — preserved-shape, names derivable, one extra CTE per reject site);
  (β) inline `CASE WHEN V THEN CAST(…) END` in the node's own CTE + a separate `WHERE NOT V`
  terminal (fewer CTEs, V computed twice textually); (γ) guard folded into the terminal SELECTs
  only (no intermediate CTE — breaks if downstream nodes also consume `out`). *Lean: α — it is
  E-b's own logic applied.*
- **R-C2-b · Double-evaluation fencing (R-Q4).** V and the real expression referencing the same
  volatile input can diverge. Options: forbid volatile functions in reject-capable expressions
  (catalogue flag — v1.x set has none anyway: casts and `/` are pure); PG `WITH … AS MATERIALIZED`
  fence + MSSQL equivalent (none — needs temp table, ugly); accept-and-document. *Lean: forbid via
  catalogue purity flag; revisit if a volatile reject-capable function ever exists.*
- **R-C2-c · The `_reject_code` ladder (R-B4-α).** Multiple reject sites in one node emit a
  document-order `CASE WHEN NOT _v1 THEN 'TTRP-RJ-001' WHEN NOT _v2 THEN …` — the CASE ladder *is*
  the canonical evaluation order the engines don't natively share.
- **R-C2-d · Statement-per-port interaction.** Multi-output SQL islands (phase-3 open item #5:
  N terminal SELECTs + PG→Arrow export per port) gain one more port. No new machinery, but the
  ADBC export loop and the seven-point compare must include rejects ports — cross-links R-D3.
- **R-C2-e · Dialect matrix sketch (v1.x reject-capable set × mechanism).**

| Expression | PG < 16 | PG 16/17+ | MSSQL |
|---|---|---|---|
| `cast(text→int/decimal/date/…)` | canonical regex/bounds guard | `pg_input_is_valid` *if domain ≡ canonical, else guard* | `TRY_CAST` *same caveat* |
| `x / y` | `y = 0` guard (trivially canonical) | same | same |
| datetime parse/arith | canonical guard | `pg_input_is_valid` caveat | `TRY_CONVERT` caveat |
| overflow (int/decimal arith) | R-Q3 — bounds-check guard *if specifiable*, else no-V → ε/δ | same | same |
| `load` malformed record | declare-raw + cast rejects | PG17 `COPY ON_ERROR` accelerator | `ERRORFILE` accelerator |

## Open questions raised here

R-Q2 (PG floor), R-Q3 (overflow V), R-Q4 (volatility — lean recorded above), plus new:
- **R-Q9** · Do native-try domains *actually* differ from canonical for the v1.x type pairs?
  Empirical fixture sweep needed (whitespace/sign/locale/bounds corpus × {PG cast, pg_input_is_valid,
  TRY_CAST, Polars cast}) — this evidence decides how expensive R-A3-β really is. **Cheap spike,
  high information; do before converging R-C1.**
- **R-Q10** · MSSQL multi-statement islands: current emitter assumptions (GO batches? one
  connection?) — verify the N-terminal-SELECT model ports to the MSSQL executor binding.

## Cross-links

R-C1-α ⇐ R-A3-β (V *is* the canonical validity spec, compiled) · R-C1 → R-E1 (α is naturally a
graph rewrite: guard+branch as canonical subgraph) · R-C1-ε ↔ T5-b/T10 · R-C1-ζ ↔ R-A2 load row ·
R-C2-d → R-D3 conform · guard CTEs → E-b naming (S-sweep naming rules apply to `_guard`/`_v` names).
