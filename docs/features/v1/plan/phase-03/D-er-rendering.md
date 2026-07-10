# Phase 3.D — `er` schema rendering

**Goal:** entity nodes with attribute rows inline, relation edges with Crow's-foot cardinality glyphs at both endpoints. Reuses the adapter pipeline and toggle UI from §C; the only new visual work is the cardinality glyph renderer.

**Reads:** [contracts §4 / §8 (cardinality mapping)](../../design/phase-03-contracts.md#8-cardinality-mapping).
**Blocked by:** §C (adapter pipeline + Canvas refactor).
**Blocks:** nothing (E and F can start in parallel once §C lands).

## Tests-first

- [ ] `packages/designer/src/cy/__tests__/glyph-renderer.test.ts` — unit, jsdom for SVG-string output.
  - `glyphFor('one')` returns an SVG snippet whose root is `<g class="glyph-one">` and that contains exactly one `<line>` element (the perpendicular tick).
  - `glyphFor('zero-or-one')` returns a `<g class="glyph-zero-or-one">` with one `<circle>` and one `<line>`.
  - `glyphFor('many')` returns `<g class="glyph-many">` containing three `<line>` elements forming the crow's-foot fan.
  - `glyphFor('one-or-many')` returns `<g class="glyph-one-or-many">` with one perpendicular `<line>` plus three crow's-foot `<line>`s.
  - `glyphFor(null)` returns the empty string `''` (no glyph rendered).
  - Snapshot test for each: stable SVG output across runs (use `vi.toMatchSnapshot()` so accidental visual drift is reviewable).

- [ ] `packages/lsp/src/__tests__/cardinality.test.ts` — unit over `parseCardinality` and `extractCardinality` per [contracts §8](../../design/phase-03-contracts.md#8-cardinality-mapping). One assertion per row in the §8 test-cases table.

- [ ] `packages/designer/src/cy/__tests__/adapter-er.test.ts` — paralleling C's adapter test, focused on er.
  - One entity with `displayLabel: { cs: 'Artikl', en: 'Item' }` and `preferredLanguage: 'cs'` → node `data.label` is `'Artikl'`.
  - Same entity with `preferredLanguage: 'de'` (missing) → falls back to the bare `name` (`'foo'`) per `phase-03-contracts.md` §4. (No language-to-language fallback.)
  - Same entity with no `displayLabel` → falls back to the bare `name`.
  - Relation edge with `fromCardinality: 'one'`, `toCardinality: 'many'` → edge element's `data` carries both values (the Canvas overlay reads them).

- [ ] `packages/designer/src/components/__tests__/Canvas-er.test.tsx` — analog of C's Canvas test for the er switch + glyph overlay placement (mock the glyph renderer; assert it's called per edge with the right cardinalities).

## Library reference

Cytoscape has no built-in Crow's-foot glyphs. Two viable approaches; pick **A** by default:

- **A. SVG overlay on top of the Cytoscape canvas.** A second positioned `<div>` containing an `<svg>` element, populated per `cy.on('render', …)` by reading every edge's endpoint screen positions and rendering glyphs there. Costs O(edges) per frame; fine for ≤200 edges. Document the choice in `Canvas.tsx`'s top comment.
- **B. `cytoscape-svg` extension** — exports the whole graph as an SVG, but at runtime only on demand. Not suitable for interactive glyphs.

Run Context7 to verify A is still the recommended pattern:

```
mcp__context7__resolve-library-id { libraryName: "cytoscape", query: "render event, edge endpoint screen position, custom overlay layer" }
mcp__context7__query-docs        { libraryId: "<id>", query: "cy.on render edge.sourceEndpoint targetEndpoint screen coords for overlay rendering" }
```

## Implementation tasks

- [ ] **D.1 — `cy/glyph-renderer.ts`.** Pure function `glyphFor(card: Cardinality | null): string` returning a small SVG `<g>` snippet. The Canvas's overlay layer assembles a full `<svg>` per render by concatenating these snippets translated into endpoint coordinates. Make the glyph-renderer unit tests green here.
- [ ] **D.2 — Cardinality mapping in the LSP.** Add `parseCardinality` and `extractCardinality` (helpers) to `packages/lsp/src/model-graph.ts`. Wire `extractCardinality` into `buildModelGraph` so every `RelationDef` produces an edge with populated `fromCardinality` / `toCardinality`. Make the cardinality unit tests green.
- [ ] **D.3 — Extend the adapter for er.** `modelGraphToCyElements` already handles entity nodes via §C's code path; ensure the er-specific bits (display-label localization, attribute markers `★` / `#` for `nameAttribute` / `codeAttribute`) work. Make `adapter-er.test.ts` green.
- [ ] **D.4 — Canvas overlay for glyphs.** Add a sibling `<div class="cy-overlay">` with `pointer-events: none`. A new `useEffect` in `Canvas.tsx` registers `cy.on('render zoom pan', renderOverlay)` and computes endpoint screen positions for every relation edge, then assembles SVG from `glyphFor` snippets. Throttle to `requestAnimationFrame` so high-frequency events don't tank perf.
- [ ] **D.5 — Visual review against samples/v1-metadata/.** Boot `pnpm --filter @modeler/designer dev`, switch to er, confirm:
  - Czech entity labels render correctly (manifest sets `cs`).
  - Attribute rows show inline.
  - Every relation edge shows two glyphs whose shapes match the .ttr-side cardinality string.
  - Dragging an entity moves both endpoints' glyphs with it.

## Verify by running

```bash
pnpm --filter @modeler/designer test
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/designer dev
```

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Glyph renderer + cardinality + adapter-er + Canvas-er tests all green.
- [ ] Manual: er rendering in the dev server passes the visual review in D.5.
