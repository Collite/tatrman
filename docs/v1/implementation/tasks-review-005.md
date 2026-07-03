# Tasks — Review 005 (Phase 3, Section A)

Companion to `review-005.md`. Each task is independently verifiable with the listed command. Do them top-to-bottom; some later tasks depend on earlier ones (noted inline).

## 0. Reset expectations

- [ ] **0.1** Re-read `review-005.md` and acknowledge that **Section A is not yet ready** — A.1 and A.2 are done, A.3–A.6 are not started.
- [ ] **0.2** Open `docs/plan/progress-phase-03.md` and confirm A.3, A.4, A.5, A.6 checkboxes are still unchecked. (They are — leave them unchecked until each task's "Verify by running" is green.)

## 1. Decide and record: keep or back out the early LSP work

You wrote ~200 LOC of Section B code in `packages/lsp/src/model-graph.ts`. Before doing anything else, pick one path and record the choice.

- [ ] **1.1** Choose **either**:
  - **(a)** Keep `model-graph.ts` and finish it to Section-B-quality (tests for every exported function, plus `extractCardinality`); **or**
  - **(b)** Revert `model-graph.ts` and the related re-export changes in `packages/lsp/src/index.ts` / `lsp-index.ts` / `server-stdio.ts`, and let Section B re-introduce them with their own tests.
- [ ] **1.2** If you picked **(a)**, do tasks 2–6 below. If you picked **(b)**, jump to task 7.
- [ ] **1.3** Either way: open a one-paragraph "Amendments" entry in `docs/design/phase-03-contracts.md` (under §"Changelog") noting that some §4 / §5 / §6 / §8 helpers landed in Section A's PR and listing exactly which exports were added (or were reverted). Bump the version note at the top of the file from v0 to v1.

---

## 2. (Path-a only) Add the missing `extractCardinality` function

Required by contracts §8.

- [ ] **2.1** In `packages/lsp/src/model-graph.ts`, add `extractCardinality(obj: ObjectValue | undefined): { from: Cardinality | null; to: Cardinality | null }` exactly as spelled in `docs/design/phase-03-contracts.md` §8.
- [ ] **2.2** Import the `ObjectValue` type from `@modeler/parser` (the existing `Definition` import is in the same file; mirror it).
- [ ] **2.3** Add `extractCardinality` to the exports in `packages/lsp/src/index.ts` (and remove from `lsp-index.ts` — see task 6).
- [ ] **2.4** Verify by running: `pnpm --filter @modeler/lsp typecheck`. Expect exit 0.

## 3. (Path-a only) Tests for `parseCardinality`

Contracts §8 spells out 8 assertions.

- [ ] **3.1** Create `packages/lsp/src/__tests__/model-graph-cardinality.test.ts`.
- [ ] **3.2** Add 8 `expect(parseCardinality(...)).toBe(...)` cases for each input in §8: `'1' → 'one'`, `'0..1' → 'zero-or-one'`, `'n' → 'many'`, `'*' → 'many'`, `'1..n' → 'one-or-many'`, `'1..*' → 'one-or-many'`, `'foo' → null`, `'' → null`.
- [ ] **3.3** Add at least 2 cases for `extractCardinality`: (i) returns `{from:null,to:null}` for `undefined`; (ii) returns the parsed `from`/`to` for an `ObjectValue` with valid string entries; (iii) returns `null` for non-string entries (lists). Hand-construct minimal `ObjectValue` literals — do not parse real source.
- [ ] **3.4** Verify by running: `pnpm --filter @modeler/lsp test`. Expect all tests pass; new file should have ≥ 11 assertions.

## 4. (Path-a only) Tests for `renderDataType`

Contracts §4.1 spells out 4 assertions.

- [ ] **4.1** Create `packages/lsp/src/__tests__/model-graph-render-data-type.test.ts`.
- [ ] **4.2** Add the 4 cases verbatim from §4.1: `{kind:'simple',name:'int'} → 'int'`; `{kind:'structured',typeName:'varchar',length:40} → 'varchar(40)'`; `{kind:'structured',typeName:'decimal',length:10,precision:2} → 'decimal(10,2)'`; `undefined → null`.
- [ ] **4.3** Verify by running: `pnpm --filter @modeler/lsp test`. Expect all tests pass.

## 5. (Path-a only) Tests for `validateLayout` / `emptyLayout`

Contracts §6.3 prescribes the behavior.

- [ ] **5.1** Create `packages/lsp/src/__tests__/model-graph-layout.test.ts`.
- [ ] **5.2** Cases:
  - **5.2a** `validateLayout(emptyLayout())` returns the value unchanged (round-trip valid).
  - **5.2b** `validateLayout({})` returns `null`.
  - **5.2c** `validateLayout({ ...emptyLayout(), version: 2 })` returns `null` (wrong version).
  - **5.2d** `validateLayout({ ...emptyLayout(), nodes: { foo: { x: 'bad', y: 0 } } })` returns `null` (wrong node type).
  - **5.2e** A round-trip with non-empty `nodes` and `edges`: build a `LayoutFile` with one node and one edge with one bend point, validate, expect the same value back.
  - **5.2f** Confirm `validateLayout` does not throw on `null` / `undefined` / strings / numbers — all return `null`.
- [ ] **5.3** Verify by running: `pnpm --filter @modeler/lsp test`. Expect all tests pass.

## 6. (Path-a only) Collapse the triple re-export

The same model-graph types are re-exported from `index.ts`, `lsp-index.ts`, and `server-stdio.ts`.

- [ ] **6.1** Move every type listed in `lsp-index.ts` into `packages/lsp/src/index.ts` (the `DataType*` types are missing there — add them).
- [ ] **6.2** Delete `packages/lsp/src/lsp-index.ts`.
- [ ] **6.3** In `packages/lsp/src/server-stdio.ts`, delete the `export type { ... }` block and the `export { renderDataType, parseCardinality, emptyLayout, validateLayout }` line. The `import` statement at the top stays only if the file actually uses any of those symbols (today it does not — also delete the import if so).
- [ ] **6.4** Run a grep: `grep -rn "from '@modeler/lsp/lsp-index'" packages/` should return no hits. Same for `from '@modeler/lsp/server-stdio'` outside of `package.json` build wiring.
- [ ] **6.5** Verify by running: `pnpm -r build && pnpm -r typecheck`. Expect exit 0.

---

## 7. (Path-b only) Revert the early Section B work

If you picked path (b) in 1.1.

- [ ] **7.1** Move `extractCardinality`-shaped helpers (none today) and any wiring stubs out of the way; then `git rm packages/lsp/src/model-graph.ts`.
- [ ] **7.2** Delete `packages/lsp/src/lsp-index.ts`.
- [ ] **7.3** In `packages/lsp/src/index.ts`, remove the entire model-graph re-export block (lines 1–17 of the current file).
- [ ] **7.4** In `packages/lsp/src/server-stdio.ts`, remove the model-graph imports and the re-export block.
- [ ] **7.5** In `packages/lsp/package.json`, remove `ajv` from `dependencies` if no other LSP code uses it. (Run `grep -rn "from 'ajv" packages/lsp/src/` to confirm.)
- [ ] **7.6** Update `packages/designer/src/state/designer-state.ts` to define `RenderableSchemaCode`, `DisplayMode`, and `SymbolDetail` locally (or as `unknown` placeholders for `SymbolDetail`) until Section B introduces them. Update the reducer test the same way (see task 8).
- [ ] **7.7** Verify by running: `pnpm -r build && pnpm -r test && pnpm -r typecheck`. All green.

---

## 8. Decouple the reducer test from Section B types

Required regardless of path 1.1 — keeps the test honest to Section A's scope.

- [ ] **8.1** In `packages/designer/src/state/__tests__/designer-reducer.test.ts`, replace the hand-built `SymbolDetail` literal at lines 70–75 with a minimal placeholder, e.g.:

  ```ts
  const stateWithDetails = {
    ...initialDesignerState,
    symbolDetails: { 'er.entity.artikl': {} as Record<string, unknown> },
  } as DesignerState;
  ```

- [ ] **8.2** Verify by running: `pnpm --filter @modeler/designer test`. Expect 6 passing.

## 9. Split the reducer file (contract A.2)

`designer-state.ts` and `designer-reducer.ts` were specified as two files.

- [ ] **9.1** Create `packages/designer/src/state/designer-reducer.ts` containing only the `designerReducer` function and the `DesignerAction` discriminated union.
- [ ] **9.2** Leave `designer-state.ts` with `ViewportState` (or its import — see task 10), `DesignerState`, and `initialDesignerState` only.
- [ ] **9.3** Update the test import to:
  ```ts
  import { designerReducer } from '../designer-reducer';
  import { initialDesignerState } from '../designer-state';
  import type { DesignerAction } from '../designer-reducer';
  ```
- [ ] **9.4** Verify by running: `pnpm --filter @modeler/designer test && pnpm --filter @modeler/designer typecheck`. Expect 6 passing, exit 0.

## 10. Eliminate duplicate `ViewportState`

`designer-state.ts` redefines a type already exported by `@modeler/lsp`.

- [ ] **10.1** If you took path-a in task 1, delete the local `interface ViewportState { ... }` from `designer-state.ts` and add `ViewportState` to the existing `import type { RenderableSchemaCode, DisplayMode } from '@modeler/lsp'` line.
- [ ] **10.2** If you took path-b in task 1, leave the local definition; add a one-line comment that this is the canonical `ViewportState` until Section B introduces the LSP-side one.
- [ ] **10.3** Replace the inlined `loadLayout` action shape with `layout: LayoutFile` (path-a) or keep a minimal local layout shape that matches contracts §6 (path-b).
- [ ] **10.4** Verify by running: `pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer test`. Expect exit 0 and 6 passing.

## 11. Make A.1 vestige sweep clean

The grep is supposed to come back empty in `packages/designer/`.

- [ ] **11.1** Edit `packages/designer/README.md` to remove the words "Quests" / "gamification" from the opening sentence. Suggested replacement: *"Graphical designer for TTR (Tatrman) models. Forked from the Ontology Playground project; only the read-only schema-rendering surface is retained."*
- [ ] **11.2** Verify by running:
  ```
  grep -rinE 'quest|gamif|school' packages/designer/src packages/designer/package.json packages/designer/README.md
  ```
  Expect zero hits except for `InitializeRequest` (LSP API name — fine).

## 12. Pre-stage A.4 test infrastructure

Don't open A.4 without the test deps wired.

- [ ] **12.1** In `packages/designer/package.json`, add to `devDependencies`:
  ```
  "@testing-library/react": "^16.0.0",
  "@testing-library/dom": "^10.0.0",
  "@testing-library/jest-dom": "^6.5.0"
  ```
  (Resolve via `pnpm install` and pin the lockfile.)
- [ ] **12.2** Create `packages/designer/src/test-setup.ts` that imports `'@testing-library/jest-dom'` and is referenced from `vitest.config.ts` via `test.setupFiles`. (If `vitest.config.ts` does not exist yet, create one extending the Vite config.)
- [ ] **12.3** Verify by running: `pnpm --filter @modeler/designer test`. Existing 6 reducer tests must still pass.

## 13. Now do A.3 — refactor `App.tsx` onto the reducer

Once tasks 1–12 are clean, proceed with the original Section A plan, in order.

- [ ] **13.1** Replace the four `useState` calls in `packages/designer/src/App.tsx` with `const [state, dispatch] = useReducer(designerReducer, initialDesignerState)`.
- [ ] **13.2** Move `error` into the reducer (`setError` action). Delete the local `useState<string|null>(null)`.
- [ ] **13.3** Translate the existing `handleFileLoad` → dispatch `loadProject` after the LSP `openDocument` resolves; the LSP-graph call moves into a `useEffect` keyed on `state.projectUri`.
- [ ] **13.4** Translate `handleNodeSelect` → dispatch `selectSymbol`.
- [ ] **13.5** Verify by running: `pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer dev` (browse to localhost:5173, load a `.ttr` file, confirm no console errors). Tick the A.3 checkbox in `progress-phase-03.md`.

## 14. A.4 — extend Header

Tests-first.

- [ ] **14.1** Create `packages/designer/src/components/__tests__/Header.test.tsx` with the 5 cases listed in `A-designer-scaffold.md` "Tests-first" §2 (schema toggle disabled when no project; schema toggle fires `onSchemaChange('er')`; display-mode toggle reflects active schema; "Read-only" badge always visible; NL pane toggle present and toggles).
- [ ] **14.2** Confirm tests fail (red). Then extend `packages/designer/src/components/Header.tsx`:
  - Schema toggle (button group: db / er), disabled until `projectUri !== null`.
  - Display-mode toggle (button group: just-names / with-types / with-constraints) reflecting `viewports[activeSchema].displayMode`.
  - Read-only badge (always visible).
  - NL pane toggle button (sets a parent-controlled prop or calls a callback).
- [ ] **14.3** Update `App.tsx` to wire the new props (`activeSchema`, `displayMode`, `projectUri`, `onSchemaChange`, `onDisplayModeChange`, `onToggleNlPane`).
- [ ] **14.4** Verify by running: `pnpm --filter @modeler/designer test`. Header tests pass; reducer tests still pass. Tick A.4.

## 15. A.5 — `NlPane.tsx`

- [ ] **15.1** Create `packages/designer/src/components/NlPane.tsx`. Collapsible bottom panel; placeholder input is `disabled`; "Coming in v1.x" badge visible. Default collapsed.
- [ ] **15.2** Top-of-file `// TODO: v1.4 — wire to LLM. See docs/plan/implementation-plan.md "v1.4".`
- [ ] **15.3** Mount in `App.tsx`; visibility controlled by reducer state (add `nlPaneOpen: boolean` to `DesignerState` *only* if needed; otherwise local `useState` is fine — pick one and stick with it; document the choice in `App.tsx`).
- [ ] **15.4** Verify by running: `pnpm --filter @modeler/designer dev`. The pane appears collapsed; the Header button toggles it. Tick A.5.

## 16. A.6 — Phase-3 visual treatment

- [ ] **16.1** Pick the accent palette per `A-designer-scaffold.md` A.6: `text-sky-500` for active toggle, `border-slate-300` for the bar.
- [ ] **16.2** Add a top-of-file comment in `Header.tsx` recording the choice (so D and E don't drift).
- [ ] **16.3** Restyle Header / NlPane / panel borders to the chosen palette.
- [ ] **16.4** Verify by running: `pnpm --filter @modeler/designer dev` and visually confirm the new look. Tick A.6.

## 17. Update progress doc honestly

- [ ] **17.1** In `docs/plan/progress-phase-03.md`, tick A.1, A.2, A.3, A.4, A.5, A.6 only after each one's "Verify by running" command has been run on a fresh tree.
- [ ] **17.2** Run on a fresh checkout: `pnpm install && pnpm -r build && pnpm -r test && pnpm -r lint && pnpm -r typecheck`. All exit 0.
- [ ] **17.3** Confirm no Phase-1 / Phase-2 regressions: open `samples/v1-metadata/` in VS Code (F5 from `packages/vscode-ext`); syntax highlighting works; diagnostics fire as before.

## 18. Final check

- [ ] **18.1** `pnpm --filter @modeler/integration-tests test` exits 0.
- [ ] **18.2** Re-read `docs/plan/phase-03/A-designer-scaffold.md` §"DONE when". Confirm every line is true.
- [ ] **18.3** Open Section B (`docs/plan/phase-03/B-lsp-integration.md`) and start its "Tests-first" section. Do not start B.1 implementation until B's tests are red.
