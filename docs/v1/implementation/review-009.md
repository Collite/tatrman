# Review 009 — Phase 3, Section B re-review (review-008 follow-up)

**Date:** 2026-05-15
**Branch:** `phase-03` — `git status` shows the staged Section B work plus working-tree edits answering `tasks-review-008.md`. No new commits since `528810b`; the dev is asking for a re-review of the unstaged delta.
**Scope verified:** `git diff HEAD` (15 files, +763 / −49) against `tasks-review-008.md`, contracts §2 / §6 / §7, full pipeline.

## Verdict

**Closer, but not done.** The four showstoppers from review-008 are addressed at the design level, three of them well; review them below for one remaining gap each. Two should-fix items from review-008 are still open, plus one new contract drift introduced by the `setLayout` round-trip test, plus a handful of small tidies. Two more rounds of work, not five.

## Showstoppers — re-check

### F1 (review-008) — `getModelGraph` end-to-end ✅ fixed

`server.ts:322-336` now ignores the per-document `params.textDocument.uri` and walks `documents.all()` into `buildProjectModelGraph(asts, schema)`. `model-graph.ts` exports `buildProjectModelGraph` as a sibling of `buildModelGraph`, with a known-qnames set spanning every contributing AST so cross-document FKs resolve. `App.tsx`'s synthetic `file:///<rootName>` is now harmless — the server doesn't look it up. The new unit test (`buildProjectModelGraph (multi-document)` → "cross-document FK resolves when def is in a different AST") proves the project-scoped path. Good.

### F2 (review-008) — `getSymbolDetail` returns real per-kind data ✅ fixed for top-level defs, ⚠ silently broken for nested qnames

`buildSymbolDetail` now takes `getDocument` and `parseDocument` callbacks (`server.ts:385-389`) and re-derives the real `Definition` via `findDefByQname`. The new integration test 4.4 confirms: `er.entity.artikl` returns `perKindData.kind === 'entity'`, `attributes.length > 0`, non-null `description`, non-empty `referencedBy`. The fake-`Definition` husk is gone. Good.

One unflagged limitation: `findDefByQname` (`model-graph.ts:560-580`) splits the qname on `.` and uses `name = parts.slice(2).join('.')`, then loops `result.ast.definitions` looking for a top-level def whose `def.name === name`. For `db.dbo.QZBOZI_DF.IDZBOZI` (a column qname), `parts.slice(2).join('.')` gives `'QZBOZI_DF.IDZBOZI'` — no top-level def has that name, so `findDefByQname` returns null and `getSymbolDetail` returns `null` for every column / attribute / nested symbol. This is acceptable for v1 (the Designer inspector only opens on table / entity / view nodes), but it should be either fixed or commented as an explicit limitation, with a test pinning the behaviour so a future refactor doesn't think it's a bug.

### F3 (review-008) — race-condition guard ✅ done; comment vs. code wording mismatch

`App.tsx:43-52` now has the prescribed comment **and** uses `Promise.all`, which is what the mini-list asked for. The dispatch happens after `await Promise.all(...)`, so the invariant holds. The comment text says *"We intentionally await each open in sequence; do not parallelise without preserving the all settled before dispatch invariant"* — the code parallelises, so the "in sequence" wording is now wrong. Reword to match: "We `await Promise.all` so every open settles before the dispatch; do not change the order so the dispatch fires before all opens."

Also: line 55 has a stray `void state.activeSchema;` left over from when the LSP-call effect lived here. Delete.

### F4 (review-008) — six prescribed integration tests ⚠ five done, one off-spec

`tests/integration/src/lsp-phase-03-custom-methods.test.ts` is created and contains five new cases. Round-trip layout, empty-layout, applyGraphEdit refusal, and getSymbolDetail-with-real-data all land cleanly.

The remaining one is the `getModelGraph` test for **`schema: 'db'`** on a multi-file project (mini-list "Tests-first" §3 item 2; review-008 task 4.5 explicitly named it: *"Required by the mini-list; currently only `'er'` is covered"*). The new test 4.5 (`lsp-phase-03-custom-methods.test.ts:154-185`) opens every `.ttr` file in `samples/v1-metadata/` — good — and then sends `schema: 'er'` (line 171). It's *another* `'er'` test, not the prescribed `'db'` one. Same regression as review-008. Change line 171 to `schema: 'db'` and update the assertions to match the db nodes/edges in the samples.

## New issues introduced by this iteration

### N1 — `LayoutFile.edges` shape drift

To make the round-trip integration test 4.2 pass, the layout JSON schema was changed (this iteration's diff to `model-graph.ts:144-160`) from:

```jsonc
edges: { "<qname>": { "bendPoints": [[x, y], ...] } }
```

to:

```jsonc
edges: { "<qname>": [ { "x": ..., "y": ... }, ... ] }
```

The TypeScript `LayoutFile` interface at `model-graph.ts:99` is **unchanged**:

```ts
edges: Record<string, { bendPoints: Array<[number, number]> }>;
```

The runtime schema and the typed contract no longer agree. The integration test 4.2 only passes because `setLayout` doesn't validate input — it `JSON.stringify`s whatever it receives. `getLayout` then validates against the new schema, which matches the array-of-points shape, and ajv returns true. But:

- The Designer (Section F) writes `LayoutFile` values typed with the old `bendPoints` shape — ajv will reject every save.
- Contracts §6.1 / §6.2 still describe `bendPoints`. No changelog entry was added.

Either back the schema change out (revert to `bendPoints`) and rewrite test 4.2's payload, **or** amend contracts §6.1 + §6.2 + the `LayoutFile` TypeScript interface to use the new shape and bump the changelog. Pick one; do not ship with the schema and the type disagreeing.

### N2 — Lint warning: unused `eslint-disable` directive

`pnpm --filter @modeler/designer lint` now reports:

```
packages/designer/src/components/Header.tsx
  28:5  warning  Unused eslint-disable directive (no problems were reported from 'no-param-reassign')
```

`no-param-reassign` isn't enabled in the workspace eslint config, so the directive does nothing. Remove the line. Do not silence the rule by enabling it; just delete the comment so the warning goes away.

## Should-fix items from review-008 — re-check

| review-008 | Status |
|---|---|
| F5 — drop `setProjectUri`, restore `loadProject` | ✅ done; reducer no longer has `setProjectUri`, `App.tsx:53` dispatches `loadProject` |
| F6 — undocumented `setGraph`/`graph` field | ✅ done; both removed from `designer-state.ts` and `designer-reducer.ts` |
| F7 + F8 — `webkitdirectory`, `accept` mismatch | ✅ done; `Header.tsx:74-83` adds `accept=".ttr,.ttrl,.toml"`, spreads `webkitdirectory`, renames the button to "Load Project Folder"; the new Header test 1.2f asserts the attribute |
| 8.1 — drop duplicate layout tests in `model-graph.test.ts` | ❌ not done; the four `validateLayout` cases at the bottom of `model-graph.test.ts:72-94` still duplicate `model-graph-layout.test.ts` (which has 9 cases). 8 mentions of `validateLayout` in the dup file vs 19 in the canonical file |
| 8.2 — comment on `extractFkRef` first-item rule | ✅ done; `model-graph.ts:526-528` |

## Verification commands run

```
pnpm -r build       → all green
pnpm -r test        → designer 17, lsp 33+8=41 (lsp.test.ts not output above; recount), vscode-ext 6, integration 20 — all pass
pnpm -r lint        → 0 errors, 1 warning (Header.tsx)
pnpm -r typecheck   → all green
```

`pnpm --filter @modeler/integration-tests test` shows 5 new cases in `lsp-phase-03-custom-methods.test.ts` plus the original 15 in `integration.test.ts`. `getSymbolDetail` for `er.entity.artikl` now passes a substantive assertion: label is the bare `'artikl'` (the sample has no Czech `displayLabel` on `artikl`, only a `description`, so the bare-name fallback is correct — the contract behavior is satisfied even though review-008's task 4.4 wrote the assertion as `'Artikl'`).

## Summary

Three of four review-008 showstoppers are properly closed. F4 is one assertion away from done. F2 is functionally done with one limitation worth pinning. N1 is the only *new* showstopper introduced — the schema change was a quick way to make the round-trip test pass, but it broke alignment with the contract and the TypeScript type. N2 is a one-line cleanup.

Recommended next PR: change the `getModelGraph` integration test to `schema: 'db'`, decide on the `LayoutFile.edges` shape (and bring contracts / type / schema back in sync), drop the duplicated layout tests, kill the lint warning and the dead `void state.activeSchema;`, fix the race-guard comment wording, and add a `// not v1` comment (or a passing null-test) on `findDefByQname`'s nested-qname behavior. After that, Section B is honestly done.
