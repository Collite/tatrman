# Phase 3.E — Inspector / detail panel

**Goal:** the right-hand panel populates from `modeler/getSymbolDetail` whenever the user selects a node or edge. Shows name, kind, qname, localized description, tags, source `file:line`, per-kind data, and a clickable "Related symbols" list.

**Reads:** [contracts §5](../../design/phase-03-contracts.md#5-symbol-detail-dto).
**Blocked by:** §B (`getSymbolDetail` handler), §H (relation/query/role indexed as symbols so the per-kind sections have data).
**Blocks:** nothing.

## Tests-first

- [ ] `packages/lsp/src/__tests__/symbol-detail.test.ts` — unit-level over `buildSymbolDetail`.
  - For an entity with two attributes: returns `SymbolDetail` with `perKindData.kind === 'entity'`, `perKindData.attributes.length === 2`.
  - For a table with `primaryKey: ['id']` and three columns: `perKindData.kind === 'table'`, `perKindData.primaryKey === ['id']`, `perKindData.columns.length === 3`.
  - With `manifest.preferredLanguage = 'cs'` and a `displayLabel: { cs: 'Artikl', en: 'Item' }`: `detail.label === 'Artikl'`.
  - With `manifest.preferredLanguage = 'de'`, same input: falls back to `'Item'` (en), and if en is missing too, falls back to the bare name.
  - With no `description` on the def: `detail.description === null` (not undefined, not empty string).
  - Unknown qname: returns `null`.
  - With a `ReferenceIndex` containing two refs to the symbol: `detail.referencedBy.length === 2`.

- [ ] `tests/integration/src/symbol-detail.test.ts` — component-scope via paired LSP harness.
  - `modeler/getSymbolDetail` for `er.entity.artikl` on `samples/v1-metadata/`: non-null; Czech `label` and `description`; `perKindData.kind === 'entity'`; `referencedBy` non-empty (the map-side `er2db_entity` mapping shows up).

- [ ] `packages/designer/src/components/__tests__/InspectorPanel.test.tsx` — RTL + jsdom.
  - With `selectedDetail === null`: panel shows an "Nothing selected" empty state.
  - With a fixture `SymbolDetail` for an entity: panel shows the qname header, kind chip, description block, tag chips, source `file:line` link, and an attributes list of the expected length.
  - Clicking a row in "Related symbols" dispatches `selectSymbol` with that qname.

## Library reference

No new external libraries. Inspector is plain Tailwind + React. The `file:line` link uses `navigator.clipboard.writeText` on click (already available in jsdom test env via `vi.spyOn(navigator.clipboard, 'writeText')`).

## Implementation tasks

- [ ] **E.1 — `buildSymbolDetail` in the LSP.** Implement per [contracts §5](../../design/phase-03-contracts.md#5-symbol-detail-dto). Localize using the helper already used in Phase 2's hover formatter (`pickLocalized(label, preferredLanguage)`). Discriminate `perKindData` by `Definition.kind`. Make the symbol-detail unit tests green.
- [ ] **E.2 — Register `modeler/getSymbolDetail`.** Add to both `server-stdio.ts` and `server-browser.ts`. Cache the latest parsed AST per document in the existing document cache so the handler doesn't reparse. Make the integration test green.
- [ ] **E.3 — Extend the Designer reducer + client.** Add the `storeSymbolDetail` action handling (already in [contracts §2](../../design/phase-03-contracts.md#2-designer-side-state-types)). Add `client.getSymbolDetail(qname)`. In `App.tsx`, an effect on `state.selectedSymbol` calls `getSymbolDetail` (only if not cached in `state.symbolDetails`), then dispatches `storeSymbolDetail`.
- [ ] **E.4 — Rewrite `InspectorPanel.tsx`.** Replace the Phase-0 stub with the full layout per [contracts §5](../../design/phase-03-contracts.md#5-symbol-detail-dto): name/kind/qname header, description, tags, source `file:line` (clickable; on click, `navigator.clipboard.writeText(\`<file>:<line>\`)` plus a toast "Copied"). Per-kind sections rendered as a small component switch on `perKindData.kind`. "Related symbols" list at the bottom; each item is a button that dispatches `selectSymbol`. Make the Inspector RTL test green.
- [ ] **E.5 — Wire selection from Canvas.** Cytoscape's `cy.on('tap', 'node, edge', evt => dispatch({ type: 'selectSymbol', qname: evt.target.data('qname') }))`. Background tap clears selection (`'selectSymbol', qname: null`).

## Verify by running

```bash
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/designer test
pnpm --filter @modeler/integration-tests test
pnpm --filter @modeler/designer dev
# Click any node or edge; inspector populates. Click a "Related symbols" entry; selection shifts.
```

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Symbol-detail unit + integration tests green.
- [ ] InspectorPanel component test green.
- [ ] Manual: clicking an entity in `samples/v1-metadata/` shows Czech description + tags + a clickable source link + the map-side `er2db_entity` in Related symbols.
