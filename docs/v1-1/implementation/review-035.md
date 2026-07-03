# Review 035 — Section C2 (`.ttrg` LSP custom methods)

**Date:** 2026-05-21
**Scope:** C2 (`docs/v1-1/plan/tasks/C2-ttrg-lsp-methods.md`) — the six new `modeler/*` methods, the four updated layout methods, the `WorkspaceEdit` builders, and the Designer `LspClient` wrappers. Verified against runtime. Companion: [`tasks-review-035.md`](tasks-review-035.md).
**Verdict:** **Changes requested — C2 is not done.** The **read** side (`listGraphs`, `getGraph`, `getPackageGraph`) is wired into both entry points and returns data, so the happy-path smoke tests pass. But the **write** side is largely stubbed, buggy, or untested: `setLayout` is a stub that returns an error, both object-edit builders produce malformed/no-op output on their untested branches, `autoImport`/`pruneUnusedImport` are ignored, half the Designer wrappers are missing, the `.ttrl` removal (D4) and the `getModelGraph`/`getGraph` decision (D3) were never done, and the unit-test file that would have caught the builder bugs doesn't exist. The integration tests pass only because they never exercise any of the broken paths.

> **The "all tests pass" claim is misleading here.** The suite is green (parser 82, semantics 107, lsp 53, designer 61, vscode-ext 7, integration 59 passed | 1 skipped), but the green is hollow on the write side — see F14/F15.

---

## What works (verified)

- **C2.1 `listGraphs`** — registered, returns `{ graphs: [...] }` with name/schema/objectCount; tested for the empty case and the one-graph case.
- **C2.2 `getGraph`** — registered, returns `{ schema, nodes, edges, layout, missingObjects }`; `missingObjects` is correctly computed and the layout block passes through (the `er.entity.foo` layout-preservation test passes).
- **C2.5 `getPackageGraph`** — registered, returns `{ packages, dependencies, cycles }`.
- **Both entry points covered.** `server-stdio.ts` and `server-browser.ts` both delegate to `createServerConnection`, so registering the handlers there satisfies "registered in both entry points."
- **Re-exports** (`@modeler/lsp/index.ts`, `@modeler/edit/index.ts`) expose the new request/response types and builders without duplication.

---

## Findings

### Write-side builders (`packages/edit/src/graph-edits.ts`)

#### F1 [High] — builders are regex/string-based, not CST/offset-based (architectural deviation)

C2.6 was explicit: *"Use the parser's CST view (`getHiddenTokensToLeft/Right`) to detect indentation and trailing-comma policy in the `objects: [...]` array; insert/remove cleanly."* The architecture invariant (CLAUDE.md) is that the edit synthesizer makes **surgical** patches off AST/CST source locations. Instead the builders use ad-hoc regex over the raw string. The consequences (F2/F3) are demonstrable bugs, not hypotheticals.

#### F2 [High] — `buildAddObjectEdit` emits malformed TTR when the list is non-empty

```ts
newObjectsContent = `${existingContent.trim()}, ${qname}]`;   // <-- no opening '['
const newText = `objects: ${newObjectsContent}`;
```

For `objects: [er.entity.a]`, adding `er.entity.b` yields `objects: er.entity.a, er.entity.b]` — **the opening `[` is dropped**, producing invalid syntax. Only the empty-list branch (which builds `[${qname}]`) is correct, and that is the **only** branch any test exercises (`objects: []`). The builder also collapses a multi-line `objects` array onto one line, discarding indentation — the opposite of the CST-aware behaviour C2.6 required.

#### F3 [High] — `buildRemoveObjectEdit` has a regex bug and rewrites the whole document

```ts
const objectPattern = new RegExp(`([,s])\\s*${qnameEscaped}\\s*(,?\\s*])`, 'g');
```

- `[,s]` is a character class matching a comma **or the literal letter `s`** — almost certainly a typo for `[,\s]`. As written it matches stray `s` characters and misses real delimiter cases.
- The leading delimiter never matches `[`, so removing the **first or only** object in a list silently does nothing (returns `{ documentChanges: [] }`).
- On a match it replaces the **entire document** (`range: { start: 0/0, end: MAX/MAX }`, `newText: newContent`) rather than a surgical patch — the opposite of the design intent.

There is **no test for `removeObjectFromGraph` at all** (see F15), so none of this is caught.

#### F4 [High] — `autoImport` / `pruneUnusedImport` are ignored

Both params are received as `_autoImport` / `_pruneUnusedImport` and never used. C2's tests-first explicitly required: *"With `autoImport: true`, the `WorkspaceEdit` also adds an `import` statement"* and *"when `pruneUnusedImport: true` and no other object needs the import, the `import` line is also removed."* Neither is implemented.

#### F5 [Med] — `version: null as unknown as number` type hack

`buildRemoveObjectEdit` and `buildCreateGraphEdit` write `version: null as unknown as number`. The field is `OptionalVersionedTextDocumentIdentifier.version` = `integer | null`, so `version: null` is valid with **no cast**. The local `buildTextEdit` helper already types it correctly (`number | null`); reuse it instead of the `as unknown as` backdoor (ESLint forbids `any`-equivalents).

### Read-side handlers (`packages/lsp/src/graph-methods.ts`)

#### F6 [Med] — `getGraph` duplicates the per-kind node/row builders

C2.2 said *"reuses the v1 per-kind row builders from `model-graph.ts`."* Instead `getGraph` re-implements table/view/entity row construction inline — a third copy of logic that already lives in `buildProjectModelGraph`. Same DRY problem C1-F2 fixed via `buildEdgeForDef`; do the same here: extract a shared `buildNodeForDef` in `model-graph.ts` and call it from both `buildProjectModelGraph` and `getGraph`.

#### F7 [Med] — `listGraphs` hardcodes `missingObjectCount: 0`

`GraphMetadata.missingObjectCount` is always `0` — never computed against the symbol table. Either compute it (resolve each `objects` entry as `getGraph` does) or remove the field from the contract; a field that's permanently `0` is fake data.

#### F8 [Med] — `getPackageGraph` fakes the symbol table and rebuilds from scratch

```ts
const mockProjectSymbols = { all() { return allSymbolEntries; } } as unknown as ProjectSymbolTable;
const builder = new PackageGraphBuilder(mockProjectSymbols, docs);
```

C2.5 said *"Reads from the **cached** `PackageGraph` produced in B3.7."* Instead it re-parses every document and rebuilds the package graph on each call, behind an `as unknown as` cast. Read the cached graph (`server.ts` already maintains a `PackageGraph`) and drop the mock.

#### F9 [Low] — inconsistent default `schemaCode`

`buildQnameToDef` defaults a missing schema directive to `'db'`; `computeGraphEdges` defaults to `'er'`. For a doc with no schema directive, node qnames and edge qnames are then computed under different schemas and never match. Pick one default and share it.

### Updated methods / decisions (`server.ts`, C2.7)

#### F10 [High] — `setLayout` via `graphUri` is a stub that returns an error; D1 not implemented

```ts
if (_params.graphUri) {
  return { ok: false, reason: 'setLayout via graphUri requires workspace/applyEdit — C2.6 pending' };
}
```

Writing layout back into a `.ttrg` is the point of C2.7, and it isn't implemented — it returns failure with reason *"C2.6 pending"* even though C2.6 supposedly landed. The D1 requirement (*"`setLayout` must emit `layout.nodes` keys as unquoted dotted ids"*) has **no implementation** — there is no layout-writing edit builder.

#### F11 [High] — D3 not settled, D4 not done, `.ttrl` still present

- **D3:** `getModelGraph` is unchanged (still whole-project, `{ schema }`-based) and `getGraph` was added alongside. Two near-identical render methods now coexist with **no documented decision** — exactly what D3 existed to prevent. The F6 node-builder duplication is the concrete cost.
- **D4 / `.ttrl`:** C2.7 said *"the project-wide layout (`.modeler/layout.ttrl`) is gone … remove the `.ttrl` read/write paths."* The `.ttrl` read/write code in `getLayout`/`setLayout`/`exportLayout` is **still there** (just made conditional on `projectRoot`). The "layout is canonical inside the `.ttrg`" goal is unmet.
- **CC2 / CC3** were (correctly) not applied — but only because `.ttrl` wasn't actually removed. That's a symptom of F10/F11, not completion.

#### F12 [Med] — `getLayout` via `graphUri` returns hardcoded fake viewports

```ts
viewports: { db: { zoom: 1.0, … }, er: { zoom: 1.0, … } },
```

It ignores the parsed `graph.layout.viewport` and returns fixed per-schema defaults — fake data, and per-schema viewports contradict the v1.1 "each `.ttrg` is locked to a single schema, no per-schema viewports" decision (contracts §11).

### Designer client (`lsp-client.ts`, C2.8)

#### F13 [High] — only half of C2.8 done

C2.8 required wrappers for *"all six new methods plus the four updated signatures."* Present: `listGraphs`, `getGraph`, `getPackageGraph` (3 of 6). **Missing:** `addObjectToGraph`, `removeObjectFromGraph`, `createGraph`. The four layout signatures were **not** updated to take `graphUri` — the client still exposes `getLayout(projectRoot)` / `setLayout(projectRoot, layout)`, and `useLayoutSync` still reads layout from the project root, not the `.ttrg`.

### Testing

#### F14 [High] — the unit-test file for the builders is missing

C2's tests-first required `graph-builders.test.ts` covering insertion point / indentation / trailing comma / import add+remove. It does not exist; `@modeler/edit` has **no `__tests__` directory at all**. This is exactly where F2/F3/F4 would have surfaced.

#### F15 [High] — the integration tests are shallow and miss every broken path

`lsp-v1.1-graph-methods.test.ts` asserts shape, not behaviour:
- `getGraph` is only called with `objects: []` or an unresolvable object — so it **never builds a single node or edge**. The entire F6 node-building path is untested at runtime.
- `addObjectToGraph` only asserts `documentChanges.length > 0` (never applies the edit, never checks content, never tests `autoImport: true`).
- **No** `removeObjectFromGraph` test, **no** `setLayout` test, **no** `getLayout` test.
- `createGraph` only checks `length > 0`, not that it contains a `CreateFile` + the canonical body.

The stubbed/broken paths (F2 non-empty add, F3 remove, F4 autoImport, F10 setLayout) are precisely the ones with no test.

---

## Recommendation

Land C2 in the two reviewable slices the task list already calls for, and treat them honestly:

- **C2-read** is close. Fix F6 (share node builders), F7 (`missingObjectCount`), F8 (cached package graph, drop the cast), F9 (default schema). Add an integration test where `getGraph` resolves real objects and asserts the nodes **and** edges.
- **C2-write is not implemented.** It needs: real CST/offset-based builders (F1–F4), the `graph-builders.test.ts` unit suite (F14), `setLayout`-into-`.ttrg` with unquoted keys (F10/D1), the D3 decision on `getModelGraph` vs `getGraph` (F11), actual `.ttrl` removal + CC2/CC3 (F11), the missing client wrappers (F13), and integration tests that apply the edits and verify content (F15).

The `tasks-review-035.md` list is ordered so C2-read can be closed quickly and C2-write is broken into concrete, testable steps.
