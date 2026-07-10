# Tasks — Review 010 (Phase 3, Section C)

Companion to `review-010.md`. Tasks 1–5 are showstoppers; 6–8 are should-fix; 9 is final acceptance. Do them in order — each verification command must pass before you tick the box.

## 1. File the contract amendment for `graphsBySchema` + `storeGraph` (F2)

Mini-list C.5 made this explicit ("Open a contract amendment if the cache field is needed (it is)"). Do this before the implementation tasks below so the rest of the PR has a stable target.

- [ ] **1.1** Open `docs/design/phase-03-contracts.md`. At the top, change `**Status:** v1, 2026-05-15` to `**Status:** v2, 2026-05-15`.
- [ ] **1.2** In §2 ("Designer-side state types"), inside the `DesignerState` interface, add the new field with a comment:

  ```ts
    /** Per-schema graph cache. Flipping the schema toggle reads from here
     *  before falling back to a getModelGraph round-trip. Cleared on loadProject. */
    graphsBySchema: Record<RenderableSchemaCode, ModelGraph | null>;
  ```

- [ ] **1.3** In the `initialDesignerState` constant in §2, add:

  ```ts
    graphsBySchema: { db: null, er: null },
  ```

- [ ] **1.4** In the §2 reducer-actions block, add the action variant **before** `setError`:

  ```ts
    | { type: 'storeGraph'; schema: RenderableSchemaCode; graph: ModelGraph }
  ```

- [ ] **1.5** At the very bottom of `phase-03-contracts.md`, under `### Changelog`, add one line:

  ```
  - **v2 (2026-05-15)** — Section C: added `graphsBySchema: Record<RenderableSchemaCode, ModelGraph | null>` to `DesignerState` (cleared on `loadProject`) and the `storeGraph` action; cache exists so schema toggles after the first round-trip don't refetch. Name shortened from the mini-list's `graphsByCachedSchema` suggestion to `graphsBySchema`.
  ```

- [ ] **1.6** Verify by running: `grep -n "graphsBySchema\|storeGraph" docs/design/phase-03-contracts.md` returns four hits (state-type, initialDesignerState, action variant, changelog). Tick this box only if all four are present.

## 2. Reset `graphsBySchema` on `loadProject` (F3)

`designer-reducer.ts:18-23` resets `symbolDetails` but not `graphsBySchema`. Fix the same way.

- [ ] **2.1** In `packages/designer/src/state/designer-reducer.ts`, change the `loadProject` case to:

  ```ts
  case 'loadProject':
    return {
      ...state,
      projectUri: action.projectUri,
      symbolDetails: {},
      graphsBySchema: { db: null, er: null },
    };
  ```

- [ ] **2.2** Add a reducer test in `packages/designer/src/state/__tests__/designer-reducer.test.ts`, modelled on the existing `'loadProject' resets symbolDetails cache` test:

  ```ts
  it("'loadProject' resets graphsBySchema cache", () => {
    const stateWithGraphs: DesignerState = {
      ...initialDesignerState,
      graphsBySchema: {
        db: { schemaCode: 'db', nodes: [], edges: [] },
        er: { schemaCode: 'er', nodes: [], edges: [] },
      },
    };
    const action: DesignerAction = { type: 'loadProject', projectUri: 'file:///y' };
    const state = designerReducer(stateWithGraphs, action);
    expect(state.graphsBySchema).toEqual({ db: null, er: null });
  });
  ```

- [ ] **2.3** Verify by running: `pnpm --filter @modeler/designer test`. Designer test count should go from 24 to 25 — confirm the new test is green.

## 3. Delete the dead `currentGraph` mirror and consume reducer state directly (F4)

`App.tsx:17` and the effect at `App.tsx:50-52` mirror `state.graphsBySchema` into a local `useState`. The mirror is pointless and introduces a render-cycle delay.

- [ ] **3.1** In `packages/designer/src/App.tsx`, delete line 17:

  ```ts
  const [currentGraph, setCurrentGraph] =
    useState<typeof initialDesignerState.graphsBySchema>({ db: null, er: null });
  ```

- [ ] **3.2** Delete the mirror `useEffect` (lines 50-52):

  ```ts
  useEffect(() => {
    setCurrentGraph(state.graphsBySchema);
  }, [state.graphsBySchema]);
  ```

- [ ] **3.3** Change the `<Canvas graph=...>` prop at line 86 from `currentGraph[state.activeSchema]` to `state.graphsBySchema[state.activeSchema]`.
- [ ] **3.4** Remove the now-unused `useState` import from the top of `App.tsx` (it's still used by `nlPaneOpen`, so leave it — just confirm there's only one `useState` import line).
- [ ] **3.5** Verify by running: `pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer test`. Tests still green; one less `useState` and one less `useEffect` in `App.tsx`.

## 4. Guard the getModelGraph effect against cache hits (F5)

The effect at `App.tsx:40-48` fires unconditionally on `[state.projectUri, state.activeSchema]`. C.5 promised "flipping back is instant".

- [ ] **4.1** In `packages/designer/src/App.tsx`, change the cache-fetch effect to:

  ```ts
  useEffect(() => {
    const client = clientRef.current;
    const uri = state.projectUri;
    if (!client || !uri) return;
    const schema = state.activeSchema;
    // Cache hit: skip the LSP round-trip and reuse the prior graph (including
    // any drag positions baked into Cytoscape state from the first render).
    if (state.graphsBySchema[schema]) return;
    client.getModelGraph(uri, schema)
      .then((graph) => dispatch({ type: 'storeGraph', schema, graph }))
      .catch((err) => dispatch({ type: 'setError', message: String(err) }));
  }, [state.projectUri, state.activeSchema, state.graphsBySchema]);
  ```

  Note the added `state.graphsBySchema` dep — required so the effect re-runs after the *first* fetch's dispatch (otherwise the same effect-run that started the fetch wouldn't realise the cache is now populated). The cache-hit guard prevents the second run from firing a duplicate fetch.

- [ ] **4.2** Verify by running: `pnpm --filter @modeler/designer test`. Existing tests still green.

## 5. Add the three (now four) App-level behavioral tests (F1)

The mini-list's three "Tests-first" cases live in App.tsx, not Canvas.tsx. Plus the cache-hit case from task 4. Either rename `Canvas.test.tsx` to `App-getModelGraph.test.tsx` or add a new file alongside it.

- [ ] **5.1** Create `packages/designer/src/__tests__/App-getModelGraph.test.tsx`. Mock the LSP client at the module-level via `vi.mock('../lsp-client', ...)` exposing a `mockGetModelGraph: vi.fn(() => Promise.resolve(emptyGraph))` and a `mockOpenDocument: vi.fn(() => Promise.resolve())`. Mock cytoscape and the two extensions the same way `Canvas.test.tsx` already does, so the Canvas's effects can mount without blowing up.
- [ ] **5.2** Test case `5.1`: after `loadProject` settles, `mockGetModelGraph` is called exactly once with the current `(projectUri, 'db')`.
  - Render `<App />`.
  - Resolve the createLspClient mock so `clientRef.current` is set.
  - Dispatch `loadProject` via a test helper that simulates the Header's directory pick (or simpler: expose a controlled `useReducer` initial state — but the cleanest path is to render the real App and drive it via Header callbacks fired through `fireEvent`). If the indirection is too painful, **lift the cache-fetch logic into a custom hook** (`useProjectGraph(state, dispatch, client)`) and unit-test that hook in isolation.
  - Use `await waitFor(() => expect(mockGetModelGraph).toHaveBeenCalledExactlyOnceWith(uri, 'db'))`.
- [ ] **5.3** Test case `5.2`: dispatching `setDisplayMode` does NOT re-fire `mockGetModelGraph`.
  - From the same App-rendered tree (or hook test), call `setDisplayMode` for the active schema and assert `mockGetModelGraph` has still been called only once.
- [ ] **5.4** Test case `5.3`: dispatching `switchSchema` from `db` to `er` DOES re-fire `mockGetModelGraph` with `'er'`.
  - Assert call count is 2; second call's args are `(uri, 'er')`.
- [ ] **5.5** Test case `5.4`: switching back to `db` after `er` does NOT re-fire (cache hit).
  - Set `activeSchema: db` again; assert call count is still 2.
- [ ] **5.6** Verify by running: `pnpm --filter @modeler/designer test`. Designer total should be 25 (from task 2) + 4 = 29. Tick this box only after all four cases are green.

## 6. Style: make HTML labels readable on dark nodes (F7)

Either lighten the node background or style the row classes. Pick **one**; do not ship illegible labels.

### Path A (recommended) — style the row classes light

- [ ] **6.1A** In `packages/designer/src/index.css` (create if missing), add:

  ```css
  .cy-node-label { color: #f8fafc; font-family: ui-monospace, monospace; font-size: 11px; padding: 4px 6px; line-height: 1.3; }
  .cy-row { display: flex; gap: 6px; align-items: baseline; }
  .cy-row-name { color: #f8fafc; }
  .cy-row-type { color: #94a3b8; font-style: italic; }
  .cy-row-badge { color: #facc15; font-weight: 600; }
  ```

- [ ] **6.2A** Confirm `index.css` is imported by `main.tsx` (it should be; the Phase-0 scaffold imports it).
- [ ] **6.3A** Verify by running: `pnpm --filter @modeler/designer dev` and loading `samples/v1-metadata/`. Row text is legible on the dark node background.

### Path B — lighten the node background, leave HTML default-styled

- [ ] **6.1B** In `packages/designer/src/components/Canvas.tsx:55-65`, change `'background-color': '#0f172a'` to `'#f8fafc'` (slate-50) and `'border-color': '#475569'` to `'#cbd5e1'` (slate-300). Leave `color: '#1e293b'` for the bare label.
- [ ] **6.2B** Verify by running: `pnpm --filter @modeler/designer dev`. Confirm rows are readable.

## 7. Add HTML escaping in `adapter.ts` (F8)

ttr identifiers are safe today but the adapter has no defence in depth.

- [ ] **7.1** In `packages/designer/src/cy/adapter.ts`, add a small helper near the top:

  ```ts
  function escape(s: string): string {
    return s
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
  ```

- [ ] **7.2** Apply `escape(...)` to every interpolation of `row.name`, `row.type`, and the constraint badge strings in `renderRowHtml`. The constraint strings are literals (`'PK'`, `'NN'`) — wrap them too for consistency.
- [ ] **7.3** Update the existing adapter tests if needed (the assertions use `toContain` substring matching, so `&amp;`-encoded ampersands would only break a test that asserted a literal `&` substring — unlikely).
- [ ] **7.4** Add one adapter test:

  ```ts
  it('escapes < > & in row names and types', () => {
    const graph = makeGraph([
      makeNode({ rows: [{ name: 'a<b>', qname: 'q', kind: 'column', type: 'int&', isKey: false, optional: true }] }),
    ]);
    const html = (modelGraphToCyElements(graph, 'with-types')[0].data as Record<string, unknown>).labelHtml as string;
    expect(html).toContain('a&lt;b&gt;');
    expect(html).toContain('int&amp;');
    expect(html).not.toContain('<b>');
  });
  ```

- [ ] **7.5** Verify by running: `pnpm --filter @modeler/designer test`. Adapter test count should rise by 1.

## 8. Hoist `cytoscape.use(...)` / `nodeHtmlLabel(...)` to module scope (F nit)

Extensions register *globally* on the cytoscape module. The current effect re-registers them on every mount. React strict mode in dev mounts twice; same registration twice. Cytoscape's `use` is idempotent for the same instance, but the pattern is wrong.

- [ ] **8.1** In `packages/designer/src/components/Canvas.tsx`, lift the dynamic imports out of the effect:

  ```ts
  const cytoscapeReadyPromise = Promise.all([
    import('cytoscape'),
    import('cytoscape-cose-bilkent'),
    import('cytoscape-node-html-label'),
  ]).then(([cyMod, coseMod, nlMod]) => {
    const cytoscape = (cyMod as any).default ?? cyMod;
    const cose = (coseMod as any).default ?? coseMod;
    const nl = (nlMod as any).default ?? nlMod;
    cytoscape.use(cose);
    nl(cytoscape);
    return cytoscape;
  });
  ```

  Hoist this declaration to module scope (above the component). Inside the mount effect, `await cytoscapeReadyPromise` once.

- [ ] **8.2** Verify by running: `pnpm --filter @modeler/designer test && pnpm --filter @modeler/designer build`. Bundle splits should be unchanged.

## 9. Final acceptance

- [ ] **9.1** From the repo root: `pnpm -r build && pnpm -r test && pnpm -r lint && pnpm -r typecheck`. All exit 0, no warnings.
- [ ] **9.2** Designer test count is 29 (24 baseline + 1 reducer test in task 2 + 4 App-level tests in task 5; the adapter HTML-escape test in task 7 brings it to 30 if you took that path).
- [ ] **9.3** Run the dev server (`pnpm --filter @modeler/designer dev`), pick `samples/v1-metadata/` via "Load Project Folder", confirm:
  - The first paint shows the `db` schema with ≥5 FK edges and row content visible on each table.
  - Flipping the display-mode toggle changes row detail (names → with-types → with-constraints) without a network round-trip (check devtools).
  - Switching to `er` triggers exactly one new `getModelGraph` request.
  - Switching back to `db` does NOT trigger another request and preserves any node positions the user dragged.
- [ ] **9.4** Tick C.1–C.6 in `docs/plan/progress-phase-03.md` only after 9.1–9.3 pass.
- [ ] **9.5** Stage and commit. Commit message should name (a) Path A or B for task 6, (b) confirm the contract bump to v2.
