# Tatrman Platform Frontends — Detailed Design

> The exhaustive companion to [`design.md`](design.md): full prose, worked examples, and the *why* behind every load-bearing choice. Audience: a reader who was not in the design sessions. The append-only decision log ([`design/00-control-room.md`](design/00-control-room.md) §7) remains ground truth; the interactive prototype ([`design/prototypes/c-entry-grid-v0.html`](design/prototypes/c-entry-grid-v0.html)) renders most of what this document describes and is worth opening beside it.

---

## 1. Why these products exist

When the Tatrman Platform was split out of Kantheon (platform design, 2026-07-08/09), every product frontend deliberately stayed behind: Iris because it is agent-coupled (a chat surface over LLM dispatch — intelligence, which the platform explicitly does not carry), Sysifos because it is Midas-coupled (its only backend is Midas-core's book of record). The Designer moved, but the Designer is a metadata and operations surface — catalog, model graphs, runs, lineage — not a data product. The result: **a data platform with no frontend for its own output.**

Two product needs fill that gap. First, an **analysis need**: see the results of TTR-P transformations interactively, and *author* the TTR-P that produces them in a live loop. Second, an **entry need**: a planning/budgeting surface over MD-model cubelets where the differentiating capability is **spreading** — enter a number at a summary level and have the system derive the detail, honoring what the planner has pinned down. Neither of these is a generic BI tool or a generic CRUD grid; both are consequences of Tatrman's own languages (TTR-P programs, TTR-M models) being the platform's citizens.

The effort resolved the product topology early (**the split verdict**): the analysis need ships as **Designer extensions** — the Designer is the platform's single pane of glass for everyone who reads and builds — while the entry need ships as **`tatrman-entry`**, a deliberately simple standalone product, because the planner persona (opens a form during a budget round, enters numbers, leaves) collides with the Designer's modeler/operator chrome. Planners never author canon; modelers author the forms planners use.

## 2. The concept vocabulary

A short glossary, because several everyday words acquired precise meanings — and one word ("lock") had to be banned from one of its three jobs:

- **Cubelet** — a cube × version slice at **storage grain**: the md-declared object the entry product writes to. Nothing new was invented; the term pins which existing md objects the entry contracts bind.
- **Form** — a `ttrl`-family layout document, *authored canon* (written by a modeler, git-reviewed): binds a cubelet, declares the **load grain**, chooses one hierarchy per dimension, sets the initial layout, and declares entry affordances (enterable measures, legal seed sources, guards, narrowing). The form's version is a *parameter role* — the concrete version (BUD2028) is chosen at check-out, so forms survive rounds.
- **The three grains** — *storage grain* (cubelet leaves) < *load grain* (the form's view leaves, in memory) < *display grain* (whatever the pivot currently shows). Display grain is pure presentation and appears in **no contract**. The load grain is the load-bearing one: it is simultaneously the working view's extent, the reservation's scope, the boundary of every gesture's ripple, and the home of the session draft.
- **Working view** — the in-memory copy of the loaded slice the planner manipulates. Everything that happens here is the **visible pass**: rich, fast, and *non-authoritative*.
- **Pin** (in the UI: the grid's "lock" 🔒) — a frontend-only constraint: "this cell's value must not change; move the others." Ephemeral; stored nowhere; never leaves the browser tab.
- **Reservation** (check-out) — an exclusive *write lease* on a slice, held server-side. Not a pin, not a version lock.
- **Version lock** — TTR-M md metadata: a version is OPEN or LOCKED (ACT is read-only by metadata). The round's start and end switch.
- **Journal** — the append-only entry substrate beside the cube. Its rows come in two kinds: **seed rows** (storage-grain slice copies) and **assignment rows** (load-grain values).
- **Entry record** — the provenance artifact of every check-in (a compile-record sibling): principal, program version, run reference, batch content hash, a free-text note.
- **Round** — not an object at all: the interval between a version's OPEN and LOCKED states. Everything else the round needs (who's working, what landed, what's done) is a *view* over reservations, entry records, and done flags.

## 3. A planner's session, end to end

The hero scenario — the FY2028 OPEX budget for the Sales division — walks every mechanism. (All of this is drivable in the prototype's guided tour.)

**Opening and checking out.** The planner opens the `opex` budget form. The view is **read-only**: editing is an explicit act. The header offers "Start editing"; clicking it acquires a **reservation** on the form's load slice — version BUD2028, the Sales Division subtree, all accounts, all months. Had a colleague held an overlapping slice, the header would instead say who ("checked out by petra@…, contact them or an admin") — the platform *prevents* write conflicts rather than detecting them, because cube data does not merge (§6).

**Top-down entry.** The planner types `120M` into the Sales Division × FY cell. The grid spreads the value proportionally over every unpinned view leaf — instantly, in memory. Proportional scaling of all leaves by one factor preserves every intermediate subtotal's share automatically, so the quarters, departments and accounts all remain consistent without any per-level computation.

**Override and pin.** March marketing campaigns get `+2M`; the planner pins that cell. Re-entering the 120 M total now spreads over everything *except* the pinned cell — the total reconciles exactly, the override survives.

**Block operations with a held total.** The planner pins the Field Sales × FY total, selects the January–June block, and applies −10 %. The gesture is atomic: the six months drop, the pinned total is violated, and the **compensation cascade** re-establishes it by scaling July–December up — the planner watches H2 "catch up." If the cascade cannot succeed (say Q3 and Q4 are also pinned), the whole gesture errors and rolls back: over-constraint is an explicit failure, never a silent compromise.

**Seeding.** The Marketing · Software row is empty — it was never budgeted, and its storage base is zero. The grid knew this at load time (the **base-presence bitmap** marks such cells) and the row renders with a warning. Entering a total there refuses: **zero base = error** — proportional spreading over nothing is a question with no honest answer. The fix is explicit: the **seed** gesture copies the ACT2027 slice into the empty cells (a storage-grain copy operation, fill-empties-only, never a spread). Then the total entry works.

**Preview.** "Preview & check in" runs the journal batch through a dry run of the committed pass. The preview shows verdict chips (reservation held, version open, write-RLS, zero-base check) and the batch list — and each assignment can be expanded to its **storage-grain expansion**: the actual cubelet leaves that will change, old → new. This is the moment the invisible pass becomes visible, and the planner ratifies exactly what will land.

**Check-in.** Commit = a run of the standing canon `opex-entry-apply` program with the batch as typed input; the result is an **entry record**. Because the planner holds the reservation, nothing can have moved underneath — preview and commit are congruent *by construction* (the old drift check survives only as a server-side invariant that must never fire). The planner chooses "keep working" (reservation held; more edits, more check-ins) or "release" (the slice becomes available; the natural pairing with declaring the slice **done**).

**The round around it.** The version owner opened BUD2028 (an md canon change, gated by git permissions — "not everyone, everytime" is discipline, not code); the round dashboard shows who holds what, what's landed, and which form instances are flagged done; when the board is green the owner locks the version. That is the whole workflow machinery of v1: one bit and a dashboard.

## 4. The grid semantics, precisely

### 4.1 The cascade (CL-β)

The model is a leaf matrix at load grain plus two hierarchies (rows, columns). A **pin** stores the pinned cell's aggregate at pin time. A **gesture** is a batch of region assignments (a single edit, a block op, a curve application). The algorithm:

1. **Apply** each assignment: a single-leaf assignment sets the value; a region assignment scales the region's *editable* leaves (not pinned-this-gesture, not under a satisfied inner pin) by one factor so the region sums to the assigned value. If the editable base is zero and the target isn't — **zero-base error**. Assigned leaves become *pinned-for-this-gesture*.
2. **Reconcile**: while any pinned cell's aggregate deviates, re-assign the smallest violated pin region to its pinned value (excluding gesture-pinned leaves and satisfied inner-pin regions, which are frozen — the rule that makes arbitrary *nesting* work). Iteration cap ⇒ **over-constrained error**.
3. **Atomicity**: any error rolls the entire gesture back.

Two facts carry the design: *uniform scaling preserves proportionality at every intermediate level for free*, and *pins constrain sums, not contents* — pinning a row total and cutting its first half is exactly what makes the second half catch up. Non-nested **crossing pins** (a pinned row total and a pinned column total fighting over their intersection) can oscillate; v1 answers with an honest error (the iterative-proportional-fitting escalation is parked — it would accept more states at the price of explanations degenerating to "the solver found a point").

Every leaf keeps an **op chain** (`entry · spread ×f · catch-up ×f · seed · curve`), rendered in the cell inspector — P4's explanation is an inspectable artifact, not a log. The chain is session ephemera; after check-in, the entry record takes over as the permanent explanation.

### 4.2 The vocabulary

Typed entry accepts shorthand (`120M`, `450k`, `+2M`, `-500`, `+8%`, `-10%`). Summary entry spreads proportionally over unpinned leaves. Block operations scale each selected leaf (pinned cells are skipped with a count — but *directly editing* a pinned cell is a hard error; skipping what the user explicitly touched would be a lie). Curve application redistributes each row's block total along a declared profile. Seed is a first-class gesture, offered right from the zero-base error. Ephemeral formulas are deferred to v1.5 (their reference scope — view cells only, or driver slices like `=ACT2027×1.05` — is the open question). Whether manual edits auto-pin themselves is a **user toggle**: both behaviors are legitimate planner intents, so neither is imposed.

### 4.3 Why the committed pass carries none of this

Because ratified assignments live at load grain, and load-grain cells *partition* the storage leaves, each assignment expands within a **disjoint** storage region — no pin, no other assignment, nothing can interact with it. Every rich mechanism above is therefore free to be pure client tooling: the committed pass needs exactly one deterministic construct (*scale the existing base under the assignment proportionally; zero base = error; largest-remainder rounding*), which is small enough to be a TTR-P language capability — reviewable text, engine-executable, MIT. The platform ships a deterministic fragment generator so surfaces never string-build TTR-P. The audit story needs no locks: an entry record's derivation replays from (assignments + leaf-set hash + program version) alone.

## 5. The write path

The entry plane is built almost entirely from machinery the platform already has. The surface writes to the **entry journal**; check-in executes the standing canon **`<cube>-entry-apply`** program with the batch as table-valued typed input — so entries ride the same doors, validation (Argos write predicates), scheduling (Kyklop), run store, events and lineage as every other program run, and appear in lineage like any transform. Seeds apply before assignments — the ordering that lets "seed an empty row and spread a total onto it" be one commit. Version protection (ACT read-only; BUD open-then-locked) is md metadata enforced at the write check. Session **drafts** autosave into the journal — values only (assignments + seeds); recovering a session restores your numbers but not your pins or formulas, which is the decided semantics, not a gap (the UI says so).

## 6. Concurrency: why check-out/check-in

The first design pass converged an optimistic model (cell-level last-write-wins, advisory presence claims, and rebase-on-commit: if the base drifted between preview and commit, re-preview). It was coherent — and it was answering the wrong question. Optimistic concurrency exists to let concurrent writers *merge*; line-based text merges, **cube data does not**. Two planners' proportional spreads over the same slice have no meaningful union. So the model was replaced (formally amending the earlier decision) with a **reservation** discipline — SVN's lock-modify-unlock for non-mergeable content: conflicts are *prevented at acquisition*, not detected at commit.

The scope algebra keeps it sound. A reservation is **one product slice** — per dimension, either all members or a member set — and concretely **the form's load slice as opened** (optionally narrowed by a subtree, which narrows the load equally). It cannot be finer, because of the **ripple problem**: gestures write beyond the clicked cells (spreads reach every leaf under a summary; compensations reach *siblings*), so the reservation must cover everything a gesture may touch — which is exactly the loaded slice. Overlap is computed on **resolved member sets at storage grain** — never by comparing hierarchy node names, which breaks the moment two scopes are phrased through different hierarchies of the same dimension. The member sets are **snapshotted at acquisition**, so even a (disciplined) mid-round model change never silently resizes a held reservation. Version is always pinned in the scope, so a BUD2028 checkout never blocks FCST work on the same cube.

The lifecycle is deliberately calm: open read-only → explicitly start editing (acquire) → work → check in (commit *requires* the reservation; commit does not release) → release. A taken slice shows *who* holds it; a stale reservation is broken **manually by an admin** in v1 (TTL and heartbeats are parked enhancements — machinery waiting for demonstrated need, per P5). Under a held reservation the base cannot drift, so preview→commit congruence is structural; the old drift check remains as a server-side invariant whose firing would indicate a platform bug, not a user conflict.

## 7. Forms, views, and the ttrl family

Everything a form says is knowledge about *how a model is presented and interacted with* — which is precisely what the `ttrl` layout-document family exists for. So the form is not a new document kind: it joins ttrl as the family's first **authored canon** citizen (the family previously held generated view state — Designer layouts, panel state). One schema family now carries two lifecycles, and the line between them is the old canon/preferences line: **form documents live in the repo** (modeler-authored, reviewed, versioned); **planners' pivot state and personal saved views are the same ttrl shapes in the platform prefs store** — never canon. The same split answers saved *analysis* views: shared/published views are authored canon ttrl documents; personal ones live in prefs.

A minimal form is two blocks — binding and grain — with everything else defaulted from the model (`default_hierarchy` on dimensions being the one md addition this requires). The grain block is the consequential one: it defines the load grain, and with it the working view, the reservation scope, the ripple boundary and the draft's home. The author-time validation catalogue (grain not finer than storage, single rollup path per dimension, additive enterable measures, resolvable seed sources, guard under the platform cap) turns each would-be runtime surprise into a review-time error — which is what earns the form its canon status.

## 8. The analysis surface

The read side is a **composite**, because the two demands it faces — "hierarchy-true, lineage-aware exploration of MD models" and "our controllers live in PowerBI" — have no single-tool answer. The platform ships a **semantic projection**: TTR-M `md` deterministically projected into a Cube-model/OSI document (a regenerable projection, never a second source of truth), with an explicit lossiness ledger. A Cube-shaped **semantic layer service** carries that model and — critically — **the policy**: its row-level access rules are generated from Perun's bundles, and all external BI connects to *it*, never to the engines. This resolves the structural finding that shaped the whole workstream: every external BI tool speaks SQL to a database, while the platform's governed read path is a plan-validating door; the semantic layer is the one honest choke point that reconciles them. Superset is the documented reference pairing (verified: its embedded SDK and guest-token row-level security live in the Apache-2.0 open core); PowerBI rides the same layer.

Alongside the external clients, two **Designer extensions**: the **thin native viewer** — a Perspective-based pivot with true hierarchy drill and Vega-Lite charts, small because the heavy clients are external, and carrying the one feature nobody else can copy: **drill from any number through column lineage to the program, the run, and the entry commit that produced it** — and **B-author**, the interactive TTR-P loop (author with the browser-worker LSP, preview through the query door, save, graduate to a deployed program). Scheduled report packs are out of v1 (report-renderer territory, which stayed kantheon).

## 9. Architecture and stack

**React 19 everywhere platform-side**, with a discipline that matters more than the framework: everything below the rendering shells is **framework-free TypeScript**. The grid's solver/model/provenance layer already is (it runs in Node for its tests); the shared kit follows. The kit splits along the license line: MIT `org.tatrman` packages for clients of published contracts (`door-client`, `md-client`, `ttrl`, `format`, `tokens`) — the MIT extension surface needs them anyway — and `cz.tatrman` packages for the entry plane (`entry-client`, `grid-core`). All names are technical (the ecosystem abandoned mythological naming).

Nothing is reused in place from kantheon and nothing needed re-owning — the old "re-own the envelope pipeline?" question dissolved once the query door returned typed results, the grid brought its own model, and charts became Vega-Lite-as-a-library. What did cross the line costs nothing to carry: Sysifos's write-model DNA (preview→commit, layered validation), Iris's Vega-Lite fluency, and Iris's OpenTelemetry web-instrumentation pattern (the entry app is born instrumented). A future Iris/Sysifos migration to React is recorded as a kantheon-side proposal; meanwhile the framework-free kit is legally consumable by the Vue apps as-is, so "one package family" begins at the logic level immediately.

## 10. Rejected roads (the short honest list)

Full rationale lives in the option docs; the ones a future reader will ask about:

- **Rich committed spread vocabulary** (proportional/even/profile/driver as server semantics) → the two-pass model made richness free client-side and shrank the committed language demand to one construct.
- **Locks as stored/server state** → locks (pins) can never affect committed math (disjoint regions), so storing them is audit dead weight.
- **Optimistic concurrency with rebase-on-commit** → built to merge the unmergeable; replaced by reservations.
- **A global constraint solver (IPF) for pins** → accepts more states, explains none of them; parked behind an honest error.
- **Submit/approve workflow in v1** → the most org-idiosyncratic feature of every planning tool; designed against a real demand or not at all.
- **Wholesale-embedding an OSS BI** → hierarchies flatten, two UX worlds, and the policy-honesty bill arrives in full.
- **Metabase / Lightdash as the embedded read surface** → embedding+sandboxing is paid-tier (Metabase); dbt-coupled semantics and an agentic drift (Lightdash).
- **Form as platform config** → the reservation scope defined by unreviewed config; forms earned canon status instead.
- **Workflow-as-data, entry-as-ephemeral-program, no-UI-Excel-only, form-as-saved-plan** — the catalogued "weird" options that mapped the space's edges.

## 11. Deferred futures

The parking lot is a commitment device, not a graveyard; every entry has a revisit condition. The notable ones: **non-additive measures** (rates, prices — the likely shape is already sketched: a visible-pass multiplicative re-level that ratifies leaves and needs no committed construct); **sub-slice check-out with pinned boundary** (the machinery exists in the cascade; waits for the shared-form/many-planners demand); **selective commit**, **reservation TTLs**, **mechanics-persisting drafts**, **formulas' driver-slice references**, **the Excel add-in client**, **scenario branches**, and **the R1 SQL façade on the query door**.

## 12. The prototype

[`design/prototypes/c-entry-grid-v0.html`](design/prototypes/c-entry-grid-v0.html) (v0.8) is a single self-contained HTML file — no dependencies, no network; double-click to run. It was built *during* divergence as a design instrument and several decisions were made or unmade by driving it (the freeze-contents lock semantics died there; the auto-pin toggle was decided there). It demonstrates: the working-view grid with the full v1 vocabulary and the compensation cascade; the reservation flow (read-only open, explicit check-out, an overlap denial, admin force-release); the check-in preview with per-assignment storage-grain expansion over a simulated storage grain finer than the load grain; seeds as journal rows; the entry record; and the annotated form declaration behind the view. Its model layer and simulated backend are covered by 31 scripted assertions. It illustrates the design; the decision log defines it.
