# Tasks — Review 020

Section H is partly built but the integration test is red and the unit tests are tautological. The "fix" to `symbol-table.ts` is a no-op. Plan and implementation disagree on qname-namespacing. There's one real product bug (`referencedBy` returning self-references) that H surfaced.

Work top-down. Tasks marked ⚠ are the ones currently blocking H.

---

## H-A ⚠ — Decide the qname-namespacing rule before writing more code

This is the upstream question. The plan says e.g. `map.er2db.<name>`; the code does `map.<name>`. Pick one. Until you decide, the rest of the tasks have arbitrary acceptance criteria.

Pick **one** of:

- [ ] **Option 1 (recommended) — keep code, revise plan.** Qnames track `<schemaDirective.schema>.<schemaDirective.namespace>.<def-name>`. The document author controls placement. Consumers find symbols by `kind`, not by qname pattern.
  - Update `docs/plan/phase-03/H-symbol-indexing.md` examples to match: e.g. "query def in `schema query namespace query` document emits `query.query.<name>`", "er2db_entity in `schema map` (no namespace) emits `map.<name>`".
  - Update samples if you want a tidier story (e.g. add `namespace er2db` to `map.ttr` so qnames become `map.er2db.<name>`). Optional.
- [ ] **Option 2 — keep plan, revise code.** Implement per-kind synthetic namespacing: when adding a def whose kind is `query|role|er2db*|er2cnc*`, splice in a synthetic namespace component. Document that the qname namespace component is now sometimes synthetic.
  - Heavier: requires changes to `DocumentSymbolTable.addEntry`, the `Resolver` (which currently looks up by exact qname), and probably every test and sample that hard-codes qnames.
  - Genuinely surprising for users — qnames diverging from `schema X namespace Y` would need to be called out prominently.

Write the choice and rationale into the top of `progress-phase-03.md`'s Section H block. Everything downstream depends on it.

**Verify:** the choice is documented; subsequent tasks reference the chosen qname shape.

---

## H-B ⚠ — Replace the no-op block in `symbol-table.ts`

`packages/semantics/src/symbol-table.ts:108-112` re-sets the same key the unconditional `this.entries.set(qnameStr, entry)` at line 42 already set. Delete it.

- [ ] Open `packages/semantics/src/symbol-table.ts`.
- [ ] Delete lines 108–112:
  ```diff
  -    if (def.kind === 'relation' || def.kind === 'query' || def.kind === 'role' ||
  -      def.kind === 'er2dbEntity' || def.kind === 'er2dbAttribute' ||
  -      def.kind === 'er2dbRelation' || def.kind === 'er2cncRole') {
  -      this.entries.set(qnameStr, entry);
  -    }
  ```
- [ ] If you took **H-A Option 2**, add the synthetic-namespace logic where this block was. Otherwise no replacement is needed — line 42 already covers the top-level emit.

**Verify:**
```bash
pnpm --filter @modeler/semantics test    # still green (the deletion changes nothing semantically)
```

---

## H-C ⚠ — Rewrite the unit tests so they actually test Section H, not the existing top-level emit

`packages/semantics/src/__tests__/symbol-table-extended-kinds.test.ts` would pass with or without H-B. Make the tests meaningful.

If you took **H-A Option 1** (keep code, revise plan), the unit tests should assert that:

- [ ] **Per-kind:** each of the seven kinds, when defined inside its idiomatic `schema X namespace Y` document, produces a `SymbolEntry` with `kind: <that kind>` at qname `<X>.<Y>.<name>`. Drop the "all under `er.entity.*`" pattern — use one constructor per kind with the schema/namespace that matches the kind's typical location (`query/query`, `cnc/role`, `map/<empty or er2db>`, `er/entity`).
- [ ] **Through `ProjectSymbolTable.all()`:** add a single test that loads multiple documents of different kinds into a `ProjectSymbolTable` and asserts the right set of `kind`s shows up in `.all()`. This exercises what the LSP actually consumes.

If you took **H-A Option 2** (synthetic namespacing), the per-kind tests should match the synthetic shape (`query.query.<name>`, `map.er2db.<name>`, etc.) and there should be a separate test that asserts a kind change at the same name produces a different qname.

**Verify:**
```bash
pnpm --filter @modeler/semantics test    # 7 new cases pass
# Sanity-check that the tests would have failed without H-B's deletion (they would not — same as before).
# Sanity-check that the tests would fail if the def's kind were silently changed to 'entity' (they should).
```

---

## H-D ⚠ — Fix the `referencedBy` self-reference bug (real product bug)

`buildSymbolDetail.referencedBy` outputs `qname: loc.targetQname` for every entry, which equals the queried qname. The data needs to carry the *referrer*'s qname.

### H-D.1 Extend `ReferenceLocation`

- [ ] In `packages/semantics/src/reference-index.ts:12-17`, add a field:
  ```ts
  export interface ReferenceLocation {
    documentUri: string;
    source: SourceLocation;
    targetQname: string;
    referrerQname: string | null;   // <-- new; null when the ref is not inside a def
  }
  ```

### H-D.2 Populate it in `upsertDocument`

- [ ] In the same file, around lines 37–53, capture `enclosingQnameOf(ownerDef, schemaCode, namespace)` into a const at the top of the loop (it's already computed for the resolver call) and assign it to `loc.referrerQname`:
  ```ts
  for (const { ref, ownerDef } of collectAllReferences(ast)) {
    const referrerQname = enclosingQnameOf(ownerDef, schemaCode, namespace) ?? null;
    const res = resolver.resolveReference(/* … */);
    if (!res.resolved) continue;
    const loc: ReferenceLocation = {
      documentUri: uri,
      source: ref.source,
      targetQname: res.symbol.qname,
      referrerQname,
    };
    /* …push and index… */
  }
  ```

### H-D.3 Extend `enclosingQnameOf` to cover the seven kinds

Currently (`reference-index.ts:5-10`) it only handles `entity | table | view | procedure`. A `relation` def's `from:`/`to:` references will report `referrerQname: null` unless we add the new kinds. That's how `er.entity.artikl`'s `referencedBy` ends up empty for relations.

- [ ] Add the seven kinds to `enclosingQnameOf`. All seven follow the same shape — top-level def, no nesting:
  ```ts
  function enclosingQnameOf(def: Definition, schemaCode: string, namespace: string): string | undefined {
    if (
      def.kind === 'entity' || def.kind === 'table' || def.kind === 'view' || def.kind === 'procedure' ||
      def.kind === 'relation' || def.kind === 'query' || def.kind === 'role' ||
      def.kind === 'er2dbEntity' || def.kind === 'er2dbAttribute' ||
      def.kind === 'er2dbRelation' || def.kind === 'er2cncRole'
    ) {
      return [schemaCode, namespace, def.name].filter((s) => s !== '').join('.');
    }
    return undefined;
  }
  ```
  (Or factor out the kind whitelist as a constant if it appears elsewhere.)

### H-D.4 Use `referrerQname` in `buildSymbolDetail`

- [ ] In `packages/lsp/src/model-graph.ts:321-325`, replace `loc.targetQname` with `loc.referrerQname ?? loc.targetQname`:
  ```ts
  const referencedBy = refIndex.findByQname(qname).map((loc) => ({
    qname: loc.referrerQname ?? loc.targetQname,
    sourceUri: loc.documentUri,
    sourceLine: loc.source.line,
  }));
  ```
  (The fallback to `targetQname` is benign — it preserves current behaviour for the rare loc with `referrerQname === null`. Once the kind whitelist in H-D.3 is comprehensive, this fallback should never fire in practice.)

### H-D.5 Deduplicate `referencedBy`

Multiple refs from the same def (e.g. a relation with both `from:` and `to:` pointing at the same entity) shouldn't produce two identical rows. Deduplicate by `referrerQname`:

- [ ] After building `referencedBy`, fold to one entry per referrer qname (keep the first location):
  ```ts
  const seen = new Set<string>();
  const dedup: typeof referencedBy = [];
  for (const r of referencedBy) {
    if (seen.has(r.qname)) continue;
    seen.add(r.qname);
    dedup.push(r);
  }
  ```
  Or, equivalently, build a `Map<string, …>` and `Array.from(...values())`. The Inspector renders one row per referrer; duplicates are noise.

### H-D.6 Unit test the fix

- [ ] Add a unit test to `packages/lsp/src/__tests__/symbol-detail.test.ts` (or new file) that:
  - Loads two TTR docs: an `entity X` and a `relation R { from: X, to: X }`.
  - Calls `buildSymbolDetail('er.entity.X', …)`.
  - Asserts `referencedBy.length === 1` (deduplicated) and `referencedBy[0].qname === '<schema>.<ns>.R'` (the relation's qname per H-A's chosen scheme), not `er.entity.X`.

**Verify:**
```bash
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/integration-tests test
# integration test case 3 (er.entity.artikl referencedBy) should now turn green
```

---

## H-E ⚠ — Rewrite the integration test against the chosen qname scheme

The current substring filters (`.relation.`, `.er2dbEntity.`) don't match the implementation's qnames. Fix the test, don't fight the implementation.

- [ ] Open `tests/integration/src/symbol-indexing-extended.test.ts`.
- [ ] For case 1 (`workspace/symbol query="rel"`): instead of substring-matching the symbol *name*, iterate the response and call `modeler/getSymbolDetail` for each candidate qname, then filter by `detail.kind === 'relation'`. This is `O(N)` extra RPCs in a test but it's robust against qname-shape changes and tests what the user actually cares about. Alternative: add a `modeler/listSymbols` custom request that returns `{ qname, kind }[]` (see H-F) and filter on the client side.
- [ ] For case 2: same fix — find a relation by calling `getSymbolDetail` per candidate; assert `perKindData.kind === 'relation'` on the first hit. The current `res.find(s => s.name.includes('.relation.'))` returns undefined and the assertion crashes on `relationSymbol!`.
- [ ] For case 3 (`referencedBy` for `er.entity.artikl`): keep the assertion's *spirit* but filter by `detail.kind === 'relation' | 'er2dbEntity'` instead of qname substring. Concretely:
  ```ts
  const detail = await client.sendRequest('modeler/getSymbolDetail', { qname: 'er.entity.artikl' }) as
    | { qname: string; referencedBy: Array<{ qname: string }> }
    | null;
  expect(detail).not.toBeNull();

  const referrerKinds = await Promise.all(
    detail!.referencedBy.map(async (r) => {
      const d = await client.sendRequest('modeler/getSymbolDetail', { qname: r.qname }) as { kind: string } | null;
      return d?.kind;
    }),
  );
  expect(referrerKinds).toContain('relation');
  expect(referrerKinds).toContain('er2dbEntity');
  ```
- [ ] Once H-A is decided and H-D fixed, this should turn green.

**Verify:**
```bash
pnpm --filter @modeler/integration-tests test
# expected: 27/27 passes; the three H cases now green
```

---

## H-F (optional but recommended) — Add `modeler/listSymbols` for kind-aware queries

The standard LSP `workspaceSymbol` response collapses TTR's def-kind into an LSP `SymbolKind` enum. Several future Designer features (Inspector kind chips, kind-filtered search) want the original TTR kind without making an N+1 round-trip.

- [ ] In `packages/lsp/src/server.ts`, register a new handler:
  ```ts
  connection.onRequest('modeler/listSymbols', (params: { kinds?: string[]; limit?: number }) => {
    const limit = params.limit ?? 500;
    const allowed = params.kinds ? new Set(params.kinds) : null;
    return projectSymbols.all()
      .filter((s) => !allowed || allowed.has(s.kind))
      .slice(0, limit)
      .map((s) => ({ qname: s.qname, kind: s.kind, name: s.name }));
  });
  ```
- [ ] Add a type to `packages/lsp/src/index.ts` if Designer will consume it.
- [ ] Add an integration test asserting `listSymbols({ kinds: ['relation'] })` returns >= 1 row.

Skip if Section H's only consumer is the Inspector's `referencedBy` (already handled via `getSymbolDetail`). Drop it from this task list if you choose Option 1 of H-A and the integration test ends up RPC-per-symbol.

**Verify (if implemented):**
```bash
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/integration-tests test
```

---

## H.5 — Update the Phase 2 progress doc

- [ ] In `docs/plan/progress-phase-02.md` find the row in "Deferred to later phases" that reads:
  > `Indexing relations/queries/roles/er2db_* as separate symbol-table entries | Phase 3 if navigation needs it`
- [ ] Change the right column to `Completed in Phase 3.H (YYYY-MM-DD)` once H-A/B/C/D/E/H.5 are all done.
- [ ] In `docs/plan/progress-phase-03.md`, tick boxes H.1, H.2, H.3, H.4, H.5 only after the integration test is green end-to-end. Do not pre-tick.

---

## Final gate

After every task above:

```bash
pnpm -r build
pnpm -r test
pnpm -r lint
pnpm -r typecheck
pnpm --filter @modeler/integration-tests test    # 27/27 pass (was 24/27)
```

Then update the test totals at `progress-phase-03.md:136` from the actual run output, and tick the H boxes. Per [MEMORY → feedback-progress-doc-skepticism]: tick only after the runner is green for the integration tests, not just the unit ones.
