# Tasks — Review 006 (Phase 3, Section A — final cleanup)

Companion to `review-006.md`. Section A is one PR away from genuinely done. Tasks 1 and 2 below are the must-fix items; 3 and 4 are the should-fix items; 5 is opportunistic. Do them in order — each verification command must pass before you tick the box.

## 1. Add `Header.test.tsx` (the missing A.4 tests-first work)

A.4 mini-list explicitly enumerated five test cases. None exist. The test infrastructure (RTL deps, jsdom, `vitest.config.ts`, `test-setup.ts`) is already in place, so this is purely test authoring.

- [ ] **1.1** Create `packages/designer/src/components/__tests__/Header.test.tsx`. Use `@testing-library/react` and `@testing-library/jest-dom` (already in `devDependencies`).
- [ ] **1.2** Add the five test cases verbatim from `docs/plan/phase-03/A-designer-scaffold.md` "Tests-first" §2:
  - **1.2a** *Schema toggle starts disabled when `projectUri === null`; becomes enabled after `projectUri` is set.* Render `<Header projectUri={null} ... />`, find the "db" and "er" buttons, assert `.disabled === true`. Re-render with `projectUri="file:///x"`, assert both buttons are `.disabled === false`.
  - **1.2b** *Clicking the `er` button when active is `db` fires `onSchemaChange('er')` once.* `const onSchemaChange = vi.fn(); render(<Header activeSchema="db" onSchemaChange={onSchemaChange} projectUri="file:///x" .../>); fireEvent.click(screen.getByRole('button', { name: 'er' })); expect(onSchemaChange).toHaveBeenCalledExactlyOnceWith('er');`
  - **1.2c** *Display-mode toggle reflects the active schema's `viewports[activeSchema].displayMode`.* Render with `displayMode="with-constraints"`; assert the "with constraints" button has the active class (`text-sky-500`); the other two do not.
  - **1.2d** *The "Designer mode: Read-only" badge is always visible.* Render twice (`projectUri=null`, `projectUri='file:///x'`); both times `screen.getByText(/read-only/i)` resolves.
  - **1.2e** *The NL-pane toggle button is present; clicking it fires `onToggleNlPane`.* `const onToggleNlPane = vi.fn(); render(<Header onToggleNlPane={onToggleNlPane} ... />); fireEvent.click(screen.getByRole('button', { name: /nl/i })); expect(onToggleNlPane).toHaveBeenCalledOnce();`
- [ ] **1.3** Verify by running: `pnpm --filter @modeler/designer test`. Expect 6 reducer tests + 5 Header tests = **11 passing**.
- [ ] **1.4** Tick A.4 in `docs/plan/progress-phase-03.md` only after 1.3 passes.

## 2. Add the A.6 accent-palette comment to `Header.tsx`

A.6 explicit requirement, missed.

- [ ] **2.1** Insert at the very top of `packages/designer/src/components/Header.tsx`, before the `import { useRef }` line:

  ```ts
  // Phase-3 visual treatment: accent = text-sky-500 (active toggle), border-slate-300 (bar).
  // Owned here so §D and §E don't drift — see docs/plan/phase-03/A-designer-scaffold.md A.6.
  ```

- [ ] **2.2** Verify by running: `pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer lint`. Expect exit 0.

## 3. Document the `nlPaneOpen` state choice in `App.tsx`

Task 15.3 from `tasks-review-005.md` asked for this; it slipped.

- [ ] **3.1** Above the `useState(false)` call on `App.tsx:14`, add:

  ```ts
  // NL-pane open/close is UI-only and not project-scoped, so it lives in local
  // useState rather than DesignerState. If the pane gains state that must persist
  // across project loads, move it into the reducer.
  ```

- [ ] **3.2** Verify by running: `pnpm --filter @modeler/designer typecheck`. Expect exit 0.

## 4. Remove the dead-wire `useEffect` in `App.tsx`

Lines 36–42 fire an LSP request and discard the result. It overpromises the Section-B wiring and silently swallows rejected promises.

- [ ] **4.1** Delete the entire `useEffect` block at `packages/designer/src/App.tsx:36-42`.
- [ ] **4.2** Add a one-line comment in its place:

  ```ts
  // Graph fetching lives in §B (see docs/plan/phase-03/B-lsp-integration.md):
  // a useEffect on (projectUri, activeSchema) will dispatch a 'setGraph' action.
  ```

- [ ] **4.3** Verify by running: `pnpm --filter @modeler/designer dev`. Load a `.ttr` file. Confirm there are no console errors and no calls to `modeler/getModelGraph` in the worker logs (open browser devtools → Network → WS to confirm).
- [ ] **4.4** Verify by running: `pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer test`. Expect exit 0 and 11 passing.

## 5. Replace inline string-literal types with the `@modeler/lsp` exports

Opportunistic. Two files, two-line refactors each.

- [ ] **5.1** In `packages/designer/src/components/Header.tsx`, add to the imports:
  ```ts
  import type { RenderableSchemaCode, DisplayMode } from '@modeler/lsp';
  ```
  Replace the inline `'db' | 'er'` types in `HeaderProps` (lines 4 and 8) with `RenderableSchemaCode`. Replace the inline `'just-names' | 'with-types' | 'with-constraints'` (lines 5 and 9) with `DisplayMode`.
- [ ] **5.2** In `packages/designer/src/components/Canvas.tsx`, do the same: import the two types and replace the inline literals at lines 6–7.
- [ ] **5.3** In `packages/designer/src/lsp-client.ts`, replace the inline `schema: 'db' | 'er'` (line 17) with `schema: RenderableSchemaCode`.
- [ ] **5.4** Verify by running: `pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer test && pnpm --filter @modeler/designer lint`. Expect exit 0 and 11 passing.

## 6. Final acceptance run

- [ ] **6.1** From the repo root: `pnpm -r build && pnpm -r test && pnpm -r lint && pnpm -r typecheck`. All exit 0.
- [ ] **6.2** `pnpm --filter @modeler/integration-tests test`. Exit 0 (15 passing).
- [ ] **6.3** Re-read `docs/plan/phase-03/A-designer-scaffold.md` §"DONE when". Confirm every line is true *with the new Header tests on disk* — the last bullet about test-vs-implementation `git log` ordering is unverifiable retrospectively, but the *spirit* (tests exist before B starts) is satisfied if 1.3 is green.
- [ ] **6.4** Tick the remaining A.6 entry in `docs/plan/progress-phase-03.md` only after 6.1–6.3 pass.
- [ ] **6.5** Open `docs/plan/phase-03/B-lsp-integration.md`; start with its "Tests-first" section. Do not touch `server.ts`'s `modeler/getModelGraph` handler until B's tests are red.
