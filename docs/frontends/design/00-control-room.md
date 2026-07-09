# Platform Frontends — Control Room

> The single dashboard for the **Platform Frontends** design effort: giving the Tatrman Platform its own product surfaces — a **BI/analysis frontend** over TTR-P results and an **entry/planning frontend** over MD-model cubelets (summary-level entry, spread down) — after the platform design (A–K, 2026-07-08/09) deliberately left Iris and Sysifos on the Kantheon side.
> Open this first every session. Companion docs: [`01-design-space-map.md`](./01-design-space-map.md) (the branches); option docs `02+` as workstreams deep-dive.
>
> **Status:** 🔵 **FRAMING DONE, DIVERGENCE OPEN.** Effort opened 2026-07-09. No workstream converged yet.

---

## 0. How we run this

Same protocol as the TTR-P and Platform efforts (`../../ttr-p/design/00-control-room.md` §0, `../../platform/design/00-control-room.md` §0): **diverge before we converge**; three gears (Framing → Divergence → Convergence); ≥3 alternatives per fork including the weird one; leans are not decisions; deferred is a tracked outcome; the append-only decision log is ground truth.

Cross-effort citations use the source prefix: **`PL`** = the platform design effort (e.g. `PL FI-3`, `PL D-3`, `PL Q-4-a`, `PL G-1`), `TTR-P`, `MD`n (ttr-metadata), `TR`-n (ttr-translator). This effort's own IDs are bare letters; other efforts cite them as `PF <id>`.

---

## 1. Workstream dashboard

Status legend: ⚪ not started · 🔵 diverging · 🟡 options captured · 🟢 converged/decided · 🟠 converged-but-dirty · ⏸ parked

| # | Workstream | Status | Core question | Notes |
|---|---|---|---|---|
| **A** | Product shape & placement | 🟢 | How many surfaces (one workbench / two apps / Designer Extensions), for whom, and on which side of the license line? | **Converged 2026-07-09 → the SPLIT VERDICT** ([`05-product-shape-options.md`](./05-product-shape-options.md)): analysis (B-read viewer + B-author) = **Designer Extensions**; entry = **its own standalone platform product**; **Excel add-in = recorded future alternative client** of D's contracts; both surfaces `cz.tatrman` (no new MIT frontend); planners never author canon (config-vs-use line). Resolves LF-1 + LF-6. |
| **B** | The BI/analysis surface | 🟡 | Buy/embed an OSS BI vs build on the query door vs reuse Iris's render pipeline — and what does "interactively generate TTR-P" mean as a product? | **Options captured 2026-07-09** → [`02-bi-surface-options.md`](./02-bi-surface-options.md): long list (3 tiers, researched); **two structural findings — the policy-honesty problem (R0–R3) and the B-author/B-read decomposition**; lean = δ-shaped composite (semantic projection + bring-your-BI + thin native viewer). Converge *with* D (the R fork couples); BQ-6 verification pass owed. |
| **C** | The entry/planning surface (UX) | ⚪ | The cubelet grid: how a planner enters at summary level, spreads down, overrides, locks, re-spreads, and commits — versions and entry workflow included. | Sysifos UX DNA (bulk grid, preview→commit, Draft+SSE) is prior art, not a constraint. |
| **D** | The data-write path | 🟢 | Where do entered numbers *land*? The platform has two doors (query, program) and git for canon — a budget cell is none of these. Third door? Entry-as-program? Direct write? | **Converged 2026-07-09** → [`03-write-path-options.md`](./03-write-path-options.md): **γ-front + β-ii-back composite** — entry journal beside the cube (draft/session/audit substrate) + commit = run of the standing **canon entry-apply program** (table-valued typed input) + **entry record** provenance; D-i journal semantics, D-ii Argos-symmetric write RLS (PL amendment), D-iv cell-LWW + advisory claims, D-v version metadata in md. DQ-1/2 = named PL amendment proposals; DQ-3..5 → planning-stage if effort graduates. |
| **E** | Spreading & allocation semantics | 🟢 | Where does spread/split logic live — frontend, a platform service, or **the TTR-P language itself** — and what is the operation vocabulary (proportional/even/profile/driver, locks, rounding)? | **Converged 2026-07-09 → the TWO-PASS MODEL (Bora's amendment)**: visible pass = in-memory working view, rich vocabulary non-authoritative (→ C); invisible committed pass = **single mechanism: proportional-over-unlocked-remainder**, zero base = ERROR, existing support only, additive measures v1, rebase-on-commit; placement γ (one TTR-P construct) + δ ergonomics; defaults in TTR-M md. → [`04-spreading-options.md`](./04-spreading-options.md) resolution. |
| **F** | Reuse, stack & contracts | ⚪ | What is actually reusable from Iris / Sysifos / report-renderer / the Designer given `PL D-3` ("kantheon owns nothing shared") and the Vue-vs-React split; which contracts must move or be re-owned? | Owns LF-5. Naming (PL J register: new platform-native = Slavic) parked to the end. |

---

## 2. Framing inputs (Bora, 2026-07-09 — FIXED)

- **FI-1 · The gap is real and named.** The platform design (`PL` C-0 inventory) left *all* product frontends on the Kantheon side; the Designer is a metadata/ops surface (catalog, graphs, runs, lineage), not a data product. After the split, **the Tatrman Platform has no frontend for its own output**. This effort fills that gap.
- **FI-2 · Two product needs.** (a) A **BI frontend** to see the results of TTR-P transformations, *and* to interactively author/generate the TTR-P that produces them; (b) a **data-entry frontend** ("post-Sysifos") over MD-model cubelets, with **splitting/spreading as the core capability** — planning & budgeting style: enter on a summary level, spread down.
- **FI-3 · Iris and Sysifos both stay in Kantheon — for different reasons.** Iris is agent-coupled (chat surface over Themis dispatch — intelligence, `PL FI-3`). Sysifos has **no intelligence** (its own design doc: BFF has "no business logic"; all derivation in Midas-core) but is **Midas-coupled** — its only backend is Midas-core's book of record. The platform entry surface is a **sibling** of Sysifos (same UX DNA), not a move or rename of it.
- **FI-4 · Effort home & timing.** New effort folder (this one), separate from the closed `PL` control room. **Design-ahead, build post-v1:** full freedom to *propose* amendments to platform contracts, but the PL-P0–P7 plan is untouched except by explicit amendment PRs; the frontends build after (or late in) the strangler ①–⑦.
- **FI-5 · Surface count is a fork, not framing.** One workbench vs two apps vs Designer Extensions is workstream A's LF-1, decided consciously.

### Grounding inputs (diverge-worthy, not decisions)

- **GI-1 · "So many OSS BI solutions that developing another one seems overkill"** (Bora). Buy/embed leans first for B — but gets ≥3 recorded alternatives like everything else.
- **GI-2 · "Maybe we can reuse a lot of Iris and Report Renderer?"** (Bora). Recorded as a branch of B/F — with the `PL D-3` ownership problem stated (see §4 LF-5).
- **GI-3 · Spreading is the entry surface's *point*.** Not a nice-to-have: summary-level entry with spread-down (planning/budgeting) is the differentiating capability vs a generic CRUD grid.

---

## 3. What we already have (asset inventory)

- **The query door (Theseus, `PL C-3-γ`)** — synchronous single-validated-plan door, built for "agents, Designer previews, debug", interactive priority at Kyklop (`PL F-5-γ`), caller-principal identity (`PL H-2`). **The BI read path exists by construction.**
- **The Designer + Extensions surface (`PL Q-4-a`, `PL G`)** — MIT React 19 frontend, backend-selectable; platform-shipped panels on an MIT-defined extension surface; reader-first v1 (catalog, graphs, runs, column lineage); edit mode designed (writes-through-git). A candidate *home* for BI panels — and a competing option to any standalone app.
- **TTR-M `md` modeling + `@tatrman/md-catalog`** — the MD calc-map catalog (`MD_CALC_CATALOG`); cubes/dimensions/hierarchies are already language citizens. The entry surface's "cubelet" has a modeling-language substrate to bind to.
- **TTR-P + the E-5 manifest** — programs, static column lineage, read-only program graphs already rendered in the Designer. "Interactively generate TTR-P" composes with the existing compile/validate path (browser-worker LSP exists).
- **Sysifos design DNA** (`kantheon/docs/design/sysifos/sysifos-design.md`) — propose-don't-write; preview→commit; single form + paste-friendly bulk grid; hybrid sync/Draft+SSE write model; three-layer validation (FE Zod ← BFF pre-flight ← core authority); quick-create-mid-entry. Proven shape for correct-and-fast entry.
- **Iris render pipeline** (`kantheon/frontends/iris`) — Vue 3 + PrimeVue + **Vega-Lite** chart rendering, `envelope/v1` block model (text/table/chart + drilldowns + chips), dockview workspace. Technically mature; **kantheon-owned** (see LF-5).
- **report-renderer, kallimachos, pinakes** — swept "stays kantheon" (`PL D-6`); pinakes = verify-then-place in ②.
- **The platform contract set** (`PL` plan/contracts.md v1.1) — envelope, run/lineage/audit events, door API, snapshot archives, `ttr.lock`, policy bundles. The frontends *consume* these; gaps become amendment proposals (FI-4).

---

## 4. Load-bearing forks (decide consciously, not by drift)

1. ~~**LF-1 · Surface topology.**~~ **RESOLVED 2026-07-09 → the split verdict: analysis = Designer Extensions; entry = own standalone product; Excel add-in = future alternative client (A).**
2. **LF-2 · BI make-or-take.** Embed/integrate an existing OSS BI (Superset/Metabase/Lightdash/…) · build a native surface on the query door · reuse Iris's render pipeline (re-owned) · headless-BI hybrid (semantic layer ours, viz theirs). → **B** (🟡; lean δ-composite; owes BQ-6)
3. ~~**LF-3 · The write path for entered data.**~~ **RESOLVED 2026-07-09 → journal-front + standing canon entry-apply program + entry record (D = γ+β-ii composite).**
4. ~~**LF-4 · Where spreading lives.**~~ **RESOLVED 2026-07-09 → the two-pass model: rich tooling in the in-memory working view (non-authoritative, → C); committed pass = single proportional TTR-P construct (E = γ+δ).**
5. **LF-5 · Reuse vs re-own.** `PL D-3`: kantheon owns nothing shared; arrow is `tatrman → platform → kantheon`. Reusing `envelope/v1`, `envelope-ts`, or report-renderer **in place is illegal** — options are move/fork/re-own platform-side, or don't reuse. Compounded by the stack split (Iris/Sysifos = Vue; Designer = React). → **F** (narrowed by A: extensions ⇒ React kinship for analysis; entry app's stack still F's call)
6. ~~**LF-6 · License & repo placement.**~~ **RESOLVED 2026-07-09 → both surfaces platform-side `cz.tatrman`; analysis extensions ride the MIT extension surface (`PL D-2` pattern); no new MIT frontend (A).**

---

## 5. Design principles (P-n; cite by ID)

- **P1 · Inherited: `PL P1–P3` + `PL B-4` apply verbatim.** Standalone is not a demo; one-way arrow; no miracles; seam legality.
- **P2 · Deterministic surfaces. (candidate — ratify)** Platform frontends contain no LLM/agent intelligence; NL analytics stays Iris/Kantheon (`PL FI-3`). If a surface wants "ask in natural language", that is a kantheon integration point, not a platform feature.
- **P3 · Propose, don't write. (candidate — ratify)** Every entry surface assembles a proposal, previews server-derived effects (spread results, validation), and commits explicitly; the frontend is never the authority. (Sysifos DNA, generalized.)
- **P4 · Spreads are explainable. (candidate — ratify)** Every derived number can show *how it was derived* (method, driver, locks honored, rounding) — P3-no-miracles applied to allocation.

---

## 6. Hero scenario (carried through every workstream)

**"The FY2028 OPEX budget."** MD model: cube `opex` — measure `amount`; dimensions `account` (hierarchy), `cost_center` (company → division → department → cc), `month` (12 + quarter/year rollups), `version` (ACT / BUD / FCST).

1. **Enter.** A planner opens the entry surface on the `opex` cubelet (version `BUD2028`), enters **Sales division, FY total = 120 M CZK** at division × year level.
2. **Spread.** The system spreads down to department × account × month **proportional to FY2027 actuals** (driver = `ACT2027` slice). The planner overrides one leaf — March marketing campaign, +2 M — **locks it**, and re-spreads the remainder; totals still reconcile to 120 M. Every cell can explain its derivation (P4).
3. **Commit.** Preview shows the derived cell set; commit lands the entries through the platform write path (LF-3) under the planner's principal, RLS-checked, audited; version `BUD2028` gains a new state.
4. **Analyze.** An analyst opens the BI surface: budget vs FY2027 actuals variance, pivots department × quarter, drills to account, charts the bridge — reads ride the query door. She then interactively authors a small TTR-P transform (variance bridge as a derived table), sees results live, and saves it — the program can graduate to a deployed bundle later.

Every workstream renders its options against this scenario.

---

## 7. Decision log

> Append-only. Format: `YYYY-MM-DD · [id] · Decision · Why · Alternatives rejected`.

- **2026-07-09 · [FRAME] · FI-1..FI-5 accepted as fixed framing** (the gap; two product needs; Iris agent-coupled + Sysifos Midas-coupled both stay kantheon, entry surface = sibling not move; new effort folder, design-ahead/build-post-v1; surface count = fork). · Bora's opening statement + framing Q&A, this session. · Alternatives (reopening the PL control room; pushing frontends into v1 scope) consciously rejected in the same Q&A.

### D / write path + E / spreading (options + resolutions in `03-` / `04-`)

- **2026-07-09 · [D = γ+β-ii composite] · THE ENTRY WRITE PATH: journal-front + standing-program-back.** The surface writes to an **append-only entry journal beside the cube** (draft/session/audit substrate); **commit = a run of the standing, canon `<cube>-entry-apply` TTR-P program** with the journal batch as table-valued typed input; the version slice is its materialization; every commit produces an **entry record** (compile-record sibling: principal, assignments, method params, locks, leaf-set hash, run ref). Rides the whole existing spine (Argos, Kyklop, quota, run store, events, lineage). · Why: program materialization is the platform's one existing data-plane write mechanism; the composite keeps interactive feel (journal) without a second authority. · Rejected: α full third door (reduces to the missing door verb + session endpoints once the composite is seen); β-i ephemeral program-per-commit (run pollution; unreviewed generated text as authority); δ direct BFF write (bypasses the spine — floor).
- **2026-07-09 · [D-i/ii/iv/v] · Write-semantics sub-forks:** journal + materialized current state (rejected: versioned overwrite — thin audit); **RLS-on-write = Argos-symmetric write predicates** with door-PEP coarse check in front (a `PL H` amendment proposal); concurrency v1 = cell-LWW over the journal + advisory slice claims (G-1-γ's presence idiom at the data plane) + **rebase-on-commit** (leaf-set-hash drift ⇒ re-preview, never silent re-portioning); version protection (ACT read-only, BUD open-then-locked) = **TTR-M md version-dimension metadata** (canon), enforced at the write check. Scenario-as-data-branch → parking lot.
- **2026-07-09 · [E = TWO-PASS MODEL] · Spreading splits into a visible and an invisible pass (Bora's amendment — supersedes the single-pass lean).** **Visible pass:** the planner works in an **in-memory working view** (the cubelet aggregated over declared dims to displayable size); ALL rich manipulation mechanisms (proportional, even, profiles, driver-based, manual+re-spread, …) live here as **non-authoritative tooling** — the user ratifies the resulting summary assignments (their vocabulary design moves to **C**). **Invisible pass (committed):** ratified assignments → underlying leaves by **exactly one mechanism: proportional-over-unlocked-remainder against the current base**, rounding = largest-remainder with stable tie-break. · Why: bounds the interactive problem to displayable size; dissolves the client-authority tension (ratified output ≠ trusted computation); shrinks the committed semantics — and the TTR-P demand — to one deterministic construct. · Rejected: rich-vocabulary-as-committed-semantics (the original lean — more language surface for no authority gain); E-α authoritative client (P3/P4); E-β service (semantics as unreviewable service code); E-ζ no-spread floor.
- **2026-07-09 · [E-zero/null] · Zero base = ERROR; existing support only.** If the base under an edited summary cell sums to zero, the spread **refuses** (P3-explicit) — the planner opens a finer view or **seeds explicitly** (seed = a leaf-level copy operation, e.g. `BUD2028 := ACT2027`, a distinct C-level entry operation involving no spreading). The invisible pass distributes only over **existing leaf combinations**; creating combinations is a separate explicit act (no silent densification). · Rejected: seed-automatically + even fallback (miracle-shaped); even-fallback-only (flat distributions masquerading as decisions); dense-over-enterable-region (silent densification).
### A / product shape (compact catalogue in `05-`)

- **2026-07-09 · [A = the SPLIT VERDICT] · Analysis = Designer Extensions; entry = its own product; Excel add-in = future client.** (1) B-read's thin viewer + B-author ship as platform extensions on the `PL Q-4-a` surface — the Designer is the platform's single pane of glass for everyone who reads and builds; (2) the entry surface is a dedicated, deliberately simple standalone platform product (planner persona, round rhythm, journal sessions; Slavic name parked → J register); (3) **A-δ Excel add-in recorded as a future alternative client** of D's client-agnostic entry contracts (parked: post-v1 planner-adoption demand); (4) **LF-6: both surfaces `cz.tatrman`** — extensions ride the MIT extension surface (`PL D-2` pattern), the entry app is "operate" (`PL A-1`); no new MIT frontend; (5) config-vs-use line: **planners never author canon** — entry-form/working-view declarations are modeling-time canon, authored IDE/Designer-side (details → C's Q-7). Resolves **LF-1 + LF-6**. · Why: D made frontends thin clients; B's decomposition made both analysis halves extension-shaped; personas differ hard on the entry side. · Rejected: β one-workbench (persona collision); pure γ (planning inside metadata chrome); pure α (BI shell duplicates the Designer).

- **2026-07-09 · [E-placement = γ+δ, scope cuts] ·** The one committed construct is a **TTR-P language capability** (MIT per `PL A-1`, engine-executable, reviewable text — the demand spec to the TTR-P effort shrinks to this single construct + error semantics); the platform ships the **deterministic fragment generator** (δ ergonomics) so surfaces never string-build TTR-P. **Additive measures only in v1** (non-additive entry → parking lot). Spread/driver defaults declared in **TTR-M md** (E-i), overridable per entry, recorded in the entry record. **Consequence: “drivers” (e.g. ACT2027) are visible-pass tooling + seed sources — the committed pass has no driver concept (its base IS the current slice).** · Resolves EQ-2 (over-constrained = error always) and EQ-5 (base = current state at commit + rebase-on-commit); EQ-4 (echo latency) moves to C.

---

## 8. Parking lot

| Item | Why parked | Revisit when |
|---|---|---|
| NL/TTR-B surfaces on the Platform | Inherited (`PL` parking lot); intelligence stays kantheon | kantheon-side agent design matures |
| Scheduled/published/bursted reports (report-renderer territory) | v1 lean = interactive-first; report-renderer stays kantheon (`PL D-6`) | B converges; first "email me the budget pack" demand |
| Surface naming (Slavic register, `PL J`) | Name after the products have shapes | A + B + C converged |
| Mobile / tablet entry | No demand named | first field-planning use case |
| PL-P6.S2.T6 smoke-drill amendment: name an Iris **and Sysifos** e2e query explicitly in the adoption acceptance | Belongs to the PL plan, not this effort; offered to Bora 2026-07-09, pending | at PL-P6 execution (or next PL plan-amendment batch) |
| Scenario-as-data-branch (Iceberg/Nessie-style branch per what-if, merge on approve) | D-iv weird option; alien on PG/MSSQL v1 | first real scenario-planning demand |
| Non-additive measure entry (rates, prices, averages — don't spread proportionally) | E scope cut: additive-only v1 | first rates/prices entry demand |

---

## 9. Rolling open questions

- **Q-1 ·** Does the BI surface need *saved/shared artifacts* (dashboards, published views) in v1, or is interactive exploration + TTR-P authoring enough? Shapes B heavily (embedding an OSS BI buys dashboards "for free"; building native makes them scope).
- ~~**Q-2 ·** Entry concurrency model~~ **RESOLVED 2026-07-09 v1 → cell-LWW over the journal + advisory slice claims + rebase-on-commit (D-iv).**
- **Q-3 ·** Entry workflow (submit / approve / lock-version) — in v1 scope or a later arc? (C.)
- **Q-4 ·** Does TTR-M need new vocabulary for *entry forms* — **narrowed 2026-07-09**: spread/driver defaults + version protection ARE md metadata (decided, E-i/D-v); still open: the *working-view/form* declaration (which dims summarize, which levels display — C's half) as canon vs platform config.
- **Q-5 ·** What exactly is a "cubelet" contractually — an MD-model object that already exists in TTR-M `md`, or a new derived concept (cube × slice × level-set)? Pin early; C and the two-pass model's "working view over declared dims" key on it.
- ~~**Q-6 ·** Where do *drivers* for spreads come from~~ **RESOLVED 2026-07-09 → drivers are visible-pass tooling + seed sources (md-declared defaults, ad-hoc legal, recorded in the entry record); the committed pass has no driver — its base is the current slice.**
- **Q-7 ·** The working-view definition: who declares the summarize-over dimensions (modeler in md? planner ad hoc? both) — and is a saved working view a canon document? (C; Q-4's sibling.)

---

## 10. Session index

| Date | Gear | What happened | Artifacts |
|---|---|---|---|
| 2026-07-09 | Framing | Effort opened from the "where are Iris and Sysifos" gap. Grounding: PL design set re-read; kantheon sysifos-design/iris-design read; Sysifos = no-intelligence-but-Midas-coupled established (FI-3). FI-1..FI-5 fixed; workstreams A–F cut; LF-1..LF-6 named; P2–P4 candidate principles proposed; hero scenario pinned (FY2028 OPEX budget). | this doc, `01-design-space-map.md` |
| 2026-07-09 | Divergence (B) | OSS-BI landscape researched (semantic layers + components verified vs official sources; Tier-1 classics flagged ⚠ unverified-2026). B-α..ε catalogued + rendered vs hero; **policy-honesty problem named (external BI speaks SQL; the platform's governed read is a plan door — reconciliations R0–R3)**; **decomposition finding: B-author (TTR-P authoring loop) is platform-native in every branch, LF-2 really decides B-read**; leans recorded (δ-composite; Vega-Lite-as-library instead of envelope re-own; ε floor-marked). BQ-1..6 opened. B → 🟡 | `02-bi-surface-options.md` |
| 2026-07-09 | Divergence (D+E, joint) | **Key observation reframes D: program materialization is the platform's one existing data-plane write mechanism — every branch is a way to ride/extend/bypass it.** D-α..δ catalogued + cross-cutting sub-forks D-i..v (journal write semantics, RLS-on-write via Argos symmetry, the **entry record** as compile-record sibling, concurrency incl. weird scenario-as-data-branch, version protection in md metadata). E: method vocabulary pinned as candidate set with invariants (sum-preservation, locks-or-error, declared rounding); placements α..ζ. **The load-bearing pairing: E-γ spread-in-TTR-P inside D-β-ii's standing canon entry-apply program** — leans recorded accordingly (D: γ-front+β-ii-back; E: γ+δ ergonomics, TTR-M-declared defaults). DQ-1..5, EQ-1..5 opened; TTR-P demand-spec = cross-effort item. D, E → 🟡 | `03-write-path-options.md`, `04-spreading-options.md` |
| 2026-07-09 | Convergence (D+E closed) | **Bora ratified the D+E leans with the TWO-PASS AMENDMENT** (visible in-memory working view with rich non-authoritative tooling → C; invisible committed pass = single proportional-over-unlocked-remainder construct) + **zero base = ERROR** (seed = explicit C-level copy op) + **existing support only** + additive-only v1 + rebase-on-commit. D 🟢 (γ+β-ii composite, entry record, D-i/ii/iv/v) · E 🟢 (γ+δ placement, one-construct TTR-P demand spec, md-declared defaults). Q-2/Q-6 resolved; Q-4 narrowed; Q-7 opened; EQ-2/EQ-5 discharged; EQ-4 → C. Scenario-branches + non-additive entry → parking lot. **Board: B 🟡 · D 🟢 · E 🟢 · A/C/F ⚪.** | decision log, `03`/`04` resolution sections |
| 2026-07-09 | Convergence (A closed) | **A discussed briefly in chat and converged: the SPLIT VERDICT** (analysis = Designer Extensions · entry = own product · Excel add-in = future client · both `cz.tatrman` · planners never author canon). LF-1 + LF-6 resolved; compact catalogue written post-hoc. **Board: B 🟡 · A/D/E 🟢 · C/F ⚪.** | `05-product-shape-options.md`, decision log |
