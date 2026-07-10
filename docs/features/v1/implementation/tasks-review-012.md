# Tasks — Review 012 (Phase 3, Section D)

Follow these in order. Each task names the exact file, the exact change, and the exact verification command. Do **not** mark §D done in `docs/plan/progress-phase-03.md` until **every** box below is ticked **and** the §D.5 visual review checkbox is ticked from running the dev server against `samples/v1-metadata/`.

---

## A. Plan / contract decisions (do these first — they unblock everything else)

- [ ] **A.1 — Decide the `"0..*"` question and amend the contract.**
  - Open `docs/design/phase-03-contracts.md` § 8 ("Cardinality mapping").
  - Either (a) add `"0..*"` → `'many'` to the accepted-strings table and append an entry under "Changelog" describing the addition, **or** (b) decide it is invalid input and update `samples/v1-metadata/er.ttr` to use one of the existing strings (likely `"*"`). Pick exactly one path; write the decision in the changelog.
  - If (a): also update `packages/lsp/src/model-graph.ts` `parseCardinality` to add the `case '0..*': return 'many';` arm, and add a `parseCardinality('0..*') → 'many'` assertion to `packages/lsp/src/__tests__/model-graph-cardinality.test.ts`.
  - If (b): run a single `sed`-style replacement across `samples/v1-metadata/er.ttr` substituting `"0..*"` with the chosen string. Manually scan the diff afterwards to confirm only `cardinality` entries changed.

- [ ] **A.2 — Decide the glyph-rendering architecture and amend the plan or document the deviation.**
  - Open `docs/plan/phase-03/D-er-rendering.md` and `docs/design/phase-03-contracts.md`.
  - Pick one:
    - **(Recommended) Revert to the planned SVG overlay**: implement what `D-er-rendering.md:31-48` describes (a sibling `<div class="cy-overlay">` populated per-frame via `cy.on('render zoom pan', ...)` with `requestAnimationFrame` throttling and endpoint-positioned glyphs oriented along the edge tangent). The rest of these tasks assume this path.
    - **Keep the centered-label approach**: add a new amendment in `phase-03-contracts.md` § Amendments that documents (i) why approach A was abandoned, (ii) the concrete visual cost (no per-endpoint glyphs, no edge-tangent orientation), (iii) what the new acceptance bar is for §D.5 step 3. Then rewrite `D-er-rendering.md` § D.4 to describe the centered-label approach.
  - Whichever path is chosen, the decision must be committed before §C and §D below.

- [ ] **A.3 — Run Context7 for cytoscape `cy.on('render')` / edge endpoint screen coordinates (only if you picked the overlay path in A.2).**
  - Command:
    ```
    mcp__context7__resolve-library-id { libraryName: "cytoscape", query: "render event, edge endpoint screen position, custom overlay layer" }
    mcp__context7__query-docs        { libraryId: "<id>", query: "cy.on render edge.sourceEndpoint targetEndpoint screen coords for overlay rendering" }
    ```
  - Paste the relevant docs snippet as a comment at the top of `packages/designer/src/components/Canvas.tsx` (per the plan: "Document the choice in `Canvas.tsx`'s top comment.").

---

## B. LSP fixes (model-graph.ts)

- [ ] **B.1 — Localize `ModelGraphNode.label` per `manifest.preferredLanguage`.**
  - Edit `packages/lsp/src/model-graph.ts`:
    - Change `buildProjectModelGraph(asts: Document[], schema: RenderableSchemaCode)` to `buildProjectModelGraph(asts: Document[], schema: RenderableSchemaCode, preferredLang: string)`.
    - For entity nodes (line ~487), set `label: getDisplayLabel(def, preferredLang)`. The helper already exists at lines 215-229; reuse it. Note: it currently uses `def.description` as a fallback for tables — keep that behavior unchanged.
    - Update `buildModelGraph(ast: Document, schema: RenderableSchemaCode)` (the test helper at line 408) to accept an optional `preferredLang = 'en'` and pass it through.
  - Edit `packages/lsp/src/server.ts:322-336` so the `modeler/getModelGraph` handler resolves the project manifest (the surrounding handlers already do this for `getProjectInfo`) and passes `manifest.preferredLanguage` to `buildProjectModelGraph`.
  - Add a test in `packages/lsp/src/__tests__/model-graph.test.ts`:
    ```
    it('entity with displayLabel honors preferredLanguage', () => {
      const content = `schema er namespace entity def entity foo {
        displayLabel: { cs: "Artikl", en: "Item" },
        attributes: [def attribute id { type: int }]
      }`;
      const ast = parseString(content, 'file:///x.ttr').ast!;
      expect(buildModelGraph(ast, 'er', 'cs').nodes[0].label).toBe('Artikl');
      expect(buildModelGraph(ast, 'er', 'de').nodes[0].label).toBe('Item');
      expect(buildModelGraph(ast, 'er', 'fr').nodes[0].label).toBe('Item'); // first entry as last-ditch fallback OR === 'foo'; match getDisplayLabel's actual behavior
    });
    ```
    Pick the assertion in the last line to match whatever `getDisplayLabel` actually does for missing languages — read the helper and stay consistent.
  - Verify: `pnpm --filter @modeler/lsp test`.

- [ ] **B.2 — Add `★` / `#` markers to `ModelGraphRow` for `nameAttribute` / `codeAttribute`.**
  - Edit `packages/lsp/src/model-graph.ts`:
    - Extend `ModelGraphRow` with `isNameAttribute: boolean; isCodeAttribute: boolean;`.
    - In `buildProjectModelGraph`'s entity branch, compute both flags by comparing the attribute's name (`attr.name`) to `def.nameAttribute?.path` and `def.codeAttribute?.path` (the `path` field on the `IdValue`).
    - Default both to `false` for table/view rows.
  - Edit `packages/designer/src/cy/adapter.ts`:
    - In `renderRowHtml`, prepend `★ ` (with a non-breaking space) when `row.isNameAttribute`, and prepend `# ` when `row.isCodeAttribute`. Both can be true at once; show both prefixes in that case.
  - Add a test in `packages/lsp/src/__tests__/model-graph.test.ts`:
    ```
    it('entity with nameAttribute marks the row', () => {
      const content = `schema er namespace entity def entity foo {
        attributes: [def attribute id { type: int }, def attribute label { type: text }],
        nameAttribute: label
      }`;
      const node = buildModelGraph(parseString(content, 'file:///x.ttr').ast!, 'er', 'en').nodes[0];
      expect(node.rows.find(r => r.name === 'label')!.isNameAttribute).toBe(true);
      expect(node.rows.find(r => r.name === 'id')!.isNameAttribute).toBe(false);
    });
    ```
  - Update the contract `docs/design/phase-03-contracts.md` § 4 (`ModelGraphRow`) to document the two new fields, and add a changelog entry.
  - Verify: `pnpm --filter @modeler/lsp test`.

- [ ] **B.3 — (Only if A.1 (a) was chosen) cardinality test for `0..*`.**
  - Already covered in A.1 — confirm the new assertion is committed.

---

## C. Designer fixes (`glyph-renderer.ts` + tests)

- [ ] **C.1 — Rewrite `packages/designer/src/cy/glyph-renderer.ts` to match the plan's `glyphFor` shape.**
  - Replace the file with a single export:
    ```ts
    import type { Cardinality } from '@modeler/lsp';
    export function glyphFor(card: Cardinality | null): string { ... }
    ```
  - Each non-null case must return a `<g class="glyph-<name>">…</g>` snippet with the exact child shape the plan demands (`D-er-rendering.md:11-17`):
    - `'one'` → `<g class="glyph-one"><line .../></g>` (single perpendicular tick).
    - `'zero-or-one'` → `<g class="glyph-zero-or-one"><line .../><circle .../></g>`.
    - `'many'` → `<g class="glyph-many">` containing **three separate `<line>` elements** forming the crow's-foot fan (not one `<path>` with three strokes).
    - `'one-or-many'` → `<g class="glyph-one-or-many">` with one perpendicular `<line>` plus three crow's-foot `<line>`s.
  - `glyphFor(null)` returns `''`.
  - Delete `GlyphResult.offset` (dead). Delete `cardinalityToGlyph` and `buildEdgeStyle` (no callers will remain after C.3).
  - Keep `buildEdgeLabelHtml` and `edgeHasCardinalityLabel` **only if** A.2 picked the centered-label path; otherwise delete them too.

- [ ] **C.2 — Create `packages/designer/src/cy/__tests__/glyph-renderer.test.ts`.**
  - Use `jsdom` (already configured for the designer's vitest env).
  - One `it(...)` per case in the plan (D-er-rendering.md:11-17), parsing the returned string with `DOMParser` and asserting:
    - Root element is `<g>` and its `class` attribute equals `glyph-<name>`.
    - Counts: `g.querySelectorAll('line').length`, `g.querySelectorAll('circle').length`, `g.querySelectorAll('path').length` match the plan exactly.
  - One `it('glyphFor(null) returns empty string', () => expect(glyphFor(null)).toBe(''));`.
  - One snapshot test per case: `expect(glyphFor('one')).toMatchSnapshot();` etc. Run vitest once with `-u` to seed snapshots, review the saved snapshots in `__snapshots__/`, then commit them.
  - Verify: `pnpm --filter @modeler/designer test`.

- [ ] **C.3 — Update `packages/designer/src/cy/adapter.ts` to consume the new `glyphFor`.**
  - If A.2 chose the overlay path: remove all references to `buildEdgeLabelHtml`/`edgeHasCardinalityLabel` from the adapter. The edge element's `data` should carry `fromCardinality` / `toCardinality` (which it already does) but no longer the `edgeLabelHtml` / `hasCardinalityLabel` fields. The overlay reads `fromCardinality` / `toCardinality` directly.
  - If A.2 chose the centered-label path: rewrite `buildEdgeLabelHtml` to use `glyphFor(card)` (returning `''` for nulls) and remove `cardinalityToGlyph` references.

- [ ] **C.4 — Create `packages/designer/src/cy/__tests__/adapter-er.test.ts`** with the four cases from `D-er-rendering.md:21-26`:
  1. Entity with `displayLabel: { cs: 'Artikl', en: 'Item' }`, `preferredLanguage: 'cs'`: the adapter's input `ModelGraph` already has `label === 'Artikl'` (after B.1); assert the node's `labelHtml` opens with `Artikl`.
  2. Same entity but `preferredLanguage: 'de'` (missing in displayLabel) → `label === 'Item'`; assert `labelHtml` opens with `Item`.
  3. Same entity with no `displayLabel` → `label === 'foo'`; assert `labelHtml` opens with `foo`.
  4. Relation edge with `fromCardinality: 'one'`, `toCardinality: 'many'` → the produced edge `data` has both values.
  - Build the `ModelGraph` input fixture by hand (don't call into `buildModelGraph` — the adapter test is meant to be input-driven, see how `adapter.test.ts` does it).
  - Verify: `pnpm --filter @modeler/designer test`.

---

## D. Canvas + glyph integration

(Do whichever sub-path matches the A.2 decision.)

- [ ] **D.1 — Canvas overlay implementation (overlay path).**
  - Edit `packages/designer/src/components/Canvas.tsx`:
    - Remove the second `nodeHtmlLabel` entry (lines 119-124) and the duplicate `edge[kind = "relation"][hasCardinalityLabel = true]` style (lines 105-108).
    - Add a second `<div ref={overlayRef} className="cy-overlay" style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }} />` sibling of the cytoscape container; wrap both in a `position: relative` parent.
    - In a new `useEffect`, register `cy.on('render zoom pan', renderOverlay)` where `renderOverlay` reads each relation edge's `sourceEndpoint()`/`targetEndpoint()` (screen coordinates after panning/zooming), computes the edge tangent angle, and assembles an SVG document with two `<g transform="translate(x,y) rotate(angle)">` wrappers per edge, each containing `glyphFor(edge.data('fromCardinality'))` / `glyphFor(edge.data('toCardinality'))`.
    - Throttle the handler with `requestAnimationFrame` (one frame's worth of pending updates collapses to a single repaint).

- [ ] **D.1' — Canvas centered-label implementation (label path).**
  - Edit `packages/designer/src/components/Canvas.tsx` to keep the centered label but **fix the duplicate style block** (lines 97-108 — drop the second copy) and add inline comments explaining the centroid-positioning trade-off acknowledged in A.2's amendment.

- [ ] **D.2 — Create `packages/designer/src/components/__tests__/Canvas-er.test.tsx`** (per `D-er-rendering.md:27`):
  - Mock `cytoscape`, `cytoscape-cose-bilkent`, `cytoscape-node-html-label`, and the glyph-renderer module (`vi.mock('../../cy/glyph-renderer', () => ({ glyphFor: vi.fn(() => '<g/>') }))`).
  - Feed a `ModelGraph` with one relation edge `fromCardinality: 'one', toCardinality: 'many'`.
  - For the overlay path: trigger the mocked `cy.on('render', ...)` callback and assert `glyphFor` was called with `'one'` and `'many'`.
  - For the centered-label path: assert the adapter's call chain produces an edge element whose `data` includes the two cardinality values, and that the mocked `nodeHtmlLabel` was registered with two entries (one node-query, one edge-query).
  - Verify: `pnpm --filter @modeler/designer test`.

- [ ] **D.3 — Add an integration test for `modeler/getModelGraph` returning relation edges with cardinality.**
  - Create or extend `tests/integration/src/lsp-phase-03-custom-methods.test.ts` with an `it('modeler/getModelGraph returns relation edges with from/toCardinality and localized entity labels')` test.
  - Boot the LSP via the existing PassThrough harness, `initialize` with a workspace that contains a `modeler.toml` setting `preferred = "cs"` and an `er.ttr` with one entity that has `displayLabel: { cs: "Artikl", en: "Item" }` and one relation with `cardinality: { from: "1", to: "n" }`.
  - Send `modeler/getModelGraph` with `schema: 'er'` and assert: node `label === 'Artikl'`, edge `fromCardinality === 'one'`, edge `toCardinality === 'many'`.
  - Verify: `pnpm --filter @modeler/integration-tests test`.

---

## E. Visual review (the one §D cannot be done without)

- [ ] **E.1 — Run `pnpm --filter @modeler/designer dev` against `samples/v1-metadata/`** and tick **every** sub-item below before checking D.5. If any fails, fix it and re-run.
  - [ ] E.1.a — Switch the schema selector to `er`. The graph populates with all entities from `er.ttr`. No "empty project" placeholder.
  - [ ] E.1.b — Czech entity labels: pick three entities at random and confirm their on-canvas label matches the `def.name` (since the sample has no `displayLabel`). If you decide to add a `displayLabel` to one entity to exercise the localization code path, do it on a single entity, confirm the label changes when `preferred` is flipped between `cs` and `en` in `modeler.toml`, then revert.
  - [ ] E.1.c — Attribute rows: confirm `nameAttribute` and `codeAttribute` rows show their `★` / `#` markers (B.2). Pick an entity that defines `nameAttribute` (search `er.ttr` first; if none, add one to a test entity for the duration of the visual review).
  - [ ] E.1.d — Glyphs at edge endpoints: for at least three relation edges, screenshot the two endpoint glyphs and visually match them against the `.ttr` source `cardinality: { from: "...", to: "..." }`. The glyph shapes must follow the legend (one=bar, zero-or-one=bar+circle, many=crow's-foot, one-or-many=bar+crow's-foot).
  - [ ] E.1.e — Drag one entity 200px in any direction; both endpoint glyphs follow the new edge geometry (orientation re-computes, not just position).
  - [ ] E.1.f — Switch back to `db`; confirm `db` rendering still works (no regression from any of the changes above). Switch back to `er`; confirm no double-render or stale-glyph artifacts.

- [ ] **E.2 — Update `docs/plan/progress-phase-03.md` Section D**: tick D.5 only after E.1 is fully green; add a one-line note under the section pointing at the contract amendments produced by A.1 / A.2.

---

## F. Cleanup

- [ ] **F.1 — Remove dead code from `glyph-renderer.ts`** (the unused `GlyphResult.offset`, the constant-returning `buildEdgeStyle`, the unused `cardinalityToGlyph` if C.1 dropped it).
- [ ] **F.2 — Remove the duplicate `edge[kind = "relation"]` style block** in `Canvas.tsx:97-108` (covered by D.1 / D.1').

---

## Final verification (run all four; all must pass)

- [ ] `pnpm --filter @modeler/lsp test`
- [ ] `pnpm --filter @modeler/designer test`
- [ ] `pnpm --filter @modeler/integration-tests test`
- [ ] `pnpm -r lint && pnpm -r typecheck && pnpm -r build`

Only after every checkbox above is ticked may `docs/plan/progress-phase-03.md` Section D be marked complete.
