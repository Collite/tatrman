# Tatrman Platform Frontends — Design (v1)

> **The technical result of the Platform Frontends design effort** (2026-07-09/10, all workstreams A–F converged, sweep ratified). **Audience: the `/planning` session** — this document carries every decision, contract-shaped constraint, scope boundary, and deferred item planning needs. It references rather than restates: ground truth = [`design/00-control-room.md`](design/00-control-room.md) §7 (append-only decision log); depth = option docs [`design/02`](design/02-bi-surface-options.md)–[`design/10`](design/10-stack-reuse-options.md); the human-readable manual = [`detailed-design.md`](detailed-design.md).
>
> **Normative companion artifact:** [`design/prototypes/c-entry-grid-v0.html`](design/prototypes/c-entry-grid-v0.html) (v0.8, self-contained, zero dependencies — open in any browser). It renders the decided semantics end-to-end: the working-view grid (spread, locks, compensation cascade, block ops, seed), the check-out/check-in reservation flow, the check-in preview with storage-grain expansion, the entry record, and the annotated form declaration. Its model layer is verified by 16 (grid) + 15 (backend-sim) scripted assertions. Where prose and prototype disagree, the decision log wins; the prototype is illustration, not contract.

> **⚠ TIER ASSIGNMENT (2026-07-10, post-close — ecosystem RO-23):** the **analysis plane** (§4 + the open kit) = **Tatrman Server** (open, Apache-2.0; sequenced as post-v1 Server arcs — the projection is STRAT-8's arc); the **entry plane** (`tatrman-entry` + §6's entry contracts) = **Tatrman Platform** (commercial; wakes on the first planning workload; rides the operate tier's program door). Reads on this document: "MIT" → Apache-2.0 (STRAT-3); the `PL A-1` license line → "interoperate vs administrate" (STRAT-2); the one-way arrow → `tatrman → tatrman-server → {tatrman-platform, kantheon}` (RO-6); BQ-2's "policies from Perun bundles" gains an open-tier leg — the Server generates from the open validator policy store (RO-7). Details: control room §7 "Ecosystem tier assignment" + platform control room RO-23.

---

## 1. What is being built

Two product surfaces for the Tatrman Platform, filling the gap the platform split left (all product frontends stayed kantheon-side; the Designer is a metadata/ops surface):

1. **The analysis surface** — *not a new app*: **Designer extensions** (`PL Q-4-a` extension surface, React 19) — the **B-author panel** (interactive TTR-P authoring: author → validate → preview via the query door → graduate to a deployed program) and the **thin native viewer** (hierarchy-true pivot + charts + **lineage-aware drill**: cell → column lineage → program → run → entry commit) — plus one new backend organ, the **semantic layer service** (R3), which projects TTR-M `md` into a Cube-shaped/OSI semantic model and carries policy for **external BI clients** (Superset as the documented reference pairing; PowerBI et al.).
2. **The entry product `tatrman-entry`** — a standalone `cz.tatrman` planning/budgeting application over MD-model cubelets: summary-level entry with spread-down, locks, seeding, check-out/check-in, and the minimal round. Its UX is fully specified by workstream C and rendered in the prototype.

Personas are split by A's verdict: **analysts** read/build in the Designer; **planners** use `tatrman-entry` and **never author canon**. An Excel add-in is a recorded *future alternative client* of the same entry contracts (parked).

## 2. Principles (ratified)

- **P1** — `PL P1–P3` + `PL B-4` verbatim (standalone ≠ demo; one-way arrow `tatrman → platform → kantheon`; no miracles; seam legality).
- **P2 · Deterministic surfaces.** No LLM/agent intelligence on platform frontends; NL analytics stays Iris/kantheon.
- **P3 · Propose, don't write.** Every entry surface assembles a proposal, previews server-derived effects, commits explicitly; the frontend is never the authority.
- **P4 · Spreads are explainable.** Every derived number can show how it was derived — in-session via op-chains, after commit via the entry record.
- **P5 · Discipline over machinery.** Prefer visibility + convention (canon review, holder-in-the-header, round dashboard) over enforcement subsystems; add machinery only when a real round demands it.

## 3. The entry pipeline — decisions

### 3.1 The three grains and the two passes (E, C-T1)

- **storage grain** (cubelet leaves, engine-side) < **load grain** (the form's view leaves, in memory) < **display grain** (pivot/collapse — *presentation only; appears in no contract*).
- **Visible pass** = the in-memory working view at load grain: ALL rich manipulation (spread, locks, block ops, curves, ephemeral formulas) is **non-authoritative client tooling**. The FE operates on **loaded** scope, never visible scope (collapse hides, never unloads).
- **Committed pass** = exactly **one lock-free mechanism**: *per ratified assignment, scale the existing storage base beneath it proportionally to the assigned value; zero base = ERROR; largest-remainder rounding with stable tie-break; existing support only (no densification)*. Load-grain cells partition storage leaves ⇒ each assignment expands within a disjoint region. **This construct is a TTR-P language capability (MIT)** + a platform-shipped **deterministic fragment generator** (emits TTR-P **text**). Additive measures only in v1.
- **Locks and formulas are frontend-only, ephemeral, stored nowhere.** The backend spread is lock-free by construction. Entry record carries no locks field.

### 3.2 The write path (D, converged 2026-07-09, amended)

Surface → **entry journal** (beside the cube; draft/session/audit substrate) → **check-in = a run of the standing canon `<cube>-entry-apply` TTR-P program** with the journal batch as table-valued typed input → the version slice is its materialization → every check-in produces an **entry record** (compile-record sibling: principal, assignments, method params, batch leaf-set hash, run ref, free-text **note**). Rides the whole existing spine (Argos, Kyklop, quota, run store, events, lineage).

**Journal batch = two row kinds** (D contract refinement): **seed rows** (storage-grain slice copies, e.g. `BUD2028 := ACT2027`, fill-empties-only, applied **first**) and **assignment rows** (load-grain values, expanded by the committed construct). Deterministic order: seeds → assignments.

**RLS-on-write** = Argos-symmetric write predicates (PL H amendment). **Version protection** (ACT read-only; BUD open→locked) = TTR-M md version-dimension metadata, enforced at the write check.

### 3.3 Concurrency = CHECK-OUT / CHECK-IN reservations (C-T14, amends D-iv)

"Merging data cubes makes no sense" — a pessimistic **reservation** model (SVN's lock-modify-unlock for non-mergeable content) replaces the optimistic composite. cell-LWW is moot; advisory claims upgraded to mandatory reservations; the old drift/rebase-on-commit check survives **only as a server-side internal invariant** (firing = platform bug). Preview→commit congruence holds by construction.

- **Check-out** = exclusive write reservation on a slice. **Scope = one product slice = the form's load slice as opened** (version pinned + form filters + optional subtree narrowing that equally narrows the load — the *ripple problem* makes sub-load scopes unsound in v1). **Overlap = per-dimension resolved-member-set intersection at storage grain** (never hierarchy-node comparison). **Snapshot at acquisition** (mid-round model edits never resize a held reservation).
- **Acquisition = explicit "edit" toggle:** open is read-only; "I want to start editing this" acquires; a taken slice's header shows **who** holds it.
- **Check-in** = D's commit, **requiring the held reservation**; commit ≠ release (check-in-and-keep-working and check-in-and-release are both first-class); release-without-check-in abandons cleanly.
- **Stale reservations v1 = manual admin unfreeze** (header shows whom to call); TTL/heartbeat/auto-expiry parked.
- **Terminology (binding for all contracts/UX copy):** *reservation* (this) ≠ FE *value pin/lock* (grid tool, ephemeral) ≠ *version lock* (D-v md metadata). Never call the reservation a "lock".
- The **reservation registry** lives beside the journal, enforced at the journal-write/check-in API — a named **PL contract amendment** (joins DQ-1/2).

### 3.4 The grid (C threads 1–2; prototype = normative illustration)

- **Lock semantics = CL-β pin-sum + compensation cascade:** a pin holds a cell's aggregate; contents reshuffle. Gestures are atomic: apply assignments (each spreads proportionally over unpinned leaves; satisfied inner-pin regions frozen) → reconcile violated pins smallest-region-first (user-touched cells excluded) → non-convergence/over-constraint = error + full rollback. Crossing pins that oscillate = **error-only v1** (IPF parked). Uniform scaling preserves proportionality at every level; zeros stay zero.
- **Immediate per-gesture compensation**; batch ops = one gesture; per-gesture undo over the op log.
- **Vocabulary v1:** typed entry with shorthand (`120M`/`450k` absolute · `+2M`/`-500` delta · `±x%`) · summary entry (proportional spread over unpinned view leaves) · block select + scale (pinned cells skipped-with-count; direct edit of pinned = error) · curve/pattern apply (per-row block total preserved) · **seed as a first-class gesture** (reachable from the zero-base error). Ephemeral formulas = v1.5 (scope question CQ-6 open). Auto-pin-on-edit = **user toggle**.
- **Host:** custom pure-TS model layer (`grid-core`) + thin virtualized React grid shell; no heavyweight grid dependency; FINOS Perspective = recorded at-scale fallback (>~10⁵ loaded cells).
- **Zero-base preflight:** the load payload carries a **base-presence bitmap** (one boolean per load-grain cell); no-base cells are marked at first glance; the preview names missing seeds authoritatively.
- **Check-in preview (T8 β):** verdict chips as the gate (reservation held ✓ · version OPEN ✓ · write-RLS ✓ · storage zero-base ✓) + batch list + per-assignment on-demand **storage-grain expansion**.
- **Session drafts (T10 β):** journal-draft autosave of **values only** (assignments + seeds), attached to the reservation, private to the holder; mechanics (op log/pins/formulas) die with the session (persistence parked). **Whole-batch check-ins (T11 α)**; selective commit parked.
- **Validation topology:** 3 layers (FE instant echoes ← preview dry-run authoritative ← check-in final). **Contract: nothing may newly fail at check-in except permission/version changes** (drift is impossible under the reservation).
- **Post-check-in:** view reloads exactly; FE pins re-pin at current values; op log clears; provenance hands off from FE op-chain to the entry record; others' changes outside scope marked quietly; session history panel.
- **Load guard (CQ-7):** form-author-time validation + load-time check; default cap ~10⁵ cells (exact number = planning).

### 3.5 Forms & views = `ttrl`-family documents (C thread 3)

The form is **not a new document kind** — it is a **`ttrl`-family layout document** ("how this thing is presented and interacted with"), the family's first **authored canon** citizen (vs generated view state — two lifecycles, one schema; the GQ-1 coordination owns the schema, never forked). Modelers author forms (IDE / Designer edit mode, git-reviewed); **planners never author canon**; planners' pivot state & personal saved views = the same ttrl shapes in the **platform prefs store**, never the repo (CQ-4).

Form fields (decision-traced inventory, `design/08` §0): **binding** (cube) · **version = declared parameter role** (picked at check-out; form instance = form × version) · **grain** (per dim: leaf / level / aggregate away / pin — defines load grain **= working-view extent = reservation scope = ripple boundary = draft home**) · **hierarchies** (≤1 per dim) · **layout** (initial only) · **entry** (enterable measures — additive, single-measure v1; legal **seed_sources**; defaults from model) · **guards** (max_loaded_cells) · **narrowing** (check-out subtree dims). **Minimal mandatory core = binding + grain**; the rest md-defaulted (needs `default_hierarchy` on dimensions).

Author-time validation catalogue (review-time errors; cross-document, LSP/compiler turf): grain ≥ storage grain · declared hierarchy exists, single rollup path · enterable measures exist and are additive (v1) · seed sources resolve · guard ≤ platform cap · narrowing ⊆ grain dims.

**Saved analysis views (BQ-4)** get the identical treatment: shared/published = authored canon ttrl documents; personal = ttrl-in-prefs.

### 3.6 The minimal round (Q-3)

**Round = the version lifecycle** (D-v OPEN→LOCKED; both ends are disciplined md canon changes gated by git permissions — "not everyone, everytime" is convention, not code; also covers mid-round dimension/form edits). New machinery is deliberately tiny: a **read-only round dashboard** (reservations = who holds what · entry records · commit coverage · done flags) + a **per-form-instance "done" flag** (planner-declared, pairs with check-in-and-release) + the entry-record **note field**. Drafts private. Roles (version open/lock, admin unfreeze) = platform admin + git convention. Submit/approve chains **parked** (revisit: first customer round demanding in-tool sign-off).

## 4. The analysis surface — decisions (B)

**B-read = the δ-composite:**
1. **Semantic projection:** TTR-M `md` → Cube model / OSI document, **deterministically generated** (a `PL I-1` "projection out"), with an explicit **lossiness ledger** (BQ-3; hierarchies, calc maps, version dims).
2. **R3 policy carrier:** a Cube-shaped **semantic layer service** between engines and all external BI; its row-level policies **generated from Perun bundles** (BQ-2 = named PL H amendment work); BI tools connect to it (Postgres-wire), never to engines. R2 (per-engine RLS provisioning) rejected — second enforcement surface; R1 (SQL façade on the door) parked with BQ-1.
3. **α-3 bring-your-BI:** orgs point their BI at the layer. **Superset = documented reference pairing** (verified 2026-07: embedded SDK + guest-token RLS in the Apache-2.0 open core); PowerBI via the same layer (feeds `PL IQ-1`).
4. **Thin native viewer** (Designer extension, Perspective + Vega-Lite): hierarchy-true drill with subtotals, calc-map-aware measures, and the differentiator — **lineage-aware drill** (cell → column lineage → program → run → entry commit).

**B-author** (Designer extension): author/generate TTR-P → validate (browser-worker LSP) → preview via the query door → save → graduate to a deployed program. Native in every considered branch; no external tool can host it.

**v1 scope:** interactive exploration + saved views (per §3.5); scheduled/emailed report packs parked (report-renderer stays kantheon, `PL D-6`).

## 5. Stack, packages & license line (F)

- **React 19 everywhere platform-side** (analysis = extensions by construction; `tatrman-entry` chose React — FQ-1 answered) with the **pure-TS-core discipline**: everything below the rendering shells is framework-free TypeScript (the grid model layer already is — it runs in Node for its tests).
- **One kit family, split on the `PL A-1` license line:** MIT `org.tatrman` — `@tatrman/door-client`, `@tatrman/md-client`, `@tatrman/ttrl`, `@tatrman/format`, `@tatrman/tokens` (data-only design tokens); platform `cz.tatrman` — `@tatrman/entry-client` (journal/reservation/check-in/entry-record API), `@tatrman/grid-core` (the solver/provenance model). All names technical/descriptive (**the mythological register is abandoned ecosystem-wide** — supersedes `PL J`; rider owed to PL docs).
- **LF-5 resolved by dissolution:** nothing reused in place, nothing re-owned. Iris/Sysifos = design DNA + Vega-Lite spec knowledge + the OTel web-instrumentation *pattern* (the entry app is born instrumented). envelope/v1 has no platform consumer. **Iris+Sysifos→React migration = kantheon-side initiative proposal** (parked handoff); interim, the framework-free kit is legally consumable by the Vue apps today (`PL D-3` downstream).

## 6. Contract inventory for planning

Planning must produce contracts/objects for (grouped by owner; details in the cited docs):

**Entry plane (platform, new):** entry journal (two typed row kinds; draft rows per reservation/principal) · reservation registry (acquire w/ overlap check on resolved member sets, release, holder query, admin break; enforced at journal/check-in API) · check-in preview API (dry run: expansions + verdicts + zero-base errors) · check-in API (requires reservation; returns entry record) · entry record schema (principal, program@ver, run ref, batch sizes, leaf-set hash, note) · base-presence bitmap in the form-load payload · round dashboard reads (reservations, records, coverage, done flags) · done-flag + note-field writes · prefs store for ttrl view state (CQ-4).
**Language/canon:** the TTR-P spread construct + error semantics (demand spec) · the fragment generator (MIT, text out) · `<cube>-entry-apply` standing program shape · TTR-M md package (E-i defaults, D-v version metadata, cubelet, `default_hierarchy`) · ttrl schema additions (form kind, saved-view kind, lifecycle distinction).
**Analysis plane:** md→Cube/OSI projection generator + lossiness ledger · semantic layer service deployment + Perun→policy generation (BQ-2) · viewer + B-author extensions on `PL Q-4-a`.

## 7. Cross-effort demand-spec packages (ratified S-3)

1. **→ TTR-P effort:** the single committed spread construct + precise error semantics (zero base, over-cap) + the fragment generator contract (text; same-plan-bytes determinism test). EQ-1 (construct vs stdlib vs macro) = their call, travels in the spec.
2. **→ TTR-M effort:** one md package — E-i spread/driver defaults, D-v version-dimension metadata, the cubelet contract, `default_hierarchy` (CQ-19 collision-check rider).
3. **→ ttrl schema owners (GQ-1):** form document kind + saved-analysis-view kind + the authored-canon vs generated-view-state lifecycle distinction (CQ-20 field-mapping rider).
4. **→ PL plan (amendment proposals):** DQ-1 run-deployed-program-with-input door verb · DQ-2 table-valued typed params (+ two-row-kinds refinement) · the reservation registry · D-ii Argos-symmetric write RLS · BQ-2 Perun→Cube/engine policy generation · the naming-register rider (mythological register abandoned).

## 8. v1 scope boundary

**In:** everything in §3–§5; the FY2028 OPEX hero end-to-end; additive single-measure entry; interactive analysis + saved views.
**Out / parked (with revisit conditions — control room §8):** non-additive measures (rates/prices; likely shape = visible-pass re-level, `design/06` §6c) · multi-measure forms · submit/approve chains · reservation TTL/heartbeat · mechanics-in-drafts persistence · sub-slice checkout with pinned boundary · selective commit · IPF for crossing pins · ephemeral formulas (v1.5) · enterable-regions-beyond-measures · Excel add-in client · scenario-as-data-branches · form templates/inheritance · scheduled report packs · R1 SQL façade · Iris/Sysifos React migration (kantheon handoff).

## 9. v1 acceptance statement (ratified, S-6 as amended)

> A planner opens the `tatrman-entry` form on `opex`/BUD2028 read-only, checks out the Sales Division slice, enters 120 M at division×FY, pins the March campaign override, block-adjusts H1 with the total held, seeds the empty account from ACT2027, previews the storage-grain expansion, and checks in — producing an entry record; the round dashboard shows the slice done. An analyst drills the result hierarchy-true in the Designer viewer, follows lineage from a cell to that entry commit, and the org's controller reads the same numbers in PowerBI through the semantic layer. Every derived number explains itself.

## 10. Assets

- **Prototype:** `design/prototypes/c-entry-grid-v0.html` (v0.8) — normative illustration; guided tour covers the acceptance path; solver + backend-sim assertions in the design sessions' record.
- **Option docs:** `design/02` (B) · `03` (D, +amendments) · `04` (E, +amendments) · `05` (A) · `06` (grid core) · `07` (commit/reservations) · `08` (forms) · `09` (round) · `10` (stack).
- **Ground truth:** `design/00-control-room.md` §7 decision log (append-only; every decision with rejected alternatives).
