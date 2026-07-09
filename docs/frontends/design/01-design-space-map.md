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

**Open:** BQ-1..6 in the options doc (R1 feasibility · Perun→engine-RLS/Cube-policy generation · md→OSI/Cube lossiness ledger · views-as-canon · v1 dashboard scope · ⚠ Tier-1 re-verification).

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

**Open:** Q-2 concurrency semantics; Q-5 what a cubelet is contractually; how ACT (actuals) slices are protected from entry (read-only regions).

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

**Open:** does Collite have frontend capacity/preference that should be a grounding input (team knows Vue from Iris/Sysifos)?
