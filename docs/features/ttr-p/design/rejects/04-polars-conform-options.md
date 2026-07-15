# R-D — Polars Producer & A4 Conform: Option Catalogue (DIVERGENCE)

> **Mode: divergence.** No decisions here. Control surface: [`00-control-room.md`](./00-control-room.md).
> SQL counterpart: [`03-sql-producer-options.md`](./03-sql-producer-options.md). Opened 2026-07-15.

**The question.** Polars *looks* like the easy case (dataframes have `strict=False`), but A4 makes
it the other half of the same problem: the Polars producer must reject **exactly the rows** the
SQL producers reject, and `ttrp-conform` must be able to prove it. Also on the table: Polars is
v1's escalation destination (R-C1-ε) — if its own semantics are fuzzy, escalation is jumping from a
leaky boat into a leakier one.

## What's already banked

- E-c-γ emit: straight-line Polars script + generated inline prelude (dependency-free) — reject
  machinery must fit that shape (helpers go to the prelude, not an imported runtime).
- `PolarsGraphEmitter` skips rejects-mapped OUT ports today (same deferral as SQL).
- Q9 seven-point conform over Arrow IPC; multiset compare; schema fingerprints.
- Polars has real null semantics (near-SQL) — the 3VL "NULL input casts to NULL successfully" rule
  holds natively.

---

## R-D1 · Polars mechanism

- **R-D1-α · Mask-and-split (the guard shape again).** Compute the non-strict result next to the
  raw column; the failure mask is `result.is_null() & raw.is_not_null()` (the same sentinel
  disambiguation as `TRY_CAST`); split into two frames:

  ```python
  parsed_g = returns_raw.with_columns(
      pl.col("returned_qty_raw").cast(pl.Int64, strict=False).alias("returned_qty"))
  _m = pl.col("returned_qty").is_null() & pl.col("returned_qty_raw").is_not_null()
  parsed         = parsed_g.filter(~_m)
  parsed_rejects = parsed_g.filter(_m).drop("returned_qty").with_columns(
      pl.lit("TTRP-RJ-001").alias("_reject_code"))
  ```

  - *Buys:* vectorized (no row loop); structurally the *same* guard-and-branch as R-C1-α — one
    canonical elaboration, two emitters (the R-E1-α argument writes itself); straight-line-emit
    compatible; R-B2-α partition by construction.
  - *Costs:* `strict=False` implements **Polars'** acceptance domain, not the canonical one —
    the R-A3/R-D2 fork applies identically (e.g. Polars won't parse `' 12 '` to int where PG will);
    the mask idiom must be part of the emit contract (prelude helper candidate).
- **R-D1-β · Row-loop try/except** (`map_elements` with exception capture).
  - *Buys:* captures exactly what Polars raises. *Costs:* destroys vectorization (the same
    catastrophe as R-C1-δ); engine-defined domain again; anti-pattern in emitted code meant to be
    read. Fallback tier at most, same verdict shape as δ.
- **R-D1-γ · Native non-strict per catalogue function.** `cast(strict=False)`,
  `str.strptime(strict=False)`, `pl.when(y==0)` for division... — the manifest-tier idea (R-C1-β)
  on the Polars side: per-function native form where its domain ≡ canonical, else enforcing guard
  (regex via `str.contains`, bounds checks) before the non-strict op.
  - Same fold-in verdict: γ is α's V-implementation tier, manifest-selected.

*Lean: α with γ as its implementation tier — deliberately isomorphic to the SQL lean, because the
point is one canonical semantics.*

## R-D2 · Failure identity, engine end (= R-A3)

The concrete divergence surface (to be *measured* by the R-Q9 spike, not guessed):
leading/trailing whitespace; explicit `+`; scientific notation to int; locale decimal commas;
int64 bounds; date formats accepted by `str.strptime` vs PG `dateout` vs MSSQL `CONVERT` styles;
empty string vs NULL.

- **R-D2-α · Trust engines, exempt rejects from conform.** Declared A4 exception for the rejects
  stream. *Costs:* placement changes which rows are "bad" — the seal (T5.4.8-style PG↔Polars
  identical) silently loses coverage of the error path; the escalation story (ε) becomes
  semantics-changing. Recorded to map the space; hard to love.
- **R-D2-β · Canonical validity spec + enforcing emit** (R-A3-β instantiated): the catalogue's
  per-function validity domain is compiled into the guard on *every* engine; native non-strict
  forms allowed only where the R-Q9 corpus proves domain equality.
  - *Buys:* conform stays total; escalation is semantics-preserving; the validity corpus becomes a
    shipped test fixture (each catalogue function carries its accept/reject example set — doubles
    as documentation and as the conform generator).
  - *Costs:* the canonical domain must be *chosen* (a real language-design act: is `' 12 '` a valid
    int? — precedent says pick SQL-ish semantics, as with 3VL: *lean — canonical domain = a defined
    subset near PG's cast domain*, exact spec a convergence deliverable); enforcing guards cost
    cycles where native domains differ.
- **R-D2-γ · Per-engine domains, canonicalized at the *edges*** — normalize inputs (trim, strip
  `+`…) before reject-capable ops so all engines see pre-canonicalized values.
  - *Buys:* smaller guards. *Costs:* silently *mutates data* on the happy path (a P2 violation in
    spirit — the pipeline changes values the author never asked to change); normalization is just
    the guard wearing a disguise, and a lossy one.

*Lean: β. Same lean as R-A3-β because it is the same fork; ratifying R-P2 closes both.*

## R-D3 · Conform coverage of the rejects stream

- **R-D3-α · Full first-class result.** Every wired rejects port exports Arrow like any OUT port;
  seven-point compare applies verbatim (multiset, schema fingerprint, canonical types); the
  partition invariant adds an **eighth check**: `|in| = |out| + |rejects|` per reject site
  (cheap, catches producer bugs conform's per-port compare can't see).
  - *Buys:* rejects are results, full stop (R-P1); the RH-1 seal covers the error path.
  - *Costs:* conform fixtures need reject-triggering rows (the R-D2-β validity corpus supplies
    them — synergy, not cost, if β wins).
- **R-D3-β · Count-only smoke.** Compare reject *counts* per site, not contents. *Buys:* dodges
  metadata-column comparison. *Costs:* two engines rejecting *different* rows with equal counts
  pass — exactly the A4 failure mode; and R-B1-β's canonical codes make content-compare workable
  anyway (R-Q6).
- **R-D3-γ · Full compare minus metadata columns** (compare the verbatim-row projection only).
  A pragmatic middle if canonical codes prove contentious; loses code-equality checking.

*Lean: α (with the eighth check), γ as the fallback if R-B1 converges on α-verbatim.*

## R-D4 · Fail-fast parity (R-Q8)

Today an unconnected-rejects failure aborts: SQL by statement error, Polars by strict-cast panic
mid-script. Divergence hazard: *which* failure aborts first differs by execution order, and the
**err signal** / exit-code surface differs by engine.

- **R-D4-α · Abort is abort.** Any row-level failure with unconnected rejects = nonzero exit +
  diagnostic; no cross-engine promise about *which* of several failures reports. Cheap, honest.
- **R-D4-β · First-failure canonical.** Impose document-order failure reporting even in fail-fast
  (the R-C2-c CASE-ladder logic applied to abort paths). *Costs:* forces guard evaluation on the
  happy path *even when rejects is unwired* — violates R-P3 (fail-fast costs nothing).
- *Lean: α — R-P3 outranks failure-report determinism; the run failed either way.*

## RESOLVED (2026-07-15) — R-D converged

All leans ratified (full text in the control-room decision log):

- **R-D1 = α** mask-and-split, γ native forms as manifest V-tier; β row-loop excluded (δ's twin).
- **R-D2 = β** — formal record of R-A3 = β; canonical domain spec (leaning near-PG semantics) is a
  `design.md` deliverable; γ edge-canonicalization rejected as anti-P2.
- **R-D3 = α** — rejects streams are first-class conform results **+ the eighth (partition) check**;
  amends the parent Q9/E-e seven-point procedure; validity corpus doubles as fixtures.
- **R-D4 = α** — abort is abort; resolves R-Q8; R-P3 outranks failure-report determinism.

Carried to the plan as spikes: R-Q9 (domain corpus), R-Q11 (Polars version behavior), R-Q12
(Polars Decimal non-strict).

## Open questions raised here

- **R-Q9** (shared with `03`): the empirical domain-divergence corpus spike — **the** de-risking
  step before converging R-A3/R-C1/R-D2.
- **R-Q11** · Does `strict=False` cast exist/behave uniformly across the Polars versions the world
  manifests pin? (Version-parameterized manifest entry like R-Q2's PG floor.)
- **R-Q12** · Decimal in Polars (hero uses `pl.Decimal`) — non-strict cast support and bounds
  behavior are historically wobbly; verify for the validity corpus.

## Cross-links

R-D1-α ↔ R-C1-α (one guard shape, two emitters → R-E1) · R-D2 ↔ R-A3 ↔ R-P2 · R-D3-α → Q9
procedure text gains an eighth point (amends E-e's list if ratified) · R-D4 ↔ R-P3 · validity
corpus ↔ C2-g diagnostics-tables-as-fixtures pattern.
