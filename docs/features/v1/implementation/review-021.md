# Review 021 — Re-review of review-020 fixes (Section H)

**Scope:** Verify the developer's claim that all of `tasks-review-020.md` is done and Section H is ready.

## TL;DR

All review-020 tasks have landed correctly and Section H integration is green end-to-end. Build, test, lint, and typecheck pass; the four `symbol-indexing-extended.test.ts` cases — including the `er.entity.artikl referencedBy` case that drove the `referrerQname` fix — all pass.

**Section H is done.** Two cosmetic points worth tightening before phase close-out; neither blocks.

---

## Confirmed fixed

| Task | Verification |
|---|---|
| H-A qname-namespacing rule | Option 1 (code wins, plan follows) — implicit via the chosen test shape: unit tests assert `query.q1.find_artikl`, `map.er2db.tabulka_artikl`, etc., matching whatever the document's `schema X namespace Y` declares. Integration test no longer depends on qname shape at all (filters by kind via `getSymbolDetail`). |
| H-B no-op block deletion | Lines 108–112 of `packages/semantics/src/symbol-table.ts` removed; semantics tests still green (47 → 48 with the new mixed-kinds project test). |
| H-C meaningful unit tests | `symbol-table-extended-kinds.test.ts` rewritten to use the real parser, exercise each of the seven kinds in its idiomatic schema/namespace, and includes a `ProjectSymbolTable.all() with mixed kinds` test that asserts kinds and qnames across multiple documents. |
| H-D.1 / H-D.2 `referrerQname` field | `reference-index.ts:17-24` adds `referrerQname: string \| null` to `ReferenceLocation`; `upsertDocument` populates it from `enclosingQnameOf(ownerDef, schemaCode, namespace) ?? null`. |
| H-D.3 extended `enclosingQnameOf` | `reference-index.ts:5-15` whitelist now includes `relation`, `query`, `role`, and the four er2*/er2cnc* kinds. |
| H-D.4 `buildSymbolDetail` uses `referrerQname` | `model-graph.ts:357-365`: `qname: loc.referrerQname ?? loc.targetQname`. Fallback safety preserved. |
| H-D.5 dedup | Same block uses a `seen` Set keyed on referrer qname to drop duplicate rows. |
| H-D.6 unit test for the fix | `symbol-detail.test.ts:196-224` adds `'two relations targeting the same entity → referencedBy.length === 2'` covering distinct-referrer behaviour. |
| H-E integration test rewrite | `symbol-indexing-extended.test.ts` now: (1) iterates `workspace/symbol` results and filters via `getSymbolDetail.perKindData.kind`; (2) uses new `modeler/listSymbols` for direct kind-filtered queries; (3) resolves `referencedBy` referrer qnames through `getSymbolDetail` and asserts on `kind`. No more brittle qname substring matching. |
| H-F `modeler/listSymbols` | `server.ts:400-407` — accepts optional `kinds` and `limit`, returns `{ qname, kind, name }[]`. Integration test 4 ("listSymbols with kinds=[relation]") asserts non-empty result with the right kind. |
| H.5 Phase 2 progress doc update | `progress-phase-02.md:129` flipped to `Completed in Phase 3.H (2026-05-16)`. |

## End-to-end verification

```bash
pnpm -r build                                # green
pnpm -r test                                 # 207 tests passed
pnpm -r lint                                 # 0 errors
pnpm -r typecheck                            # 0 errors
pnpm --filter @modeler/integration-tests test
#   symbol-indexing-extended.test.ts (4 tests) all ✓
#   28/28 integration cases pass (was 24/27 with 3 failing before review-020)
```

Test totals at `progress-phase-03.md:136` read `207 tests total (19 parser, 48 semantics, 45 lsp, 61 designer, 6 vscode-ext, 28 integration)` — matches the actual runner output exactly. First time this number has been honest mid-phase.

---

## Minor leftovers (not blocking)

### L-1. The plan doc still illustrates a qname scheme the implementation doesn't follow

`docs/plan/phase-03/H-symbol-indexing.md:15` reads:

> `er2dbEntity` def in `schema map namespace er2db` emits `map.er2db.<name>` with `kind: 'er2dbEntity'`.

The repo's `samples/v1-metadata/map.ttr` opens with `schema map` (no namespace), so the production er2db_entity defs land at qname `map.<name>`. The plan's example would only hold if the sample added `namespace er2db`. The H-A Option 1 choice was implicit; document it explicitly:

- Update `H-symbol-indexing.md` to phrase the rule as "qname = `<schemaDirective.schema>.<schemaDirective.namespace>.<def-name>` — namespace component is whatever the document declares". The seven kinds are no longer special-cased; they slot under their document's schema-directive like every other def.
- Either update the sample `map.ttr` to add `namespace er2db` (and adjust any cross-refs that depend on the qname shape) **or** delete the per-kind qname examples from the plan and replace with one note pointing at `samples/v1-metadata/` as the canonical shape.

Pick whichever; just don't leave the plan illustrating a scheme nobody implements. A future maintainer reading the plan first will be confused.

### L-2. Dedup test only covers distinct referrers

`symbol-detail.test.ts:196-224` asserts "two relations → 2 referencedBy entries" (distinctness preserved). The dedup code also collapses *the same referrer's two refs into one* — e.g. a relation with `from: X, to: X` should yield one row, not two. That branch isn't directly tested.

Add one short case:

```ts
it('one relation with from === to dedups to a single referencedBy entry', () => {
  // … parseAndUpsert with `def relation self_loop { from: er.ent.x, to: er.ent.x, … }`
  const result = buildSymbolDetail('er.ent.x', /* … */);
  expect(result!.referencedBy).toHaveLength(1);
  expect(result!.referencedBy[0].qname).toBe('er.ent.self_loop');
});
```

Two-line addition; closes the only remaining behaviour the dedup logic claims to do that isn't pinned.

### L-3. (carried over from review-019) `tsx` is still an orphan devDep

`packages/designer/package.json:36` still lists `"tsx": "^4.19.0"`. Removed prebuild script means nothing in `packages/designer/` invokes `tsx`. Drop on next pass:

```bash
pnpm --filter @modeler/designer remove -D tsx
```

Independent of Section H; mentioned because it's the only known untouched cleanup item.

### L-4. Progress doc's H block doesn't record the review-fix history

Sections F and G show explicit lines like `[x] F-1 review fix: …`. Section H jumps straight to ticked boxes without noting which review surfaced which fix. Optional; nice for future archaeology.

---

## Verdict

Section H is done. The carryover from Phase 2 is closed cleanly: the seven new kinds index correctly, the resolver and ReferenceIndex see them (verified by the rewritten integration test), and the real product bug exposed during review-020 (`referencedBy` self-references) is fixed at the data-model layer with end-to-end coverage.

No task list this time. L-1 (plan doc tidy-up) and L-2 (one extra dedup test) are worth picking up before phase close-out but neither blocks moving on to Sections I, J, K.
