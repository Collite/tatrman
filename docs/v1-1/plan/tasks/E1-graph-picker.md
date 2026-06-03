# 1.1.E.1 — Designer entry modes: open + browse

**Goal:** the Designer's entry flow supports two of the three modes (open existing `.ttrg` via file picker; browse project graphs via `modeler/listGraphs`). The third mode — create new graph — lands in E2.

**Reads:** [contracts §8 (LSP custom-method contracts)](../../design/v1-1-contracts.md#8-lsp-custom-method-contracts), [design doc §8.5 (Designer surface changes)](../../design/v1.1-packages-and-graphs.md#85-modelerdesigner), `packages/designer/src/App.tsx`, `packages/designer/src/state/designer-reducer.ts`, `packages/designer/src/Header.tsx`.
**Blocked by:** 1.1.C.2.
**Blocks:** E2 (creation wizard adds the third entry mode), E3 (canvas affordances operate on the loaded graph).
**Estimated time:** 2 days.

## Tests-first

- [ ] `packages/designer/src/__tests__/graph-picker.test.tsx` — RTL. Cases:
  - Mount `<GraphPicker projectUri="..." graphs={[{name: 'a', schema: 'er', ...}, {name: 'b', schema: 'db', ...}]} onSelect={mock} />`. Two list items render with names + schema badges. Clicking 'a' calls `onSelect` with the right URI.
  - Search filter: typing 'a' into the search input narrows the list to one item.
  - Schema filter: clicking the 'db' badge in the filter row hides 'a'.
- [ ] `packages/designer/src/__tests__/designer-reducer-v1.1.test.ts` — extend the existing reducer tests. New cases:
  - `openGraph` action with a graph URI sets `state.currentGraphUri` and clears `state.nodePositions` / `state.symbolDetails`.
  - `closeGraph` action clears `state.currentGraphUri` (back to the picker).
  - `storeGraphList` action populates `state.availableGraphs: GraphMetadata[]`.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "react", query: "useReducer with discriminated-union actions" }
mcp__context7__resolve-library-id { libraryName: "@testing-library/react", query: "fire events, user-event v14, async findBy" }
```

The existing reducer + RTL pattern in `packages/designer/src/state/__tests__/designer-reducer.test.ts` is the template — match it.

## Implementation tasks

- [ ] **E1.1 — Implement the reducer per the contract.** [Contracts §11](../../design/v1-1-contracts.md#11-designer-state-types) is the source of truth for the v1.1 `DesignerState`, `ViewportState`, and `DesignerAction` shapes. Replace the v1 reducer in `packages/designer/src/state/designer-state.ts` and `designer-reducer.ts` to match. Key v1.1 changes: `currentGraphUri`, `availableGraphs`, `currentGraph`, `creatingGraph` are new; per-schema `viewports` is gone (replaced by a single `currentViewport`); the schema-toggle UI is removed (see E4.1).
- [ ] **E1.2 — Create `<GraphPicker />` component.** New file `packages/designer/src/GraphPicker.tsx`. Renders a searchable, filterable list of `GraphMetadata`. Search input + schema-badge filter row + list. On select, calls the `onSelect: (uri: string) => void` prop. Use the same Tailwind classes as Phase 3's inspector for visual consistency.
- [ ] **E1.3 — Wire the project-open flow.** In `App.tsx`, after the user picks a project folder (existing `loadProject` flow), call `client.listGraphs(projectUri)` and dispatch `storeGraphList`. Then show the `<GraphPicker />` instead of jumping straight to the canvas. The canvas appears once a graph is selected.
- [ ] **E1.4 — Wire the file-picker entry mode.** Add a "Open .ttrg…" button to the header. Uses the File System Access API (or the upload fallback) restricted to `.ttrg` extension. On select, dispatches `openGraph` with the file URI and triggers `client.getGraph(uri)` → `storeGraph`.
- [ ] **E1.5 — Add "Back to graph picker" affordance.** When a graph is open, the header gains a left-arrow button that dispatches `closeGraph`. Returns to the picker without re-fetching `listGraphs`.
- [ ] **E1.6 — Handle missing-objects badge surface.** When `state.currentGraph?.missingObjects.length > 0`, show a small warning badge next to the graph name in the header: "N stale object(s)". Clicking opens a side panel listing them with "Remove" buttons (E3.4 implements the removal action; this task just shows the count).

## Verify by running

```bash
pnpm --filter @modeler/designer test
pnpm --filter @modeler/designer typecheck
pnpm --filter @modeler/designer build
```

The new RTL + reducer tests pass; existing Designer tests pass.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Opening a project folder shows the graph picker, not the canvas.
- [ ] Selecting a graph from the picker renders it on the canvas.
- [ ] The "Open .ttrg…" button works for ad-hoc files.
- [ ] No creation flow yet — E2.
