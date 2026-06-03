# Tasks — Review 013 (Phase 3 Section D follow-up)

All seven structural fixes from `tasks-review-012.md` landed. These tasks close the remaining runtime defects in the SVG overlay and the test-coverage gaps. **Do not** tick D.5 in `progress-phase-03.md` before every box below is ticked **and** the manual visual review has actually been performed.

---

## A. Fix the overlay so glyphs actually render (highest priority)

- [ ] **A.1 — Resolve the async-cy race in `Canvas.tsx`.**
  - Open `packages/designer/src/components/Canvas.tsx`.
  - Add a React state: `const [cyReady, setCyReady] = useState(false);`.
  - Inside the existing `cytoscapeReadyPromise.then((cytoscape) => { ... })` block (around line 59), after the line `cyRef.current = cy;` (currently line 125), call `setCyReady(true)`.
  - Add `cyReady` to the overlay `useEffect`'s deps array (currently `[]` on line 251) → `[cyReady]`. Inside the effect, keep the early-return guards (`if (!cy || !overlay) return;`) — they now serve as a safety net rather than as the dominant code path.
  - Run `pnpm --filter @modeler/designer test` to confirm nothing broke.

- [ ] **A.2 — Replace the screen-coordinate transform with `renderedPosition`.**
  - In the same `Canvas.tsx`, inside `renderOverlay()` (around line 182), delete the `pan` and `zoom` lookups and the four `(... − pan) * zoom` lines (lines 183-184, 196-199).
  - Replace with:
    ```ts
    const sx = sourceNode.renderedPosition('x');
    const sy = sourceNode.renderedPosition('y');
    const tx = targetNode.renderedPosition('x');
    const ty = targetNode.renderedPosition('y');
    ```
  - Re-run the designer suite.

- [ ] **A.3 — Anchor glyphs at edge–node intersections, not node centers + offset.**
  - Still in `renderOverlay()`, replace the source/target `renderedPosition()` calls from A.2 with:
    ```ts
    const sEnd = edge.sourceEndpoint();   // { x, y } in rendered coords
    const tEnd = edge.targetEndpoint();
    const sx = sEnd.x, sy = sEnd.y, tx = tEnd.x, ty = tEnd.y;
    ```
    (Verify the return shape against cytoscape 3.30's API — it returns a `{ x, y }` pos object in rendered coordinates.)
  - Drop the 16 px `offset` block — the endpoints already sit on the node boundary, no manual offset needed.
  - Keep the tangent-angle computation (`Math.atan2(dy, dx)`) and the `fromAngle = angle + 180` flip for the source-side glyph (so the bar/crow's-foot points *toward* the node, not away).

- [ ] **A.4 — Local sanity check before tests.**
  - Run `pnpm --filter @modeler/designer dev`, switch the schema to `er` against `samples/v1-metadata/`, confirm:
    - Glyphs visibly appear at both ends of every relation edge.
    - Glyphs sit at the node border, not inside the node body.
    - Pan + zoom keep glyphs glued to the edge.
  - If anything is wrong here, fix it now — do **not** proceed to the test-coverage tasks first.

---

## B. Fix the glyph geometry

- [ ] **B.1 — Delete the duplicate `<line>` in `glyphFor('one-or-many')`.**
  - In `packages/designer/src/cy/glyph-renderer.ts:12`, the SVG currently emits four `<line>`s but the 1st and 3rd are identical (`x1="0" y1="-16" x2="0" y2="-22"`). Remove the third `<line>`. Final output should be: 1 perpendicular tick + 3 crow's-foot lines = exactly 4 distinct lines.
  - Re-run snapshot tests: `pnpm --filter @modeler/designer test -- -u`. Verify the regenerated `glyph-renderer.test.ts.snap` no longer contains the duplicate. Commit the updated snapshot.

- [ ] **B.2 — Reconcile fan length between `many` and `one-or-many`.**
  - `many` spans 16 px vertically; `one-or-many`'s crow's-foot currently spans only 6 px. Pick one of:
    - **Shrink `many` to a 6 px fan** (smaller glyphs overall, leaves more room for labels), or
    - **Grow `one-or-many`'s fan to 16 px** (consistent with `many`, but the combined glyph becomes tall).
  - Apply the chosen geometry to both functions and update the snapshot with `-u`.
  - Add a comment at the top of `glyph-renderer.ts` documenting the chosen design baseline so future edits stay consistent.

---

## C. Strengthen the test coverage

- [ ] **C.1 — Make `Canvas-er.test.tsx` actually exercise the glyph pipeline.**
  - Open `packages/designer/src/components/__tests__/Canvas-er.test.tsx`.
  - Change the cytoscape mock so the recorded handlers are accessible. One pattern:
    ```ts
    const handlers: Record<string, Function[]> = {};
    const mockDefault = vi.fn((_opts: unknown) => ({
      elements: vi.fn(() => ({ remove: vi.fn().mockReturnThis() })),
      add: vi.fn().mockReturnThis(),
      layout: vi.fn(() => ({ run: vi.fn() })),
      nodes: vi.fn(() => ({ forEach: vi.fn() })),
      edges: vi.fn(() => ({
        forEach: (cb: (e: unknown) => void) => cb({
          data: (k: string) => k === 'fromCardinality' ? 'one' : k === 'toCardinality' ? 'many' : undefined,
          source: () => ({ renderedPosition: () => 0 }),
          target: () => ({ renderedPosition: () => 100 }),
          sourceEndpoint: () => ({ x: 0, y: 0 }),
          targetEndpoint: () => ({ x: 100, y: 0 }),
        }),
        [Symbol.iterator]: function*() { yield {
          data: (k: string) => k === 'fromCardinality' ? 'one' : k === 'toCardinality' ? 'many' : undefined,
          source: () => ({ renderedPosition: () => 0 }),
          target: () => ({ renderedPosition: () => 100 }),
          sourceEndpoint: () => ({ x: 0, y: 0 }),
          targetEndpoint: () => ({ x: 100, y: 0 }),
        }; },
      })),
      on: vi.fn((evt: string, cb: Function) => { (handlers[evt] ??= []).push(cb); }),
      off: vi.fn(),
      nodeHtmlLabel: vi.fn(),
      destroy: vi.fn(),
      pan: vi.fn(() => ({ x: 0, y: 0 })),
      zoom: vi.fn(() => 1),
    }));
    ```
    (Adjust to match whichever API the overlay actually uses after A.2/A.3.)
  - Add tests:
    1. `it('registers a render/zoom/pan handler when cy becomes ready', ...)` — render the component, await `waitFor(() => expect(handlers['render zoom pan']).toBeDefined())`, assert it's a function.
    2. `it('calls glyphFor with each edge\'s cardinalities when the handler fires', ...)` — invoke `handlers['render zoom pan'][0]()`, then assert the mocked `glyphFor` was called with `'one'` and `'many'`. Wait through a `requestAnimationFrame` (vi can fake-timer it: `vi.useFakeTimers(); vi.advanceTimersByTime(16); vi.useRealTimers();` or use `await Promise.resolve()` + `await new Promise(r => requestAnimationFrame(r))` depending on env).
  - This will fail today (because of N1) and pass after A.1 — exactly the regression net you want.

- [ ] **C.2 — Reconcile fallback policy and align `adapter-er.test.ts` with it.**
  - Read `phase-03-contracts.md` § 4 (current rule: fallback to `name`) and `D-er-rendering.md` lines 22-23 (plan-bullet 2 says fallback to `en`).
  - Pick one. The contract is currently authoritative (and matches the LSP impl); the most likely outcome is:
    - **Update `D-er-rendering.md:22-23`** to read: *"Same entity with `preferredLanguage: 'de'` (missing) → falls back to the bare `name` ('foo'). The contract does not specify a language-to-language fallback."*
    - Add a corresponding test to `packages/lsp/src/__tests__/model-graph.test.ts`:
      ```ts
      it('entity with displayLabel falls back to def.name when preferredLanguage is missing', () => {
        const content = `schema er namespace entity def entity foo {
          displayLabel: { cs: "Artikl", en: "Item" },
          attributes: [def attribute id { type: int }]
        }`;
        const ast = parseString(content, 'file:///x.ttr').ast!;
        expect(buildModelGraph(ast, 'er', 'de').nodes[0].label).toBe('foo');
      });
      ```
      (You already have a similar assertion at `model-graph.test.ts:78` — verify it's exactly this and add a comment pointing at the contract clause.)
  - **Then decide what to do with `adapter-er.test.ts`:**
    - **(Recommended) Delete it** and move all four bullets' coverage to `model-graph.test.ts` — that's where the localization logic actually lives. The adapter is a thin pass-through; the existing `adapter.test.ts` already covers its node/edge wiring.
    - Or keep it and rewrite the three label-related tests to assert the adapter passes `node.label` through unchanged to `data['label']` (and leave localization to the LSP-layer tests). The current file is already doing this — just delete the misleading test names that claim to verify localization.

- [ ] **C.3 — Strengthen integration test 4.5b's localization claim.**
  - In `tests/integration/src/lsp-phase-03-custom-methods.test.ts:188-221`, the test name promises "localized entity labels". Either:
    - Rename to `'… returns relation edges with from/toCardinality'`, dropping the localization phrase, **or**
    - Add a positive assertion:
      ```ts
      const artikl = result.nodes.find(n => n.qname === 'er.entity.artikl');
      expect(artikl).toBeDefined();
      // er.ttr does not declare displayLabel on artikl, so fallback is def.name.
      expect(artikl!.label).toBe('artikl');
      ```
      This locks in the contract's fallback behavior end-to-end.

---

## D. Run the visual review (D.5) and tick the box

- [ ] **D.1 — `pnpm --filter @modeler/designer dev`**, open against `samples/v1-metadata/`.
  - [ ] D.1.a — Schema toggle: `er` populates without errors.
  - [ ] D.1.b — Pick three entities at random; their on-canvas titles match their `def.name` (the sample has no `displayLabel`).
  - [ ] D.1.c — `nameAttribute` rows on at least one entity show `★ `; `codeAttribute` rows show `# ` if any entity defines one. (Search `er.ttr` first for `nameAttribute:`/`codeAttribute:` to find an entity that defines them.)
  - [ ] D.1.d — For at least three relation edges, both endpoint glyphs are visible **at the edge–node boundary** (not at the edge midpoint, not inside the node body) and their shape matches the source `cardinality: { from: "...", to: "..." }`. The mapping is in `phase-03-contracts.md` §8.
  - [ ] D.1.e — Drag one entity 200 px in any direction; both endpoint glyphs follow with correct rotation along the new edge angle.
  - [ ] D.1.f — Pan + zoom the canvas; glyphs stay glued to their edges and remain at the boundary.
  - [ ] D.1.g — Switch back to `db`; no regression (db rendering still works, no leftover overlay artifacts). Switch back to `er`; no stale glyphs from the previous render.

- [ ] **D.2 — Tick D.5 in `docs/plan/progress-phase-03.md`** once every box in D.1 is green. Add a one-line note pointing at this review (`review-013.md`).

---

## Final verification

- [ ] `pnpm --filter @modeler/lsp test`
- [ ] `pnpm --filter @modeler/designer test`
- [ ] `pnpm --filter @modeler/integration-tests test`
- [ ] `pnpm -r lint && pnpm -r typecheck && pnpm -r build`

Only after **every** box above is ticked may Section D be considered complete.
