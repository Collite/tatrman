# Tasks — review-040 (Section E1)

Findings in [`review-040.md`](review-040.md). **E1 is approved — done and tested.** There is no E1 rework. The items below are tracked carry-overs so they aren't lost; only N1 is worth touching at the E1/E2 boundary.

---

## N1 [Med] — Don't let "Create New Graph" blank the screen (do at E1/E2 boundary)

- [ ] **N1.1** `App.tsx` has no render branch for `creatingGraph === true` with no graph open, so clicking "+ Create New Graph" in the picker shows a blank screen. Either: (a) render a placeholder/`<CreateWizard>` branch when `state.creatingGraph` is true (this is E2 anyway — do it as E2's first step), or (b) until E2 lands, render the picker with a "coming soon" note instead of blanking. Pick one so the picker never dead-ends.

## Carry into E3/E4 (not E1 — flag in those task lists)

- [ ] **N2 [Med] → E3/E4** — `App.tsx:247-248` hardcodes `activeSchema={'er'}` and a synthetic two-schema `viewports` object passed to `<Canvas>`. Thread the real `state.currentGraph.schema` through so a `schema: db` `.ttrg` renders as db. (E4.1 removes the schema-toggle model; fold this in there.)
- [ ] **N3 [Med] → E4** — `App.tsx:109` discards the `WorkspaceEdit` returned by `client.setLayout(...)`. Since C2's `setLayout` returns an edit (no longer writes a file), layout changes are not persisted. In E4's layout round-trip, apply the returned `WorkspaceEdit` (via the host / in-memory doc update) so node drags + viewport changes survive a reload, and add a round-trip test.

## Optional

- [ ] **N4 [Low]** — If you want strict parity with the E1 tests-first spec, also clear `symbolDetails` in the `openGraph` reducer case. Current behaviour (keep the project-scoped, qname-keyed cache; clear it on `loadProject`) is defensible, so this is optional.

---

## Done when (for the carry-overs)

- [ ] The picker's "Create New Graph" no longer leads to a blank screen (N1).
- [ ] N2/N3 are recorded in the E3/E4 task lists so they're addressed when those sections land.
