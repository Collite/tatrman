# Review 006 — Phase 3, Section A re-review

**Date:** 2026-05-15
**Branch:** `phase-03` (HEAD `f0c51ce` — *"Section A: review-005 fixes — all review tasks 0-16 complete, Section A done"*)
**Scope claimed:** every checkbox in `tasks-review-005.md` (tasks 0–18) plus Section A (A.1–A.6).
**Scope verified:** `git diff 7d77cef..f0c51ce` (23 files, +596 / −223), the actual files, and the full `pnpm -r build && test && lint && typecheck` pipeline.

## Verdict

**Section A is *almost* done.** The big-ticket items from `review-005.md` are all closed: path-(a) chosen and recorded in the contracts changelog (v0 → v1); `extractCardinality` added with tests; three new test files in `packages/lsp/src/__tests__/` covering cardinality / data-type rendering / layout validation; the triple re-export collapsed to `index.ts`; reducer split into `designer-state.ts` + `designer-reducer.ts`; `ViewportState` import-and-re-export pattern lands; `LayoutFile` is properly imported into the `loadLayout` action; the README vestige sentence is rewritten; React Testing Library deps added; `vitest.config.ts` and `test-setup.ts` wired; `App.tsx` on `useReducer`; `Header.tsx` extended with schema toggle, display-mode toggle, read-only badge, NL toggle; `NlPane.tsx` exists. Build / test / lint / typecheck all green; integration tests still pass (15/15).

**What's still missing** is mostly small but explicit in the task list and the mini-list:

1. **F1 (must fix) — `Header.test.tsx` was never written.** A.4 "Tests-first" §2 listed five named test cases. `tasks-review-005.md` task 14 asked for them. The directory `packages/designer/src/components/__tests__/` doesn't exist; designer is still at 6 tests (the reducer ones). Tests-first discipline is *non-negotiable* per `tasks-phase-03-designer.md` §"TDD discipline". Marking A.4 done without these is the same pattern that triggered review-005.
2. **F2 (must fix) — A.6 top-of-file comment in `Header.tsx` is missing.** A.6 explicitly says: *"Document the choice [accent palette] in `Header.tsx`'s top-of-file comment so D and E don't drift."* The file's first line is `import { useRef } from 'react';` — no comment.
3. **F3 (should fix) — A.5 reducer-vs-local-state choice is undocumented.** `App.tsx:14` keeps `nlPaneOpen` in `useState` (rather than the reducer). That's a defensible choice, but task 15.3 specifically said *"pick one and stick with it; document the choice in `App.tsx`"*. There is no comment.

Beyond the review-005 list, two things found during this pass:

4. **F4 (should fix) — Dead-wire `useEffect` in `App.tsx`.** Lines 36–42 fire `client.getModelGraph(...).then((graph) => { void graph; })` and throw the result away. The promise is dispatched on every change to `state.projectUri` *or* `state.activeSchema`, so the schema toggle now triggers an unused LSP round-trip. It's not a correctness bug today, but it is a *dead wire*: a Section-B implementer reading this will either think it works and not refactor it, or wire it up wrong. Either delete it until B owns it, or have it dispatch a result action.
5. **F5 (nice to have) — `Header.tsx` and `Canvas.tsx` redefine `RenderableSchemaCode` / `DisplayMode` inline as string literals.** `@modeler/lsp` exports both. Importing them keeps the wire contract single-sourced and means a future change in `model-graph.ts` is a compile error rather than a silent drift. (This isn't a deviation from the contract — both files predate the LSP types — but it's the obvious next refactor and was implicit in the contract-amendment work.)

## What was done well

- The `extractCardinality` test in `model-graph-cardinality.test.ts` constructs minimal `ObjectValue` literals with hand-built source locations rather than parsing real text. That's exactly the right test isolation.
- `validateLayout` tests cover the §6.3 invariant *"never throws"* with four primitive inputs (`null` / `undefined` / `string` / `number`) — better than what the contract literally asked for.
- Path-(a) was the right call: the LSP types are now testable, used by the Designer reducer through a single import, and re-exported from one place. The contract changelog entry names every export so a future reader can audit drift.
- `vitest.config.ts` + `test-setup.ts` wire was done up-front (task 12) so when F1 above is fixed, Header tests can be added in a single PR with no infra detour.
- The reducer-test decoupling (task 8.1) is in place: `symbolDetails: { 'er.entity.artikl': {} as SymbolDetail }` is the right pattern.

## Findings detail

### F1 — Header.test.tsx is missing

`A-designer-scaffold.md` "Tests-first" §2 enumerates five named cases. None exist. The directory:

```
packages/designer/src/components/__tests__/   ← does not exist
```

Designer test count is unchanged at 6 (all reducer). Without these tests, the schema-toggle / display-mode-toggle / disabled-state behaviour has zero automated coverage, which means the Section B implementer can't refactor `Header.tsx` confidently.

### F2 — A.6 visual-treatment comment missing

`Header.tsx` line 1 jumps straight to imports. A.6 was explicit. Easiest fix is one line:

```ts
// Phase-3 visual treatment: accent = text-sky-500 (active toggle), border-slate-300 (bar).
// Owned here so §D / §E don't drift — see docs/plan/phase-03/A-designer-scaffold.md A.6.
```

### F3 — `nlPaneOpen` choice undocumented

`App.tsx:14`:

```ts
const [nlPaneOpen, setNlPaneOpen] = useState(false);
```

Live alongside the reducer. Fine, but the choice is silent. Either move into `DesignerState` or add a one-line comment naming the rationale (e.g. *"NL-pane visibility is UI-only; not project-scoped, so kept out of DesignerState"*). Pick one.

### F4 — Dead-wire `useEffect`

```ts
useEffect(() => {
  if (!state.projectUri || !clientRef.current) return;
  const client = clientRef.current;
  client.getModelGraph(state.projectUri, state.activeSchema).then((graph) => {
    void graph;
  });
}, [state.projectUri, state.activeSchema]);
```

Two issues:

- The promise is unhandled (`.catch` missing), so a rejected request silently swallows the error rather than dispatching `setError`.
- The result is discarded. If left in, a Section-B implementer might assume the wiring already works and add the renderer at the wrong end.

Either delete the effect (the LSP call belongs in §B, after the reducer learns a `setGraph` action), or make it dispatch a result. *Don't ship dead wires.* Also note that `client.getModelGraph` is *typed* as returning `ModelGraph` in `lsp-client.ts:17`, but the server endpoint at `packages/lsp/src/server.ts:314` still returns the legacy `{ qname, kind, label }` shape. The types lie about the runtime — fine while the result is discarded, breakage waiting to happen once it isn't.

### F5 — Header / Canvas use inline string-literal types

`Header.tsx:4-9` and `Canvas.tsx:6-7`:

```ts
activeSchema: 'db' | 'er';
displayMode: 'just-names' | 'with-types' | 'with-constraints';
```

`@modeler/lsp` already exports `RenderableSchemaCode` and `DisplayMode`. Mirror the reducer's import. One-line refactor, future-proof.

## Verification commands run

```
pnpm -r build        → all green
pnpm -r test         → all green (designer 6, lsp 4 + 8 + 4 + 9 = 25, vscode-ext 6, integration 15)
pnpm -r lint         → all green
pnpm -r typecheck    → all green
grep -rinE 'quest|gamif|school' packages/designer/src packages/designer/package.json packages/designer/README.md
                     → only false positive in "sendRequest" — vestige sweep is clean
```

## Architectural assessment

The architecture is *more* solid than after review-005. One source of truth for shared types (`packages/lsp/src/model-graph.ts`); a clean barrel export (`index.ts`); two-file reducer pattern that scales; tested layout validator and cardinality parser; and a Designer that talks to the LSP through one typed client surface. The contracts changelog discipline is now exercised, which is the most valuable habit for a multi-section phase.

The remaining gaps are small enough that I'm comfortable approving Section A *after* F1–F3 land. F4 is technically Section B's problem but is so clearly a placeholder that fixing it now is cheaper than explaining it in a code review later. F5 is opportunistic.

If the developer ships F1–F4, Section A is genuinely done and Section B can start without carrying any debt.
