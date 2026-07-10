# Review 040 — Section E1 (Designer entry modes: open + browse)

**Date:** 2026-05-21
**Scope:** E1 (`docs/v1-1/plan/tasks/E1-graph-picker.md`) — v1.1 reducer/state, `<GraphPicker>`, project-open → list flow, open-`.ttrg` mode, back affordance, missing-objects badge. Verified against runtime (designer tests + typecheck + code read). Companion: [`tasks-review-040.md`](tasks-review-040.md).
**Verdict:** **Approve — E1 is done.** Every E1 task and DONE-when criterion is implemented and tested; the suite is green (designer **76** tests, up from 61; typecheck clean). A few forward-looking concerns are noted below — none are E1 blockers (they fall in E2/E3/E4 scope), but they should be tracked so they aren't lost.

> Note: this is a UI task. I verified the reducer/component logic via RTL + unit tests and code reading, but did **not** run the Designer in a browser. A manual pass (open a project → pick a graph → open a `.ttrg` → back) is still worth doing before the E-series ships.

---

## Done correctly (verified)

- **E1.1 — reducer/state match contract §11.** `designer-state.ts` has `currentGraphUri`, `availableGraphs`, `currentGraph`, `currentViewport`, `creatingGraph`; per-schema `viewports` is gone (single `currentViewport`). `designer-reducer.ts` implements `loadProject`/`storeGraphList`/`openGraph`/`closeGraph`/`storeGraph`/`loadLayout`/`startCreateWizard`/`cancelCreateWizard`/viewport+node+symbol actions. Covered by `designer-reducer-v1.1.test.ts` (24 cases); the old v1 reducer test was removed cleanly.
- **E1.2 — `<GraphPicker>`** (`components/GraphPicker.tsx`): search input + schema-badge filter (shown only when >1 schema) + list with name/description/tags/schema badge; `onSelect(uri)`. `graph-picker.test.tsx` covers render, click→onSelect(uri), search filter, schema-badge filter, empty state.
- **E1.3 — project-open flow.** `handleFileLoad` opens docs, dispatches `loadProject`, calls `client.listGraphs`, dispatches `storeGraphList`; `showPicker = hasProject && !hasGraph && !creatingGraph` renders the picker, canvas appears only after select.
- **E1.4 — open `.ttrg`.** Header "Open .ttrg…" button → `handleOpenTtrg` (file input restricted to `.ttrg`, opens the doc, selects it).
- **E1.5 — back affordance.** Header `←` button → `closeGraph`; `closeGraph` keeps `availableGraphs` (no re-fetch), confirmed by a reducer test.
- **E1.6 — missing-objects badge.** Header shows `{missingObjectsCount} stale` when a graph is open and the count > 0 (count sourced from `currentGraph.missingObjects`). The clickable side-panel listing is correctly deferred to E3.4.

---

## Forward-looking concerns (not E1 blockers — track for E2/E3/E4)

### N1 [Med] — "Create New Graph" leads to a blank screen until E2

`GraphPicker` renders a "+ Create New Graph" button wired to `startCreateWizard`, which sets `creatingGraph: true`. But `App.tsx`'s render has only three branches (`!hasProject` → landing, `showPicker` → picker, `hasGraph` → canvas) and falls through to `: null` when `creatingGraph` is true with no graph open. So clicking the button **blanks the screen** today. E2 adds the wizard branch, so this closes next — but until then, either render a placeholder for the `creatingGraph` state or hold the button until E2. Worth a one-line guard now.

### N2 [Med] — `activeSchema` is hardcoded to `'er'` in `App.tsx`

`App.tsx:247-248` passes `activeSchema={'er'}` and a synthetic two-schema `viewports` object to `<Canvas>`, regardless of `currentGraph.schema`. A `.ttrg` with `schema: db` will be rendered as if it were `er`. This is a v1 carry-over and squarely E3/E4 territory (E1 explicitly defers schema-toggle removal to E4.1), but flag it: **db-schema graphs will misrender until E3/E4 thread the real `currentGraph.schema` through**.

### N3 [Med] — `setLayout`'s returned `WorkspaceEdit` is discarded (layout save is a no-op)

`App.tsx:109` calls `client.setLayout(...).catch(() => {})` and ignores the result. Since C2 changed `setLayout` to **return** a `WorkspaceEdit` (rather than write a file), this effect no longer persists anything — the edit is never applied to the document. Layout persistence is therefore currently dead. This is E4 (reducer-layout / round-trip) scope, not E1, but it means dragging nodes / changing viewport won't survive a reload until E4 applies the returned edit.

### N4 [Low] — `openGraph` doesn't clear `symbolDetails`

The task tests-first said `openGraph` clears `nodePositions` **and** `symbolDetails`; the reducer clears only `nodePositions`. This is defensible — `symbolDetails` is a qname-keyed, project-scoped cache, so keeping it across graphs in the same project is harmless/beneficial (and `loadProject` does clear it). Noting the spec deviation; no action needed unless you want strict spec parity.

---

## Recommendation

E1 is complete and genuinely well-tested — sign it off. Address **N1** (the blank-screen guard) either now or as the first step of E2 so the picker never dead-ends. Carry **N2** and **N3** into E3/E4 explicitly (real correctness gaps for db graphs and for layout persistence, but out of E1 scope). N4 is optional. `tasks-review-040.md` lists these as tracked carry-overs, not E1 rework.
