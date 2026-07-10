# Platform Frontends — Round & Workflow Options (workstream C, Q-3 — compact catalogue)

> Q-3 was converged in chat 2026-07-10 after the fork was presented with its accumulated context; this compact catalogue is the record (the A/`05` precedent). Companions: [Control Room](./00-control-room.md) §7 · [`07`](./07-commit-preview-options.md) (reservations, drafts) · [`08`](./08-form-vocabulary-options.md) (forms).

## 0. What Q-3 had accumulated

Originally (2026-07-09): "entry workflow (submit/approve/lock-version) — v1 or later?" Landed in its lap since: the **round view** (reservation-registry visibility, ex-CQ-9), **draft visibility** (CQ-10), the **model/form-change discipline** ("not everyone, everytime"), **admin-break roles** (from CQ-14's manual-unfreeze), and the **entry-record note field** (CQ-12).

## 1. The key observation

Most of a "round" already exists as a by-product of converged decisions: **D-v** gives versions OPEN/LOCKED state in md canon (the round's start/end switch); every check-in yields an **entry record** (audit without workflow); the **reservation registry** is a live who-works-on-what; **journal drafts** are per-planner WIP; **md/ttrl-as-canon** means git review already gates *how* models and forms change — "not everyone, everytime" is repo permissions on the canon paths, a documented convention, not new machinery. The fork was only: *how much workflow does v1 add on top?*

## 2. The options

- **α · No workflow — the round IS the version lifecycle.** Round = the interval between version-open and version-lock (both disciplined md changes). Round view = read-only dashboard over reservations + entry records + commit coverage. Approval happens *around* the tool, enforced by the version lock. *Costs:* no in-tool "I'm finished" signal.
- **γ · α + a "done" flag.** One bit per form instance, planner-set (naturally paired with check-in-and-release); the dashboard shows done/not-done coverage; the version owner locks when green. Rejection = a phone call (discipline again).
- **β · Submit/approve states in v1.** Draft → submitted → approved/rejected per slice, approver roles, inbox, rejection reopening. A genuine new subsystem — and approval flows are the most org-idiosyncratic part of every planning tool; designed speculatively they become its ugliest part.
- **δ · Weird: workflow as data.** Statuses in a status cubelet, entered through the same grid/journal; approval = an entry. Dogfoods the platform; puts control-plane state in the data plane — catalogued to mark the boundary.

## RESOLVED 2026-07-10 → α + γ, "the minimal round" (Bora ratified the lean)

- **Round = version lifecycle** (D-v OPEN→LOCKED; opening/locking = disciplined md canon changes).
- **Round dashboard, read-only:** reservations (who holds what) · entry records (what landed) · commit coverage · done flags. No new authority — a view over existing objects.
- **Done flag:** one bit per form instance, planner-declared, pairs with check-in-and-release; the only new state object Q-3 adds.
- **CQ-12 resolved → yes:** free-text note on the entry record, v1.
- **CQ-10 resolved → drafts private in v1:** only commits and the done flag are visible state (P3: proposed ≠ real).
- **Roles** (version open/lock, reservation admin-break): platform admin role + git permissions on canon paths — documented convention, no new machinery.
- **Parked:** submit/approve chains (β) — revisit when a real customer round demands sign-off *inside* the tool rather than around it. **Rejected:** α-pure (missing completeness signal costs one bit to fix); δ (control-plane state in the data plane).

**This closes Q-3 — and with it, workstream C: threads 1 (grid core), 2 (commit/reservations), 3 (forms) and the workflow arc are all converged.**
