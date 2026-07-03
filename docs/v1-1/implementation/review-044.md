# Review 044 — Section E3 re-review (after `tasks-review-043`)

**Date:** 2026-05-22
**Scope:** re-review of Section 1.1.E.3 after the developer reported the `tasks-review-043` fixes done. Verified against runtime: `pnpm --filter @modeler/designer lint` (clean), `typecheck` (clean), `test` (114 pass, +8), plus a full read of the apply-edit helper, the App wiring, the LSP changes, and the new tests. Companion: [`tasks-review-044.md`](tasks-review-044.md).
**Verdict:** **Not done — close, but one new correctness bug and three test gaps.** Most of `tasks-review-043` landed cleanly: lint is green (F2), the edit is genuinely applied now via a shared, well-tested helper (F1), `packageName` flows through `listSymbols` (F4), the picker filters to addable kinds (F5), the drawer's auto-close is in an effect (F6), `imports` comes from the graph (F7), and the context menu got real click-outside + cursor positioning (F8/F9). **But** the apply path introduced a **stale-cache bug that corrupts the `.ttrg` on the *second* mutation** (G1), and the two tests that were supposed to prevent exactly this — the end-to-end add (F3.1) and the context-menu/remove path (F3.4) — are still missing or vacuous, and the toast remains untested (F3.3).

> Suite green at 114, but green is still misleading: no test drives `handleAddObject`/`handleRemoveNode` through `applyWorkspaceEdit` + refetch, so neither the round-trip nor G1 is covered.

---

## Fixed and verified

- **F1 — the edit is applied for real.** New `packages/designer/src/lsp/apply-workspace-edit.ts` groups `documentChanges` by uri, applies each uri's `TextEdit[]` in **descending start order** (offsets stay valid), and re-opens the patched text. `handleAddObject`/`handleRemoveNode` (`App.tsx:209`,`:227`) now call `applyWorkspaceEdit(client, edit, getText)` then `refetchGraph`, and the dead `applyGraphEdit` stub call is gone. The helper is well unit-tested (`apply-workspace-edit.test.ts`: single edit, the two-edit import+object case, multi-uri, empty). **A single add or remove now works end-to-end.**
- **F2 — lint green.** The unused `GetGraphResponse` import is gone; `eslint src` exits 0.
- **F4 — `packageName` at the source.** `modeler/listSymbols` returns `packageName` (`server.ts:497`), the client type carries it (`lsp-client.ts:32`), and `AddObjectPicker` uses `sym.packageName` directly — the `slice(0,-3)` magic is gone from both the picker and `App`.
- **F5 — picker lists addable kinds only.** `listSymbols({ kinds: [...TOP_LEVEL_KINDS], … })` (`AddObjectPicker.tsx:18-21`); attributes/columns no longer appear.
- **F6 — drawer auto-close is an effect.** No more `onClose()` during render; `MissingObjectsDrawer.tsx:17-23` closes via the same animated `setVisible(false)`+timeout the close button uses.
- **F7 — imports from the graph.** `GetGraphResponse.imports` populated from `result.ast.imports` (`graph-methods.ts:154`); `App` sets `currentImports = state.currentGraph?.imports ?? []`.
- **F8 / F9 — context menu.** A `document` `pointerdown` listener dismisses on true click-outside, and the menu now opens at `evt.renderedPosition()` (the cursor). Both optional — nice to have them.
- **F3.2 / F3.5 — covered.** Out-of-scope + default-on → `autoImport:true` is asserted; the `MissingObjectsDrawer` renders qnames + per-row Remove and fires `onRemove`.

---

## New blocker introduced by the fix

### G1 [High] — Consecutive add/remove corrupts the `.ttrg`; the cache goes stale after the first edit

`App` caches document text in `docTextCache` and reads it via `getText` (`App.tsx:61,71`). The cache is written **only** by `openDoc` (`:73`), which is used on the load paths. But `applyWorkspaceEdit` re-opens the patched text by calling `client.openDocument` **directly** (`apply-workspace-edit.ts:54`) — it never goes through `openDoc`, and `App` ignores its returned `affectedUris`. **So the cache is never updated after a mutation.** It now disagrees with the worker:

1. Open graph → `openDoc` caches `v0`; worker = `v0`.
2. Add object **A** → server builds the edit from the worker's text (`server.ts:453`, `documents.get(uri)` = `v0`). Helper reads `getText` = `v0`, patches to `v1`, `openDocument(v1)`. Worker = `v1`. **Cache still `v0`.** Refetch shows A. ✓
3. Add object **B** → server builds the edit from the worker's **`v1`**, so the edit's ranges are **`v1`-relative** (the objects-block close offset is later now that A is inside). Helper reads `getText` = stale **`v0`** and applies `v1`-relative ranges onto the shorter `v0` → wrong offsets, **corrupted/wrong text** written back. A is lost or the file is mangled.

This isn't a corner case — it breaks **E3.4 directly**: the missing-objects drawer cleans up stale entries "one click each", and each click is a separate `removeObjectFromGraph` against the worker's just-updated text. With ≥2 stale entries, the second Remove operates on stale cache and resurrects/garbles the first removal. Same for any two consecutive adds, or add-then-remove.

**Fix:** keep the cache in sync with what's written. Cleanest options (pick one):
- Have `applyWorkspaceEdit` write back through an injected setter (pass `openDoc` instead of hardcoding `client.openDocument`, or add a `setText(uri, text)` param), **or**
- Drop the cache entirely: add a `modeler/getDocumentText` request to the LSP and have `getText` read the worker's live text each time (no staleness possible).

A test doing two adds in a row (see G2/F3.1) must accompany the fix.

---

## Still-open test gaps from `tasks-review-043`

### G2 [High] — F3.1 (end-to-end add through App) was never written

There is still **no** test that drives `handleAddObject`/`handleRemoveNode` through `applyWorkspaceEdit` + `refetchGraph`. The `canvas-affordances.test.tsx` additions test the picker's `onSelect` and the drawer in isolation — none of them mount `App` or assert that `client.addObjectToGraph` is called and a *changed* graph is stored. This is the test the previous task list explicitly flagged as the one that "would have caught F1" — and it would also have caught **G1** had it performed two operations. It's the highest-value missing test. Write it: mount the add flow with a mocked client whose `addObjectToGraph` returns a realistic edit and whose `getGraph` returns the post-edit graph; assert `addObjectToGraph(uri, qname, true)` was called and the new node is stored. Then extend it to **two consecutive adds** to lock down G1.

### G3 [Med] — F3.4 (context-menu remove) is a vacuous test

`canvas-affordances.test.tsx:206-227` renders a **hand-copied static `<div>`** replicating the menu markup inside the test and clicks its button. It exercises none of the real code — it would pass with `Canvas.tsx` deleted. It does not test Canvas's `cxttap` → `contextMenu` state, nor `App.handleRemoveNode` → `client.removeObjectFromGraph(uri, qname, true)`. Replace it with a test that drives the actual component(s): either capture the `cxttap` handler via the cytoscape mock and assert the menu renders + `onRemoveNode` fires, or (more valuable) assert `handleRemoveNode` calls `removeObjectFromGraph` with `pruneUnusedImport: true`.

### G4 [Low] — F3.3 toast still untested; misleading test name not fixed

The toast on a failed/empty add (`App.tsx:220`) is still unverified, and the test at `canvas-affordances.test.tsx:116` is still named `'…and shows a toast'` while asserting no toast. Either fix the name or (better) add a test that drives `handleAddObject` with `{ documentChanges: [] }` and asserts the toast renders.

---

## Recommendation

One real bug and three test holes stand between this and done. Fix **G1** first — it silently corrupts the user's file on the second edit, which is worse than the original "does nothing" because the failure isn't obvious. Then write **G2** (the end-to-end add, including a two-op sequence that pins G1) and fix **G3** (make the context-menu test exercise real code), and close **G4**. Everything else from `tasks-review-043` is genuinely done and verified. `tasks-review-044.md` lists the exact steps.
