# Tasks — Review 016 (Phase 3 Section E, follow-up)

All five plan tasks (E.1–E.5) plus all six review-015 findings are resolved at the code level. The only remaining work is strengthening two LSP unit-test assertions whose current shape lets regressions through.

---

## G1 — Strengthen test 2 in `symbol-detail.test.ts`

- [ ] Open `packages/lsp/src/__tests__/symbol-detail.test.ts`.
- [ ] In the test starting at line 65 (`'table with primaryKey and three columns → …'`), replace the assertion block (lines 93-97) with:
  ```ts
  expect(result).not.toBeNull();
  expect(result!.perKindData.kind).toBe('table');
  const pk = (result!.perKindData as { primaryKey: string[] }).primaryKey;
  expect(pk).toEqual(['id']);
  const cols = (result!.perKindData as { columns: unknown[] }).columns;
  expect(cols).toHaveLength(3);
  ```
- [ ] Run `pnpm --filter @modeler/lsp test`. Should stay green at 45.

## G2 — Strengthen test 7 in `symbol-detail.test.ts` with a real fixture

The current test uses `type: target` as an attribute type, which doesn't produce a reference the index tracks. Replace with a fixture that the ReferenceIndex actually captures. The cleanest pattern in this codebase is an entity `nameAttribute` reference or a relation `from:` / `to:` reference.

- [ ] Replace the test at lines 194-219 with something like:
  ```ts
  it('two refs to same entity → referencedBy.length === 2', () => {
    const table = new ProjectSymbolTable();
    const resolver = makeResolver();
    const refIndex = new ReferenceIndex();
    const manifest = makeManifest();

    parseAndUpsert(table, refIndex, resolver, `
  schema er namespace ent
  def entity target { attributes: [def attribute id { type: int }] }
  def relation r1 { from: er.ent.target, to: er.ent.target, cardinality: { from: "1", to: "*" } }
  def relation r2 { from: er.ent.target, to: er.ent.target, cardinality: { from: "1", to: "*" } }
    `);

    const result = buildSymbolDetail(
      'er.ent.target',
      table, resolver, refIndex, manifest,
      (uri) => documents.get(uri) ?? null, parseString,
    );

    expect(result).not.toBeNull();
    expect(result!.referencedBy.length).toBeGreaterThanOrEqual(2);
  });
  ```
- [ ] Run `pnpm --filter @modeler/lsp test`.
  - If green: keep this form. Note: each `def relation` contributes two references (one from + one to), so the actual count may be ≥4 — that's still a real assertion and matches the plan's *spirit* (at least N refs flow through). Adjust the lower bound (≥4 if you'd rather be exact about the relation case).
  - If red because relations aren't yet indexed as referencers (Phase 3.H carryover): change the fixture to use a single entity referring to `target` via, say, `nameAttribute: target.id` syntax — whichever pattern the existing Phase-2 reference-index tests use successfully (`grep -rE 'ReferenceIndex|refIndex.findByQname' packages/semantics/src/__tests__ packages/lsp/src/__tests__`). If neither pattern works in v1, mark the test `.skip` with a comment pointing at §3.H:
    ```ts
    it.skip('two refs to same entity → referencedBy.length === 2 — pending §3.H (relations indexed as symbols)', ...);
    ```
    Better an honest skip than a vacuous green.

## G3 — Decide whether tests should be typechecked

- [ ] Optional. If you want it: change `packages/lsp/tsconfig.json` to remove the `"exclude": ["src/__tests__/**/*"]` line, or move tests into the include scope of a separate `tsconfig.test.json` that's wired into `pnpm typecheck`. Then fix the `new Resolver()` / partial-`ResolvedManifest` calls in `symbol-detail.test.ts` (and any siblings) to pass real args.
- [ ] Skip if you'd rather defer — but **add it to the §3.K documentation pass** so future-you knows it's intentional.

## Final verification

- [ ] `pnpm --filter @modeler/lsp test` (45)
- [ ] `pnpm --filter @modeler/designer test` (46)
- [ ] `pnpm --filter @modeler/integration-tests test` (22)
- [ ] `pnpm -r lint && pnpm -r typecheck && pnpm -r build`
- [ ] Manual demo run (one-off, since this is the §E close-out): load `samples/v1-mini/`, click an entity, verify:
  1. Inspector populates with kind / name / qname / description / tags / source / attribute table / Referenced By.
  2. Click the source `file:line` button → "Copied" toast appears, clipboard contains `<absolute-path>:<line>`.
  3. Click a Referenced By entry → selection shifts and the new detail loads automatically.
  4. Click a relation edge → inspector shows `perKindData.kind === 'relation'`.
  5. Click empty canvas → inspector reverts to "Select a node to see its details."

Section E is **done** when G1 + G2 are in and the final verification passes.
