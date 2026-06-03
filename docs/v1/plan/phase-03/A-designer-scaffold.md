# Phase 3.A — Designer scaffold cleanup

**Goal:** turn the Phase-0 fork into a tidy, schema-aware Designer shell — Ontology-Playground vestiges removed, `App.tsx` state reshaped around a reducer, top menu bar with schema and display-mode toggles, inert NL-pane shell. No rendering changes yet; that's §C / §D.

**Reads:** [`tasks-phase-03-designer.md`](../tasks-phase-03-designer.md) → [`docs/design/phase-03-contracts.md`](../../design/phase-03-contracts.md) §2 (state types).
**Blocks:** §B (the LSP client expansion needs the new reducer to dispatch into).
**Blocked by:** Pre-flight.

## Tests-first

Write these test cases **before** writing any implementation. They define what "done" means for §A.

- [ ] `packages/designer/src/state/__tests__/designer-reducer.test.ts` — pure-reducer tests, jsdom-free, fast.
  - `'switchSchema' updates activeSchema`: dispatch `{ type: 'switchSchema', schema: 'er' }` against `initialDesignerState`; expect `state.activeSchema === 'er'`.
  - `'setDisplayMode' updates the named viewport only`: dispatch `{ type: 'setDisplayMode', schema: 'db', mode: 'with-constraints' }`; expect `state.viewports.db.displayMode === 'with-constraints'` and `state.viewports.er.displayMode === 'just-names'` (unchanged).
  - `'setViewport' merges zoom/panX/panY for the named schema`: dispatch with `{ schema: 'db', viewport: { zoom: 2, panX: 100, panY: -50 } }`; expect those three values updated, `displayMode` unchanged.
  - `'setNodePosition' upserts by qname`: dispatch twice with the same qname and different x/y; expect the second wins.
  - `'selectSymbol' with null clears selection`: dispatch `{ type: 'selectSymbol', qname: 'er.entity.artikl' }` then `{ type: 'selectSymbol', qname: null }`; expect `state.selectedSymbol === null`.
  - `'loadProject' resets symbolDetails cache`: pre-populate `state.symbolDetails`; dispatch `{ type: 'loadProject', projectUri: 'file:///x' }`; expect `state.symbolDetails === {}`.

- [ ] `packages/designer/src/components/__tests__/Header.test.tsx` — React Testing Library + jsdom.
  - Schema toggle starts disabled when `projectUri === null`; becomes enabled after `projectUri` is set.
  - Clicking the `er` button when active is `db` fires `onSchemaChange('er')` once.
  - Display-mode toggle reflects the active schema's `viewports[activeSchema].displayMode`.
  - The "Designer mode: Read-only" badge is always visible.
  - The NL-pane toggle button is present; clicking it toggles the pane visible/hidden.

## Library reference

No external libraries beyond what Phase 0 already wired. If you find yourself adding a state-management library (Redux, Zustand, Jotai), stop — `useReducer` is sufficient for v1 and the contracts treat reducer state as the only abstraction.

## Implementation tasks

Do not start before the test files above exist and run red.

- [ ] **A.1 — Audit and remove Ontology-Playground vestiges.** Grep `packages/designer/` for `quest`, `gamif`, `school` (case-insensitive). Remove dead files. Run `pnpm --filter @modeler/designer typecheck` to confirm nothing references the removed code. Drop any RDF deps (`rdflib`, `n3`, `oxigraph-*`) from `package.json` if present.
- [ ] **A.2 — Create the reducer skeleton.** Write `packages/designer/src/state/designer-state.ts` and `designer-reducer.ts` matching [contracts §2](../../design/phase-03-contracts.md#2-designer-side-state-types). Export `initialDesignerState`, `DesignerState`, `DesignerAction`, `designerReducer`. Make the reducer-test go green.
- [ ] **A.3 — Refactor `App.tsx` onto the reducer.** Replace the `useState` calls with `const [state, dispatch] = useReducer(designerReducer, initialDesignerState)`. Move the existing `error` state into the reducer (`setError` action). Existing `onFileLoad` / `onNodeSelect` paths translate to dispatched actions; the LSP-client wiring stays in a `useEffect` that consumes `state.projectUri`.
- [ ] **A.4 — Extend `Header.tsx` with schema toggle, display-mode toggle, read-only badge, NL-pane toggle.** Schema and display-mode controls render as compact button groups; both disabled until `state.projectUri !== null`. Wire them to dispatched actions. Make the Header test go green.
- [ ] **A.5 — Add `packages/designer/src/components/NlPane.tsx`.** Collapsible bottom panel; placeholder input disabled; "Coming in v1.x" badge. Default collapsed; toggled from a Header button. No backend wiring. Add a TODO comment referencing the v1.4 plan entry.
- [ ] **A.6 — Re-style for Phase-3 look.** Match Ontology Playground's compact, dark-accent visual treatment via Tailwind utility classes. The Phase-0 styles were placeholder colors; Phase 3 chooses one accent color (`text-sky-500` for active toggles, `border-slate-300` for the bar). Document the choice in `Header.tsx`'s top-of-file comment so D and E don't drift.

## Verify by running

```bash
pnpm --filter @modeler/designer typecheck
pnpm --filter @modeler/designer lint
pnpm --filter @modeler/designer test
pnpm --filter @modeler/designer dev  # then visit http://localhost:5173 and click around
```

All three commands exit 0. The dev server shows the new Header with disabled toggles, an empty Canvas, an empty Inspector, and a collapsed NL pane shell. No console errors.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] No `quest`/`gamif`/`school` references remain in `packages/designer/`.
- [ ] `App.tsx` uses `useReducer`; no `useState` for project-scoped data.
- [ ] The reducer and Header tests are green and were written before the implementation tasks (verify via `git log` ordering on the test files vs. the implementation files in the PR).
