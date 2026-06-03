# Tasks — Review 011 (Phase 3, Section C — last gap)

Companion to `review-011.md`. One PR. Section C is one task away from honestly done.

## 1. Write the four App-level behavioral tests (review-010 F1 / task 5)

Mini-list `C-db-rendering.md` "Tests-first" §2 named three cases. The cache-hit guard the dev added in task 4 implies a fourth. None are on disk.

Pick **one** of the two implementation paths below.

### Path A (recommended) — extract a `useProjectGraph` hook and unit-test it

Cleaner separation, no full-App render needed, no Cytoscape mock juggling.

- [ ] **1.1A** Create `packages/designer/src/hooks/useProjectGraph.ts`:

  ```ts
  import { useEffect } from 'react';
  import type { DesignerState, DesignerAction } from '../state/designer-reducer';
  import type { LspClient } from '../lsp-client';

  export function useProjectGraph(
    state: DesignerState,
    dispatch: React.Dispatch<DesignerAction>,
    client: LspClient | null
  ): void {
    useEffect(() => {
      const uri = state.projectUri;
      if (!client || !uri) return;
      const schema = state.activeSchema;
      // Cache hit: skip the LSP round-trip and reuse the prior graph.
      if (state.graphsBySchema[schema]) return;
      client.getModelGraph(uri, schema)
        .then((graph) => dispatch({ type: 'storeGraph', schema, graph }))
        .catch((err) => dispatch({ type: 'setError', message: String(err) }));
    }, [state.projectUri, state.activeSchema, state.graphsBySchema, client, dispatch]);
  }
  ```

  Be precise about the import paths — `DesignerState` lives in `designer-state.ts`, `DesignerAction` in `designer-reducer.ts` per the Section A split.

- [ ] **1.2A** In `packages/designer/src/App.tsx`, delete the inline `useEffect(() => { ... }, [state.projectUri, state.activeSchema, state.graphsBySchema])` block at lines ~37-46. Replace with a single line below the reducer setup:

  ```ts
  useProjectGraph(state, dispatch, clientRef.current);
  ```

  Add `import { useProjectGraph } from './hooks/useProjectGraph';` at the top. Verify `pnpm --filter @modeler/designer typecheck` is still green.

  Note: passing `clientRef.current` directly means the hook will re-run when the ref's `.current` settles — which happens once during the `createLspClient().then(...)` callback. If the hook fires before `clientRef.current` is set, the first guard (`if (!client || !uri) return`) handles it cleanly. The subsequent re-render (after `dispatch({ type: 'setError', ...})` or the first `setError(null)` from `onDiagnostics`) will re-evaluate with the populated ref. If you find this fragile, lift `client` into a `useState<LspClient | null>` inside the bootstrap effect and pass *that* value in — same shape, but explicit dependencies.

- [ ] **1.3A** Create `packages/designer/src/hooks/__tests__/useProjectGraph.test.tsx`. Use `@testing-library/react`'s `renderHook` to drive the hook through state transitions. The hook depends on `state`, `dispatch`, and `client` — pass mocks for all three. No Cytoscape involvement, no jsdom canvas, no DOM.

  ```ts
  import { renderHook } from '@testing-library/react';
  import { describe, it, expect, vi } from 'vitest';
  import { useProjectGraph } from '../useProjectGraph';
  import { initialDesignerState } from '../../state/designer-state';
  import type { DesignerState } from '../../state/designer-state';
  import type { ModelGraph } from '@modeler/lsp';

  function makeClient() {
    return {
      getModelGraph: vi.fn(() => Promise.resolve<ModelGraph>({ schemaCode: 'db', nodes: [], edges: [] })),
      openDocument: vi.fn(() => Promise.resolve()),
      getLayout: vi.fn(),
      setLayout: vi.fn(),
      exportLayout: vi.fn(),
      applyGraphEdit: vi.fn(),
      getSymbolDetail: vi.fn(),
      onDiagnostics: vi.fn(),
      dispose: vi.fn(),
    };
  }

  function makeState(over: Partial<DesignerState> = {}): DesignerState {
    return { ...initialDesignerState, ...over };
  }
  ```

- [ ] **1.4A** Add the four test cases inside one `describe('useProjectGraph', ...)`:

  - **Case 1 — first load fires exactly one getModelGraph call.**
    ```ts
    it('first load fires getModelGraph(uri, "db") exactly once', async () => {
      const client = makeClient();
      const dispatch = vi.fn();
      const state = makeState({ projectUri: 'file:///x', activeSchema: 'db' });
      renderHook(() => useProjectGraph(state, dispatch, client));
      await vi.waitFor(() => expect(client.getModelGraph).toHaveBeenCalledExactlyOnceWith('file:///x', 'db'));
    });
    ```

  - **Case 2 — displayMode change does NOT call getModelGraph.**
    ```ts
    it('displayMode change does not trigger getModelGraph', async () => {
      const client = makeClient();
      const dispatch = vi.fn();
      const initial = makeState({
        projectUri: 'file:///x',
        activeSchema: 'db',
        graphsBySchema: { db: { schemaCode: 'db', nodes: [], edges: [] }, er: null },
      });
      const { rerender } = renderHook(({ s }) => useProjectGraph(s, dispatch, client), { initialProps: { s: initial } });
      // Cache populated => no initial call.
      expect(client.getModelGraph).not.toHaveBeenCalled();
      // Flip displayMode only.
      const next = makeState({
        projectUri: 'file:///x',
        activeSchema: 'db',
        graphsBySchema: initial.graphsBySchema,
        viewports: {
          ...initial.viewports,
          db: { ...initial.viewports.db, displayMode: 'with-constraints' },
        },
      });
      rerender({ s: next });
      expect(client.getModelGraph).not.toHaveBeenCalled();
    });
    ```

  - **Case 3 — switching schema from db to er triggers getModelGraph('er').**
    ```ts
    it('switching activeSchema fetches the new schema', async () => {
      const client = makeClient();
      const dispatch = vi.fn();
      const initial = makeState({
        projectUri: 'file:///x',
        activeSchema: 'db',
        graphsBySchema: { db: { schemaCode: 'db', nodes: [], edges: [] }, er: null },
      });
      const { rerender } = renderHook(({ s }) => useProjectGraph(s, dispatch, client), { initialProps: { s: initial } });
      expect(client.getModelGraph).not.toHaveBeenCalled();
      const next = makeState({ ...initial, activeSchema: 'er' });
      rerender({ s: next });
      await vi.waitFor(() => expect(client.getModelGraph).toHaveBeenCalledExactlyOnceWith('file:///x', 'er'));
    });
    ```

  - **Case 4 — toggling back to a cached schema does NOT refetch.**
    ```ts
    it('toggling back to a cached schema does not refetch', async () => {
      const client = makeClient();
      const dispatch = vi.fn();
      const both = makeState({
        projectUri: 'file:///x',
        activeSchema: 'db',
        graphsBySchema: {
          db: { schemaCode: 'db', nodes: [], edges: [] },
          er: { schemaCode: 'er', nodes: [], edges: [] },
        },
      });
      const { rerender } = renderHook(({ s }) => useProjectGraph(s, dispatch, client), { initialProps: { s: both } });
      rerender({ s: { ...both, activeSchema: 'er' } });
      rerender({ s: { ...both, activeSchema: 'db' } });
      expect(client.getModelGraph).not.toHaveBeenCalled();
    });
    ```

- [ ] **1.5A** Verify by running: `pnpm --filter @modeler/designer test`. Expect designer count to go from **26 → 30** (4 new hook tests). All pass.

### Path B — full-App test driven through `<App />` render

Heavier, but matches the mini-list's wording literally. Only pick this if you don't want to introduce the hook abstraction.

- [ ] **1.1B** Create `packages/designer/src/__tests__/App-getModelGraph.test.tsx`. Mock `'../lsp-client'` (`createLspClient` resolves to a client whose `getModelGraph` is a `vi.fn()`), plus `'cytoscape'` / `'cytoscape-cose-bilkent'` / `'cytoscape-node-html-label'` per `Canvas.test.tsx`'s shape.
- [ ] **1.2B** Drive each scenario through the rendered tree: simulate `loadProject` by calling the Header's `onDirPick` (mock `loadProjectViaFileSystemAccessAPI` to resolve with `{ rootName: 'p', files: new Map() }`), then assert `getModelGraph` call counts as in Path A.
- [ ] **1.3B** Same four assertions as Path A.1.4 cases 1–4.
- [ ] **1.4B** Verify by running: `pnpm --filter @modeler/designer test`. Expect designer count to go from **26 → 30**.

## 2. Refresh the test-results block in the progress doc

The block currently says `1 warning (designer Header eslint-disable no-param-reassign)` — stale; lint is clean.

- [ ] **2.1** In `docs/plan/progress-phase-03.md`, the "Test Results" block, change the lint line to `pnpm -r lint:         ✅  0 errors, 0 warnings`.
- [ ] **2.2** Update the count to `151 tests total` (147 + 4 new hook/App tests; adjust per-package number that gained them: `30 designer`).
- [ ] **2.3** Verify by running: `pnpm -r test 2>&1 | tail -5` and confirm the totals match.

## 3. Final acceptance

- [ ] **3.1** From the repo root: `pnpm -r build && pnpm -r test && pnpm -r lint && pnpm -r typecheck`. All exit 0, no warnings.
- [ ] **3.2** Stage and commit. Commit message names the path picked in task 1 (A or B) and notes that Section C is now genuinely complete.
- [ ] **3.3** Section C boxes in `progress-phase-03.md` stay ticked — they were ticked already; this PR just makes that honest.
