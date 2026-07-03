# Tasks — Review 009 (Phase 3, Section B — final cleanup)

Companion to `review-009.md`. Section B is one focused PR away from done. Tasks 1–3 are the must-fix items; 4–7 are tidies. Do them in order — each verification command must pass before you tick the box.

## 1. Switch the multi-file `getModelGraph` integration test to `schema: 'db'` (review-008 F4 / task 4.5)

The test at `tests/integration/src/lsp-phase-03-custom-methods.test.ts:154-185` opens every `.ttr` file (good) but sends `schema: 'er'` (line 171). The mini-list explicitly required `schema: 'db'` here — the `'er'` case is already covered by the existing `integration.test.ts` test.

- [ ] **1.1** In `tests/integration/src/lsp-phase-03-custom-methods.test.ts:171`, change `schema: 'er'` to `schema: 'db'`.
- [ ] **1.2** Update line 178 from `expect(result.schemaCode).toBe('er')` to `'db'`.
- [ ] **1.3** Pick a real db file URI for the `textDocument.uri` hint — e.g. `file://${ttrFiles.find(f => f.endsWith('db.ttr')) ?? ttrFiles[0]}` (currently looks for `er.ttr`). The handler ignores the URI for graph building, but pass something accurate for clarity.
- [ ] **1.4** Re-run the test on the actual sample data and confirm `result.edges.length >= 5` still holds for db. If db has fewer than 5 edges in `samples/v1-metadata/`, lower the threshold to the actual count and add a one-line comment naming the sample-derived expectation, e.g. `// samples/v1-metadata has N FK edges in db; bump if the sample changes`.
- [ ] **1.5** Verify by running: `pnpm --filter @modeler/integration-tests test`. Expect 20 passing.

## 2. Resolve the `LayoutFile.edges` schema-vs-type drift (N1)

The runtime JSON schema now expects `edges: { "<qname>": [{x, y}, ...] }`; the TypeScript `LayoutFile.edges` and contracts §6.1 / §6.2 still describe `{ "<qname>": { bendPoints: [[x, y], ...] } }`. Pick one shape; do not ship both.

Pick **one**:

### Path A (recommended) — back the schema change out

The original `bendPoints` shape is what contracts §6 specifies and what Cytoscape-side bend-point persistence (Section F) will want.

- [ ] **2.1A** In `packages/lsp/src/model-graph.ts`, revert the `edges` block of `layoutSchema` (current lines ~144-160) to:

  ```ts
  edges: {
    type: 'object',
    patternProperties: {
      '^.+$': {
        type: 'object',
        required: ['bendPoints'],
        additionalProperties: false,
        properties: {
          bendPoints: {
            type: 'array',
            items: { type: 'array', items: { type: 'number' }, minItems: 2, maxItems: 2 },
          },
        },
      },
    },
    additionalProperties: false,
  },
  ```

- [ ] **2.2A** In `tests/integration/src/lsp-phase-03-custom-methods.test.ts:92`, change `edges: { 'db.dbo.rel1': [{ x: 150, y: 250 }] }` to `edges: { 'db.dbo.rel1': { bendPoints: [[150, 250]] } }`.
- [ ] **2.2A.cont** Update the assertion at line 107 from `expect(getResult.edges['db.dbo.rel1']).toEqual([{ x: 150, y: 250 }])` to `expect(getResult.edges['db.dbo.rel1']).toEqual({ bendPoints: [[150, 250]] })`.
- [ ] **2.3A** Verify by running: `pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test`. All green.

### Path B (alternative) — amend the contract to match the new shape

Only pick this if you have a concrete reason the array-of-points shape is better than `bendPoints`.

- [ ] **2.1B** In `packages/lsp/src/model-graph.ts`, change the TypeScript `LayoutFile.edges` (line 99) to `Record<string, Array<{ x: number; y: number }>>`. Delete the `EdgeLayout` type if it exists.
- [ ] **2.2B** In `docs/design/phase-03-contracts.md` §6.1, replace the `EdgeLayout` interface and the `LayoutFile.edges` line with the new shape. In §6.2, update the JSON Schema fragment to match. Bump the version note from v1 → v2 and add a one-line changelog entry naming the shape change.
- [ ] **2.3B** Verify by running: `pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test`. All green.

## 3. Delete the duplicate layout tests in `model-graph.test.ts` (review-008 task 8.1)

`model-graph.test.ts:72-94` duplicates four cases that live in `model-graph-layout.test.ts` (which is the canonical 9-case file).

- [ ] **3.1** In `packages/lsp/src/__tests__/model-graph.test.ts`, delete the entire `describe('validateLayout / emptyLayout', ...)` block at the bottom (the four `it` cases). Also delete the `emptyLayout, validateLayout, type LayoutFile` imports from the top if they become unused.
- [ ] **3.2** Verify by running: `pnpm --filter @modeler/lsp test`. Total LSP tests should drop by 4 (e.g., 37 → 33). `model-graph-layout.test.ts` still owns these.

## 4. Fix the race-guard comment wording (review-009 F3 nit)

`App.tsx:43-47` says *"We intentionally await each open in sequence"*; the code now uses `Promise.all`.

- [ ] **4.1** Replace the block comment with:

  ```ts
  // RACE-CONDITION GUARD: every openDocument MUST settle before getModelGraph
  // fires, or browser-mode cross-file resolution sees an incomplete project
  // and returns a graph missing edges. We `await Promise.all` so every open
  // resolves before the loadProject dispatch; do not refactor so the dispatch
  // happens before the await completes.
  ```

- [ ] **4.2** Delete the orphan `void state.activeSchema;` at `App.tsx:55` — leftover from a removed effect.
- [ ] **4.3** Verify by running: `pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer test`. 17 passing.

## 5. Remove the unused `eslint-disable` directive (N2)

- [ ] **5.1** In `packages/designer/src/components/Header.tsx:28`, delete the line `// eslint-disable-next-line no-param-reassign`. The `input.value = ''` reset stays.
- [ ] **5.2** Verify by running: `pnpm --filter @modeler/designer lint`. Expect 0 warnings.

## 6. Pin or fix the nested-qname behaviour of `findDefByQname` (review-009 F2 limitation)

`findDefByQname` only matches top-level def names; column / attribute qnames return `null` from `getSymbolDetail`. This is acceptable for v1 but should not be silent.

Pick **one**:

### Path A (recommended for now) — pin the limitation with a test and a comment

- [ ] **6.1A** Add a comment above `findDefByQname` (`packages/lsp/src/model-graph.ts:560`):

  ```ts
  // v1 limitation: only top-level defs (table / view / entity) are looked up.
  // Nested qnames like `db.dbo.tableName.colName` return null. The Designer
  // inspector only opens on top-level nodes, so this is enough for Phase 3;
  // remove this restriction when row-level inspection lands.
  ```

- [ ] **6.2A** Add a test case in `tests/integration/src/lsp-phase-03-custom-methods.test.ts`:

  ```ts
  it('4.6 getSymbolDetail for a column qname returns null in v1', async () => {
    const ttrFiles = await getAllTtrFiles(samplesDir, ['broken']);
    for (const file of ttrFiles) {
      const content = await import('fs/promises').then(fs => fs.readFile(file, 'utf-8'));
      client.sendNotification('textDocument/didOpen', {
        textDocument: { uri: `file://${file}`, languageId: 'ttr', version: 1, text: content },
      });
    }
    await sleep(100);
    const result = await client.sendRequest('modeler/getSymbolDetail', {
      qname: 'db.dbo.QZBOZI_DF.IDZBOZI', // pick a real column qname from the samples
    });
    expect(result).toBeNull();
  });
  ```

  Replace the qname with a real column / attribute qname from the samples (look for one that the Phase-2 symbol table actually indexes).

### Path B — fix the limitation now

- [ ] **6.1B** Extend `findDefByQname` to recursively descend into `def.columns` and `def.attributes`, matching `parts[2]` against a parent and `parts[3]` against a child. Mirror the qname-builder logic from `qnameOf` in `server.ts:142`.
- [ ] **6.2B** Add a positive test asserting `getSymbolDetail` returns a non-null `SymbolDetail` for a real column qname, with `kind: 'column'` and a populated `perKindData` (extend `PerKindData` if needed; coordinate with contracts §5.1).

## 7. Final acceptance

- [ ] **7.1** From the repo root: `pnpm -r build && pnpm -r test && pnpm -r lint && pnpm -r typecheck`. All exit 0; no warnings.
- [ ] **7.2** `pnpm --filter @modeler/integration-tests test` shows the corrected `schema: 'db'` test passing and the optional 4.6 nested-qname test if you took path A in task 6.
- [ ] **7.3** Open the dev server (`pnpm --filter @modeler/designer dev`), pick `samples/v1-metadata/` via "Load Project Folder", confirm the network panel shows N `openDocument` notifications followed by the `loadProject` dispatch (no `getModelGraph` round-trip until Section C).
- [ ] **7.4** Tick B.1–B.7 in `docs/plan/progress-phase-03.md` only after 7.1–7.3 pass.
- [ ] **7.5** Stage and commit. The commit message should name (a) which path you picked in task 2, (b) which path you picked in task 6, and (c) the contracts changelog bump if path B was picked anywhere.
