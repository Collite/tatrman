# Platform Frontends — Design-Space Map

> The option tree for the **Platform Frontends** effort. One section per workstream: **Question → Branches → Cross-links → Open**. Deliberately divergent: branches are added, never silently removed. Decisions live in the [control room](./00-control-room.md) §7, never here.

---

## §A · Product shape & placement (LF-1, LF-6)

**Question:** How many surfaces does the platform ship, where do they live relative to the Designer, and on which side of the license line?

**Branches (topology):**
- **A-α · Two standalone products.** A BI app and an entry app, separate deployables, shared platform plumbing (auth, query-door client, MD metadata reads).
- **A-β · One analyst workbench.** A single app where *view*, *author TTR-P*, and *enter data* are panels/modes — the "Excel of the platform".
- **A-γ · Designer Extensions.** No new app: BI panels and entry panels ship as platform extensions on the `PL Q-4-a` surface; the Designer becomes the platform's single pane of glass.
- **A-δ · Weird: no UI at all — the platform ships an Excel add-in.** Entry and analysis happen where planners already live (Excel/Sheets); the platform contributes the write path, spread engine, and a connection add-in. (Prior art: TM1 Perspectives/PAfE, Anaplan Excel add-in — in that market Excel add-ins are not a joke.)

**Branches (license/placement, sub-fork A-ii):** MIT shell + platform panels (Designer pattern) · wholly `cz.tatrman` platform products · MIT everything with platform backend (Designer-frontend precedent).

**Cross-links:** γ constrains B and C to the Designer's React stack (collides with Iris-reuse branches of B/F); δ reframes C entirely; A-ii leans on `PL A-1` ("view" was MIT; entry is arguably "operate").

**Open:** persona split — is the *planner* (entry) even the same person as the *analyst* (BI)? Hero says no (planner enters; analyst analyzes).

**RESOLVED 2026-07-09 → the SPLIT VERDICT** (analysis = Designer Extensions · entry = own standalone platform product · A-δ Excel add-in = recorded future alternative client · A-ii: both `cz.tatrman`, no new MIT frontend · planners never author canon). LF-1 + LF-6 resolved → [`05-product-shape-options.md`](./05-product-shape-options.md) + control room §7.

---

## §B · The BI/analysis surface (LF-2)

**Question:** Take an existing BI, build native, or re-own Iris's pipeline — and what does "interactively generate TTR-P" mean as a product?

**Branches:**
- **B-α · Embed/integrate an OSS BI** (Superset, Metabase, Lightdash, Redash, Evidence, Rill, …). The platform provides a SQL-ish surface (workers already speak SQL/Polars) or a semantic-layer adapter; the BI tool provides exploration, dashboards, sharing.
  - Sub-forks: α-1 embed wholesale (iframe/whitelabel) · α-2 integrate (their viz, our catalog/auth) · α-3 just document "point your BI at the results DB" (BI is out of scope — the platform is a *source*).
- **B-β · Build native on the query door.** A platform-owned exploration surface: pivot/chart over Theseus, TTR-M-aware (knows cubes, hierarchies, calc maps — no OSS BI understands MD models natively), column-lineage-aware drill.
- **B-γ · Re-own the Iris pipeline.** Move/fork `envelope/v1` + the Vue render stack (tables, Vega-Lite charts, drilldowns) platform-side (LF-5 must be paid); Iris keeps consuming the kantheon copy or the re-owned one per `PL D-3`.
- **B-δ · Headless-BI hybrid.** The platform ships a **semantic layer** (TTR-M md → metrics/dimensions contract, cube.dev-shaped) and treats *all* BI frontends — OSS or commercial — as clients; ships one thin bundled viewer.
- **B-ε · Weird: the BI surface IS TTR-P.** No point-and-click exploration; analysts write/generate TTR-P in a notebook-style surface (author → run via query door → render results → save program). Exploration = language, rendered.

**The TTR-P-authoring half (all branches must answer):** interactive work = author/generate TTR-P, run through the query door, see results, iterate, *graduate to a deployed program*. Which branch carries this naturally? (β and ε natively; α relegates it to the Designer; δ makes it a second client.)

**Cross-links:** α/δ interact with `PL I` (OpenMetadata anchor — BI tools read catalogs too); β/γ collide with A-γ stack choice; Q-1 (dashboards in v1?) gates how much α buys; `PL FI-3` forbids NL-to-query here (that's Iris).

**Captured 2026-07-09 → [`02-bi-surface-options.md`](./02-bi-surface-options.md).** Additions from divergence: **the R sub-fork** (how external SQL-speaking tools reconcile with the plan-door read path: R0 none · R1 SQL façade on the door · R2 published marts + engine-native RLS from Perun · R3 headless semantic layer as policy carrier — couples with §D); **the decomposition** (B-author is native everywhere; LF-2 decides B-read); long list researched (Cube hierarchies, OSI spec 2026, Perspective tree-pivots/Arrow/editable-grid, Shaper, MetricFlow now Apache-2.0; Metabase embedding license-shaped out).

**RESOLVED 2026-07-10 → the δ-COMPOSITE** (Bora ratified after the BQ-6 verification pass, `02` §6): **B-author = native Designer extension** (settled by architecture) · **B-read =** semantic projection (md→Cube/OSI, `PL I-1` projection-out) + **R3** policy carrier (Cube-shaped layer; policies from Perun — BQ-2) + α-3 bring-your-BI (Superset reference pairing — verified open-core embedding; PowerBI) + thin native viewer as Designer extension (Perspective + Vega-Lite; **lineage-aware drill = the differentiator**). R2 rejected (second enforcement surface); R1 parked (BQ-1). BQ-4 = ttrl split (shared views canon / personal in prefs); BQ-5/Q-1 = interactive + saved views v1, packs parked. **LF-2 resolved.** Remaining: BQ-2, BQ-3 → cross-effort/planning.

---

## §C · The entry/planning surface — UX (with Q-2, Q-3, Q-4)

**Question:** How does a planner enter at summary level, spread, override, lock, re-spread, and commit — correctly and fast?

**Branches (entry idiom):**
- **C-α · Cube grid (pivot-with-writeback).** The planner works in an ad-hoc pivot of the cubelet; any aggregate cell is enterable; spread happens on write-to-aggregate. (TM1/Jedox idiom.)
- **C-β · Declared entry forms.** Modelers declare *forms* (cubelet slice + enterable levels + spread defaults + validation) as canon; planners get a guided, validated form. (Anaplan/board-pack idiom; pairs with Q-4's "forms as TTR-M vocabulary".)
- **C-γ · Both: forms for the round, grid for power users.** Forms are the governed path; the raw grid exists behind a permission.
- **C-δ · Weird: no grid — entry as a *document*.** The planner writes/uploads a small declarative entry file ("Sales FY 120M, spread by ACT2027, lock marketing March +2M"), the platform previews and applies. Entry-as-code; reviewable like everything else.

**Constant DNA regardless of branch (from Sysifos, generalized — candidate P3):** preview→commit; paste-friendly bulk entry; per-cell validation; Draft+SSE for long ops; explanation-on-demand for derived cells (P4).

**Inherited from E's convergence (2026-07-09) — C's scope grew:** C now owns the **visible-pass manipulation vocabulary** (proportional/even/profile/driver-based/manual+re-spread as in-memory working-view tooling, non-authoritative, freely extensible); the **explicit seed operation** (leaf-level slice copy, e.g. `BUD2028 := ACT2027` — required, since zero base = error); the **working-view definition question** (Q-7: who declares summarize-over dims; saved views as canon?); and **EQ-4's latency/echo budget**. FINOS Perspective (WASM pivot engine, editable grid, Arrow) = the natural in-memory host candidate.

**Cross-links:** every branch leans on D (write path) and E (spread semantics); versions/workflow (Q-3) shape all; A-δ (Excel add-in) is C-α wearing Excel.

**Grid core captured 2026-07-10 → [`06-entry-grid-options.md`](./06-entry-grid-options.md)** (+ working prototype `prototypes/c-entry-grid-v0.html`). Additions from divergence: **the three grains** (storage/load/display; a view-leaf "overwrite" is still a committed-pass spread unless load grain == storage grain; zero-base pre-flight CQ-5); **the lock fork C-T2** (CL-α freeze-contents — hero-refuted floor · CL-β pin-sum + compensation cascade, TM1-holds shape, lean · CL-γ global solver/IPF — escalation path for crossing locks, CQ-1); **edit-propagation fork C-T3** (immediate vs deferred compensation; auto-lock-on-edit CQ-2); **vocabulary C-T4** (shorthand codes, block ops, curves, first-class seed gesture, ephemeral formulas CQ-6); **host fork C-T5** (Perspective / custom shell / commercial grid / spreadsheet engine — solver+provenance layer is custom regardless); **non-additive tension C-T6 = CQ-3 ⚑** (GI-7 vs E's additive-only cut). The entry-idiom fork above (C-α..δ) is *narrowed* by GI-4 toward declared-form shapes (β/γ), not yet decided. **GRID CORE CONVERGED 2026-07-10** (two passes): CQ-2 → user toggle · CQ-3/Q-8 → non-additive out of v1 · **lock-free committed pass** (locks/formulas FE-only ephemeral; amends E wording + D-iii) · GI-9 loaded-scope-not-visible · **C-T2 = CL-β** (CQ-1 error-only, IPF parked) · **C-T3 = immediate per-gesture compensation** · **C-T4 = v1 vocabulary** (formulas v1.5) · **C-T5 = β custom model + thin grid** (framework → F). Remaining C threads: commit/preview UX, Q-3 workflow, form-declaration vocabulary (+ CQ-4..7).

**Thread 2 (commit/preview) captured 2026-07-10 → [`07-commit-preview-options.md`](./07-commit-preview-options.md)** (+ prototype v0.6 with simulated storage grain). Threads C-T8..T14: preview depth (α verdict-only · **β list + on-demand expansion, lean** · γ full diff · δ optimistic-commit floor) · ~~drift/rebase UX (C-T9)~~ **superseded** · session drafts (**β journal-draft autosave of values only, attached to the reservation, lean**; δ op-log persistence illegal — would store locks) · commit granularity (α whole-batch + note field; selective → parking lot) · validation topology (3 layers; "check-in may only newly fail on permission/version changes") · post-check-in reload/history. **Finding: journal = two row kinds (seed | assignment), seeds-first — refinement proposal to D's contract.** **DECIDED 2026-07-10: C-T14 CHECK-OUT/CHECK-IN reservations (amends D-iv)** — exclusive slice check-out, check-in requires reservation, commit ≠ release, drift → internal invariant, claims → mandatory reservations. **Batch convergence same day:** T8 β · T10 β journal-drafts (values-only v1) · T11 α · explicit edit toggle · CQ-14 = admin unfreeze v1. **CQ-15 RESOLVED** (`07` §10a ratified: loaded-scope product-slice reservations · member-set overlap at storage grain · snapshot-at-acquisition · mid-round model edits = disciplined governance → Q-3). **Thread 2 🟢.** Residual minor opens: CQ-8, 10, 12, 13.

**Thread 3 (form vocabulary) captured + CONVERGED 2026-07-10 → [`08-form-vocabulary-options.md`](./08-form-vocabulary-options.md)** (+ hero form in prototype v0.8). The load slice's fourfold weight named (view extent = reservation scope = ripple boundary = draft home). **DECIDED: C-T15 = α merged into the `ttrl` layout-document family** ("this is exactly it" — forms = the family's first authored-canon citizens; demand spec split: layout/entry → ttrl schema via GQ-1 · model metadata → TTR-M package; pivot state = ttrl content in prefs, CQ-4 shaped) · **C-T16 = α + parameterized version** · T17 validation catalogue + T18 lifecycle approved. **Q-4, Q-5, Q-7 RESOLVED.** Open: CQ-16 multi-measure · CQ-17 curve library home · CQ-18 enterable regions · CQ-19 `default_hierarchy` check · CQ-20 ttrl schema reconciliation (GQ-1). Rejected: form-as-TTR-P-program, platform config (floor), saved-plan+flags, baked-in version.

**Q-3 RESOLVED 2026-07-10 → α+γ "the minimal round" ([`09-workflow-options.md`](./09-workflow-options.md))**: round = the version lifecycle (D-v) · read-only round dashboard over reservations/entry-records/coverage · one done flag per form instance · note field (CQ-12 ✓) · drafts private (CQ-10 ✓) · roles = admin + git convention. Parked: β submit/approve chains. Rejected: α-pure (one bit fixes it), δ workflow-as-data (control-plane in data plane). **WORKSTREAM C FULLY CONVERGED 🟢.**

**Open:** CQ-4..13 in the options docs; Q-5 what a cubelet is contractually (sharpened in `06` §1); how ACT (actuals) slices are protected from entry (read-only regions — D-v md metadata, render as locked-gray); Q-3 workflow; form-declaration vocabulary (thread 3).

---

## §D · The data-write path (LF-3)

**Question:** Where do entered numbers land? A budget cell is data-plane, human-originated, non-canon, non-run — the platform has no door for that today.

**Branches:**
- **D-α · A third door: the entry door.** A new platform service (Slavic name pending) accepting entry proposals: validate (Argos, RLS-on-write) → spread (E) → write to the target engine → emit audit/run-ish events Veles ingests. Symmetric with the two existing doors.
- **D-β · Entry-as-program.** An entry commit *is* a (system-synthesized) TTR-P program run through Radegast: inputs = entered values + driver slices; output = writes to the version slice. Buys: reuse of the whole run/audit/lineage spine; entries appear in lineage like any transform. Costs: latency/weight for interactive entry; envelope machinery for a cell edit.
- **D-γ · Staged entries + apply step.** The surface writes rows to an *entry staging store* cheaply and synchronously; a platform apply step (scheduled or on-commit, possibly a standing TTR-P program) folds staging into the cube slice. CQRS-flavored; splits "fast capture" from "governed apply".
- **D-δ · Weird: direct worker write.** The BFF writes straight to the engine (Arges) under RLS. Cheapest possible; bypasses the hall's validation/audit spine — catalogued to mark the floor.

**Sub-forks:** append-only + reverse-and-replace (Sysifos/Midas discipline) vs versioned overwrite per (cell, version) · audit event shape (ride `PL F-6-β` spine vs new event kind) · does the entry write get a compile-record-like provenance ("entered by, spread by method M, driver D")?

**Cross-links:** β and γ make E's "spread as TTR-P" nearly free; α needs new contracts (amendment proposals per FI-4); `PL H` — RLS on *write* is new territory (Argos does read-plan injection today); Q-2 concurrency lands here.

**Captured 2026-07-09 → [`03-write-path-options.md`](./03-write-path-options.md).** Additions: β split into β-i ephemeral / **β-ii standing canon entry-apply program with table-valued typed input**; cross-cutting sub-forks D-i..v (journal semantics, Argos-symmetric write RLS, **entry record**, concurrency incl. scenario-as-data-branch weird, version protection in md); the named `PL F` gap ("run deployed program NOW with input" — no door has the verb). Open: DQ-1..5 in the options doc.

**RESOLVED 2026-07-09 → D = γ-front + β-ii-back composite** (journal · standing canon entry-apply program · entry record; D-i/ii/iv/v pinned; DQ-1/2 = PL amendment proposals). See control room §7.

---

## §E · Spreading & allocation semantics (LF-4)

**Question:** Where does spread/split logic live, and what is its vocabulary?

**Branches (placement):**
- **E-α · Client-side.** The grid computes spreads; the server stores results. Fast feel; violates P3/P4 unless the server can re-derive (two implementations or none authoritative).
- **E-β · A platform allocation service.** Spread methods as a server capability the entry door calls; one authority; frontend previews via the same endpoint.
- **E-γ · TTR-P language capability.** Spreading = a deterministic transform expressible in TTR-P (spread/allocate verbs or stdlib over md cubes): reviewable text, compilable, engine-executable, MIT (`PL A-1` — it's *compile*, the platform's edge stays data/execution). Interactive preview = the same program via the query door.
- **E-δ · Hybrid γ-with-β-ergonomics.** The language owns the semantics (γ); the platform wraps common methods as a service/API so the frontend never string-builds TTR-P; both routes produce the same plan.

**Branches (vocabulary — the method set, any placement):** proportional-to-driver · even/equal · profile/seasonal curve · driver-based formula (rate × volume) · manual leaf with re-spread of remainder · locks/holds honored on re-spread · rounding with reconciliation (sum of leaves == entered total, always).

**Cross-links:** γ/δ interact with the TTR-P design corpus (does the language have the md-cube algebra needed? → TTR-P control room) and the amendment-sweep discipline; MD_CALC_CATALOG may already carry calc-map machinery to build on; D-β/D-γ pair naturally with γ.

**Open:** Q-6 driver declaration; is "spread" also a *modeling-time* concept (default spread method per cubelet declared in TTR-M — Q-4) or purely entry-time; goal-seek / top-down + bottom-up reconciliation (park for v2?).

**Captured 2026-07-09 → [`04-spreading-options.md`](./04-spreading-options.md).** Additions: the method vocabulary as a pinned candidate set with invariants (sum-preservation, locks-or-error, declared rounding rule per method); E-α demoted to echo-at-most; **E-δ** = γ + deterministic fragment generator; **E-ζ** floor (leaf-entry-only MVP); E-i defaults-in-TTR-M sub-fork (lean yes); prior-art note (TM1/Anaplan holds & spread menus; none make the spread inspectable text — γ's differentiator). Open: EQ-1..5 in the options doc.

**RESOLVED 2026-07-09 → E = the TWO-PASS MODEL** (visible in-memory working view with rich non-authoritative tooling → C; committed pass = single proportional-over-unlocked-remainder TTR-P construct; zero base = error, seed = explicit copy op; existing support only; additive-only v1; rebase-on-commit; γ+δ placement; md-declared defaults). See control room §7.

---

## §F · Reuse, stack & contracts (LF-5)

**Question:** What is *legally and practically* reusable from the existing estate, and which contracts must the platform re-own?

**Branches (per asset):**
- **`envelope/v1` + `envelope-ts` (kantheon-owned):** F-α re-own platform-side (move the proto per `PL D-3`; kantheon consumes downstream — precedent: TR-3 plan protos) · F-β fork a platform `render/v1` (drift risk, two block models) · F-γ don't reuse — the BI surface has its own result model (query door already returns typed results).
- **Iris render components (Vue + PrimeVue + Vega-Lite):** reuse only if the new surfaces are Vue (collides with A-γ/Designer React); extract to a shared kantheon→platform-consumable lib is **illegal direction** (`PL P2`) — would need to live platform-side or tatrman-side.
- **Sysifos:** reuse = *design DNA only* (write model, validation topology, grid UX); code reuse ~none (Midas-typed throughout).
- **report-renderer:** stays kantheon (`PL D-6`); if scheduled report packs become v1 scope (Q-1/parking), the platform needs its own or a re-place proposal.
- **Designer stack (React 19, MIT, extension surface):** reuse = build the new surfaces as extensions (A-γ) or as sibling React apps sharing components tatrman-side.

**Stack sub-fork:** React (Designer kinship, tatrman-side component sharing) vs Vue (Iris/Sysifos kinship, kantheon-side muscle memory) vs per-surface split (BI React in Designer, entry Vue standalone — coherence cost).

**Cross-links:** everything here is downstream of A (topology) and B (make-or-take); `PL J` naming register applies when services appear (D-α's entry door, E-β's allocation service).

**Open:** ~~does Collite have frontend capacity/preference that should be a grounding input?~~ **Answered 2026-07-10 (FQ-1): "React is fine."**

**Captured + CONVERGED 2026-07-10 → [`10-stack-reuse-options.md`](./10-stack-reuse-options.md).** **F-1 = α React everywhere platform-side + γ pure-TS-core discipline** (C-T5's solver already framework-free; framework governs shells only) · **F-2 = α** one kit family split on the `PL A-1` MIT line (`org.tatrman` contract clients · `cz.tatrman` entry plane + grid solver; core framework-free) · **F-3 = F-γ** — the envelope question **dissolved** (typed door results, grid model, Vega-Lite-as-library) · **F-4** Iris/Sysifos design-DNA-only + OTel pattern · report-renderer per `PL D-6` standing. **LF-5 RESOLVED by dissolution.** Rider: **Iris+Sysifos→React** = kantheon-side initiative proposal (parked, handoff); the framework-free kit is Vue-consumable today — logic-level "one package" precedes the migration. FQ-2/3 (design tokens, package naming) → consolidation sweep.
