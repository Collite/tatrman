# Platform Frontends — Commit / Preview Flow Options (workstream C, thread 2)

> Divergence catalogue for **C thread 2 — the commit/preview UX**: how the working session's ratified state becomes a journal batch, gets previewed against the authoritative committed pass, survives concurrent commits (D-iv rebase-on-commit), and lands as an entry record. Session 2026-07-10 (same day as thread 1's convergence).
> Companions: [Control Room](./00-control-room.md) · [`06-entry-grid-options.md`](./06-entry-grid-options.md) (thread 1, converged) · [`03-write-path-options.md`](./03-write-path-options.md) (D — the machinery this UX drives).
>
> **Scope guard:** thread 2 owns the *flow from journal to entry record as the planner experiences it*. The write-path mechanics are D (converged); workflow/approval rounds are Q-3 (still separate); the form-declaration vocabulary is thread 3.
>
> **Prototype v0.6:** [`prototypes/c-entry-grid-v0.html`](./prototypes/c-entry-grid-v0.html) simulates the whole pipeline — a **storage grain below the load grain** (2 cost centers under every department cell), the preview dry run with per-assignment expansion, the **check-out/check-in reservation flow** (Petra denied on overlap; check-in & keep vs & release; drift check demoted to internal invariant), the entry record card, and post-check-in reload. Backend semantics verified by 15 scripted assertions. Findings marked **[proto]**. (v0.5's drift-rejection demo was superseded the same day by C-T14.)

## 0. What is already fixed (the walls of this room)

From D/E/A and thread 1, non-negotiable here: commit = journal batch → run of the standing canon `<cube>-entry-apply` program (table-valued typed input) → entry record; preview and commit are **the same computation** (dry vs wet — one authority); **rebase-on-commit** — base = current state at commit, drift ⇒ re-preview, *never* silent re-portioning; cell-LWW + advisory slice claims for concurrency; version protection from md (ACT read-only); the committed pass is **lock-free**; zero base = error; P3 propose-don't-write. The UX below is the human face of exactly that machinery.

## 1. Finding first **[proto]** · the journal batch has TWO row kinds

Building the simulation forced a refinement of D's "table-valued typed input": **seed ops are not assignments.** A seed (`BUD2028 := ACT2027` over a slice) is a **storage-grain copy**, while an assignment is a **load-grain value** that the committed pass expands. They compose in one batch — and order matters: **seeds apply first**, then assignments expand *over the seeded base* (the planner seeds an empty row, then spreads a total onto it, in one commit). So the journal's typed input is two tables (or one row type with a discriminator), with deterministic batch ordering: `seeds → assignments`. This is a **refinement proposal to D's journal contract** (not an amendment — D said "table-valued typed input" without pinning the shape); flag it when D's contracts get written at planning stage.

Second finding: **CQ-5's base-presence bitmap earns its keep** — one boolean per load-grain cell at load time marks "no storage base beneath" (red-dotted in the prototype); typing into such a leaf works in the view but previews as a zero-base error naming the missing seed. Without the bitmap the planner learns this only at preview; with it, at first glance.

## 2. C-T8 · Preview depth — what does the planner ratify?

- **α · Verdict-only.** "120 assignments + 12 seeds; version OPEN ✓; RLS ✓; zero-base none ✓; total Δ +8.7 M — Commit?" Trust the invisible pass entirely. *Buys:* speed, no cognitive load. *Costs:* P3's "preview server-derived effects" reduced to a checkmark; the planner ratifies what they cannot see.
- **β · Verdict + batch list + on-demand expansion.** The assignment list with old→new, each row drillable to its storage-grain expansion (the cost-center rows that will actually change). **[proto]** *Buys:* P4 at the commit boundary — the expansion is inspectable exactly where the invisible pass becomes real; cheap to render (expansion rows come back with the dry run). *Costs:* big batches need pagination/virtualization (CQ-8).
- **γ · Full materialized diff.** The whole storage-grain delta as a browsable grid/report (Sysifos's preview idiom taken literally). *Buys:* total transparency. *Costs:* the diff can be 100× the batch size; planners won't read it; it duplicates the grid at a grain the form deliberately hid.
- **δ · Weird: no preview — optimistic commit + easy revert.** The journal substrate makes a compensating batch cheap in principle; commit instantly, offer "undo commit". *Buys:* zero ceremony. *Costs:* violates P3 head-on; revert of a spread over a since-changed base is not an inverse; catalogued as the floor.

**DECIDED 2026-07-10 → β** (Bora) — with α's verdict chips as the header (they are the *gate*: check-in disabled until green) and γ available as an export/report later if audit asks for it.

## 3. C-T9 · Drift & rebase — ~~the concurrent-commit UX~~ SUPERSEDED by C-T14

> **2026-07-10, same session:** Bora replaced the optimistic model with **check-out/check-in** (§7a, C-T14) — "merging data cubes makes no sense". This section stands as the record of the rejected alternative; under reservations, drift *cannot occur* within a checked-out slice, and everything below demotes to an internal invariant.

Drift = anything that changes an expansion: values *or* support under any batch row changed since preview (per-row base hashes travel with the preview result **[proto]** — the server stays stateless about previews). Options for what the planner experiences:

- **α · Whole-batch re-preview.** Any drift ⇒ the entire preview is stale; redo it. Simple, blunt; a 500-row batch re-ratified because one unrelated row drifted.
- **β · Scoped re-preview.** Only drifted rows flag 🔶 (who + what changed); the rest stay ratified; one *Re-preview* recomputes portions and clears the flags; commit re-enabled. **[proto]** — the Petra demo: 6 of 120 rows flag, unrelated concurrent commits don't block at all (verified).
- **γ · Auto-rebase with confirmation.** Recompute silently, show a "portions changed" summary, one click. *Semi-silent* — sails close to the D-iv wall ("never silent re-portioning"); catalogued to mark the boundary, not leaned.

Companion fork — **when is drift surfaced:** only at commit (α; the D-iv floor, always on) · at preview open · **continuously via advisory claims** (D-iv's presence idiom: "petra@collite.cz is editing Field Sales × Q1" chips in the grid, SSE-pushed). Claims are advisory — they *warn before* the drift exists; the hash check *enforces after*. CQ-9: how loud the claims UI is (chips on slice headers vs a presence bar vs nothing until preview).

**Lean: β + claims-as-chips.** Never γ.

## 4. C-T10 · Session lifecycle — is the working session a draft?

The working view dies with the tab today. Fork on persistence:

- **α · Pure in-memory.** Close the tab, lose the session. Honest, zero machinery; brutal for a planner 300 edits into a round.
- **β · Journal-draft autosave.** The ratified state (assignments + seed ops — *values only*) autosaves as draft rows in the entry journal (D-i built the substrate for exactly this); reopening the form recovers them; cross-device continue. **Consistency note:** the lock-free decision said locks & formulas are stored *nowhere* — so a draft persists the *what* (values), never the *how* (op log, locks, formulas). A recovered session has your numbers but not your locks. That asymmetry is the decided semantics, not a bug — worth stating in the UI ("recovered draft: values restored; locks and mechanics are per-session").
- **γ · Explicit save-draft.** Sysifos-style deliberate drafts; planner-controlled, but "I forgot to save" re-enters the world.
- **δ · Weird: persist the op log** (full session replay incl. locks). Catalogued to mark the wall: **contradicts the 2026-07-10 lock-free decision** — rejected by construction.

**DECIDED 2026-07-10 → β** (Bora: yes to draft autosaves, yes to saving into the journal; **values only — locks and formulas omitted *in v1***). Debounced autosave; drafts are per-(reservation, form, version, principal); check-in consumes the draft. The "in v1" qualifier is recorded deliberately: persisting session *mechanics* (op log incl. FE locks/formulas) is now a **parking-lot item**, not an illegal state — un-parking it would consciously revisit the "stored nowhere" clause of the lock-free decision. CQ-10 (draft visibility to approvers) still open, couples with Q-3.

## 5. C-T11 · Commit granularity & annotation

- **Batch shape:** α whole-journal commit (one batch = one program run = one entry record) **[proto]** · β selective commit (choose rows — planners' "commit what's done, keep noodling on the rest") · γ auto-commit-per-gesture (Anaplan live-write — the anti-pattern under P3, floor).
- **Annotation:** does the entry record carry a free-text note ("Q1 rebudget after board call")? The record already has principal/program/params; a note field is cheap and audit loves it. CQ-12.
- **DECIDED 2026-07-10 → α** (Bora): whole-journal check-ins; selective commit = parking lot; the journal-draft β from C-T10 covers "keep noodling". Note field (CQ-12) still open — lean yes.

## 6. C-T12 · Validation topology — where can it fail?

Three layers, Sysifos DNA generalized **[proto]**:

1. **FE, instant:** parse, locked-cell refusal, view-grain zero-base, over-constraint, base-presence marking. Non-authoritative echoes.
2. **Preview dry run, authoritative:** the real fragment through the query door — storage-grain zero-base, RLS-on-write predicate, version protection, expansion itself. The verdict chips.
3. **Commit, final:** drift check + re-validation. **The contract: nothing may fail at commit that preview didn't already flag, except drift and permission/version changes since preview.** Anything else failing at layer 3 is a platform bug, not a UX state.

Long applies (huge cubelets): commit returns fast with a run reference; progress via the existing run-events SSE (Draft+SSE pattern); the grid stays usable read-only until rebase. Latency: preview is the flow's only round trip — EQ-4's number gets measured *here* (the 500 ms simulated trip feels fine **[proto]**; pin the real budget at first integration).

## 7. C-T13 · Post-commit — rebase, provenance handoff, history

**[proto]** behaviors, catalogued as the lean composite: success card shows the **entry record** (principal, program@version, run ref, batch size, leaf-set hash) — the P4 handoff moment: *before* commit a cell's explanation is the FE op chain; *after*, it is the entry record (server artifact, permanent). Then **rebase**: view reloads from the cube (including others' concurrent commits), op log/colors clear, locks re-pin at current values (session continuity — trivially correct since locks are FE-only), journal empties, the commit lands in a session history panel. Open: CQ-13 — cells changed by *others'* commits during your session: mark them quietly on rebase (lean) vs silent refresh vs a diff summary.

## 7a. C-T14 · Concurrency model = CHECK-OUT / CHECK-IN — **DECIDED 2026-07-10 (amends D-iv)**

**The decision (Bora):** the planner **checks out** a cubelet or a part of it, works, and **checks in** the results. *"Merging data cubes makes no sense, unlike line-based git."* A pessimistic **reservation** model replaces D-iv's optimistic composite (cell-LWW + advisory claims + rebase-on-commit). Prior-art precision worth recording: plain SVN checkout is copy-modify-merge too — what this decision actually adopts is SVN's **lock-modify-unlock discipline for non-mergeable (binary) files**, which is exactly the right analogy: a cubelet slice is non-mergeable content, so conflicts are *prevented at acquisition*, not *detected at commit*.

**Semantics pinned with it** (rendered in prototype v0.6 **[proto]**, 15 backend assertions):

- **Check-out** = an exclusive **write reservation** on a cubelet slice (overlap ⇒ the later request is denied, with holder identity visible; readers unaffected). Overlap = slice-intersection over the md dimension algebra (prototype: subtree containment on one axis; the real algebra is CQ-15).
- **Check-in** = D's commit exactly as converged (journal batch → `entry-apply` run → entry record) **+ requires the held reservation** — the platform refuses a check-in without it. **Commit does not release**: check-in-and-keep-working (SVN's `--no-unlock`) vs check-in-and-release are both first-class; release-without-check-in abandons cleanly.
- **Drift machinery demoted, not deleted:** under an exclusive reservation the base *cannot* change under you, so the per-row hash check survives **server-side as an internal invariant** (if it ever fires, the platform has a bug, not the planner a conflict). Preview→commit congruence is now guaranteed by construction — the strongest possible form of P3's "preview shows what commit does".
- **cell-LWW is moot** (no two writers can overlap); **advisory claims upgrade to mandatory reservations** — the presence idiom survives as *visibility of who holds what*.
- **Terminology hygiene (important):** three unrelated things must never share the word "lock": the FE **value pin** (C-T2, ephemeral, never stored) · the **version lock** (D-v md metadata, BUD open-then-locked) · the **reservation** (this section, a data-plane write lease). UI copy and contracts should say *check-out/reservation*, never "lock", for the third.

**Sub-forks — mostly DECIDED 2026-07-10 (Bora):** **acquisition = explicit "edit" toggle** — open is read-only; the planner explicitly says "I want to start editing this" to acquire; while a slice is taken, the header shows *who* has it ("hey, someone has this locked" — with the holder to contact). Rejected: auto-on-open (casual viewers holding reservations), acquire-on-first-edit (implicit acquisition contradicts the explicit-act framing). · **Stale reservations (CQ-14) = manual admin unfreeze in v1**; the header shows the holder = whom to call; **TTL/heartbeat/auto-expiry/version-close-voiding → parking lot** as enhancements. · Still open: same-principal multi-device (lean: rejoin the same reservation) · reservation registry placement = beside the journal, enforced at the journal-write/commit API — a named **`PL H`/D contract amendment proposal** (joins DQ-1/2) · **CQ-15 scope algebra → deep dive in §10a.**

**Consequences elsewhere:** C-T9 superseded (above); **C-T10 strengthened** — the reservation is a server-side session anchor, so journal-draft autosave (β) attaches to it naturally and CQ-10's visibility question gets a default (draft belongs to the reservation holder); the Petra UX flips from "your commit was rejected, re-preview" to "the slice is taken, you can read and see who has it" — a *calmer* model for planners. Cost honestly stated: reservations serialize work on overlapping slices; the mitigation is scope discipline (check out the smallest slice the round needs — form design and CQ-15's algebra decide how fine that can be).

**Rejected:** the optimistic composite (previously converged D-iv — rejected because its whole purpose was surviving concurrent writes to mergeable data, and cube slices aren't) · hybrid advisory-claims-plus-drift (soft claims planners can ignore reintroduce the drift UX for no benefit once claims exist anyway).

## 8. Prior art

Sysifos (preview→commit, Draft+SSE, three-layer validation) is the direct ancestor — this thread is that DNA stretched over a two-grain write. The **git metaphor** holds up eerily well and may be worth using in UX copy: working view = working tree, journal = index/staging, entry record = commit object, drift rejection = non-fast-forward push, re-preview = rebase. TM1 sandboxes commit without expansion preview (their audit is post-hoc); Anaplan writes live (no commit boundary at all) — both mark walls P3 already built.

## 9. Leans (not decisions)

1. **C-T8 β** — verdict chips as the gate + batch list + on-demand storage-grain expansion; γ-report parked for audit demand.
2. ~~**C-T9 β** — scoped drift…~~ **SUPERSEDED by C-T14 (decided): check-out/check-in reservations; drift check = internal invariant; reservation visibility replaces advisory-claim chips.**
3. **C-T10 β** — journal-draft autosave of *values only*, attached to the reservation (locks/formulas die with the session by decided semantics; say so in the UI).
4. **C-T11 α + note** — whole-batch commits, free-text annotation on the entry record; selective commit → parking lot.
5. **C-T12** — the three-layer contract ("commit may only newly fail on drift/permission"); SSE progress for long applies.
6. **C-T13** — rebase as prototyped; others' changes marked quietly (CQ-13 lean).
7. Journal typed input = **two row kinds (seed | assignment), seeds-first ordering** — file as a refinement to D's contract at planning stage.

## 10. Open questions (thread 2)

- **CQ-8 ·** Preview rendering at scale: pagination/virtualization threshold for the batch list; is there a batch-size guard (relates CQ-7)?
- ~~**CQ-9 ·** Advisory-claims surface~~ **superseded by C-T14** → the live question is reservation *visibility*: banner (proto) + where else (catalog? Designer? a "who has what checked out" round view — couples with Q-3).
- **CQ-10 ·** Draft rows: private to the principal or visible to approvers (couples with Q-3 workflow)? Retention/expiry?
- **CQ-11 ·** Selective (partial) commit — parked for v1; revisit when a real round demands it.
- **CQ-12 ·** Entry-record note field: v1 yes/no; free text only, or structured (reason codes)?
- **CQ-13 ·** Post-check-in reload presentation of changes *outside* your reservation scope (others' checked-in slices elsewhere in the view): quiet marks vs silent vs diff summary.
- ~~**CQ-14 ·** Stale-reservation policy~~ **RESOLVED 2026-07-10 → v1 = manual admin unfreeze; header shows the holder (whom to call); TTL/heartbeat/auto-expiry/version-close-voiding parked as enhancements.**
- ~~**CQ-15 ·** Check-out scope algebra~~ **RESOLVED 2026-07-10 → §10a v1 shape ratified** (form's-load-slice product scope · member-set overlap at storage grain · snapshot-at-acquisition); mid-round model edits = disciplined governance → Q-3.

## 10a. CQ-15 deep dive — the check-out scope algebra

"What can you check out, and how is overlap computed" sounds bureaucratic; it hides four real issues.

### Issue 1 · A reservation is a region, and regions must be intersectable

A cubelet scope is naturally a **product slice**: one member set per dimension, region = their Cartesian product. Example — Bora's checkout: `version = {BUD2028} × cost_center ∈ subtree(Sales Division) × account = * × month = *`. Two product slices overlap **iff they intersect on *every* dimension** — a per-dimension set-intersection test, cheap and exact.

Within one hierarchy this is trivial (two subtrees are either nested or disjoint — the prototype's rule). It stops being trivial the moment two scopes are phrased via **different hierarchies over the same dimension**: "Sales Division" (org hierarchy) vs "Prague offices" (region hierarchy) are neither nested nor disjoint — they share some cost centers and not others. Node-name comparison is then meaningless; the only correct test is on **resolved member sets at storage grain**. Hence the rule: *the registry stores resolved member sets, and overlap is always computed on them* — hierarchy nodes are just the UI for choosing the set.

### Issue 2 · The ripple problem (the one with teeth)

Your gestures write more cells than you click. A summary entry spreads to every view leaf beneath it; a lock compensation pushes deltas into *sibling* cells — the hero's H2 catch-up is a write into cells the planner never touched. So **the reservation must cover every cell a gesture may write**, and for the grid's core semantics that is essentially the whole loaded slice. If you held only H1 and locked the FY total, the −10 % block op would need to write H2 — outside your reservation. Three ways out:

- **(a) Scope = the form's load slice.** What you load is what you hold; "a cubelet or its part" is chosen *at open time* (the form's filters, plus an optional subtree narrowing in the check-out dialog — which then also narrows what is loaded). No boundary cases exist because there is no boundary inside the working view.
- **(b) Sub-slice scope with pinned boundary.** You load the division for context but hold only your department; every loaded-but-not-held cell behaves as **pinned** (CL-β already knows how to spread around pins). Coherent and even elegant — enter a division total and it flows into your department only — but surprising arithmetic for planners, and the compensation cascade can error on walls the user can't unlock.
- **(c) Sub-slice scope with hard refusal.** Any gesture that would ripple outside the scope errors. Planners hit invisible walls constantly; catalogued as the floor.

**Lean: (a) for v1** — it is also why the reservation UX stays *calm*: within your working view, everything is movable (subject only to your own pins). **(b) recorded as the natural v2 extension** since the pin machinery already exists; it is what "shared form, department planners" will eventually want.

### Issue 3 · Granularity economics

Finer scopes buy parallelism and cost comprehension. But budgeting's collaboration unit is organizational — "my department", "my region" — which is exactly *one subtree on one dimension × everything else*. So the practical scope vocabulary is small: **version always pinned** (you check out BUD2028, never all versions — so budget entry never blocks an FCST checkout of the same cube) **× one entity subtree × full remaining dims**. Registry bookkeeping at this shape is trivial at any realistic scale (per-dim sorted member sets or bitmaps; tens–hundreds of concurrent reservations); the "bookkeeping outweighs benefit" worry was misplaced — the true cost of very fine scopes is *human* ("who holds what" becomes a puzzle), which the round view (Q-3) will answer, not the algebra.

### Issue 4 · Time — the model can move under a symbolic scope

If the registry stored "subtree(Sales Division)" *symbolically* and md gained a new department mid-round, the reservation would silently grow. **Lean: resolve at acquisition** — the reservation is the member-set snapshot taken when you check out. A member added mid-round is covered by nobody, which is consistent with existing-support-only (new combinations are explicit acts anyway); hierarchy changes mid-round are a round-governance question (Q-3 / D-v territory), not a reservation question.

### ~~Proposed~~ **RATIFIED v1 shape (Bora, 2026-07-10) — CQ-15 RESOLVED**

Reservation = **one product slice**: per dimension either *all members* or a member set chosen via the dimension's md hierarchy; **scope = the form's load slice as opened** (version pinned + form filters + optional subtree narrowing at check-out, which equally narrows the load); overlap = **per-dimension resolved-member-set intersection at storage grain**; **snapshot at acquisition**. Parked: non-product scopes (unions/exceptions), sub-slice with pinned boundary (b), symbolic self-updating scopes.

Bora's ratification notes: (1) *"I always thought of the **loaded** scope, never the visible — visible is just FE display work"* — GI-9 extends naturally to reservations: **display grain never appears in any contract**; the reservation, the ripple boundary, and the draft all live at loaded scope. (2) **Mid-round dimension/member edits are a matter of *discipline*, not technical prohibition** — "adding a new product in the middle of planning has other consequences; not everyone, everytime, can do that." Since md is canon, the git/review path already gates *how* model changes happen; the round adds a *policy* layer over *who and when* — this belongs to **Q-3's round-governance thread** (round-scoped model-change discipline), not to the reservation algebra. Snapshot-at-acquisition stands regardless: even a disciplined mid-round change never silently resizes a held reservation.

## Convergence status

**🟢 THREAD 2 CONVERGED (2026-07-10, four passes).** Decided: **C-T14 check-out/check-in** (amends D-iv) · **C-T8 = β** (chips + list + on-demand expansion) · **C-T10 = β** (journal-draft autosave, values only in v1 — mechanics persistence parked) · **C-T11 = α** (whole-batch check-ins) · **acquisition = explicit edit toggle** (read-only open; header shows the holder) · **CQ-14 = manual admin unfreeze v1** (TTL/heartbeat parked) · **CQ-15 = §10a v1 shape ratified** (loaded-scope reservations; mid-round model edits = disciplined governance → Q-3). C-T9 superseded; C-T12/T13 stand as ratified-shape leans. **Prototype v0.7** renders the decided composite. Residual minor opens (none blocking): CQ-8 (preview at scale — planning-stage detail), CQ-10 (draft visibility ↔ Q-3), CQ-12 (note field), CQ-13 (reload marks). **C's remaining threads: form-declaration vocabulary (thread 3) + Q-3 workflow.**
