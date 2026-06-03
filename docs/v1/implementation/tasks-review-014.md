# Tasks — Review 014 (Phase 3 Section D, third pass)

All eight findings from review-013 are resolved; what remains is one missing assertion in a Canvas-er test, one missing sibling test for `codeAttribute`, and the manual visual review (D.5). After these three tasks, **Section D is done.**

---

## G. Test-quality fixes (small, independent)

- [ ] **G.1 — Add the missing assertion to the third Canvas-er test.**
  - Open `packages/designer/src/components/__tests__/Canvas-er.test.tsx`.
  - Find the test starting at line 84 (`it('calls glyphFor with each edge cardinalities when the render handler fires', …)`). Currently it has no `expect(...)` calls.
  - Rewrite the body so it (a) waits for `cy` to be ready, (b) fires the `render zoom pan` handler manually, (c) lets `requestAnimationFrame` flush, (d) asserts the mocked `glyphFor` was called with both cardinalities. Drop the fake-timer dance — it isn't needed once you invoke the handler directly. Suggested body:
    ```ts
    it('calls glyphFor with each edge cardinalities when the render handler fires', async () => {
      render(<Canvas graph={null} displayMode="just-names" onNodeSelect={vi.fn()} />);
      await waitFor(() => expect(handlers['render zoom pan']?.length).toBeGreaterThan(0));
      handlers['render zoom pan'][0]({});                       // fire the cy event
      await new Promise(r => requestAnimationFrame(() => r(null))); // let the rAF flush
      expect(glyphForMock).toHaveBeenCalledWith('one');
      expect(glyphForMock).toHaveBeenCalledWith('many');
    });
    ```
    The mocked `edges()` iterator at lines 32-40 already yields one edge with `fromCardinality: 'one'`, `toCardinality: 'many'`, so the assertion lands without any additional fixture work.
  - Verify: `pnpm --filter @modeler/designer test` stays green.

- [ ] **G.2 — Add a `codeAttribute` sibling test in `model-graph.test.ts`.**
  - Open `packages/lsp/src/__tests__/model-graph.test.ts`. Find the `'entity with nameAttribute marks the row'` test (around line 83).
  - Add a sibling test immediately after it:
    ```ts
    it('entity with codeAttribute marks the row', () => {
      const content = `schema er namespace entity def entity foo {
        attributes: [def attribute id { type: int }, def attribute sku { type: text }],
        codeAttribute: sku
      }`;
      const node = buildModelGraph(parseString(content, 'file:///x.ttr').ast!, 'er').nodes[0];
      expect(node.rows.find(r => r.name === 'sku')!.isCodeAttribute).toBe(true);
      expect(node.rows.find(r => r.name === 'id')!.isCodeAttribute).toBe(false);
    });
    ```
  - Verify: `pnpm --filter @modeler/lsp test` stays green and now shows 39 tests.

- [ ] **G.3 — (Optional) Stabilize the `handlers` map in `Canvas-er.test.tsx`.**
  - This is a low-priority cleanup; only do it if you intend to enable test parallelism for the designer suite.
  - Replace the module-scoped `const handlers: ... = {}` (line 8) and the `beforeEach` delete-loop (lines 62-64) with `vi.hoisted(() => ({ handlers: {} as Record<string, ((arg: unknown) => void)[]> }))`, then reset inside `beforeEach` with `handlers = {}` (a fresh object).
  - Skip this task if you don't plan to touch parallelism.

---

## D.5 — Run the visual review

The dev server now works (`pnpm run dev` from the repo root). Run it against `samples/v1-metadata/` and tick each sub-item:

- [ ] **D.5.a** — Switch the schema toggle to `er`. Graph populates without errors.
- [ ] **D.5.b** — Pick three entities at random; their on-canvas titles match `def.name` from the source `.ttr` (sample has no `displayLabel` declared, so labels fall back to `name` per the contract).
- [ ] **D.5.c** — Find an entity with `nameAttribute:` or `codeAttribute:` set in `samples/v1-metadata/er.ttr` (grep first). Confirm `★` shows on the name-attribute row and `#` on the code-attribute row.
- [ ] **D.5.d** — For at least three relation edges, both endpoint glyphs are visible **at the edge–node boundary** (not midpoint, not inside the node body), and their shape matches the source `cardinality: { from: "...", to: "..." }`. The sample uses `"0..*"` (now mapped to `'many'` per contract amendment v3) and `"0..1"` → `'zero-or-one'`.
- [ ] **D.5.e** — Drag one entity 200 px in any direction. Both endpoint glyphs follow with correct rotation along the new edge angle.
- [ ] **D.5.f** — Pan + zoom the canvas. Glyphs stay glued to their edges and remain at the boundary at all zoom levels.
- [ ] **D.5.g** — Switch back to `db`; no regression (db rendering works, no leftover overlay artifacts). Switch back to `er`; no stale glyphs from the previous render.

- [ ] **Tick D.5 in `docs/plan/progress-phase-03.md`** once every sub-item above is green. Add a one-line note pointing at this review (`review-014.md`).

---

## Final verification

- [ ] `pnpm --filter @modeler/lsp test` (expect 39 after G.2)
- [ ] `pnpm --filter @modeler/designer test` (expect 42)
- [ ] `pnpm --filter @modeler/integration-tests test` (expect 22)
- [ ] `pnpm -r lint && pnpm -r typecheck && pnpm -r build`

Section D is **done** when all four boxes immediately above and the D.5 sub-items are ticked.
