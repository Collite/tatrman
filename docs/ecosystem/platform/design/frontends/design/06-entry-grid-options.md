# Platform Frontends — The Entry Grid Options (workstream C, thread 1: the grid core)

> Divergence catalogue for **C — the budgeting entry grid**: how a planner enters, spreads, overrides, locks, re-spreads — in the in-memory working view that E's two-pass model created. Session 2026-07-10, grounded in Bora's grid brief (GI-4..GI-8, control room §2).
> Companions: [Control Room](./00-control-room.md) · [Map](./01-design-space-map.md) §C · [`04-spreading-options.md`](./04-spreading-options.md) (E, converged — the committed pass) · [`03-write-path-options.md`](./03-write-path-options.md) (D, converged — where commits land).
>
> **Scope guard:** this doc owns the *grid core* — the visible-pass interaction semantics. C's other threads (commit/preview flow incl. rebase-on-commit UX, entry workflow Q-3, form-declaration vocabulary Q-4/Q-7 as md syntax) are named here but deep-dived separately.
>
> **Working prototype:** [`prototypes/c-entry-grid-v0.html`](./prototypes/c-entry-grid-v0.html) — single-file, in-browser, implements the leans below against the hero scenario (solver verified by 16 scripted assertions). Several forks below were *settled or sharpened by building it*; those findings are marked **[proto]**.

## 0. The problem statement

The planner opens a **view** (form) over a TTR-MD-defined **cubelet** and gets a pivot-shaped grid: hierarchies on rows and columns, (sub)totals per available hierarchy, filters in the header. Every cell — leaf, subtotal, grand total — is enterable; entering a summary spreads; locks pin cells at any level; block operations and patterns manipulate ranges. Everything happens **in memory, on the view's copy** (E's visible pass); commit ships only ratified load-grain assignments into D's journal.

Requirements inherited from the principles and E:

- **P3 (propose, don't write):** the grid is never the authority; its numbers are a working copy until commit.
- **P4 (explainable):** every cell can show *how it got its value* — entry, spread factor, lock compensation, seed, curve.
- **E's invariants carry into the view:** proportional-over-unlocked, zero base = error (seed explicitly), existing support only, over-constrained = explicit error. The visible pass may be *richer* than the committed pass, but where the same operation exists it must behave congruently, or preview lies about commit.
- **Determinism:** a gesture sequence replays to the same view state (op-log discipline; enables undo, session recovery, and audit of the visible pass).

## 1. The three grains (the load-bearing clarification) — C-T1

The brief makes explicit what E's "working view" left implicit — there are **three grains**, not two:

```
storage grain  (cubelet leaves, engine-side)         e.g. cc × account × month × version
   ▲  committed pass: ONE mechanism (E) — invisible
load grain     (the view's leaves, in memory)        e.g. department × account × month
   ▲  visible pass: rich tooling (this doc)
display grain  (what's on screen after pivot/collapse) e.g. department × quarter
```

Consequences, rendered against the hero **[proto]**:

- **"View leaf = just overwrites"** is exact *at load grain* — but unless load grain == storage grain, even a leaf overwrite is still spread by the committed pass on its way down. The grid's "leaf" is a *ratified assignment*, not a storage write.
- **Zero-base pre-flight.** E's zero-base error can fire at *commit* for a view-leaf entry whose storage base under it is zero — invisible from the grid. And it fires *in the view* when a summary is entered over all-zero view leaves (hero: Marketing·Software). The grid should surface both the same way: refuse + offer the **seed** gesture. Whether the grid can *pre-flight* storage-grain zero-bases (needs a base-presence bitmap per load-grain cell from the server at load time) is **CQ-5** — cheap to ship in the load payload, and it turns a commit-time surprise into a load-time marking.
- **Q-5 sharpened:** the cubelet is the storage-grain object (cube × version-slice as md declares it); the **form/view** adds: load grain (summarize-over dims), ≤1 hierarchy per remaining dimension (GI-4 — a very good v1 cut: single rollup path ⇒ unambiguous subtotals), initial layout (rows/cols/filters/hidden), and entry affordances (enterable regions, defaults). Q-7's split lean: **form = canon** (modeling-time, planners never author it — A's verdict); **pivot state** (rotation, collapse, filter picks) = ephemeral per-user preference, saveable platform-side, never canon.
- **Size budget:** the form must bound the load-grain slice to in-memory scale. Lean: a declared guard (validate at form-authoring time + runtime check at load), order 10⁴–10⁵ cells. EQ-4's latency question **dissolves for the visible pass** — every gesture is client-local; the round trip only remains at load and commit-preview **[proto: 120-cell hero recomputes imperceptibly; a 100k-cell view needs the virtualized host of C-T5, not a server]**.

## 2. C-T2 · Lock semantics — the load-bearing fork

A lock says "this cell cannot change". The fork is *what that means for everything else*.

### CL-α · Freeze-contents
A lock on a cell freezes **every leaf under it**. Trivially simple, trivially explainable.
*Refuted by the hero* **[proto]**: locking the row total then decreasing H1 must let H2 *catch up* — under α the locked row's leaves can't move at all, so the signature interaction is impossible. Catalogued as the floor.

### CL-β · Pin-sum + compensation cascade (TM1-holds shape) — **the brief's implied semantics**
A lock pins the cell's **aggregate value**; the contents under it may reshuffle. Every gesture is atomic: (1) apply the user's assignments (each spread proportionally over unlocked-and-unpinned leaves; leaves under satisfied inner locks are frozen for that spread); (2) reconcile: while any locked cell's sum drifted, re-assign it to its pinned value — smallest region first, user-touched cells excluded; (3) if reconciliation can't converge or a spread has no leaves left to move → **error, whole gesture rolls back**.

*Buys:* the brief's exact behaviors — lock total → block-decrease H1 → H2 rises; lock a leaf → re-spread flows around it; nested locks (edit inside a locked quarter rebalances within the quarter). Every derived value carries a **factor chain** — P4's explanation is `entry ×1.0638 → catch-up to hold Field Sales × FY ×1.0935`, an inspectable artifact. Deterministic (fixed reconciliation order).
*Costs:* non-nested **crossing locks** (a locked row total × a locked column total, edit at the intersection) can oscillate; the cascade answers with iteration-cap ⇒ error, which is honest (P3: over-constrained = explicit) but rejects some mathematically-solvable states.
**[proto]** implemented in ~200 lines; two rules carry all the weight: *uniform scaling of unlocked leaves preserves proportionality at every intermediate level for free*, and *outer compensations must treat satisfied inner-lock regions as frozen* — that single rule makes arbitrary nesting work. Zeros stay zero under scaling, so "existing support only" is automatic.

### CL-γ · Global constraint solver (IPF / least-squares)
Treat locks + entries as a constraint system; solve (iterative proportional fitting handles the crossing-locks case natively — it's the classic RAS/biproportional problem).
*Buys:* accepts everything feasible; the 2D case is *solved*, not rejected.
*Costs:* P4 pain — "the solver found the nearest feasible point" is not a planner-explainable derivation; determinism needs care (iteration counts, convergence thresholds in the spec); harder to keep congruent with the committed pass's single mechanism.

**DECIDED 2026-07-10 → CL-β** (Bora ratified the lean), with CL-γ's IPF recorded as the *escalation path* if real planning rounds hit the crossing-locks rejection often (CQ-1 → v1 = error-only; IPF → parking lot). Sub-forks: lock **lifetime — DECIDED 2026-07-10: frontend-only, ephemeral, never stored anywhere** (Bora; supersedes the session-scoped-with-possible-persistence lean — no lock ever reaches the journal, the entry record, or the DB); lock **scope = any cell** (GI-6); locked cells are **skipped, with a count, by block ops** but **hard-error on direct edit** [proto UX finding: erroring the whole block op because one cell is locked is infuriating; silently skipping a *directly edited* lock is a lie].

> **The lock-free committed pass (decided 2026-07-10, amends E wording + D-iii).** Because ratified assignments live at load grain and load-grain cells *partition* the storage leaves, each assignment spreads within a disjoint storage region — locks can never influence the committed math, so the backend spread is **lock-free by construction**: *per assignment, scale the existing storage base beneath it proportionally; zero base = error; largest-remainder rounding*. The entry record drops its locks field; committed derivations replay from (assignments + leaf-set hash + program version) alone. Locks — like formulas (GI-8) — are purely visible-pass instruments. (D-iv's advisory slice claims = concurrency presence, not value locks; unaffected.)

## 3. C-T3 · Edit propagation policy

- **When does compensation run?** α **immediately per gesture** (TM1 feel; the grid is always consistent) · β deferred to an explicit "balance" action (Excel-paste-like freedom, but the grid shows *wrong totals* between balances — violates the always-reconciled invariant that makes summaries trustworthy) · γ refuse edits that would need compensation. **DECIDED 2026-07-10 → α** — with *batch gestures as one op* (a block −10% compensates once, not per cell) **[proto]**.
- **Do edits survive later spreads?** The brief says a summary entry spreads "to all leaves except locked ones" — so an *unlocked* earlier edit gets proportionally rescaled (its exact value is lost, its *share* is kept). Fork: **no auto-lock** (pure TM1: locking is the user's job; cheap lock gesture) · **auto-lock-on-manual-edit** (planner-friendly: "I typed it, don't wash it away"; risk: silent lock accumulation → mysterious over-constraint errors later) · **visual-pin hybrid** (edits marked, one click to lock-all-my-edits). ~~Genuinely open — **CQ-2**~~ **RESOLVED 2026-07-10 → a user toggle** ("sometimes I want the locks, sometimes I do not" — Bora, from the prototype's toggle); per-user preference, persistence home rides CQ-4.
- **Undo** is per-gesture over the op log **[proto]**; the op log is also the visible-pass replay/audit artifact (P4 at session scope).

## 4. C-T4 · The manipulation vocabulary (visible pass — freely extensible by design)

E moved all rich mechanisms here as non-authoritative tooling. **DECIDED 2026-07-10: the table below IS the v1 surface** (formulas deferred to v1.5, CQ-6 stands); all demonstrated **[proto]** except formulas:

| Gesture | Semantics | Notes |
|---|---|---|
| **typed entry + shorthand** | `120M` `450k` absolute · `+2M` `-500` delta · `+8%` `-10%` relative | TM1/Jedox "spread codes" ancestry, without the cryptic single-letter codes |
| **summary entry** | proportional spread over unlocked view leaves | congruent with the committed mechanism (same shape, view grain) |
| **block select + scale** | ±% (or set/add) each leaf in a rectangle; locked cells skipped-with-count | the hero's "decrease H1 10%" — compensation makes H2 catch up |
| **pattern / curve apply** | redistribute each row's block total along a declared curve (seasonality) | curves = reference data; block total preserved (locks inside the block honored) |
| **seed** | explicit leaf-copy from a named source slice (`BUD2028 := ACT2027`), fills empty cells only, never spreads | E's mandated answer to zero base; must be a *first-class gesture* reachable from the zero-base error itself |
| **ephemeral formulas** | `=` expressions, session-scoped, die at session end (GI-8) | fork: reference view cells only, vs reference **driver slices** (`=ACT2027 × 1.05`) — the latter is where driver-based planning lives post-E (drivers are visible-pass tooling). Scope for v1: **CQ-6**; needs an expression surface the grid must parse — the one place C might want the browser-side TTR-P LSP worker instead of inventing syntax |

Rendering-vocabulary note: **filters in the header + attribute/hierarchy pivoting** (GI-4) are table stakes from the Excel-pivot idiom; v0 prototype fixes the layout and demonstrates only collapse/expand — rotation/filters are form *initial-layout* territory and pure render-shell work (C-T5), no solver interaction.

## 5. C-T5 · The in-memory host (cross-links F)

Finding first **[proto]**: the *model layer* — leaf matrix + hierarchy closure + lock cascade + provenance chains — is **custom platform code regardless of host**; no off-the-shelf grid implements pin-sum spreading. The fork only decides the **pivot/render shell** around it:

- **α · FINOS Perspective** (Apache-2.0, WASM pivot engine, Arrow-native, virtualized). *Buys:* industrial pivoting/filtering/rotation for free, Arrow end-to-end (query door → grid). *Costs:* its aggregation pipeline owns the numbers — intercepting aggregate-cell *writes* with our solver means fighting the engine or double-bookkeeping (its rollups vs our matrix).
- **β · Custom model + thin virtualized grid** (TanStack Virtual / hand-rolled, as the prototype). *Buys:* the solver IS the model — no impedance; grid is the dumb part. *Costs:* we own pivoting UX (rotation, filters, drag-drop zones) that Perspective gives free.
- **γ · Commercial grid** (AG Grid Enterprise-class). *Buys:* pivot + editing maturity. *Costs:* license cost/vendor coupling inside a `cz.tatrman` product; still doesn't do constraint spreading.
- **δ · Weird: embed a spreadsheet engine** (Univer / HyperFormula-class) — buys formulas and range UX; costs a second computation authority with its own semantics to fence off (and license diligence ⚠ unverified). Prior-art note: A-δ (Excel add-in, parked) is this option taken literally.

**DECIDED 2026-07-10 → β for the entry app** (Bora ratified the lean: the solver-grid coupling is the product; hero-scale views don't need WASM), with **α re-evaluated if** load-grain budgets grow past ~10⁵ cells or rotation/filter UX proves expensive to hand-build. Framework/stack decision itself still belongs to F; this constrains F only to "no heavyweight grid dependency".

## 6. C-T6 · Non-additive measures — ~~a scope tension to resolve ⚑~~ RESOLVED

E converged **additive-only v1** (non-additive → parking lot). The brief (GI-7) named **two v1 mechanisms**: proportional for SUM measures *and* a different proportional for non-aggregating measures (prices). Options catalogued: (a) hold E's cut — grid v1 refuses entry on non-additive measures (display-only), parking-lot stands; (b) **amend E**: add the second mechanism — for ratio-like measures a summary entry is a *multiplicative re-level* (scale every leaf by new/old aggregate, aggregate = weighted average; no sum to preserve, locks still pin); (c) non-additive entry as **visible-pass-only** tooling that ratifies *leaf* assignments (no committed construct — the grid does the re-level, commits leaves).

**RESOLVED 2026-07-10 → (a): non-additive spreading is out of v1** (Bora retracted GI-7's second mechanism — "only the SUM-able ones"); E's cut stands, parking-lot entry unchanged. (c) stays recorded as the likely shape when rates/prices demand arrives — it needs no committed construct, so un-parking it later won't reopen E.

## 7. C-T7 · Provenance, dirty state, and the journal panel (P4 in the grid)

**[proto]** Every leaf keeps an op chain (`entry · spread ×f · catch-up ×f · seed · curve`); the inspector renders it; color taxonomy = entered / spread / compensated / seeded / locked; aggregates mark changed-underneath. The **journal panel** shows exactly the ratified load-grain assignments that commit would ship (D's journal rows) — making the two-pass boundary *visible in the UI*: "locks & mechanics never leave this tab" is written on the panel. This is the honest version of preview→commit (P3): the planner sees precisely what is proposed.

## 8. Prior art

TM1 (spread codes, holds incl. consolidation holds, sandboxes) and Anaplan (breakback) prove the interaction vocabulary — 30 years of planners lock totals and push blocks around; none make the derivation inspectable (their audit is a log; our factor chains + entry record are artifacts — the γ differentiator from `04` carried into the UI). Jedox splashing = the same codes idiom. Excel pivots: manipulation idiom only, no writeback. FINOS Perspective / Univer: host candidates (C-T5).

## 9. Leans → decisions (grid core ratified 2026-07-10)

1. ~~Lean~~ **DECIDED: CL-β** pin-sum + compensation cascade; over-constrained/oscillating = error; IPF = parked escalation (CQ-1 → error-only v1).
2. ~~Lean~~ **DECIDED:** immediate per-gesture compensation, atomic gestures, per-gesture undo over an op log.
3. **DECIDED: locks FE-only/ephemeral/never stored (lock-free committed pass)**; any cell; block ops skip locked with count; direct edit of locked = error.
4. ~~Lean~~ **DECIDED: vocabulary v1** = shorthand entry, summary spread, block scale, curve apply, seed; formulas v1.5 (scope CQ-6; formulas likewise FE-only ephemeral).
5. ~~Lean~~ **DECIDED: host β** (custom model + thin virtualized grid); Perspective re-evaluated at scale; framework/stack = F.
6. **DECIDED: non-additive out of v1** (§6); (c) recorded for the future.
7. Form = canon (load grain, ≤1 hierarchy/dim, initial layout, enterable regions, defaults); pivot state = per-user platform prefs — **still a lean** (Q-7; converge with the form-vocabulary thread).
8. **DECIDED: auto-lock-on-edit = user toggle** (§3).

## 10. Open questions (C-local, this thread)

- ~~**CQ-1 ·** Crossing locks: error-only v1, or IPF?~~ **RESOLVED 2026-07-10 → error-only in v1** (part of the CL-β ratification); IPF = parking-lot escalation with its convergence/tie-break spec to be written *if* un-parked. *(Pure-FE question since the lock-free committed pass.)*
- ~~**CQ-2 ·** Auto-lock-on-manual-edit~~ **RESOLVED 2026-07-10 → user toggle (§3).**
- ~~**CQ-3 ⚑ ·** Non-additive measures in v1~~ **RESOLVED 2026-07-10 → out of v1; E's cut stands (§6).**
- **CQ-4 ·** Where do per-user pivot states + saved personal views live (platform prefs store? Designer-style workspace state?) — contract question for D/F.
- **CQ-5 ·** Storage-grain base-presence bitmap in the load payload (zero-base pre-flight at load time)? Lean yes.
- **CQ-6 ·** Ephemeral formula scope: view-cell refs only vs driver-slice refs; syntax = TTR-P expression fragment (browser LSP) vs grid-local mini-language?
- **CQ-7 ·** The load-size guard: number (10⁴? 10⁵?), and is it form-author-time validation, load-time error, or both?

Inherited, still open at C level: Q-3 (workflow), Q-4/Q-7 (form-declaration syntax — the md-vocabulary conversation with TTR-M), EQ-4 (now: commit-preview latency budget only), D-iv's rebase-on-commit UX (drift ⇒ re-preview flow).

## Convergence status

**🟢 GRID CORE (thread 1) CONVERGED 2026-07-10** — in two passes, both Bora, from prototype feedback ("exactly what it needs to be"):

Pass 1: **CQ-2 → user toggle** · **CQ-3/Q-8 → non-additive out of v1** · **LOCK-FREE COMMITTED PASS** (locks & formulas FE-only, ephemeral, never stored; entry record drops locks; committed construct = per-assignment proportional-over-existing-base — amends E wording + D-iii; control room §7 C-section is ground truth) · **GI-9** (FE operates on *loaded* scope, not visible — collapse hides, never unloads).

Pass 2 (the leans ratified wholesale): **C-T2 = CL-β** pin-sum + compensation cascade (CQ-1 = error-only v1, IPF parked) · **C-T3 = immediate per-gesture compensation** · **C-T4 = the v1 vocabulary table** (formulas v1.5) · **C-T5 = β custom model + thin virtualized grid** (framework → F).

Still open here: CQ-4 (prefs home), CQ-5 (base-presence bitmap, lean yes), CQ-6 (formula scope, v1.5), CQ-7 (load-size guard number/enforcement) — none blocks the other C threads. **Workstream C overall stays 🔵**: still to diverge are commit/preview flow UX (incl. D-iv rebase-on-commit drift), workflow (Q-3), and the form-declaration md vocabulary (Q-4/Q-7).
