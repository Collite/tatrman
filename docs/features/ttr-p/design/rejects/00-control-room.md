# Rejects Design — Control Room

> **EFFORT COMPLETE (2026-07-15).** All workstreams 🟢, sweep ratified. Final deliverables:
> [`design.md`](./design.md) (for `/planning`) + [`detailed-design.md`](./detailed-design.md)
> (the manual). Next step: a `/planning` session consuming `design.md`; spike tasks riding along:
> R-Q9 (validity-domain corpus), R-Q10 (MSSQL multi-statement islands), R-Q11/R-Q12 (Polars
> versions / Decimal).

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
| R-A | What is a reject — trigger + per-node-kind matrix | 🟢 converged (R-Q3/R-Q5 carried) | [`02-semantics-options.md`](./02-semantics-options.md) |
| R-B | The rejects-stream contract (schema, invariants) | 🟢 converged | [`02-semantics-options.md`](./02-semantics-options.md) |
| R-C | SQL producer mechanism (PG, MSSQL) | 🟢 converged (R-Q3/R-Q9/R-Q10 carried) | [`03-sql-producer-options.md`](./03-sql-producer-options.md) |
| R-D | Polars producer + A4 conform | 🟢 converged (R-Q11/R-Q12 carried → plan spikes) | [`04-polars-conform-options.md`](./04-polars-conform-options.md) |
| R-E | Compiler integration locus + capabilities | 🟢 converged (R-Q13/R-Q14 → sweep) | [`05-integration-surfaces-options.md`](./05-integration-surfaces-options.md) |
| R-F | Fragment taps (C2-e-β) + surfaces | 🟢 converged (R-Q15 → sweep; taps follow-up parked) | [`05-integration-surfaces-options.md`](./05-integration-surfaces-options.md) |

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

- **R-P1 · Rejects are ordinary data.** (Restates C3-f.) **RATIFIED 2026-07-15** (exercised by
  R-D3 = α: the stream is a first-class conform result). Whatever mechanism produces them, the
  stream must behave like any other out-port: static schema, wireable, conform-comparable.
- **R-P2 · The catalogue defines failure; engines implement it.** **RATIFIED 2026-07-15** (via
  decision R-A3 = β — which also decides R-D2 = β, same fork). If PG, MSSQL and Polars each let
  their native parser decide what fails, A4 dies by a thousand edge cases (`' 12 '`, `'+12'`,
  overflow bounds).
- **R-P3 · Fail-fast costs nothing.** **RATIFIED 2026-07-15** (exercised by R-D4 = α and
  R-E3-fail-fast: unwired ports are never elaborated). A program that never wires `rejects` must
  emit exactly the plain code it emits today (native CAST, strict cast). Try-semantics are paid
  for only when bought.

## 6. Decision log (append-only — ground truth)

Grounding inputs:

- **R-GI1 · 2026-07-15 · Scope = full rejects semantics** (definition + contract + producers),
  SQL as the hard case. — Bora, session 1 framing.
- **R-GI2 · 2026-07-15 · Producer targets = PG + MSSQL + Polars** — design for dialect divergence
  up front. — Bora, session 1 framing.
- **R-GI3 · 2026-07-15 · Doc home** = `docs/features/ttr-p/design/rejects/` with its own control
  room (parent ttr-p control room no longer in the live tree post-restructure). — Bora, session 1.

Decisions:

- **2026-07-15 · R-A1 = α** · A reject is a **row-level evaluation failure only** (the node's row
  function is partial on that row). *Why:* smallest coherent semantics; it is exactly what
  fail-fast already means — connecting the port converts "abort" into "divert", one concept, two
  routings. *Rejected:* β expectations (declarative sugar over filter+branch; can be layered on
  later as a desugar — **parked**, see §7), γ structural misses (port overloading; unmatched ≠
  erroneous; join types + Branch anchors already express structural routing).
- **2026-07-15 · R-A2 = α** · **Uniform port, sparse capability**: every node keeps the reserved
  `rejects` port (C3-f stands); rejectability is derived per node from the catalogue; wiring a
  provably-dead rejects port = author-time **warning**. *Rejected:* β port-only-where-rejectable
  (breaks C3-f uniformity, complicates Designer/builder, saves nothing).
- **2026-07-15 · R-A3 = β** · **Failure is catalogue-defined** — every reject-capable catalogue
  function carries a canonical validity domain (per type pair); engines implement it: native
  try/non-strict forms only where provably equivalent, else enforcing emit. **Ratifies R-P2**;
  **decides R-D2 = β** (same fork, other end). The R-Q9 corpus spike is hereby *demoted from
  decision gate to implementation de-risking* — if it shows the canonical domain unimplementable at
  acceptable cost, this decision gets an explicit amendment. *Rejected:* α engine-defined (rejects
  streams diverge per placement → A4 exception → rejects become second-class results), γ
  engine-widened (α with extra steps; still breaks A4).
- **2026-07-15 · R-B1 = β** · Rejects schema = **input row verbatim ⊕ reserved canonical metadata
  columns** (`_reject_code` — canonical `TTRP-RJ-…` id, never engine text — plus `_reject_expr`);
  a reserved-prefix rule protects against user-column collision (exact rule = naming detail at
  final sweep). **Resolves R-Q6.** *Rejected:* γ envelope (type-destroying, un-replayable), δ
  declared-projection as standalone (a refinement, not an alternative), α verbatim-only (kept as
  the compatible degenerate form if staging the implementation helps).
- **2026-07-15 · R-B2 = α** · **Exact disjoint partition**: per reject site,
  `multiset(in) = multiset(processed) ⊎ multiset(rejects)`; join/aggregate phrasing via R-B3/R-Q5.
  *Rejected:* β weak containment ("engine slack" is what A4 forbids; unverifiable).
- **2026-07-15 · R-B3 = β with α fallback** · **Decompose-to-evaluate**: normalization pulls
  reject-capable subexpressions out of multi-in evaluation (join ON) into per-side `calc` nodes;
  the multi-in node's own evaluation becomes total. Residual genuinely-both-sides expressions fall
  back to pair-schema rejects (α) with a compile warning. *Consequence:* RH-2's `j.rejects`
  re-reads as the cast-carrying calc's rejects; the hero fixture gets re-authored when producers
  land (narrows R-Q1 to a fixture chore). *Rejected:* γ per-side ports (reserved-name churn; still
  can't place both-sides failures), α as primary (undeclared pair schema + engine-dependent pair
  multiplicity = A4 hazard).
- **2026-07-15 · R-B4 = α** · **First-error-wins**, deterministic in document order (imposed by the
  guard CASE ladder, R-C2-c). **Resolves R-Q7.** γ error-set column recorded as the v2 upgrade once
  nested types exist. *Rejected:* β one-row-per-error (breaks R-B2-α's multiset arithmetic).
- **2026-07-15 · R-C1 = α (guard-and-branch), with the tier ladder pinned** · The SQL producer is
  the **validity-predicate rewrite**: total V per reject-capable expression (V = the R-A3-β
  canonical validity spec, compiled), `rejects = σ(¬V)`, `out = op(σ(V))`; the rejects port emits
  as one more terminal SELECT over the shared CTE chain (bundle model unchanged). Tiers: **β
  native-try forms are V-implementation choices**, manifest-selected per (engine, function,
  version) and only where domain ≡ canonical (per R-Q9-style proof); **ζ load-boundary capture** is
  a per-(engine, version) manifest accelerator for `load` rejects, portable floor = declare-raw
  columns + cast rejects. **No-V expressions are not SQL-placeable** → degradation per policy knob
  (escalate the node à la T5-b, or compile error — knob = R-E2-γ). *Rejected:* γ two-pass
  (redundant given α + statement-per-port; staging/DDL burden), δ procedural row-at-a-time
  (excluded as a tier — perf catastrophe, breaks preserved shape, and wrong under R-A3-β),
  ε-as-the-answer (product-story failure; kept only as the no-V degradation path).
- **2026-07-15 · R-C2-a = α** · Guard columns live in a dedicated `<ssa>_guard` CTE — E-b
  preserved-shape applied; deterministic derived names (reserved-prefix rule shared with R-B1's
  metadata columns). *Rejected:* β inline-CASE (V computed twice textually), γ terminal-only
  (breaks shared downstream consumption).
- **2026-07-15 · R-C2-b** · **Volatile/non-deterministic functions are forbidden in reject-capable
  expressions** — a catalogue purity flag, author-time error. **Resolves R-Q4** (no fencing
  machinery; the v1.x reject-capable set is pure anyway). Revisit only if a volatile reject-capable
  function ever enters the catalogue. *Rejected:* MATERIALIZED-fence (no MSSQL equivalent),
  accept-and-document (silent nondeterminism is anti-P2).
- **2026-07-15 · R-C2-c confirmed** · The guard CASE ladder in document order is the imposed
  canonical evaluation order (consequence of R-B4 = α; recorded so the emit contract names it).
- **2026-07-15 · R-C1-δ exclusion confirmed explicitly** · Bora ratified the sharpened reading:
  procedural row-at-a-time capture is **excluded** (not a warned fallback tier); the only no-V
  degradation is escalate/error per knob.
- **2026-07-15 · R-D1 = α** · Polars producer = **mask-and-split** (non-strict op + sentinel
  disambiguation mask `result.is_null() & raw.is_not_null()`), with γ native non-strict forms as
  the manifest-selected V-implementation tier — deliberately isomorphic to R-C1. *Rejected:* β
  row-loop try/except (R-C1-δ's twin — same exclusion).
- **2026-07-15 · R-D2 = β** · Formal record of the R-A3 = β identity (canonical validity domain,
  enforcing emit where native domains diverge; canonical domain spec = a `design.md` deliverable,
  leaning SQL-ish/near-PG semantics per the 3VL precedent). *Rejected:* α trust-engines (A4
  exception), γ edge-canonicalization (mutates data on the happy path — anti-P2).
- **2026-07-15 · R-D3 = α** · The rejects stream is a **first-class conform result**: Arrow export
  per wired rejects port, full seven-point compare, **plus an eighth check** — the R-B2-α partition
  count `|in| = |processed| + |rejects|` per reject site. **Amends the parent Q9/E-e procedure
  text** (seven points → eight; propagate to `../../architecture/contracts.md` §9 at the final
  sweep). The R-D2-β validity corpus doubles as the reject-triggering fixture set. *Rejected:* β
  count-only (equal counts of *different* rows pass — the exact A4 failure mode), γ minus-metadata
  (unnecessary once R-B1 = β canonical codes hold).
- **2026-07-15 · R-D4 = α** · **Abort is abort**: with unwired rejects, any row-level failure =
  nonzero exit + diagnostic; no cross-engine promise about which of several failures reports first.
  **Resolves R-Q8** (fail-fast parity = exit-status parity, nothing more). *Rejected:* β canonical
  first-failure (forces guard evaluation on the happy path — violates R-P3).
- **2026-07-15 · R-E1 = α (v1.x), γ-fusion recorded as later optimization** · Reject elaboration is
  a **graph-rewrite stratum**: rejects-wired nodes rewrite to guard-calc → branch(V) → {op → out,
  project+code → rejects} using internal catalogue validity functions; emitters see plain dataflow.
  Emitter fusion to native forms (γ) is a *later optimization permitted only under manifest
  domain-equality proof*. *Rejected:* β emitter-local (semantics ×N engines, drift guarded only
  post-hoc — R-P2's anti-pattern; and R-B3-β's join decomposition can't live in an emitter).
- **2026-07-15 · R-E2 = β + γ** · Manifests carry **per-(function, type-pair) validity entries**
  `{native_form?, domain: canonical|wider|narrower|unknown, min_version}` (T10-parameterized);
  plus the **policy knob** `[ttrp] rejects-in-sql = produce | escalate | error` (T5-b-precedent).
  `domain: unknown` + no guard ⇒ not placeable on that engine. *Rejected:* α boolean node-level
  flag (rejectability is per-expression, not per-node).
- **2026-07-15 · R-E3 (bundle)** · (i) Elaboration is deterministic and applies uniformly to all
  surfaces — identity gate preserved by construction; exact stratum position = **R-Q13/R-Q14, sent
  to the consolidation sweep**. (ii) Synthesized names (`<ssa>_guard`, `_v…`, `_reject_code`)
  follow one reserved-prefix rule (shared with R-B1/R-C2-a; exact prefix = sweep item).
  (iii) **Unwired rejects ports are never elaborated** — the rewrite triggers on the wire, not the
  capability (R-P3 enforced structurally).
- **2026-07-15 · R-F1 = α now, δ as the parked follow-up** · v1.x producer release **keeps the
  C2-e graduation boundary** (fragments still surface `err` only; the unlock stays latent). The
  intended follow-up shape is **δ dialect-asymmetric taps** (TTR-B verb via S20 verb-table
  machinery; TTR-SQL/TTR-pandas keep paste-fidelity + graduation), with **β-(i) single-site
  header taps to be priced** when that follow-up opens — parked with revisit condition (§7).
  *Rejected now:* γ-for-all (TTR-SQL foreign clause breaks paste round-trips; TTR-pandas kwarg is
  fiction vs real pandas), β unrestricted (multi-site schema union impossible under T2-a).
- **2026-07-15 · R-F2 = provenance-collapsed rendering** · Designer renders the *authored* node
  (elaboration collapsed, drill-in available); rejects wire attaches to the authored port;
  synthetic nodes mint **no** `.ttrl` view-state keys. `ttrp/getGraph` exposure (R-Q15) → sweep.
  Authoring diagnostics get the `TTRP-RJ-1xx` range; row-level codes `TTRP-RJ-0xx`.

Consolidation sweep (batch-ratified 2026-07-15):

- **RS-1 · v1.x reject-capable set = casts, division, datetime parse.** Arithmetic **overflow is
  not reject-capable** — stays fail-fast/`err` (no portable total V without re-implementing engine
  arithmetic in the guard). **Resolves R-Q3.** *Revisit:* if a real program demands it.
- **RS-2 · Aggregates are `err`-only.** Group-level failure does not attribute to rows; the R-A2
  matrix row resolves to "no". Near-free given RS-1 (sum/avg overflow was the only candidate).
  **Resolves R-Q5.**
- **RS-3 · Stratum order: sugar ≺ reject-elaboration ≺ capability-check/placement ≺
  movement-synthesis.** Manifests evaluate the internal V-functions (R-E2-β); on a V-capability
  miss, escalation moves the **whole elaborated cluster as one provenance-tagged unit** (authored
  node granularity, T5-b-consistent); identity gate holds (uniform deterministic stratum).
  **Resolves R-Q13 + R-Q14.**
- **RS-4 · `ttrp/getGraph` serves authored shape by default**; `elaborated: true` request flag
  serves the compiled shape for conform/debug tooling; Designer never needs the flag.
  **Resolves R-Q15.**
- **RS-5 · Reserved prefix `_ttrp_`** for all synthesized column names (`_ttrp_v1`,
  `_ttrp_reject_code`, `_ttrp_reject_expr` — normalizing the shorter examples in `02`/`03`);
  user columns beginning `_ttrp_` = compile error. Guard **CTE** names stay `<ssa>_guard`
  (SSA-namespace collision handled by the existing SSA uniqueness rules).

## 7. Parking lot

- **Fragment reject taps, δ shape** (TTR-B `Divert failures to …` verb; TTR-SQL/TTR-pandas keep
  graduation; β-(i) single-site header tap to be priced alongside). *Revisit when:* producers have
  landed and run through conform (the RH-1 seal) — decided 2026-07-15 (R-F1).
- **R-E1-γ emitter fusion** (fuse guard subgraphs into native try-forms under manifest
  domain-equality proof). *Revisit when:* R-Q9 corpus data exists + a profiled program shows the
  guard overhead matters.

- **Store-node rejects** (constraint violations on write — `INSERT` hitting a UNIQUE/CHECK/FK;
  MERGE semantics). Real-world ETL wants it; touches write-path scope A3 ("no writes beyond
  materialize"). *Revisit when:* R-A trigger matrix converges — decide in or out then.
- **Expectation-driven rejects as a data-quality product story** (dbt-tests/Deequ/DLT-expectations
  analogue — `expect` clauses as first-class quality gates with reporting). *Revisit when:* R-A1
  converges; if β wins, this becomes its own feature effort.
- **Orchestration reactions to rejects** (quarantine tables, thresholds — "fail the run if >N%
  rejected"). v2/F territory; needs F proper. *Revisit when:* F opens.

## 8. Rolling open questions

- **R-Q1** · ~~Is `err rejects` in the v1 hero fixture header a fixture bug or meaningful?~~
  **Narrowed by R-B3 (2026-07-15):** treated as a hero-authoring artifact — when producers land,
  re-author the fixture to tap the cast-carrying calc's rejects and declare the port with `rejects`
  kind. Remaining piece is a fixture/implementation chore, not a design question.
- ~~**R-Q2** · PG version floor for `pg_input_is_valid`.~~ **Resolved 2026-07-15 by R-C1:** no
  global floor — the canonical-guard implementation exists regardless (it is the enforcing
  fallback); native forms are version-parameterized manifest entries. Concrete manifest content =
  implementation work, not design.
- ~~**R-Q3** · Overflow validity predicates.~~ **Resolved 2026-07-15 by RS-1:** overflow is not
  reject-capable in v1.x.
- ~~**R-Q4** · Volatile expressions under double evaluation.~~ **Resolved 2026-07-15 by
  R-C2-b:** forbidden in reject-capable expressions via catalogue purity flag.
- ~~**R-Q5** · Aggregate failure attribution.~~ **Resolved 2026-07-15 by RS-2:** aggregates are
  `err`-only.
- ~~**R-Q6** · Metadata must be canonical codes, not engine text.~~ **Resolved 2026-07-15 by
  R-B1 = β** (canonical `TTRP-RJ-…` codes, never engine text).
- ~~**R-Q7** · Multi-error rows: one reject row or one per error?~~ **Resolved 2026-07-15 by
  R-B4 = α** (first-error-wins, document order; error-set column = v2).
- ~~**R-Q8** · Polars fail-fast parity.~~ **Resolved 2026-07-15 by R-D4 = α:** abort is abort;
  parity = exit-status parity, no first-failure promise.

## 9. Session index

| # | Date | Mode | What happened |
|---|---|---|---|
| 1 | 2026-07-15 | Framing + divergence | Effort opened from v1 `next-steps.md` §4. Scope/targets/doc-home fixed (R-GI1..3). Control room + map + option docs `02`–`05` written; all six workstreams 🟡. No decisions. |
| 2 | 2026-07-15 | Convergence (R-A/R-B) | All `02` leans ratified as decisions (R-A1/A2/A3, R-B1/B2/B3/B4); **R-P2 ratified**; R-D2 decided by identity with R-A3; R-Q6/R-Q7 resolved, R-Q1 narrowed to fixture chore; R-Q9 demoted gate→de-risking spike. R-A/R-B 🟢 (R-Q3 overflow + R-Q5 aggregates carried open). |
| 3 | 2026-07-15 | Convergence (R-C) | All `03` leans ratified (R-C1 = guard-and-branch + tier ladder; R-C2-a guard CTE; R-C2-b purity flag; R-C2-c confirmed); R-Q2/R-Q4 resolved; R-Q9/R-Q10 carried. R-C 🟢. |
| 4 | 2026-07-15 | Convergence (R-D/R-E/R-F) | All `04`/`05` leans ratified (R-D1/D2/D3/D4, R-E1/E2/E3, R-F1/F2); δ exclusion confirmed; **R-P1/R-P3 ratified** (all three principles now standing); Q9 procedure grows the eighth (partition) check — parent contracts amendment pending; R-Q8 resolved. **All six workstreams 🟢.** Next: consolidation sweep (R-Q3, R-Q5, R-Q13/14, R-Q15, reserved prefix) → `design.md` + `detailed-design.md`. |
| 5 | 2026-07-15 | Sweep + wrap-up | RS-1..RS-5 batch-ratified (R-Q3/Q5/Q13/Q14/Q15 resolved; `_ttrp_` prefix). `design.md` + `detailed-design.md` written. **Effort CLOSED** — hand off to `/planning`; open R-Qs remaining = the four empirical spikes (R-Q9..R-Q12) + parked items in §7. |
