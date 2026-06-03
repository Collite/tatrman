# Tasks — review-046 (Section E4 re-review)

Findings in [`review-046.md`](review-046.md). **M1, M2, and the node-position half of H1 are fixed — leave them.** What's left: one verified bug (**G1**, display-mode off-by-one), the test gaps that let it through (**G2**), dead code (**G3**), and the carryover export item (**L2**). E4 is done once G1 and G2 are closed, G3 resolved, and all gates green.

Do exactly what's written.

---

## ✅ Resolution (2026-05-22) — all closed; E4 complete

- **G1** — both Canvas save paths now persist `buildLayout`'s freshly-computed `viewport` (built from the live cy pan/zoom + the `displayMode` prop), not the lagging `currentViewportRef`. The display-mode effect uses the `displayMode` prop directly; the duplicate `displayModeRef`-assign effect was removed; `saveLayout` switched from `viewport: vp ?? undefined` to the `buildLayout` viewport. Display mode now round-trips. Verified with teeth: reintroducing the stale-ref read makes H2.3 fail (`expected undefined to be 'with-types'`).
- **G2** — tests now assert payload **contents**: H2.3 asserts `payload.viewport.displayMode === 'with-types'` (the G1 pin); the H1-regression test asserts the post-toggle `payload.nodes` still equals the dragged `{ 'p.er.entity.Existing': { x:100, y:200 } }` (cy mock now returns a real node); H2.1 adds a restore test — opening a graph whose `getGraph.layout` carries `viewport.displayMode: 'with-types'` lights the "with types" header pill (and `getLayout` is not called).
- **G3** — `packages/designer/src/hooks/useLayoutSync.ts` deleted (was unused after App stopped calling it). Load-path decision recorded in `E4-reducer-layout.md` E4.2: layout comes from `getGraph`'s inline `layout` block; no separate `getLayout` on open.
- **L2** — `E4-reducer-layout.md` E4.5 amended to document the shipped "Export Layout → `layout.json`" behavior, with the rationale (the Designer doesn't own the `.ttrg` text, so a raw-file download would miss unsaved layout).

Gates: designer `typecheck`/`lint`/`test` (128) /`build` ✅ · lsp `typecheck`/`test` (53) ✅ · integration-tests (64 / 1 skip) ✅.

---

## G1 [High] — Persist the *current* display mode, not the stale ref

The bug: `Canvas.tsx:72-82` (the display-mode save effect) writes `viewport: currentViewportRef.current`, but `currentViewportRef` is updated by a later effect (`Canvas.tsx:87`), so on a display-mode change it reads the *previous* viewport. Verified: toggling persists `undefined` then the previous mode.

- [ ] **G1.1** In the display-mode effect (`Canvas.tsx:72-82`), stop using `currentViewportRef.current` for the persisted viewport. Build the viewport from the **live** values. Use `buildLayout`'s returned viewport (it already composes the latest pan/zoom from cy with `displayModeRef.current`), e.g.:
  ```ts
  useEffect(() => {
    displayModeRef.current = displayMode;
    const client = lspClientRef.current;
    const graphUri = projectRootRef.current;
    const cy = cyRef.current;
    if (!client || !graphUri || !cy) return;
    const { nodes, viewport } = buildLayout(cy, currentViewportRef.current, displayMode);
    client.setLayout(graphUri, { version: 1 as const, viewport, nodes, edges: {} }).catch(() => {});
  }, [displayMode]);
  ```
  Key point: pass the `displayMode` **prop** (the new value) into `buildLayout`, and persist `buildLayout`'s `viewport` (which carries that displayMode), not the stale ref. Do **not** destructure away the viewport this time.
- [ ] **G1.2** Remove the now-duplicate `useEffect(() => { displayModeRef.current = displayMode; }, [displayMode]);` at `Canvas.tsx:71` — the save effect already sets it. (Two effects with the same dep both assigning `displayModeRef` is redundant.)
- [ ] **G1.3** Confirm the same correctness for the drag/zoom path: `saveLayout` (`Canvas.tsx:~160`) already uses `buildLayout(cy, vp, displayModeRef.current)` and now persists `buildLayout`'s viewport — verify it sends `viewport` (not a discarded one). If it currently sends `viewport: vp ?? undefined`, switch it to the `buildLayout`-returned `viewport` so pan/zoom **and** displayMode are consistent across all save paths.

### Verify G1

- [ ] **G1.V** Re-run the probe (or the G2.1 test): toggle just-names → with-types → with-constraints and confirm each `setLayout` payload's `viewport.displayMode` equals the **just-selected** mode (no `undefined`, no off-by-one). Manual: open a graph, set "with types", close + reopen → it comes back as "with types".

---

## G2 [Med] — Make the tests assert payload contents (this is what would have caught G1)

In `layout-persistence-v1.1.test.tsx`:

- [ ] **G2.1** Strengthen **H2.3**: after clicking "with types", assert the payload's viewport, not just the URI:
  ```ts
  const payload = h.setLayout.mock.calls.at(-1)?.[1] as any;
  expect(payload.viewport?.displayMode).toBe('with-types');
  ```
  This must fail against current code and pass after G1.
- [ ] **G2.2** Fix the **H2.2 "H1 regression"** test so it verifies node preservation, not call-count. Make the cy mock's `nodes()` return real nodes with positions (e.g. `forEach(cb => cb({ position: () => ({x:100,y:200}), data: () => 'p.er.entity.Existing' }))` shaped to match `buildLayout`), drag, toggle display mode, then assert the post-toggle `setLayout` payload's `nodes` still contains `{ 'p.er.entity.Existing': { x: 100, y: 200 } }`.
- [ ] **G2.3** Fix **H2.1** so it verifies a saved layout **restores**. Make the `getGraph` fixture (`graphFromText`) return a `layout` with known node positions + a viewport (not the empty `{ nodes: {}, edges: {} }`), open the graph, and assert those positions land in state/canvas (e.g. the subsequent save or the rendered node count reflects them). Remove the unused `LAYOUT_WITH_NODES` fixture (or actually drive it through `getLayout` if you keep that path per G3).

---

## G3 [Low] — Delete dead `useLayoutSync` (or wire it back) and record the decision

- [ ] **G3.1** `App.tsx` no longer calls `useLayoutSync`. Delete `packages/designer/src/hooks/useLayoutSync.ts` (and any import) — layout now loads from `getGraph`'s embedded `layout` in `handleSelectGraph`. **OR**, if you intend to keep a separate `getLayout` fetch, re-add the `useLayoutSync(state, dispatch, client)` call in `App.tsx` and remove the `currentGraph.layout` early-return guard so it actually runs.
- [ ] **G3.2** Record the chosen load path in `docs/v1-1/plan/tasks/E4-reducer-layout.md` (E4.2/E4.6): note that the graph's layout is delivered by `getGraph` and `getLayout`/`useLayoutSync` is dropped (or kept), so the doc matches the code.

---

## L2 [Low] — Export Layout intent (carryover from review-045)

- [ ] **L2.1** Either change `App.tsx`'s `onDownloadLayout` (`:263-272`) to download the current `.ttrg` text as-is (E4.5 as written), or amend E4.5 in `E4-reducer-layout.md` to document the shipped "download layout JSON" behavior with a one-line reason.

---

## Done when

- [ ] **G1:** every `setLayout` payload (drag, zoom, display-mode) carries the *current* `viewport.displayMode`; display mode round-trips across close/reopen. No off-by-one, no `undefined` on first toggle.
- [ ] **G2:** the persistence tests assert payload **contents** — the new displayMode value, dragged node positions surviving a toggle, and a saved layout restoring on open. The display-mode assertion fails before G1 and passes after.
- [ ] **G3:** no dead `useLayoutSync`; the load-path decision is recorded in the task doc.
- [ ] **L2:** resolved (implemented or documented).
- [ ] `pnpm --filter @modeler/designer test && pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer lint && pnpm --filter @modeler/designer build && pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test` all green.
