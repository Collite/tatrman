# Tasks — review-045 (Section E4)

Findings in [`review-045.md`](review-045.md). Work top-to-bottom: **H1** (layout data-loss) and **H2** (tests cover dead code) are the blockers; **M1/M2** finalize the persistence path the way E4 intended; **L1/L2** are clarifications. E4 is done once H1, H2, M1, M2 are closed and all gates are green.

Do exactly what's written.

---

## H1 + M2 [High/Med] — One source of truth for layout; stop writing stale positions

The bug: `App.tsx:124-137` persists `nodes: state.nodePositions` on display-mode change, but `state.nodePositions` is only set by `loadLayout` — nothing dispatches `setNodePosition`, so after a drag (which `Canvas.saveLayout` persists straight from cy) the App effect overwrites the `.ttrg` with the stale, pre-drag positions. There are also **two** writers (`App.tsx` effect + `Canvas.saveLayout`) with divergent payloads.

Pick **one** approach below and implement it fully (don't leave both paths live).

### Approach A (recommended) — Canvas owns all persistence; remove the App effect

- [ ] **H1.A.1** In `Canvas.tsx`, add a save when the display mode changes. There is already a `useEffect` keyed on `[displayMode]` (the label-refresh effect, ~`:227`). After it refreshes labels, call the existing `saveLayout()` (or the debounced version) so display-mode changes are persisted from live cy state — same payload as drag/zoom saves.
- [ ] **H1.A.2** Delete the `App.tsx:124-137` viewport-save `useEffect` entirely (and the now-unused `prevViewportRef`). All persistence now flows through `Canvas.saveLayout`, which reads live cy positions + viewport — never stale state.
- [ ] **H1.A.3** Verify no other code depends on that effect. `state.currentViewport.displayMode` is still updated by `setDisplayMode` (Header) and consumed by Canvas via the `displayMode` prop — that stays.

### Approach B — React state is authoritative; Canvas dispatches, one effect persists

- [ ] **H1.B.1** In `Canvas.tsx`, on `dragfreeon` dispatch `setNodePosition` for each moved node, and on `viewport` dispatch `setViewport({ zoom, panX, panY })`, instead of (or in addition to) calling `setLayout` directly. (Canvas currently has no `dispatch`; thread an `onNodePositionsChange` / `onViewportChange` callback prop from `App` rather than passing `dispatch` in.)
- [ ] **H1.B.2** Make the single `App.tsx` effect the only writer: it already keys on `[state.currentViewport, state.currentGraphUri, state.nodePositions]`, so once those are kept current it will persist correct data. Remove the direct `setLayout` calls from `Canvas.saveLayout`.
- [ ] **H1.B.3** Drop the `prev.displayMode/prev.zoom` guard's narrowness — persist on any of nodePositions/viewport/displayMode change (debounce in the effect to avoid chatty writes).

### Verify H1 (either approach)

- [ ] **H1.V** Manual smoke (or the H2.2 test): open a graph, drag a node, toggle display mode, close + reopen → the dragged position is retained (not reverted to the pre-drag position).

---

## H2 [High] — Real persistence tests; remove or wire up the dead actions

The new `layout-persistence-v1.1.test.ts` only tests reducer transitions, two of which (`setNodePosition`, `setViewport`) are for actions the app never dispatches. Replace the false-confidence tests with ones that drive the wire path. (Pattern: the App-mount + mocked-client harness in `affordances-integration.test.tsx`.)

- [ ] **H2.1 — getLayout/getGraph load on open.** Add a test that mounts `App`, opens a graph whose `getGraph`/`getLayout` mock returns a layout with known node positions + viewport, and asserts `loadLayout` ran (node positions land in the rendered canvas state, or spy `setLayout`/dispatch). If you keep `useLayoutSync` (see L1), assert `client.getLayout` is called with the `graphUri`.
- [ ] **H2.2 — Drag → setLayout(graphUri, payload), and the H1 regression.** Drive a node drag (capture the cytoscape `dragfreeon` handler via the mock, as the context-menu test captures `cxttap`) and assert `client.setLayout` is called with `(graphUri, payload)` where `payload.nodes` contains the new position. Then toggle display mode and assert the **next** `setLayout` still carries the dragged position (not the pre-drag one). This is the H1 guard — it must fail against today's code.
- [ ] **H2.3 — Display-mode change persists per-graph.** Assert that changing display mode results in a `setLayout(graphUri, …)` whose viewport carries the new `displayMode` — to the current graph's URI, not a project-wide store.
- [ ] **H2.4 — Remove the dead-action tests.** Delete (or repurpose) the `setNodePosition`/`setViewport` reducer cases in `layout-persistence-v1.1.test.ts` **unless** Approach B is taken (in which case those actions become live and the tests are valid — keep them and add the dispatch-from-Canvas coverage in H2.2). The reducer's exhaustive action coverage already lives in `designer-reducer-v1.1.test.ts`; don't duplicate.

---

## M1 [Med] — Flatten `LayoutFile` to a single `viewport`

Remove the schema-keyed `viewports` map and the fabricated `db` entries (§11.1: no per-schema viewports; DONE: no `.ttrl`-shaped state).

- [ ] **M1.1** In `packages/lsp/src/model-graph.ts:144`, change `LayoutFile` from `viewports: Record<RenderableSchemaCode, ViewportState>` to `viewport?: ViewportState` (mirroring `GraphLayoutOutput`).
- [ ] **M1.2** In the `modeler/setLayout` handler (`packages/lsp/src/server.ts:425-432`), read `_params.layout.viewport` directly instead of indexing `_params.layout.viewports[schemaKey]`.
- [ ] **M1.3** Update both Designer callers to send a single `viewport`:
  - `Canvas.tsx:154` → `{ version: 1, viewport: vp ?? undefined, nodes, edges: {} }` (drop the `{ er, db }` map).
  - `App.tsx:130-135` → same (or remove entirely if you took H1 Approach A).
- [ ] **M1.4** `pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test` green (these exercise `setLayout`); fix any fixtures that assumed the `viewports` map.

---

## L1 [Low] — Decide the layout-load path

- [ ] **L1.1** Either (a) **drop** the redundant `getLayout`/`useLayoutSync` path and rely on `getGraph`'s embedded `layout` (then delete `useLayoutSync` and its call in `App.tsx`, and note in `E4-reducer-layout.md` that `getGraph` carries the layout), or (b) **implement** the E4.6 parallel fetch (`getGraph` + `getLayout` together on select) and remove the `currentGraph.layout.nodes` early-return guard in `useLayoutSync.ts:18`. Don't leave both half-wired.

## L2 [Low] — Export Layout intent

- [ ] **L2.1** Either change `App.tsx`'s `onDownloadLayout` to download the current `.ttrg` text as-is (E4.5 as written), or amend E4.5 in `E4-reducer-layout.md` to document the shipped "download layout JSON" behavior with a one-line reason.

---

## Done when

- [ ] **H1:** dragging then toggling display mode no longer reverts node positions (verified by the H2.2 regression test + a manual reopen). Only one layout writer remains.
- [ ] **H2:** tests drive the real `getLayout(graphUri)` load and `setLayout(graphUri, payload)` save paths; no test asserts behavior of an action the app never dispatches.
- [ ] **M1:** `LayoutFile` carries a single `viewport`; no schema-keyed `viewports` map or fabricated `db` entry anywhere in the Designer.
- [ ] **M2:** one source of truth for layout (state-authoritative or Canvas-owned), not two divergent writers.
- [ ] L1 and L2 each resolved (implemented or documented).
- [ ] `pnpm --filter @modeler/designer test && pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer lint && pnpm --filter @modeler/designer build && pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test` all green.
