# Phase 3.C — `db` schema rendering

**Goal:** Cytoscape canvas renders `db.table` nodes with column rows inline (display-mode-aware) and `db.fk` edges between them. Schema toggle (db ↔ er) wires through the reducer; display-mode toggle re-adapts the existing graph without a fresh `getModelGraph` round-trip.

**Reads:** [contracts §4](../../design/phase-03-contracts.md#4-shared-graph-dtos).
**Blocked by:** §B (needs the new `ModelGraph` shape and project loader).
**Blocks:** §D (er render reuses the same adapter + toggle wiring), §F (layout persistence hangs off Cytoscape events on this canvas).

## Tests-first

- [ ] `packages/designer/src/cy/__tests__/adapter.test.ts` — pure-function tests over `modelGraphToCyElements`. No Cytoscape instance.
  - Empty graph → empty elements array.
  - One node, no rows → one element with `group: 'nodes'`, `data.kind: 'table'`, `data.qname` set; no row HTML in `data.label`.
  - One node, two rows, `displayMode: 'just-names'` → label HTML contains row names only, no types (assert via substring: contains row names, does not contain row type strings).
  - Same node, `displayMode: 'with-types'` → label HTML contains row names AND type strings (`'int'`, `'varchar(40)'`).
  - Same node, `displayMode: 'with-constraints'` → label HTML contains key markers (`PK` for `isKey: true` rows, `NN` for `optional: false` rows).
  - One edge → one element with `group: 'edges'`, `data.source` / `data.target` matching node qnames, `data.kind: 'fk'`.

- [ ] `packages/designer/src/components/__tests__/Canvas-db.test.tsx` — component-scope with a stub LSP client + jsdom + a minimal Cytoscape mock (`vi.mock('cytoscape', ...)`).
  - On `state.activeSchema === 'db'` and a non-null `projectUri`, App calls `client.getModelGraph(uri, 'db')` exactly once after `loadProject` settles.
  - Switching `displayMode` does NOT call `getModelGraph` — only `modelGraphToCyElements` is re-invoked.
  - Switching `activeSchema` from `db` to `er` DOES call `client.getModelGraph(uri, 'er')`.

## Library reference

Run Context7 before coding any Cytoscape detail:

```
mcp__context7__resolve-library-id { libraryName: "cytoscape", query: "register extension, style selectors with data() predicates, layout API, viewport events" }
mcp__context7__query-docs        { libraryId: "<id>", query: "node-html-label registration, label template, displayed-only-when option" }

mcp__context7__resolve-library-id { libraryName: "cytoscape-cose-bilkent", query: "seed, randomize=false, nodeRepulsion, idealEdgeLength, animate" }
mcp__context7__query-docs        { libraryId: "<id>", query: "deterministic layout options" }
```

**Library reference (training-time approximation; verify above):**

```ts
import cytoscape from 'cytoscape';
import coseBilkent from 'cytoscape-cose-bilkent';
import nodeHtmlLabel from 'cytoscape-node-html-label';

cytoscape.use(coseBilkent);
nodeHtmlLabel(cytoscape);

const cy = cytoscape({
  container: el,
  elements,
  style: [
    { selector: 'node[kind = "table"]', style: { shape: 'round-rectangle', 'background-color': '#0f172a', 'border-width': 1, 'border-color': '#475569', width: 220, height: 'label' } },
    { selector: 'edge[kind = "fk"]',    style: { width: 1.5, 'line-color': '#3b82f6', 'target-arrow-color': '#3b82f6', 'target-arrow-shape': 'triangle', 'curve-style': 'bezier' } },
  ],
  layout: { name: 'cose-bilkent', randomize: false, /* seed: 1 (deterministic) */ animate: false, nodeRepulsion: 4500, idealEdgeLength: 200 },
});

cy.nodeHtmlLabel([
  {
    query: 'node[kind = "table"]',
    halign: 'center', valign: 'center',
    tpl: (data) => data.labelHtml,   // produced by modelGraphToCyElements per displayMode
  },
]);
```

The `data.labelHtml` shape lives in your adapter — keep it small, plain HTML, no React. `displayMode` only changes what HTML the adapter emits.

## Implementation tasks

- [ ] **C.1 — Add Cytoscape extensions to the workspace.** `pnpm --filter @modeler/designer add cytoscape-cose-bilkent cytoscape-node-html-label`. Confirm `pnpm --filter @modeler/designer build` still produces a working bundle.
- [ ] **C.2 — Write `packages/designer/src/cy/adapter.ts`.** Export `modelGraphToCyElements(graph: ModelGraph, displayMode: DisplayMode): cytoscape.ElementDefinition[]`. Emit one node element per `ModelGraphNode` and one edge element per `ModelGraphEdge`. The node's `data.labelHtml` is a plain-HTML string assembled from rows according to display mode. Make the adapter unit tests green here.
- [ ] **C.3 — Refactor `packages/designer/src/components/Canvas.tsx` to consume `ModelGraph`.** Replace the Phase-0 `nodes` / `edges` props with a single `graph: ModelGraph | null` prop plus `displayMode: DisplayMode`. Drop the `circle` layout; use `cose-bilkent` per the library-reference snippet (with `randomize: false`, fixed seed if Cytoscape's seed API supports it — check Context7). Register the `node-html-label` extension once at module scope, not per-render.
- [ ] **C.4 — Layout-once-on-load semantics.** When `graph` changes from `null` → non-null, run the auto-layout. When `graph` stays non-null and `displayMode` changes, refresh `data.labelHtml` without re-running layout (drag positions must survive). When `displayMode` changes only, re-apply the `node-html-label` template (no `cy.elements().remove()`).
- [ ] **C.5 — Wire schema toggle.** In `App.tsx`, an effect on `[state.activeSchema, state.projectUri]` calls `client.getModelGraph(uri, state.activeSchema)` and dispatches a new action to store the graph. The reducer holds a `graphsByCachedSchema?: Record<RenderableSchemaCode, ModelGraph>` cache so flipping back is instant. Open a contract amendment if the cache field is needed (it is).
- [ ] **C.6 — Wire display-mode toggle.** No LSP call; the Canvas effect that watches `state.viewports[state.activeSchema].displayMode` calls a `refreshLabels` helper. Make `Canvas-db` component test green.

## Verify by running

```bash
pnpm --filter @modeler/designer test
pnpm --filter @modeler/designer dev
# In a browser: load samples/v1-metadata/, switch schema=db,
# see tables with rows, flip displayMode, see row detail change without re-fetch.
```

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Adapter unit tests green.
- [ ] Canvas-db component test green.
- [ ] Manual: in dev mode, loading `samples/v1-metadata/` and clicking the db schema button shows tables with FK edges between them, no broken HTML in node labels.
