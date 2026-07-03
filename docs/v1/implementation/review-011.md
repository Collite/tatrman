# Review 011 — Phase 3, Section C re-review (review-010 follow-up)

**Date:** 2026-05-15
**Branch:** `phase-03` — staged Section B work + working-tree Section C edits answering `tasks-review-010.md`. Still no new commit.
**Scope verified:** every task in `tasks-review-010.md`, plus the full pipeline.

## Verdict

**Seven of eight tasks landed cleanly. Task 5 is missing.** The contract amendment, the `loadProject` reset, the `currentGraph` mirror deletion, the cache-hit guard, the label CSS, the HTML escaping, and the module-scope `cytoscape.use` hoist are all in. The one task left unstarted is the most important — the four App-level behavioral tests that would prove the cache plumbing actually works. Without them, Section C is mechanics-only: green build, green tests, no assertion that the *behavior* the user sees matches the contract.

This is the same pattern as Sections A, B, and the earlier C pass: the *shapes* land, the *test cases that pin the wiring* don't. Pipeline is green (147 tests across all packages); designer test count is 26, which is exactly +2 from the review-010 baseline (the new reducer test in task 2 + the new adapter HTML-escape test in task 7). The four App tests would have made it 30.

## Tasks — re-check

| review-010 task | Status |
|---|---|
| **1** — contract amendment v1 → v2 (F2) | ✅ done. `phase-03-contracts.md` is at v2; §2 has `graphsBySchema` and `storeGraph`; changelog entry verbatim |
| **2** — `loadProject` resets `graphsBySchema` + test (F3) | ✅ done. `designer-reducer.ts:23` adds the reset; new reducer test `'loadProject' resets graphsBySchema cache` lands; 7 reducer tests total |
| **3** — delete `currentGraph` mirror (F4) | ✅ done. `App.tsx` reads `state.graphsBySchema[state.activeSchema]` directly; the local `useState` and the mirror effect are gone |
| **4** — cache-hit guard on `getModelGraph` (F5) | ✅ done. `App.tsx:42` short-circuits when the cache is populated; the effect dep array includes `state.graphsBySchema` so the post-fetch dispatch doesn't loop; `.catch()` dispatches `setError` |
| **5** — App-level behavioral tests (F1) | ❌ **NOT done.** `Canvas.test.tsx` is unchanged — single `renders a container div` smoke test. The four cases (load → call once; displayMode → no call; schema toggle → call; cache hit on toggle-back → no call) are absent |
| **6** — readable HTML labels (F7) | ✅ done (Path A). `index.css` has `.cy-node-label` / `.cy-row` / `.cy-row-name` / `.cy-row-type` / `.cy-row-badge` with the prescribed light colors |
| **7** — HTML escape (F8) | ✅ done. `adapter.ts` defines `escape(s)` and wraps every interpolation; new adapter test `'escapes < > & in row names and types'` lands; 7 adapter tests total |
| **8** — hoist `cytoscape.use` to module scope (nit) | ✅ done. `Canvas.tsx:8-24` exports a module-level `cytoscapeReadyPromise` that registers extensions once |

## The remaining gap: F1

Mini-list `C-db-rendering.md` "Tests-first" §2 named three cases by name. The cache-hit guard added in task 4 implies a fourth. None are on disk. The behavior the dev shipped is:

- The cache prevents a refetch on toggle-back to a previously-loaded schema (task 4 / F5).
- `loadProject` clears the cache so a new project starts fresh (task 2 / F3).
- The `displayMode` toggle does not retrigger `getModelGraph` because that's the displayMode effect path in `Canvas.tsx:152-166`, not the `getModelGraph` effect in `App.tsx:37-46`.
- Switching `activeSchema` for the first time does trigger `getModelGraph` because the cache slot is `null`.

Every one of those is a sentence that should be a unit test. Right now the only thing covering the App's cache-fetch effect is the manual demo path in §9.3 of the task list — and that requires the dev server, the samples bundle, and a human looking at devtools. A regression in the effect dep array, the cache guard, or the `loadProject` reset will not be caught by CI.

The right home is `packages/designer/src/__tests__/App-getModelGraph.test.tsx` (or, if React-Testing-Library's render-then-fire-events story is too noisy, lift the fetch logic into a custom hook `useProjectGraph(state, dispatch, client)` and unit-test the hook — strictly cleaner anyway). The mocks are already in place in `Canvas.test.tsx`; copy-paste them.

## What was done well

- The contract amendment is verbatim and the changelog line names every shape change including the `graphsByCachedSchema` → `graphsBySchema` rename. This is the discipline that was missing through Section B.
- `loadProject` reset is paired with a real reducer test that pre-populates the cache. Future "I'll just dispatch `setProjectUri` again" mistakes will turn red.
- The cache-hit guard in `App.tsx:42` uses the obvious `if (state.graphsBySchema[schema]) return` shape; the dep array addition is correctly motivated by a one-line comment ("Cache hit: skip the LSP round-trip…").
- `escape(s)` is applied to every interpolation including the constant `'PK'` / `'NN'` strings — consistent, no holes.
- The Cytoscape extension registration is now a single module-load side effect; React strict mode double-mount in dev is safe.

## Pipeline

```
pnpm -r build       → all green
pnpm -r test        → 147 total
                       parser 19, semantics 40, lsp 35, designer 26, vscode-ext 6, integration 21
pnpm -r lint        → 0 errors, 0 warnings
pnpm -r typecheck   → all green
```

(The progress-doc test-results block says `1 warning (designer Header eslint-disable no-param-reassign)` — that's stale; the directive was removed in review-009 task 5 and lint is clean now. Refresh the table when you commit.)

## Recommendation

One short PR: write the four App-level tests (task 5 of `tasks-review-010.md`). Either resurrect the recommended path in 5.1–5.5 (mock `createLspClient`, render `<App />`, drive via Header callbacks) or — cleaner — extract the cache-fetch effect into `useProjectGraph(state, dispatch, client)` and unit-test the hook with `renderHook`. Either is ≤ 80 LOC of test code. After that, Section C is honestly done.
