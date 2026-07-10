# Tasks — review-043 (Section E3: canvas affordances)

Findings in [`review-043.md`](review-043.md). Work top-to-bottom: **F1** (make add/remove actually do something) and **F3** (test it) are the real blockers; **F2** is a one-line lint fix. F4–F6 are cleanliness; F7–F9 are polish.

Each box is one concrete change. Do exactly what's written — don't infer extra scope.

---

## F1 [High] — Make add/remove apply the edit so the canvas updates

The problem: `handleAddObject` / `handleRemoveNode` call `client.applyGraphEdit(edit)`, which is a dead v1 stub (`server.ts:448`, returns `{ ok:false }` and applies nothing). The returned `WorkspaceEdit` is a set of **text edits** against the existing `.ttrg`, but nothing applies them, so `refetchGraph` reloads the unchanged document. You must apply the edit to the in-memory worker document yourself, then re-open it.

### F1.1 — Add a shared "apply WorkspaceEdit to worker docs" helper

- [ ] **F1.1.1** Create `packages/designer/src/lsp/apply-workspace-edit.ts`. Export one async function:
  ```ts
  import type { WorkspaceEdit } from 'vscode-languageserver-types';
  import type { LspClient } from '../lsp-client';

  /**
   * Applies a WorkspaceEdit to the LSP worker's in-memory documents.
   * Handles `documentChanges` of two shapes:
   *   - TextDocumentEdit  ({ textDocument:{uri}, edits:[{range,newText}] })  → patch existing content
   *   - CreateFile        ({ kind:'create', uri })                          → start from '' then apply later text edits
   * For each affected uri, fetches the current text (pass it in via `getText`),
   * applies the edits in reverse document order, and re-opens the patched text.
   */
  export async function applyWorkspaceEdit(
    client: LspClient,
    edit: WorkspaceEdit,
    getText: (uri: string) => string | undefined,
  ): Promise<string[]>; // returns the list of uris that were re-opened
  ```
- [ ] **F1.1.2** Implement it:
  - Group `edit.documentChanges` by `uri` (a `TextDocumentEdit` has `textDocument.uri`; a `CreateFile` has `.uri` + `kind === 'create'`).
  - For each uri: start from `getText(uri)` (or `''` if there was a `create` op / no existing text).
  - Apply that uri's `TextEdit[]` to the string. **Sort edits by start position descending** before applying (so earlier offsets don't shift). Convert each `range` (line/char) to a string offset against the current content.
  - `await client.openDocument(uri, patchedText)` for each affected uri.
  - Return the affected uris.
- [ ] **F1.1.3** This must correctly handle the add-import case: `buildAddObjectEdit` can return **two** text edits on the same `.ttrg` (one inserting `import …`, one inserting the qname into `objects { }`). Verify your reverse-order application produces the right combined text (write the unit test in F1.4 first if helpful).

### F1.2 — Route add/remove through the helper; delete the `applyGraphEdit` call

- [ ] **F1.2.1** In `App.tsx`, the component needs the current text of the graph doc. The worker holds it, not React state. Add a tiny accessor to the client: in `lsp-client.ts`, the worker connection doesn't expose document text, so instead **keep a local cache**. Simplest correct approach: maintain a `Map<string,string>` of opened doc text in `App` (or a ref) that you update every time you call `client.openDocument(uri, content)` (in `handleFileLoad`, the demo loader effect, `handleOpenTtrg`, and the new helper). Pass its `.get` as the `getText` argument.
  > If you prefer not to cache: add a `getDocumentText(uri)` request to the LSP (`server.ts`) that returns `documents.get(uri)?.getText() ?? null`, expose it on `LspClient`, and use that as `getText`. Either is acceptable — pick one and be consistent.
- [ ] **F1.2.2** Rewrite `handleAddObject` (`App.tsx:211`):
  ```ts
  const edit = await client.addObjectToGraph(uri, qname, autoImport);
  if (edit?.documentChanges?.length) {
    await applyWorkspaceEdit(client, edit, getText);
    await refetchGraph(uri);
  } else {
    addToast(`Couldn't add ${qname} — out of scope and auto-import is off.`);
  }
  ```
  (The `else` covers the LSP returning an empty edit — e.g. out-of-scope with `autoImport:false`.)
- [ ] **F1.2.3** Rewrite `handleRemoveNode` (`App.tsx:227`) the same way: apply via `applyWorkspaceEdit`, then `refetchGraph(uri)`; toast on empty/failed.
- [ ] **F1.2.4** Delete the `client.applyGraphEdit(edit)` calls from both handlers. Leave the `applyGraphEdit` method on the client/server as-is (it's an unrelated v1 stub) — just stop using it here.

### F1.3 — Manually confirm the round-trip

- [ ] **F1.3.1** `pnpm --filter @modeler/designer dev`, open the demo, open a graph, click **+ Add object**, pick an in-scope object → it appears on the canvas. Right-click a node → **Remove from graph** → it disappears. Pick an out-of-scope object with auto-import **on** → it appears and an `import` was added. (If you can't run the browser easily, the F1.4/F3 tests must stand in for this — but at least one of the two must actually exercise a *changed* graph.)

### F1.4 — Unit-test the helper

- [ ] **F1.4.1** New `packages/designer/src/lsp/__tests__/apply-workspace-edit.test.ts`. Given a starting `.ttrg` string and the two-edit `WorkspaceEdit` shape `buildAddObjectEdit` returns (import insert + objects insert), assert the patched text contains both the new `import` line and the new qname inside `objects { }`, in the right places. Add a remove case too.

---

## F3 [High] — Write the missing Tests-first cases

These are the four behaviors the task's "Tests-first" section requires that are currently untested. Put them in `packages/designer/src/__tests__/canvas-affordances.test.tsx` (extend it; keep the existing `<AddObjectPicker/>` tests). Mock `LspClient` methods with `vi.fn()` as the existing tests do.

- [ ] **F3.1 — Add-object end-to-end through App.** Render `<App/>` (or a thin harness that mounts the picker + `handleAddObject`). With a mocked client whose `addObjectToGraph` resolves to a realistic `documentChanges`, `applyWorkspaceEdit` path, and `getGraph` (the refetch) resolving to a graph that **now contains the new node**: open the picker, select an object, and assert (a) `client.addObjectToGraph` was called with `(uri, qname, true)`, and (b) a `storeGraph` happened with the new node present (assert the new node renders, or spy the dispatch). This is the test that would have caught F1 — make sure it fails if the apply step is removed.
- [ ] **F3.2 — Out-of-scope, auto-import default on → `autoImport: true`.** In the picker, with `currentImports={['billing.invoicing']}`, click the out-of-scope `Product` **without** toggling anything; assert `onSelect('billing.products.er.entity.Product', true)`.
- [ ] **F3.3 — Toast on failed/empty add.** Drive `handleAddObject` with a mocked `addObjectToGraph` that resolves to `{ documentChanges: [] }` (the out-of-scope + auto-import-off case); assert a toast with the failure copy is rendered (`screen.getByText(/out of scope/i)` or similar). Rename the existing test `'…and shows a toast'` or fold it in — right now that name lies (it asserts no toast).
- [ ] **F3.4 — Context menu remove.** Test the context-menu remove path. Cytoscape is mocked to a no-op, so test the menu behavior directly: render `<Canvas/>` with a stub graph and an `onRemoveNode` spy, simulate the menu being shown (either factor the menu into a tiny testable piece, or set the `contextMenu` state via a forced `cxttap` through the cytoscape mock's `on` capture), click **Remove from graph**, and assert `onRemoveNode(qname)` fired. Then assert `App`'s `handleRemoveNode` calls `client.removeObjectFromGraph(uri, qname, true)`.
- [ ] **F3.5 — Missing-objects drawer.** New test for `<MissingObjectsDrawer/>`: given `missingObjects={['a.b.er.entity.Gone','a.b.er.entity.AlsoGone']}`, assert both qnames render with a "Remove" button each, clicking one calls `onRemove(qname)`, and (after F6) that an empty list closes the drawer without erroring.

---

## F2 [Med] — Fix the lint failure

- [ ] **F2.1** In `packages/designer/src/MissingObjectsDrawer.tsx:2`, delete the unused import `import type { GetGraphResponse } from '@modeler/lsp';`.
- [ ] **F2.2** Run `pnpm --filter @modeler/designer lint` → must exit 0.

---

## F4 [Med] — Kill the duplicated `slice(0, -3)` package extraction

- [ ] **F4.1** In `packages/lsp/src/server.ts:491` (`modeler/listSymbols` handler), add `packageName` to each returned object: `.map((s) => ({ qname: s.qname, kind: s.kind, name: s.name, packageName: s.packageName ?? null }))`.
- [ ] **F4.2** Update the `listSymbols` return type in `packages/designer/src/lsp-client.ts:32` (and the implementation at `:99`) to `Array<{ qname: string; kind: string; name: string; packageName: string | null }>`.
- [ ] **F4.3** In `AddObjectPicker.tsx`: delete `getPackage` (`:11`). Use the symbol's `packageName` directly in `isOutOfScope` — `if (!sym.packageName) return false;` then compare against `currentImports`.
- [ ] **F4.4** In `App.tsx:69`, the `currentImports` computation must stop using `slice(0,-3)`. See F7 for the preferred fix (declared imports). If you defer F7, derive packages from the graph nodes' `packageName` instead — but that requires nodes to carry `packageName`; if they don't, do F7 now rather than reintroducing the `slice`.

---

## F5 [Med] — Picker lists only addable top-level objects

- [ ] **F5.1** Decide the addable kind set (top-level objects only — e.g. `entity`, `table`, `view`, plus any other top-level def kinds; **not** `attribute`/`column`). Confirm the list against `packages/semantics/src/symbol-table.ts` child kinds.
- [ ] **F5.2** Pass it through `listSymbols`: call `client.listSymbols({ kinds: [...addableKinds], limit: 1000 })` in `AddObjectPicker.tsx:25`. The server already filters by `kinds` (`server.ts:493`). No client-side child filtering needed.
- [ ] **F5.3** Add a picker test asserting a `column`/`attribute` symbol returned by the mock is **not** listed (or that `kinds` was passed).

---

## F6 [Med] — Move the drawer's auto-close out of render

- [ ] **F6.1** In `MissingObjectsDrawer.tsx`, remove the `if (missingObjects.length === 0) { onClose(); return null; }` block (`:25`).
- [ ] **F6.2** Replace it with an effect that runs the same animated close the Close button uses:
  ```tsx
  useEffect(() => {
    if (missingObjects.length === 0) {
      setVisible(false);
      const t = setTimeout(onClose, 300);
      return () => clearTimeout(t);
    }
  }, [missingObjects.length, onClose]);
  ```
  Keep rendering the (now-empty) drawer until the effect closes it, or guard the body so it doesn't flash an empty list — your call, but no `onClose()` during render.

---

## F7 [Low] — Use the graph's declared imports, not node-derived ones

- [ ] **F7.1** Add `imports: string[]` to `GetGraphResponse` (`packages/lsp/src/graph-methods.ts:15`) and populate it from the `.ttrg`'s parsed `imports` block where the graph is built (`graph-methods.ts` ~`:153`).
- [ ] **F7.2** In `App.tsx`, set `currentImports = state.currentGraph?.imports ?? []` and delete the node-mapping `slice(0,-3)` block (`:69`).
- [ ] **F7.3** Add an LSP/integration test (in `tests/integration/`, per CLAUDE.md) that `getGraph` on a `.ttrg` with `import a.b` returns `imports` containing `a.b`.

> If you do F7, F4.4 is satisfied automatically.

---

## F8 [Low] — Real click-outside dismiss for the context menu

- [ ] **F8.1** In `Canvas.tsx`, when `contextMenu.visible`, add a `document` `pointerdown` listener (in a `useEffect` keyed on `contextMenu.visible`) that hides the menu unless the click is inside the menu element; remove it on cleanup. Optionally also close on `Escape`.

## F9 [Low] — Open the menu at the cursor

- [ ] **F9.1** In `Canvas.tsx:169`, use `evt.renderedPosition` (the click point) instead of `evt.target.renderedPosition()` (the node center) for the menu `x`/`y`.

---

## Done when

- [ ] **F1:** adding an object (in-scope and out-of-scope+auto-import) makes it appear on the canvas, and remove makes it disappear, at runtime — verified by F1.3 manually and by the F3.1/F3.4 tests (which fail if the apply step is removed). The `applyGraphEdit` stub is no longer called by the affordances.
- [ ] **F3:** all five Tests-first behaviors are covered: add-through-App, out-of-scope-default-on, toast-on-failure, context-menu-remove, missing-objects-drawer.
- [ ] **F2:** `pnpm --filter @modeler/designer lint` exits 0.
- [ ] **F4 + F5 + F6** done; no `slice(0,-3)` remains in the designer; picker lists only addable kinds; no setState-during-render in the drawer.
- [ ] `pnpm --filter @modeler/designer test && pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer lint` all green, and (if F4.1/F7 touched the LSP) `pnpm --filter @modeler/lsp test` + `pnpm --filter @modeler/integration-tests test` green.
- [ ] F7–F9 either done or explicitly left as follow-ups (they're Low).
