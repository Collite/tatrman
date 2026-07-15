# R-E / R-F — Compiler Integration & Fragment/Surface Options (DIVERGENCE)

> **Mode: divergence.** No decisions here. Control surface: [`00-control-room.md`](./00-control-room.md).
> Producers: [`03-sql-producer-options.md`](./03-sql-producer-options.md) /
> [`04-polars-conform-options.md`](./04-polars-conform-options.md). Opened 2026-07-15.

---

## R-E1 · Locus — where does reject elaboration live?

The producer options in `03`/`04` converge structurally on **guard-and-branch**. The fork is where
that shape materializes.

- **R-E1-α · Graph rewrite (reject-elaboration stratum).** A normalization pass rewrites every
  rejects-connected node into a canonical subgraph: `guard-calc (adds V columns) → branch(V) →
  {op on true-side → out, project+code on false-side → rejects}` — all *existing* node kinds plus
  internal validity functions in the catalogue (`is_castable(x, T)`, `is_nonzero(y)` — internal-only
  entries, not authorable surface syntax). Emitters never see a rejects port that carries rows;
  they see plain dataflow.
  - *Buys:* **one implementation of the semantics** for all engines (the R-D1-α/R-C1-α isomorphism
    made literal); emitters stay dumb (E-b/E-c untouched — the guard subgraph emits through the
    existing CTE-per-node / straight-line machinery); the optimizer (Z, later) sees honest dataflow;
    R-B3-β's join decomposition is just another rule in the same stratum; testable at graph level
    without any engine.
  - *Costs:* graph blowup (3–4 nodes per reject site — Designer view noise → R-F2 collapse);
    **provenance obligation**: diagnostics and the graphical surface must render the *authored* node,
    not the elaboration (E-d-γ's mandatory-provenance precedent applies verbatim); the internal
    validity functions must themselves be capability-checked per engine (they become the manifest
    surface, R-E2); rewrite ordering vs capability lowering needs pinning (elaborate before
    placement checks, so escalation sees the real shape? or after, so V-functions escalate as a
    unit? — **R-Q13**).
- **R-E1-β · Emitter-local.** Each emitter recognizes rejects-mapped ports and generates its guard
  natively (today's skip-sites become produce-sites).
  - *Buys:* shortest diff from current code; each emitter free to use native forms with no internal
    catalogue entries.
  - *Costs:* the semantics exist **N times** (PG, MSSQL, Polars, every future engine) with drift
    guarded only by conform-after-the-fact — the exact failure mode R-P2 exists to prevent;
    graph-level tests impossible; join decomposition (R-B3-β) can't live in an emitter (it is a
    graph transformation), so the semantics split across layers.
- **R-E1-γ · Hybrid: canonical elaboration + emitter fusion.** α's rewrite is normative; emitters
  *may* pattern-match the guard subgraph and fuse it into a native form (`TRY_CAST` single-eval)
  **when the manifest asserts domain equality** (R-Q9 evidence), else emit the subgraph literally.
  - *Buys:* α's single-semantics + β's native performance where it is provably safe; fusion is an
    *optimization with a proof obligation*, the right power balance.
  - *Costs:* pattern-matching machinery in emitters; two emit paths per site to keep conformant
    (fused/unfused) — the conform matrix doubles for those functions.

*Lean: α for v1.x, with γ's fusion as a recorded later optimization (fuse only under manifest
proof). β mapped and declined in advance: it is R-P2's anti-pattern.*

## R-E2 · Capability-manifest shape

- **R-E2-α · Boolean node-level flag** (`rejects: true|false` per engine). Too coarse: rejectability
  is per *expression/function*, not per node (a calc with only additions can't reject anywhere).
- **R-E2-β · Per-(function, type-pair) validity entries**, parameterized (T10-consistent):
  each reject-capable catalogue function carries, per engine, `{native_form?, domain: canonical |
  wider | narrower | unknown, min_version}` — feeding R-E1-γ fusion decisions, R-C1-ζ accelerators,
  and R-C1-ε escalation (`domain: unknown` + no guard = not SQL-placeable → escalate/error knob).
  - This is also where R-Q2 (PG16 floor) and R-Q11 (Polars versions) get *answered structurally* —
    version parameters, not global floors.
- **R-E2-γ · Policy knob** (project defaults, `[ttrp]`): `rejects-in-sql = produce | escalate |
  error` mirroring the T5-b escalation-policy precedent. Orthogonal to β; both can hold.

*Lean: β + γ. α recorded as the strawman.*

## R-E3 · Identity gate & determinism

- The elaboration must run **after** the graph-identity comparison point (the Phase-7 gate compares
  *normalized authored* graphs across surfaces) or be *itself* part of normalization applied
  uniformly to all three surfaces — either way byte-identity must keep holding. **R-Q14:** pin
  which normalization stratum (relative to sugar ≺ capability-lowering ≺ movement-synthesis)
  elaboration occupies; interacts with R-Q13.
- Deterministic naming for synthesized nodes/columns (`<ssa>_guard`, `_v1`, `_reject_code`) — the
  S-sweep naming rules apply; collisions with user columns need the reserved-prefix rule (R-B1-β).
- Fail-fast path (R-P3): nodes whose rejects port is unwired are **not elaborated** — zero graph
  delta, zero emit delta. The rewrite triggers on the *wire*, not the capability.

## R-E4 · Runtime surface of the rejects stream

Where do the rows physically land per engine (they are ordinary OUT-port results, but now three
engines produce them): SQL islands — one more terminal SELECT → ADBC/Arrow export per the
multi-output island model (phase-3 item #5 grows one port); Polars — one more sink write. Movement
of a rejects stream across engines is ordinary synthesized movement (already banked, F-d-i note).
No new runtime concepts — flagged here only so the executor work lands in the plan.

---

## R-F1 · Fragment reject taps (C2-e-β) — how far does the unlock go?

Producer semantics existing makes fragment taps *possible*; whether to open them is a scope choice.

- **R-F1-α · Keep the boundary (latent unlock).** Fragments still surface `err` only; wanting
  `rejects` still graduates you to a canonical container. The deferred-register promise is
  satisfied technically (the semantics exist; the boundary is now a *choice*, not a limitation).
  - *Buys:* zero grammar work across three dialects; C2-e's graduation logic stays crisp;
    fragment tails (TTR-B roster fidelity etc.) stay small.
  - *Costs:* the most common real-world reject site — a cast inside a SQL fragment — keeps
    requiring canonicalization of the whole island; users doing data cleaning in `.ttr.sql` feel
    the boundary exactly where it hurts most.
- **R-F1-β · Header-level tap.** The fragment's *container header* may declare a `rejects` port
  (`container clean(out ok, rejects bad) target pg """sql … """`); the fragment body stays pure
  dialect; **all** reject-capable sites inside the fragment feed the one port.
  - *Buys:* no dialect-grammar change at all (the header is canonical syntax already); the C2-e
    "single default output" rule relaxes minimally (out + rejects, both known kinds).
  - *Costs:* the **schema problem**: two reject sites with different input schemas can't union onto
    one statically-typed port (T2-a) — β is only sound with (i) exactly one reject site per
    fragment (compile error otherwise — crisp but limiting), or (ii) the R-B1-γ envelope (already
    disliked), or (iii) per-site ports (header explosion).
- **R-F1-γ · In-dialect syntax.** TTR-SQL grows a clause (`… REJECTS INTO <port>` — Oracle
  `LOG ERRORS INTO` ancestry); TTR-pandas a kwarg (`df.astype(…, rejects="bad")`); TTR-B a verb
  (`Convert X to number, sending failures to bad`).
  - *Buys:* the reject site is *named where it happens* — solves β's schema problem exactly (each
    clause is one site, one schema); TTR-B's verb reads beautifully for the business persona.
  - *Costs:* three grammars + three synonym-table/diagnostic updates (C2-g); TTR-SQL stops being
    "a SQL subset" the moment it has a non-SQL clause (the dialect's whole pitch is
    paste-your-SQL-in — a foreign clause breaks paste-back-out round-trips to real engines);
    TTR-pandas kwargs on *methods* don't map to pandas reality (no such kwarg exists — the
    "closed synonym table over canonical" story stretches).
- **R-F1-δ · Dialect-asymmetric:** γ for TTR-B only (verb tables are *designed* to be extended,
  S20 localization machinery exists), α for TTR-SQL/TTR-pandas (paste-fidelity dialects keep the
  graduation boundary).
  - *Buys:* each dialect stays true to its persona contract. *Costs:* asymmetry itself (docs,
    authoring-context capability rosters diverge per dialect — though that roster machinery exists).

*Lean: α for the v1.x producer release (decouple: land producers, keep boundary), then δ as the
follow-up — with β-(i) (single-site header tap) as a dark-horse worth pricing, since it needs no
grammar work anywhere.*

## R-F2 · Designer & diagnostics rendering

- Elaborated guard subgraphs (R-E1-α) render **collapsed to the authored node** by default —
  provenance-driven (E-d-γ), with a drill-in like containers get; the rejects wire attaches to the
  authored node's port visually. `.ttrl` view state keys stay on authored SSA names (ζ) — synthetic
  nodes must not mint view-state keys. **R-Q15:** does `ttrp/getGraph` expose elaborated or
  authored shape (or a `?elaborated=true` flag)? Designer wants authored; conform tooling wants
  elaborated.
- Dead-wire warning (R-A2-α) and forced-escalation warning (R-C1-ε) join the diagnostics tables
  with named codes (`TTRP-RJ-1xx` authoring diagnostics vs `TTRP-RJ-0xx` row-level codes — split
  the range up front).

## RESOLVED (2026-07-15) — R-E/R-F converged

All leans ratified (full text in the control-room decision log):

- **R-E1 = α** graph-rewrite elaboration stratum for v1.x; γ fusion parked as a proof-gated later
  optimization; β emitter-local declined as R-P2's anti-pattern.
- **R-E2 = β + γ** — per-(function, type-pair) manifest validity entries + the
  `rejects-in-sql = produce|escalate|error` knob.
- **R-E3** — deterministic uniform elaboration (identity gate held); reserved-prefix naming (exact
  prefix → sweep); **unwired ports are never elaborated** (R-P3 structural). Stratum position =
  R-Q13/R-Q14 → sweep.
- **R-F1 = α now, δ parked follow-up** — graduation boundary stands for the producer release;
  TTR-B-verb taps (+β-(i) pricing) revisit once producers pass the RH-1 seal.
- **R-F2** — provenance-collapsed Designer rendering; no `.ttrl` keys for synthetic nodes;
  `TTRP-RJ-0xx` row codes / `TTRP-RJ-1xx` authoring diagnostics; R-Q15 → sweep.

## Open questions raised here

- **R-Q13** · Elaboration vs placement/capability-check ordering (escalate the authored node or
  its elaborated pieces?).
- **R-Q14** · Which normalization stratum (identity-gate safety) — with R-Q13, probably one
  decision.
- **R-Q15** · `ttrp/getGraph` authored-vs-elaborated exposure.

## Cross-links

R-E1-α ⇐ R-C1-α/R-D1-α (the isomorphism argument) · R-E1-γ fusion ⇐ R-Q9 evidence · R-E2-β ↔
T6/T10 manifests · R-E3 ↔ Phase-7 identity gate · R-F1 ↔ C2-e/C2-g · R-F1-γ TTR-B verb ↔ S20
localization · R-F2 ↔ E-d-γ provenance + `.ttrl` ζ keys.
