# Review 043 — Section E3 (Canvas affordances: add / remove / extend imports)

**Date:** 2026-05-22
**Scope:** first review of Section 1.1.E.3 against [`E3-canvas-affordances.md`](../plan/tasks/E3-canvas-affordances.md). Verified against runtime: `pnpm --filter @modeler/designer test` (106 pass), `typecheck` (clean), `lint` (**fails**), plus a full read of the new/changed designer files and the LSP edit path. Companion: [`tasks-review-043.md`](tasks-review-043.md).
**Verdict:** **Not done.** The UI shell is built and looks right, but the central affordance is non-functional: **add/remove never apply the edit**, so the canvas never changes (E3.6 + the DONE criteria are unmet). The required "Tests-first" set is **mostly absent** — 4 of the 5 specified behaviors are untested and the one test file tests none of the *canvas* affordances. Lint is also red. Two High blockers (F1 apply-edit round-trip, F3 tests), one red gate (F2 lint), plus cleanliness items.

> Suite: designer **106** tests pass, typecheck clean — but `lint` errors (F2), and the green suite is misleading because it exercises almost none of E3 (F3).

---

## High — blockers

### F1 [High] — Add/remove build an edit but never apply it; the round-trip is broken

`handleAddObject` (`App.tsx:211`) and `handleRemoveNode` (`App.tsx:227`) do:

```ts
const edit = await client.addObjectToGraph(uri, qname, autoImport);
if (edit) {
  await client.applyGraphEdit(edit);   // ← no-op stub
  await refetchGraph(uri);             // ← refetches the UNCHANGED document
}
```

`client.applyGraphEdit` routes to `modeler/applyGraphEdit`, which is the **v1 dead stub** (`packages/lsp/src/server.ts:448`):

```ts
connection.onRequest('modeler/applyGraphEdit', (_params) => {
  return { ok: false, reason: 'edit-mode-not-available-in-v1' };
});
```

It applies nothing and the return value (`{ ok:false }`) is ignored. The server's `addObjectToGraph`/`removeObjectFromGraph` handlers (`server.ts:452`, `:471`) only *read* `documents` and return a `WorkspaceEdit` of **text-modify edits** against the existing `.ttrg` (`buildAddObjectEdit` → `documentChanges: TextDocumentEdit[]`, `packages/edit/src/graph-edits.ts:90`); they never mutate the in-memory document store. So after the call, the worker's document is byte-for-byte the same, and the immediately-following `refetchGraph(uri)` returns the same graph. **Nothing the user adds or removes ever appears on the canvas.**

This directly violates:
- **E3.6** — "After `addObjectToGraph` / `removeObjectFromGraph` succeeds **and the host applies the `WorkspaceEdit`**, immediately call `client.getGraph(uri)` again." The host-applies step is missing.
- **DONE when** — "Add-object … works for in-scope and (with auto-import) out-of-scope qnames" and "Right-click on a node removes it." Neither works at runtime.

The Designer runs **only** the browser-worker transport (`createLspClient`, `lsp-client.ts:37` — `BrowserMessageReader` over `@modeler/lsp/browser?worker`). There is no VS Code host behind it to honor `workspace/applyEdit`, so the Designer must apply the edit itself. Note this is *not* even calling the standard `workspace/applyEdit` — it calls the custom `modeler/applyGraphEdit` stub, which would be wrong in any host.

**The pattern already exists one section over.** E2's `CreateGraphWizard` applies its edit by reading `documentChanges` and re-opening the document (`CreateGraphWizard.tsx:495–510`: find the op, take `newText`, `await lspClient.openDocument(createOp.uri, newText)`). E3 should do the analogous thing — but for **text-modify** edits (apply each `TextEdit` range to the current content, then `openDocument` the patched text). The wizard's create-only shortcut won't work here, so this needs a small, *shared* "apply a `WorkspaceEdit` to the worker's in-memory docs" helper that both flows use. See tasks F1.1–F1.3.

### F3 [High] — The required "Tests-first" set is mostly missing; the one file tests no canvas affordance

The task lists five Tests-first cases. Delivered: `packages/designer/src/__tests__/canvas-affordances.test.tsx` — **7 tests, all against `<AddObjectPicker />` in isolation.** Coverage against the spec:

| Required case (task §Tests-first) | Covered? |
|---|---|
| Toolbar "Add object" → picker → `client.addObjectToGraph(uri, qname, autoImport)` → `storeGraph` on success | ❌ none — App wiring (`handleAddObject` + refetch) is untested |
| Out-of-scope object, auto-import **on** by default → call has `autoImport: true` | ⚠️ partial — picker emits `onSelect(qname, true)` for *in-scope*; the out-of-scope+default-on path is not asserted |
| Toggle auto-import **off**, out-of-scope → `autoImport: false` **and** a non-modal toast | ⚠️ `onSelect(…, false)` asserted; **toast never asserted** — the test is even named "…and shows a toast" but checks no toast |
| Right-click node → context menu "Remove from graph" → `removeObjectFromGraph(uri, qname, true)` | ❌ none — Canvas context menu entirely untested |
| Missing-objects badge clickable → side panel lists stale qnames with per-row "Remove" | ❌ none — `MissingObjectsDrawer` has zero tests |

So **4 of 5** behaviors are unverified, including both mutation paths and the toast. The filename `canvas-affordances` is a misnomer — it covers none of the canvas affordances (context menu, drawer, toolbar wiring). With F1 unfixed, none of these would pass anyway, which is precisely why they're needed: a test that drives `handleAddObject` and asserts a *changed* `storeGraph` would have caught F1 immediately.

---

## Red gate

### F2 [Med, blocks `lint`] — Unused import fails ESLint

`packages/designer/src/MissingObjectsDrawer.tsx:2` imports `GetGraphResponse` from `@modeler/lsp` and never uses it:

```
src/MissingObjectsDrawer.tsx
  2:15  error  'GetGraphResponse' is defined but never used  @typescript-eslint/no-unused-vars
✖ 1 problem (1 error, 0 warnings)
```

`pnpm --filter @modeler/designer lint` exits non-zero. The task's "Verify by running" lists only `test` + `typecheck`, but lint is part of the repo's standard gate (CLAUDE.md: "ESLint forbids `any` outside `generated/**`" and the `pnpm -r lint` workflow). Trivial fix, but the section can't be signed off red.

---

## Medium — architecture / cleanliness

### F4 [Med] — Fragile, duplicated, undocumented package extraction (`slice(0, -3)`)

The "which package is this qname in?" logic is hand-rolled string surgery, **duplicated** in two places:

- `AddObjectPicker.tsx:11` — `getPackage`: `qname.split('.').slice(0, -3).join('.')`
- `App.tsx:69` — `currentImports`: the same `slice(0, -3)` inline.

It works for the common shape only by coincidence. Real qnames are built as `[packageName] [cnc?] schemaCode (namespace|kind) name` (`packages/semantics/src/symbol-table.ts:40–62`), so a top-level def is `pkg.schema.kind.Name` and dropping the last 3 segments recovers `pkg`. But:
- **Child symbols break it.** Attributes/columns get an extra two segments (`pkg.schema.kind.Parent.child`, `makeQnameChild` at `symbol-table.ts:50`), so `slice(0,-3)` yields `pkg.schema` — a wrong "package."
- The magic `-3` has no comment and silently encodes the qname grammar in the UI layer, which will rot the next time qname construction changes.

Root cause: `modeler/listSymbols` (`server.ts:491`) returns only `{ qname, kind, name }`, even though every `SymbolEntry` already carries `packageName` (`symbol-table.ts:75`). **Fix at the source:** add `packageName` to the `listSymbols` payload and use it directly; delete both `slice(0,-3)` sites. See F4 tasks.

### F5 [Med] — Picker lists non-addable symbols (attributes, columns)

`AddObjectPicker` shows every symbol `listSymbols` returns (`AddObjectPicker.tsx:25`, `limit: 1000`). That includes attributes and columns, which cannot be "added to a graph" — graphs hold top-level objects (`entity`, `table`, `view`, …). Selecting one would emit an unaddable qname (and, per F4, compute a bogus package for the auto-import decision). The picker should filter to addable top-level kinds. Cleanest with F4: have `listSymbols` distinguish top-level defs (e.g. pass `kinds`, or have the server omit children), so the UI doesn't re-derive "is this a child" from segment counts.

### F6 [Med] — `MissingObjectsDrawer` calls `onClose()` during render

`MissingObjectsDrawer.tsx:25`:

```tsx
if (missingObjects.length === 0) {
  onClose();        // setShowMissingDrawer(false) in the PARENT — during render
  return null;
}
```

Calling the parent's state setter while rendering a child triggers React's "Cannot update a component while rendering a different component" warning and is a render side-effect. The "auto-close when empty" behavior (task E3.4: "Drawer auto-closes when the missing list is empty") belongs in a `useEffect`, and should reuse the same `setVisible(false)` + timed `onClose` exit the close button uses (`:44`) so it doesn't skip the slide-out animation.

---

## Low — UX / correctness nits

### F7 [Low] — `currentImports` is derived from existing nodes, not the graph's declared imports

`App.tsx:69` computes the "current imports" by mapping over `state.currentGraph.nodes` qnames. A package that *is* imported in the `.ttrg` but has no node placed yet reads as out-of-scope, so the picker flags it "not imported" and (with auto-import on) would synthesize a redundant `import`. The true source is the `.ttrg`'s `imports` block, which `GetGraphResponse` does not currently surface (`packages/lsp/src/graph-methods.ts:15`). Acceptable as a stopgap, but record it as a deviation; the correct fix is to add declared `imports` to `GetGraphResponse` and read those.

### F8 [Low] — Context menu dismiss isn't a real click-outside

The menu is dismissed only by `cy.on('tap', …)` (`Canvas.tsx:174`), which fires for taps **inside** the Cytoscape canvas. Clicking the header, the inspector, or anywhere outside the canvas leaves the menu open; Escape doesn't close it either. Task E3.3 says "Dismiss on click-outside." Add a `document`-level `mousedown`/`pointerdown` listener (and optionally Escape) while the menu is open.

### F9 [Low] — Context menu opens at the node center, not the cursor

`Canvas.tsx:169` positions the menu at `evt.target.renderedPosition()` (the node's center) instead of the click point `evt.renderedPosition`. Conventional right-click menus appear at the cursor. Minor, but trivially better with the event position.

---

## What's genuinely good

- The component decomposition is clean and matches the task's file plan: `AddObjectPicker.tsx`, `MissingObjectsDrawer.tsx`, `Toast.tsx` are small, single-purpose, and presentational; App owns orchestration. `Header` gets the `+ Add object` button (gated on `hasGraph`) and a clickable stale badge wired through props — good separation, no business logic leaked into the shim.
- `cxttap` is used natively (no extension), exactly as the task's library note recommends, and the menu is a plain positioned `<div>`.
- `Toast`/`ToastContainer` correctly handle 4s auto-dismiss + manual dismiss with an exit transition; `makeToast` keeps id generation out of the view.
- The Canvas `onRemoveNode` is threaded via a ref (`onRemoveNodeRef`) consistent with the file's existing ref-for-callbacks convention, so the cytoscape init effect stays `[]`-deps.

---

## Recommendation

E3's surface is built but its substance isn't: **the add/remove affordances don't change anything** (F1) and **the behaviors aren't tested** (F3), so the green suite is hiding a non-functional feature. Fix order: (1) add a shared apply-`WorkspaceEdit`-to-worker helper and route add/remove through it instead of the dead `applyGraphEdit` stub (F1); (2) write the four missing Tests-first cases so the round-trip and the toast are actually asserted (F3); (3) clear lint (F2). Then the cleanliness items: push `packageName` into `listSymbols` and delete the `slice(0,-3)` duplication (F4), filter the picker to addable kinds (F5), and move the drawer's auto-close into an effect (F6). F7–F9 are polish. `tasks-review-043.md` has the step-by-step.
