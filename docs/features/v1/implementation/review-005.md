# Review 005 — Phase 3, Section A (Designer scaffold cleanup)

**Date:** 2026-05-15
**Branch:** `phase-03` (HEAD `7d77cef`)
**Scope claimed by developer:** Section A of `docs/plan/tasks-phase-03-designer.md` (mini-list `docs/plan/phase-03/A-designer-scaffold.md`).
**Scope verified by review:** the Section A mini-list, plus everything that actually changed in `packages/lsp/` and `packages/designer/` since the Phase 2 merge.

## Verdict

**Section A is NOT done.** The developer has completed A.1 and A.2 only (vestige sweep + reducer skeleton with tests); A.3, A.4, A.5, A.6 are **not started**. The runtime confirms this — the recent commit message ("Section A progress: reducer skeleton done, tests green") and the progress doc (only A.1/A.2 ticked) are consistent with the code; the message that "Section A is ready" is wrong.

What *did* land beyond A.1/A.2 is **scope creep into Section B**: the LSP package gained the entire `model-graph.ts` shape pile (DTOs, `LayoutFile`, ajv schema, `validateLayout`, `parseCardinality`, `renderDataType`, `emptyLayout`, `SymbolDetail`, `PerKindData`, ...) — all of it untested and unused by `server.ts`. Only two of the new exports (`RenderableSchemaCode`/`DisplayMode` and `SymbolDetail`) are referenced anywhere outside `model-graph.ts` itself, and only because `designer-state.ts` and its test need them to compile.

Build / test / typecheck / lint are all green on a fresh checkout — but green is not the same as done.

## Findings

### F1 — Progress doc and code agree; the verbal claim does not

`docs/plan/progress-phase-03.md` (lines 18–23) ticks only A.1 and A.2; A.3–A.6 are unchecked. `packages/designer/src/App.tsx` still uses `useState`, `Header.tsx` still has only the Phase-0 "Load .ttr file" button, there is no `NlPane.tsx`, and no Phase-3 visual treatment. Treat the developer's "Section A is ready" claim as inaccurate. (Auto-memory note `feedback-progress-doc-skepticism` applies.)

### F2 — Contract deviation: reducer split (A.2)

`A-designer-scaffold.md` task A.2 is explicit:

> Write `packages/designer/src/state/designer-state.ts` **and** `designer-reducer.ts` matching contracts §2.

Implementer wrote *one* file: `packages/designer/src/state/designer-state.ts` containing the state types, `initialDesignerState`, `DesignerAction`, **and** the reducer function. Two files were specified for separation of concerns; collapsing them is an undocumented deviation. Per the contract-amendment discipline (`tasks-phase-03-designer.md` §"Contract amendment discipline"), if the implementer believed one file was better, they had to update the mini-task-list in the same PR. They did not.

### F3 — Scope creep into Section B without tests (NEW LSP code)

`packages/lsp/src/model-graph.ts` (198 LOC) was created in this section. It contains everything the contracts assign to Section B:

- All `ModelGraph*` DTOs (contracts §4) — Section B.1.
- `LayoutFile`, `emptyLayout`, the ajv schema literal, `validateLayout` (contracts §6) — Section B.2.
- `SymbolDetail`, `PerKindData` (contracts §5) — Section B.4.
- `parseCardinality`, `renderDataType` (contracts §4.1, §8) — Section B / D.

None of this is currently consumed by `packages/lsp/src/server.ts` — line 314 still has the legacy stub `modeler/getModelGraph` returning the old shape. None of the new functions has a unit test, even though the contracts spell out exact assertion strings:

- §4.1 lists 4 cases for `renderDataType` — no test file exists.
- §8 lists 8 cases for `parseCardinality` — no test file exists.
- §6.3 prescribes `validateLayout(unknown) → LayoutFile | null` behavior — no test file exists.

This is the worst kind of scope creep: code that *looks* finished, isn't covered by tests, and won't trip when Section B changes its mind about a shape. Either ship Section B's tests alongside the types now, or delete the dead exports until B owns them.

### F4 — Contract deviation: `extractCardinality` missing

Contracts §8 names two functions:

```ts
export function parseCardinality(s: string): Cardinality | null;
export function extractCardinality(obj: ObjectValue | undefined): { from, to };
```

`packages/lsp/src/model-graph.ts` exports `parseCardinality` only. `extractCardinality` (the function the LSP server actually needs at the call-site for `RelationDef.cardinality`) is absent. If you are going to ship the §8 helpers ahead of Section D, ship both.

### F5 — Duplicate `ViewportState` definition

`packages/lsp/src/model-graph.ts:77-82` and `packages/designer/src/state/designer-state.ts:4-9` both define an interface named `ViewportState` with the same shape. The Designer file imports `RenderableSchemaCode` and `DisplayMode` from `@modeler/lsp` but redefines `ViewportState` locally. This is a maintenance trap: any future change to `ViewportState` (a new field, a renamed key) will be silently inconsistent across the two files. Pick one source of truth — the LSP one is already exported — and delete the other.

### F6 — Contract deviation: `loadLayout` action shape inlined

`designer-state.ts:36` writes the `loadLayout` action as:

```ts
| { type: 'loadLayout'; layout: { viewports: Record<...>; nodes: Record<...>; edges: Record<...> } }
```

Contracts §2 specifies `{ type: 'loadLayout'; layout: LayoutFile }`. `LayoutFile` is already exported from `@modeler/lsp` (line 84 of `model-graph.ts`). Importing it here would (a) fix this deviation, (b) eliminate a third place where the layout shape is stated, and (c) tie the reducer to the wire contract.

### F7 — Triple re-export indirection in `@modeler/lsp`

The same model-graph re-exports appear in three files:

- `packages/lsp/src/index.ts` (the package's documented entry point)
- `packages/lsp/src/lsp-index.ts` (additional re-export, also adds `DataType*`)
- `packages/lsp/src/server-stdio.ts` (re-exports types it has just imported, on top of being the Node entry point)

`server-stdio.ts` is the bundled stdio entry — there is no consumer that should be importing types from it. The `lsp-index.ts` file has no clear purpose and isn't wired into `package.json#exports`. Delete `lsp-index.ts` and the re-export block at the top of `server-stdio.ts`; keep `index.ts` as the single barrel.

### F8 — A.1 vestige sweep is incomplete

A.1 says: "Grep `packages/designer/` for `quest`, `gamif`, `school` (case-insensitive). Remove dead files." The grep still hits `packages/designer/README.md`:

> Graphical designer for TTR (Tatrman) models. Forked from Ontology Playground with Quests and gamification removed.

This is a historical-context sentence rather than a vestige, but the task says to make the grep clean. Either rewrite the README sentence so the grep is silent, or amend A.1 to allow the README mention.

The dependency check (RDF deps `rdflib`/`n3`/`oxigraph-*`) is clean — none present in `packages/designer/package.json`. Good.

### F9 — Test infrastructure for A.4 not in place

A.4 requires `Header.test.tsx` using React Testing Library + jsdom. `packages/designer/package.json` has `jsdom` but is missing `@testing-library/react` and `@testing-library/dom`. Add these in the same PR that opens A.4 work, not separately, so the tests-first discipline holds.

### F10 — Test for `loadProject` resets symbolDetails depends on a Section B type

`designer-reducer.test.ts:73` constructs a `SymbolDetail` literal to pre-populate the cache. This is the *only* reason the reducer test forced `SymbolDetail` to exist before Section B. A simpler approach (`symbolDetails: { 'foo': {} as SymbolDetail }` or even `Record<string, unknown>`) would have kept the test isolated to Section A. Not blocking, but it's the trigger that pulled all of `model-graph.ts` in early. If you keep the LSP work, this is fine; if you back it out, simplify the test.

## Summary of architectural concerns

The reducer + state code itself (`designer-state.ts`, the 6 tests) is clean, idiomatic, and correct. The reducer is a pure function; the actions cover the contract shapes; the tests are tight and red-then-green.

The architectural concern is **front-running Section B** without the discipline B was supposed to bring — no tests for the helpers, no consumers wiring through the server, no `extractCardinality`, no contract amendment to record the early start. The LSP package now has 200 lines of "looks done" code that no test or call-site exercises. That is exactly the shape of breakage that surfaced in earlier phases and is the reason the auto-memory `feedback-progress-doc-skepticism` exists.

Recommended path: complete A.3–A.6 as planned. Either (a) cover the early Section B types with the contract-prescribed tests in this same PR series, or (b) tear them out and let Section B introduce them with their tests.

## Verification commands run

```
pnpm -r build           → all green
pnpm -r test            → all green (designer: 6, lsp: 4, integration: 15, ...)
pnpm -r typecheck       → all green
pnpm -r lint            → all green
pnpm --filter @modeler/designer test  → 6/6 pass
```

The green CI pipeline is real, but it covers the reducer only; the new LSP exports are not exercised by any test.
