# Rejects Design — Control Room

> The single dashboard for the **rejects** design effort ("how do we handle `reject` in SQL" —
> the *erroneous-rows producer semantics* deferred since TTR-P v1 Phase 3). Open this first, every session.
> Parent effort: the TTR-P design set (`../01-design-space-map.md` … `../14-optimizer-worksheets.md`);
> parent contracts: [`../../architecture/contracts.md`](../../architecture/contracts.md).
> Effort-local IDs are prefixed **R** (`R-A`…`R-F` workstreams, `R-Qn` open questions, `R-GIn` grounding
> inputs, `R-Pn` principles) to avoid collision with the parent set's letters.

## 1. How we run this

Diverge-then-converge, per the design method used across ttr-p. Framing → per-workstream option
catalogues (no decisions) → convergence fork-by-fork, everything logged in §6. Ground truth = the
decision log; option docs are the map, not the territory.

## 2. Scope & framing

**The question.** Every TTR-P node reserves a data-shaped `rejects` out-port (C3-f): the rows the
operation failed on. Both v1 emitters *skip* rejects-mapped OUT ports today (`SqlGraphEmitter`,
`PolarsGraphEmitter` — conscious deferrals, Phase 3 deviation #1); the hero's
`crunch.rejects -> store(files.join_errors)` silently does not exist at runtime. This effort designs
**what a reject *is***, **what the rejects stream *carries***, and **how each engine *produces* it** —
so the port can be emitted, conform-checked (A4), and eventually tapped from fragments (C2-e-β).

**Scope (R-GI1, session 1).** Full rejects semantics: per-node-kind reject definition + stream
contract + producer mechanism per engine. **SQL is the hard case, not the whole case.**
**Engines in scope (R-GI2): PG + MSSQL + Polars** — dialect divergence is designed for up front,
not discovered later.

**Out of scope.** Orchestration-level error handling (on-failure islands, retries — v2, F);
cross-container `err` signals (F-d-i stands); the pandas *engine*; streaming.

**Success criteria.** (1) The hero's rejects wires emit and run on all three engines with
**identical rejects streams** under the Q9 seven-point conform procedure. (2) Fail-fast
(unconnected `rejects`) keeps costing **zero** — plain native code. (3) The graduation boundary
C2-e can be consciously revisited (fragment taps become *possible*, whether or not we open them).

**Hero scenario (RH).** Two sites, both from the existing A5 hero family:
- **RH-1 (the workhorse):** the returns-cleaning island **moved to SQL** — `load(returns.csv)`
  → `calc(returned_qty = cast(returned_qty_raw as int))` with `rejects` wired to a store, placed on
  `erp_pg` (and MSSQL), and the same graph placed on Polars. The cast-that-fails is *the* canonical
  reject producer.
- **RH-2 (the stress case):** the v1 hero's `rejects = j.rejects` — a **join** claiming to reject rows.
  Forces the "what does a multi-in node reject, and in what schema?" question (R-A/R-B).

## 3. Workstream dashboard

| WS | Title | Status | Options doc |
|---|---|---|---|
| R-A | What is a reject — trigger + per-node-kind matrix | 🟡 options captured | [`02-semantics-options.md`](./02-semantics-options.md) |
| R-B | The rejects-stream contract (schema, invariants) | 🟡 options captured | [`02-semantics-options.md`](./02-semantics-options.md) |
| R-C | SQL producer mechanism (PG, MSSQL) | 🟡 options captured | [`03-sql-producer-options.md`](./03-sql-producer-options.md) |
| R-D | Polars producer + A4 conform | 🟡 options captured | [`04-polars-conform-options.md`](./04-polars-conform-options.md) |
| R-E | Compiler integration locus + capabilities | 🟡 options captured | [`05-integration-surfaces-options.md`](./05-integration-surfaces-options.md) |
| R-F | Fragment taps (C2-e-β) + surfaces | 🟡 options captured | [`05-integration-surfaces-options.md`](./05-integration-surfaces-options.md) |

## 4. Banked constraints (not options — inherited decisions)

- **C3-f / T2-c:** two reserved error ports per node — `err` (control-shaped signal) + `rejects`
  (data-shaped rows); both modes exist in the model. Unconnected = **fail-fast** (P2-legal default).
- **T2-a / T7:** port schemas are **fully static** — whatever the rejects stream carries must be
  statically typed and author-time-checked.
- **A4 / Q9:** identical results across engines, checked by the seven-point `ttrp-conform` procedure
  (Arrow IPC, multiset compare). *The rejects stream is a result.*
- **Canonical SQL 3VL everywhere** (T5): `cast(NULL)` = NULL is a *success*, never a reject.
- **T5-b / T10:** capability misses escalate at node granularity; per-engine manifests are
  parameterized declarative entries; two rewrite kinds (authoring sugar / capability lowering).
- **E-b:** preserved-shape CTE-per-node SQL; SSA names survive as CTE names.
- **Bundle model:** one statement **per (non-rejects) container OUT port** for SQL islands
  (`SqlIslandEmitter`) — N terminal SELECTs over a shared CTE chain is already the shape.
- **C2-e:** fragments surface `err` only; `rejects` + multi-output = the graduation boundary to
  canonical containers. This effort *unlocks* revisiting β, it does not presume it.
- **Identity gate (Phase 7):** embedded ≡ canonical (≡ bare, by construction) byte-identical
  normalized graphs — any reject-elaborating rewrite must preserve this.
- **F-d-i:** cross-container `err` is a compile error in v1; `rejects` crosses engines as ordinary
  data (synthesized movement).

## 5. Design principles (proposed, to ratify at first convergence)

- **R-P1 · Rejects are ordinary data.** (Restates C3-f.) Whatever mechanism produces them, the
  stream must behave like any other out-port: static schema, wireable, conform-comparable.
- **R-P2 · The catalogue defines failure; engines implement it.** *Candidate — this is really the
  lean on fork R-A1/R-D2; ratifying it decides them.* If PG, MSSQL and Polars each let their native
  parser decide what fails, A4 dies by a thousand edge cases (`' 12 '`, `'+12'`, overflow bounds).
- **R-P3 · Fail-fast costs nothing.** A program that never wires `rejects` must emit exactly the
  plain code it emits today (native CAST, strict cast). Try-semantics are paid for only when bought.

## 6. Decision log (append-only — ground truth)

*(empty — divergence session; no decisions yet. R-GIn grounding inputs recorded below.)*

- **R-GI1 · 2026-07-15 · Scope = full rejects semantics** (definition + contract + producers),
  SQL as the hard case. — Bora, session 1 framing.
- **R-GI2 · 2026-07-15 · Producer targets = PG + MSSQL + Polars** — design for dialect divergence
  up front. — Bora, session 1 framing.
- **R-GI3 · 2026-07-15 · Doc home** = `docs/features/ttr-p/design/rejects/` with its own control
  room (parent ttr-p control room no longer in the live tree post-restructure). — Bora, session 1.

## 7. Parking lot

- **Store-node rejects** (constraint violations on write — `INSERT` hitting a UNIQUE/CHECK/FK;
  MERGE semantics). Real-world ETL wants it; touches write-path scope A3 ("no writes beyond
  materialize"). *Revisit when:* R-A trigger matrix converges — decide in or out then.
- **Expectation-driven rejects as a data-quality product story** (dbt-tests/Deequ/DLT-expectations
  analogue — `expect` clauses as first-class quality gates with reporting). *Revisit when:* R-A1
  converges; if β wins, this becomes its own feature effort.
- **Orchestration reactions to rejects** (quarantine tables, thresholds — "fail the run if >N%
  rejected"). v2/F territory; needs F proper. *Revisit when:* F opens.

## 8. Rolling open questions

- **R-Q1** · Is `err rejects` in the v1 hero fixture header (`container crunch(…, err rejects)`)
  a fixture bug (should be `rejects rejects` / a `rejects`-kind port) or a meaningful
  "signal-named-rejects"? It is *wired to a store* like data. Decide + fix fixture during R-B.
- **R-Q2** · PG version floor: is `pg_input_is_valid` (PG ≥ 16) assumable, or do we need the
  regex/CASE fallback tier for older PG? (Manifest-parameterizable either way.)
- **R-Q3** · Validity predicates for **overflow** (decimal/int arithmetic) — statable in portable
  SQL? Or is overflow out of the v1.x reject-capable set?
- **R-Q4** · Volatile/non-deterministic expressions under guard-and-branch double evaluation —
  fence with materialized CTE, forbid, or declare out of scope?
- **R-Q5** · Aggregate nodes: group-level failure attribution (sum overflow implicates the whole
  group). Reject the group's input rows, the group row, or make aggregates `err`-only?
- **R-Q6** · If the rejects schema carries error metadata, message text must be **canonical codes**,
  not engine text — else A4 breaks on the metadata columns. Confirm in R-B.
- **R-Q7** · Multi-error rows (two failing exprs in one `calc`): one reject row or one per error?
- **R-Q8** · Does solving producer semantics change the **Polars fail-fast** path too (today's
  strict cast aborts the whole script — is that the *same* failure the SQL statement abort is)?

## 9. Session index

| # | Date | Mode | What happened |
|---|---|---|---|
| 1 | 2026-07-15 | Framing + divergence | Effort opened from v1 `next-steps.md` §4. Scope/targets/doc-home fixed (R-GI1..3). Control room + map + option docs `02`–`05` written; all six workstreams 🟡. No decisions. |
