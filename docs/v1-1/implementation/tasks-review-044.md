# Tasks — review-044 (Section E3 re-review)

Findings in [`review-044.md`](review-044.md). **Most of `tasks-review-043` is done and verified — leave F1 wiring, F2, F4, F5, F6, F7, F8, F9 alone.** What remains: one new correctness bug (**G1**) and three test gaps (**G2, G3, G4**). E3 is done once these four are closed and all gates are green.

Do exactly what's written.

---

## ✅ Resolution (2026-05-22) — all four closed

- **G1 (Option A)** — `applyWorkspaceEdit` now takes an injected `openDoc(uri, content)` and writes the patched text through it instead of `client.openDocument` directly (`apply-workspace-edit.ts`). `App.tsx` passes its cache-updating `openDoc` at both call sites, so `docTextCache` and the worker no longer diverge. Verified with teeth: temporarily restoring the bypass makes the two-add test fail; with the fix it passes.
- **G2** — `affordances-integration.test.tsx` mounts `App`, drives load-project → open-graph → add, and asserts `addObjectToGraph(uri, qname, true)` + the document gains the object + a refetch. A second test does **two consecutive adds** against a shared worker store (mock `addObjectToGraph` builds its edit from the current document, as the server does) and asserts both survive and braces stay balanced — this is the G1 regression guard.
- **G3** — the vacuous static-`<div>` test is deleted. Replaced by (a) an App-level test driving the missing-objects drawer → `removeObjectFromGraph(uri, qname, true)`, and (b) a Canvas-level test that captures the real `cxttap` handler, fires it, and asserts the "Remove from graph" menu item calls `onRemoveNode`.
- **G4** — App-level test drives an out-of-scope add with auto-import off (`addObjectToGraph` returns no edit) and asserts the toast renders; the misleading picker test name no longer claims "shows a toast".
- A focused helper unit test (`apply-workspace-edit.test.ts`) also pins the write-through-`openDoc` contract directly.

Gates: `pnpm --filter @modeler/designer lint` ✅ · `typecheck` ✅ · `test` ✅ **119 passed (18 files)**. The boxes below are left as authored for the record.

---

## G1 [High] — Stop the second mutation from corrupting the file (sync the doc cache)

The bug: `applyWorkspaceEdit` re-opens the patched text via `client.openDocument` directly (`apply-workspace-edit.ts:54`), but `App`'s `docTextCache` is only updated by `openDoc` on load paths. After the first add/remove the cache is stale, while the worker (and thus the next server-built edit) is one revision ahead — so the second edit's ranges are applied to the wrong text. Pick **one** of the two fixes below.

### Option A (recommended) — write back through the cache

- [ ] **G1.A.1** Change `applyWorkspaceEdit`'s signature so it can update the caller's cache. Replace the bare `client.openDocument` call with an injected writer:
  ```ts
  export async function applyWorkspaceEdit(
    client: LspClient,
    edit: WorkspaceEdit,
    getText: (uri: string) => string | undefined,
    openDoc: (uri: string, content: string) => Promise<void>,  // NEW
  ): Promise<string[]>
  ```
  and at `apply-workspace-edit.ts:54` call `await openDoc(uri, patched);` instead of `await client.openDocument(uri, patched);`.
- [ ] **G1.A.2** In `App.tsx`, pass the existing `openDoc` (which sets `docTextCache` then calls `client.openDocument`) at both call sites (`:217`, `:234`): `await applyWorkspaceEdit(client, edit, getText, openDoc);`.
- [ ] **G1.A.3** Update `apply-workspace-edit.test.ts`: the existing tests pass a bare client; add the new `openDoc` arg (a `vi.fn()` that resolves) and assert it's called with the patched text. Keep the reverse-order test.

### Option B — drop the cache, read the worker live

- [ ] **G1.B.1** Add a `modeler/getDocumentText` request in `packages/lsp/src/server.ts`: `connection.onRequest('modeler/getDocumentText', (p: { uri: string }) => documents.get(p.uri)?.getText() ?? null);`
- [ ] **G1.B.2** Expose `getDocumentText(uri): Promise<string | null>` on `LspClient` (`lsp-client.ts`).
- [ ] **G1.B.3** In `App.tsx`, delete `docTextCache`, `getText`, and the cache-write in `openDoc` (keep `openDoc` as a thin `client.openDocument` wrapper or inline it). Make `applyWorkspaceEdit`'s `getText` async, fed by `client.getDocumentText`, or fetch the text in `App` and pass it in. Ensure the helper reads the worker's current text for **every** uri it patches.

### Verify G1 (either option)

- [ ] **G1.V** Manually (or via the G2 two-op test): open a graph, add object A, then add object B. The `.ttrg` must end up containing **both** A and B, well-formed (no merged/garbled lines). Then remove A and remove B and confirm both are gone and the file still parses.

---

## G2 [High] — Write the end-to-end add test (F3.1), including two consecutive adds

This is the test that pins both the round-trip and G1. Put it in `packages/designer/src/__tests__/canvas-affordances.test.tsx`.

- [ ] **G2.1** Add a test that exercises the **App add flow**, not just the picker. Either render `<App/>` with a fully-mocked `createLspClient`, or extract `handleAddObject` into a testable unit. The mocked client must provide: `listSymbols`, `addObjectToGraph` (resolves to a realistic `documentChanges` with a `TextDocumentEdit` inserting the qname), `openDocument`, and `getGraph` (the refetch — return a graph whose `nodes` now include the added qname).
- [ ] **G2.2** Assert: (a) `client.addObjectToGraph` was called with `(currentGraphUri, qname, true)`; (b) after the flow, a `storeGraph` happened carrying the new node (assert via the rendered canvas/inspector, or by spying `dispatch`). The test must **fail if the `applyWorkspaceEdit` step is removed**.
- [ ] **G2.3** Add a **two-add** assertion to lock down G1: drive add A then add B. Make the mocked `addObjectToGraph` build its edit against the *current* doc text (read through the same `getText`/worker the helper uses) so the test reproduces the real server behavior, and assert the final document (captured from the last `openDocument` call) contains **both** A and B. With G1 unfixed this must fail; with G1 fixed it must pass.

---

## G3 [Med] — Make the context-menu/remove test exercise real code (replace F3.4)

- [ ] **G3.1** Delete the vacuous test at `canvas-affordances.test.tsx:206-227` (the hand-copied `<div data-context-menu>`).
- [ ] **G3.2** Replace it with a test of the real path. Two acceptable forms (do at least the first):
  - **App-level:** drive `handleRemoveNode('some.qname')` (via the same harness as G2) and assert `client.removeObjectFromGraph` was called with `('…uri…', 'some.qname', true)` and that a refetch/`storeGraph` followed.
  - **Canvas-level (optional, stronger):** in the existing `cytoscape` mock, capture the handler registered for `cy.on('cxttap', 'node', …)`, invoke it with a fake event (`{ target: { data: () => ({ qname }) }, renderedPosition: () => ({x,y}) }`), then assert the "Remove from graph" button renders and clicking it calls the `onRemoveNode` prop.

---

## G4 [Low] — Cover the failure toast and fix the lying test name

- [ ] **G4.1** Add a test that drives `handleAddObject` with a mocked `addObjectToGraph` resolving to `{ documentChanges: [] }` (out-of-scope + auto-import off) and asserts the toast text renders (`screen.getByText(/out of scope/i)` or the exact copy from `App.tsx:220`).
- [ ] **G4.2** Rename the picker test at `canvas-affordances.test.tsx:116` so it no longer claims "…and shows a toast" (the toast is App-level, not picker-level). Name it for what it asserts, e.g. `'out-of-scope object with auto-import off calls onSelect with autoImport=false'`.

---

## Done when

- [ ] **G1:** two consecutive adds (and two consecutive removes) leave the `.ttrg` correct and parseable — the cache/worker no longer diverge. `applyWorkspaceEdit` no longer writes via a path that bypasses the cache.
- [ ] **G2:** an App-level add test asserts `addObjectToGraph(uri, qname, true)` + a changed `storeGraph`, and a two-add test pins G1 (fails without the G1 fix).
- [ ] **G3:** the context-menu test exercises real Canvas/App code (the static-div copy is gone) and asserts `removeObjectFromGraph(uri, qname, true)`.
- [ ] **G4:** the failure toast is tested and the misnamed test is renamed.
- [ ] `pnpm --filter @modeler/designer lint && pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer test` all green, plus `pnpm --filter @modeler/lsp test` if you took G1 Option B.
