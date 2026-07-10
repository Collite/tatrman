# Phase 3.F — Layout persistence

**Goal:** node positions, viewport state (zoom/pan), and per-schema display mode round-trip through `modeler/getLayout` / `modeler/setLayout`. Node mode reads/writes `<project-root>/.modeler/layout.ttrl`. Browser keeps layout in memory and exposes a "Download layout" affordance.

**Reads:** [contracts §6 (layout file types) / §7.3–7.5 (LSP methods)](../../design/phase-03-contracts.md).
**Blocked by:** §B (handlers exist), §C (Canvas emits events).
**Blocks:** nothing.

## Tests-first

- [ ] `packages/designer/src/cy/__tests__/debounce.test.ts` — unit.
  - `debounce(fn, 100)` invoked 5x rapidly fires `fn` exactly once after the delay.
  - Invoked with intervals > 100 ms fires `fn` for each call.
  - Use `vi.useFakeTimers()` for deterministic timing.

- [ ] `packages/designer/src/cy/__tests__/layout-round-trip.test.ts` — component-scope with a stubbed `cy` and a stubbed `LspClient`.
  - On the stub's `'dragfreeon'` event, after the 500 ms debounce, `client.setLayout(projectRoot, payload)` is called once. The payload's `nodes` map matches the stub's reported positions.
  - On `'viewport'` event, after the 750 ms debounce, `setLayout` is called once. The payload's `viewports[activeSchema]` reflects `cy.zoom()` / `cy.pan()`.
  - Calling `loadLayout` with `{ version: 1, nodes: { 'er.entity.artikl': { x: 42, y: 99 } }, ... }` sets `cy.getElementById('er.entity.artikl').position({ x: 42, y: 99 })` exactly once and **does not** trigger an auto-layout.
  - Calling `loadLayout` with a layout referencing a qname not in the current graph: the unknown qname is silently skipped (no throw, no warning beyond a single `console.warn`).

- [ ] `tests/integration/src/layout-persistence.test.ts` — component-scope, Node-mode LSP via paired connection harness.
  - `setLayout` writes a JSON file at `<root>/.modeler/layout.ttrl`; subsequent `getLayout` returns a structurally identical object.
  - Writing a malformed `.ttrl` by hand and calling `getLayout` returns `emptyLayout()`.

## Library reference

No new libraries. Debounce is the only utility — write it yourself rather than depending on lodash for one function.

```ts
export function debounce<Args extends unknown[]>(fn: (...args: Args) => void, ms: number): (...args: Args) => void {
  let t: ReturnType<typeof setTimeout> | null = null;
  return (...args: Args) => {
    if (t) clearTimeout(t);
    t = setTimeout(() => { t = null; fn(...args); }, ms);
  };
}
```

Cytoscape event names you'll need (verify via Context7 if Cytoscape's docs differ):

- `dragfreeon` — fires when the user releases a dragged node. Use `evt.target.position()` to read.
- `viewport` — fires on pan/zoom. Use `cy.zoom()` and `cy.pan()`.
- `layoutstop` — fires when the auto-layout finishes. Use this to persist the auto-layout result the *first* time a fresh graph is rendered.

## Implementation tasks

- [ ] **F.1 — `debounce` utility.** New `packages/designer/src/util/debounce.ts`. Tests green.
- [ ] **F.2 — Save flow.** In `Canvas.tsx`, register handlers:
  - `cy.on('dragfreeon', debounce(saveLayout, 500))`
  - `cy.on('viewport', debounce(saveLayout, 750))`
  - `cy.on('layoutstop', saveLayout)` — fires once after auto-layout completes; no debounce.
  - `state.viewports[state.activeSchema].displayMode` change handler calls `saveLayout` immediately (no debounce — discrete user choice).

  `saveLayout` assembles a `LayoutFile` from current `cy` state plus the reducer's `state.viewports`, then calls `client.setLayout(projectRoot, layout)`. Catch and log; do not crash the UI on a failed save.

- [ ] **F.3 — Load flow.** When `projectUri` becomes non-null, after all `openDocument` promises settle (re-use the guard from §B.7), `client.getLayout(projectRoot)` is called. The result is dispatched as `loadLayout`. The Canvas, on the next render, applies each `nodes[qname]` to the corresponding Cytoscape node's `position()` *before* auto-layout runs.
- [ ] **F.4 — Decide layout-vs-positions race.** If the loaded layout has any `nodes` entries, **skip** the auto-layout for this load. If empty, run auto-layout and persist on `layoutstop`. Tests assert both branches.
- [ ] **F.5 — "Download layout" affordance (browser mode only).** In `Header.tsx`, add a menu item visible only when the LSP transport is the browser worker (detect via a `client.transportKind: 'node' | 'browser'` property added in §B). On click: `const layout = await client.exportLayout(projectRoot); downloadFile('layout.ttrl', JSON.stringify(layout, null, 2));`.
- [ ] **F.6 — Stale-qname tolerance.** When `loadLayout` references a qname that doesn't exist in the current graph (e.g. the entity was renamed since the layout was last saved), the position is silently dropped on next save — the round-trip test asserts this. Document in the function's JSDoc.

## Verify by running

```bash
pnpm --filter @modeler/designer test
pnpm --filter @modeler/integration-tests test

# Manual:
pnpm --filter @modeler/designer dev
# Load samples/v1-metadata/ (Node-style via VS Code workspace folder, or browser upload).
# Drag two nodes. Reload the page.
# Node mode: positions restored. Browser mode: positions reset (documented).
```

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Debounce + layout round-trip + layout persistence tests all green.
- [ ] Manual: positions persist across reload in Node mode; "Download layout" button works in browser mode and produces a valid JSON file that re-imports cleanly.
