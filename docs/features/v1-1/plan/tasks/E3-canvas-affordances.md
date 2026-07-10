# 1.1.E.3 — Canvas affordances: add / remove / extend imports

**Goal:** in-canvas controls let the user add objects to the current graph, remove objects, and extend imports automatically when picking an object outside current scope. Plus the missing-objects badge with one-click cleanup.

**Reads:** [contracts §8.3–8.4 (`addObjectToGraph` / `removeObjectFromGraph`)](../../design/v1-1-contracts.md#83-modeleraddobjecttograph-new), [design doc §8.5](../../design/v1.1-packages-and-graphs.md#85-modelerdesigner), `packages/designer/src/Canvas.tsx`, `packages/designer/src/Header.tsx`.
**Blocked by:** 1.1.E.2.
**Blocks:** E4 (state-rewire makes these affordances live-update the layout block).
**Estimated time:** 2 days.

## Tests-first

- [ ] `packages/designer/src/__tests__/canvas-affordances.test.tsx` — RTL. Cases:
  - "Add object" toolbar button opens a picker; selecting an object from the picker triggers `client.addObjectToGraph(uri, qname, autoImport)`; on success, dispatches `storeGraph` with the new graph state.
  - Selecting an object whose package is NOT in current `imports`: the picker's "Auto-import" toggle is on by default; the dispatched call has `autoImport: true`.
  - Toggling auto-import off and selecting the same out-of-scope object: the dispatched call has `autoImport: false`. (LSP returns an error in that case; UI shows a non-modal toast.)
  - Right-click on a canvas node opens a context menu with "Remove from graph"; clicking calls `client.removeObjectFromGraph(uri, qname, pruneUnusedImport: true)`.
  - Missing-objects badge from E1.6 is now clickable; opens a side panel listing stale qnames with "Remove" per row.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "cytoscape", query: "context menu, right-click event, cy.on('cxttap')" }
mcp__context7__query-docs         { libraryId: "<id>", query: "context menu extension or native right-click handling" }
```

Cytoscape's `cxttap` event handles right-click natively; no extension required. The context menu is a plain React-positioned `<div>` rendered above the canvas, dismissed on click-outside.

## Implementation tasks

- [ ] **E3.1 — Add the "Add object" toolbar button.** In `Header.tsx` (when a graph is open), add a "+ Add object" button. Click opens an `<AddObjectPicker />` modal.
- [ ] **E3.2 — Implement `<AddObjectPicker />`.** New file `packages/designer/src/AddObjectPicker.tsx`. Lists every def in the project (via `client.getProjectInfo()` → `packages: PackageInfo[]`, then expand each via the symbol table). Search/filter input. Toggles: "Show only imported packages" (default off), "Auto-import on select" (default on). On select, calls `client.addObjectToGraph(currentGraphUri, qname, autoImport)`.
- [ ] **E3.3 — Implement canvas context menu.** In `Canvas.tsx`, attach `cy.on('cxttap', 'node', (evt) => ...)` to show a positioned React menu with "Remove from graph" as the only entry (more entries land in v1.2 when full edit mode arrives). Click handler calls `client.removeObjectFromGraph(currentGraphUri, qname, pruneUnusedImport: true)`. Dismiss on click-outside.
- [ ] **E3.4 — Wire the missing-objects side panel.** Click the badge from E1.6 → opens a right-side drawer listing each entry in `state.currentGraph.missingObjects`. Each row has the qname + a "Remove" button. Click "Remove" → `client.removeObjectFromGraph(uri, qname, pruneUnusedImport: true)`. Drawer auto-closes when the missing list is empty.
- [ ] **E3.5 — Handle errors with non-modal toasts.** Any failure from `addObjectToGraph` / `removeObjectFromGraph` (e.g. attempting to add an out-of-scope object with `autoImport: false`) shows a toast in the top-right corner via a new `<Toast />` component. Auto-dismiss after 4s; manual dismiss via X button.
- [ ] **E3.6 — Refetch the graph after every mutation.** After `addObjectToGraph` / `removeObjectFromGraph` succeeds and the host applies the `WorkspaceEdit`, immediately call `client.getGraph(uri)` again and dispatch `storeGraph`. This is the simplest correctness model; optimisation (incremental graph patches) is a v1.2+ concern.

## Verify by running

```bash
pnpm --filter @modeler/designer test
pnpm --filter @modeler/designer typecheck
```

All affordance tests pass.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Add-object via the toolbar works for in-scope and (with auto-import) out-of-scope qnames.
- [ ] Right-click on a node removes it via the context menu.
- [ ] Missing-objects side drawer lets the user clean up stale entries one click each.
- [ ] No reducer-state-shape changes yet beyond E1's — E4 handles the layout-persistence rewire.
