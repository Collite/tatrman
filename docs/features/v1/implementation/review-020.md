# Review 020 — Phase 3 Section H (Symbol indexing carryover)

**Scope:** Review the work-in-progress for `docs/plan/phase-03/H-symbol-indexing.md` against the plan. Section H is meant to index seven additional `Definition.kind` values (`relation`, `query`, `role`, `er2dbEntity`, `er2dbAttribute`, `er2dbRelation`, `er2cncRole`) so that `workspaceSymbols`, `getSymbolDetail`, and the Inspector's *Related symbols* list cover them.

The progress doc has H.1–H.5 all unticked, but the working tree contains modifications to `packages/semantics/src/symbol-table.ts`, new unit tests, and a new integration test. Reviewing what's there.

## TL;DR

**Section H is not ready.**

1. **The integration test fails.** All three cases of `tests/integration/src/symbol-indexing-extended.test.ts` fail when run (`pnpm --filter @modeler/integration-tests test`). The developer's own H.4 acceptance criterion is unmet.
2. **The "fix" to `symbol-table.ts` is a no-op.** Line 42 of `addEntry` (pre-existing) unconditionally calls `this.entries.set(qnameStr, entry)` for every def, including the seven "new" kinds. The added `if (def.kind === 'relation' || …) this.entries.set(qnameStr, entry)` block (lines 108–112) re-sets the same key to the same value. Delete the block and behaviour is unchanged. The Phase 2 deferral note had already been resolved by that line; nobody noticed.
3. **The seven unit tests are tautological** — they would pass before *and* after the added conditional. They prove nothing about Section H.
4. **There's a real bug in `buildSymbolDetail.referencedBy`** that Section H exposes and the integration test correctly catches: every entry's `qname` is the *target* (i.e. always equal to the queried symbol), not the *referrer*. The current `ReferenceLocation` doesn't even carry the referrer's qname, so the field can't be computed without an underlying schema change.
5. **The plan and the implementation disagree on qname-namespacing for the new kinds.** The plan envisions e.g. `map.er2db.<name>`; the actual qname is `map.<name>` because the `map.ttr` sample has no namespace and `DocumentSymbolTable` uses whatever's on the schema directive. Either the plan moves, or an explicit per-kind namespace is added; right now the integration test substring-matches `.er2dbEntity.` which exists nowhere.
6. **`workspaceSymbols`'s response carries an LSP `SymbolKind` enum number, not the TTR def kind.** The integration test's strategy of "filter by qname substring to find the relations" only works if relations live under a literal `.relation.` namespace — they don't, they live under `er.entity.<name>`. The test design needs revisiting either way.

Plus the small process problems we've seen all phase: H.5 (update Phase 2 progress doc) not done, progress-phase-03 H boxes all still `[ ]`, and the dev hasn't surfaced that the integration test is red. Same `feedback-progress-doc-skepticism` pattern — `[x]` reflects intent, but here even the ticks are missing.

---

## What's actually changed

### `packages/semantics/src/symbol-table.ts`

```diff
+    if (def.kind === 'relation' || def.kind === 'query' || def.kind === 'role' ||
+      def.kind === 'er2dbEntity' || def.kind === 'er2dbAttribute' ||
+      def.kind === 'er2dbRelation' || def.kind === 'er2cncRole') {
+      this.entries.set(qnameStr, entry);
+    }
```

Look at line 42 of the same file:

```ts
this.entries.set(qnameStr, entry);
```

That set is **unconditional** and runs before any per-kind branch. So the new block does nothing — it re-inserts the exact same key/value pair. Confirmed by:

* `git show HEAD:packages/semantics/src/symbol-table.ts` — line 42 is present pre-diff.
* `pnpm --filter @modeler/semantics test` passes with or without lines 108–112.

The Phase 2 deferral note "Indexing relations/queries/roles/er2db_* as separate symbol-table entries" was already satisfied by the existing unconditional set. There was nothing to fix in `symbol-table.ts` for the basic indexing.

If the intent of Section H was *more* than top-level indexing (e.g. per-kind nested children — there aren't any for these kinds — or kind-specific namespacing), the implementation doesn't reflect it.

### `packages/semantics/src/__tests__/symbol-table-extended-kinds.test.ts`

Seven tests, one per kind. They all use:

```ts
const tbl = new DocumentSymbolTable('test://test.ttr', doc, 'er', 'entity');
return tbl.get(`er.entity.${name}`);
```

i.e. *every* def is filed under `er.entity.<name>`, regardless of kind. Two issues:

1. **Tautological.** The unconditional set at line 42 already does this for every kind. The tests cannot distinguish "Section H landed" from "Section H was never started".
2. **Wrong namespacing for half the kinds.** A `query` def isn't supposed to live under `er.entity.*` — the plan's expected qname is `query.q1.<name>`. The tests are unit-level and the constructor takes schema/namespace as args, so the choice is up to the test author — but choosing `er.entity` for `query`, `role`, `er2dbEntity`, etc. silently encodes the "everything ends up wherever its document says" behaviour, which is what the integration test then fails on. The tests don't drive the implementation toward the plan's qname shape; they passively codify whatever already happens.

A meaningful unit test for H would either (a) assert that a `query` def in `schema query namespace query` produces qname `query.query.<name>` and that the kind survives into `SymbolEntry.kind`, or (b) assert the same via `ProjectSymbolTable.all()` filtered by kind.

### `tests/integration/src/symbol-indexing-extended.test.ts`

Three cases, all failing on `pnpm --filter @modeler/integration-tests test`:

| Case | Failure |
|---|---|
| `workspace/symbol query="rel"` includes ≥1 relation | Returns 0 relations. Filter is `n.includes('.relation.')`; relations live under `er.entity.<name>`, so the substring never appears. |
| `getSymbolDetail` for a known relation returns `perKindData.kind === 'relation'` | The lookup step (`res.find(s => s.name.includes('.relation.'))`) returns `undefined` for the same reason. |
| `er.entity.artikl referencedBy includes ≥1 relation and ≥1 er2dbEntity` | `referencedBy` returns 14 entries, all with `qname: 'er.entity.artikl'`. Not a single distinct referrer. |

The first two are test-design flaws (substring on qname). The third is a **real product bug**, covered below.

### `packages/lsp/src/__tests__/symbol-detail.test.ts`

Untracked file. Looks like an internal `buildSymbolDetail` unit test; doesn't materially affect H scope.

---

## Real bug exposed by H

`packages/lsp/src/model-graph.ts:321-325`:

```ts
const referencedBy = refIndex.findByQname(qname).map(loc => ({
  qname: loc.targetQname,
  sourceUri: loc.documentUri,
  sourceLine: loc.source.line,
}));
```

`ReferenceIndex.findByQname('er.entity.artikl')` returns the locations of all refs that resolve *to* `er.entity.artikl`. The mapper then sets each `referencedBy` entry's `qname` to `loc.targetQname` — which is the target of the ref, i.e. the queried qname itself. The result: every entry says `qname: 'er.entity.artikl'`, regardless of who actually did the referencing. That's what the integration test exhibits: 14 self-references.

The intended semantic — `referencedBy` = "what symbols mention this one" — needs the **referrer's qname**, not the target's. Currently `ReferenceLocation` (in `packages/semantics/src/reference-index.ts:12-17`) stores:

```ts
export interface ReferenceLocation {
  documentUri: string;
  source: SourceLocation;
  targetQname: string;
}
```

So even if `buildSymbolDetail` wanted to do the right thing, the data isn't there. Fix:

1. Extend `ReferenceLocation` with `referrerQname: string | undefined` (undefined when the ref isn't inside a def — should be rare).
2. Populate it in `ReferenceIndex.upsertDocument`: the loop already computes `enclosingQnameOf(ownerDef, …)`; use that.
3. In `buildSymbolDetail`, map `loc.referrerQname` (with `loc.targetQname` as a fallback for safety) into the `qname` field of each `referencedBy` entry.

Note `enclosingQnameOf` (`reference-index.ts:5-10`) currently only computes enclosing qnames for `entity | table | view | procedure`. **Relations and er2db_* defs are not covered**, so their refs would report `referrerQname: undefined`. That has to be extended to the new kinds — otherwise `er.entity.artikl`'s `referencedBy` still won't list "the relation X from-points at me" because the relation isn't recognised as an enclosing scope. Two fixes in one.

Once both fix-ups land, integration test case 3 should turn green naturally (relations point at `er.entity.artikl`; er2db_entity defs point at it via `entity:` property; both should now appear as referrers with their own qnames).

---

## Plan-vs-implementation deviations

### D-1. Qname namespacing for the new kinds

`H-symbol-indexing.md:13-16` writes:

> `query` def in `schema query namespace q1` document emits `query.q1.<name>` with `kind: 'query'`.
> `role` def in `schema cnc namespace role` document emits `cnc.role.<name>` …
> `er2dbEntity` def in `schema map namespace er2db` emits `map.er2db.<name>` …

The implementation honours the **document's** schema/namespace, not the kind's logical home. Concretely:

* `samples/v1-metadata/query.ttr` opens with `schema query namespace query` → queries end up as `query.query.<name>`. Close to the plan, but the plan's "q1" was the example placeholder.
* `samples/v1-metadata/map.ttr` opens with `schema map` (no namespace) → `er2db_entity` defs end up as `map.<name>` (no `.er2db.`). **Diverges from the plan's `map.er2db.<name>`.**

If the plan stays as-is, the implementation needs a per-kind synthetic namespace (something like "when the def kind is `er2dbEntity` and the document's namespace is empty, slot it under `er2db`"). That's invasive and surprising — qnames diverging from schema-directives is unusual.

The simpler resolution is to **revise the plan**: qnames follow the document's schema directive (already true), and consumers filter/search by `SymbolEntry.kind` instead of by qname pattern. The integration test then asserts via `getSymbolDetail`'s `perKindData.kind` (which already works) rather than substring-matching qnames.

Pick one and update the plan + tests + sample namespacing accordingly. Right now the plan says one thing, the code does another, and the test is wrong against both.

### D-2. `workspaceSymbols` doesn't expose the TTR def kind

`server.ts:533-564` maps `SymbolEntry.kind` through `symbolKindOf` to an LSP `SymbolKind` enum number before returning. So `SymbolInformation.kind: SymbolKind.Class` doesn't tell the client whether the entry is `relation` vs `entity` vs `query`. The plan's "workspaceSymbols('rel') includes at least one `kind: 'relation'` row" cannot be satisfied through the LSP standard `workspaceSymbol` response — at best the test would have to peek at `getSymbolDetail` for each candidate, which is `O(N)` extra RPCs.

Options:

* Add a `modeler/listSymbols` custom request that returns `{ qname, kind: <TTR-kind> }[]` — Designer-only consumer. Cheap.
* Or change `SymbolInformation.name` to carry the kind as a suffix (`name: '<qname> [relation]'`). Ugly but no protocol churn.
* Or accept that workspace-symbols is kind-blind and write the test using `projectSymbols.all()` via a dedicated debug endpoint or per-symbol `getSymbolDetail`.

Pick whichever and update the integration test to match.

### D-3. Snake-case vs camel-case in source

Sample `map.ttr` writes `def er2db_entity X { … }`; the parser exposes this as `Definition.kind: 'er2dbEntity'` (camelCase, verified at `walker.ts:797`). That's fine — it's purely a wire-vs-AST distinction. Mentioning so reviewers don't get confused when grepping.

### D-4. H.5 not done

`docs/plan/progress-phase-02.md`'s "Deferred to later phases" line "Indexing relations/queries/roles/er2db_* …" still reads "Phase 3 if navigation needs it." Plan H.5 says to flip it to closed. Not blocking, but it's the same flag every phase: progress docs lag behind reality.

---

## Verify-by-running

```bash
pnpm --filter @modeler/semantics test            # 47/47 pass (unit tests are tautological)
pnpm --filter @modeler/lsp test                  # untouched in this diff; passes
pnpm --filter @modeler/integration-tests test    # 24 passes, 3 FAIL in symbol-indexing-extended.test.ts
```

Smoke evidence that the no-op nature of the symbol-table change is real:

```bash
# Comment out lines 108-112 of packages/semantics/src/symbol-table.ts, then:
pnpm --filter @modeler/semantics test            # still 47/47 pass
pnpm --filter @modeler/integration-tests test    # same 3 failures (unrelated)
```

---

Tasks: `tasks-review-020.md`.
