# Review 028 — Section B3 (Resolver chain + PackageGraph)

**Branch:** `v1-1` (HEAD = `dd73de6 V1-1 section B done`, working tree contains B2 + B3 + this review's predecessor docs).
**Scope reviewed:** the resolver six-step chain and the `PackageGraph` module described in [`docs/v1-1/plan/tasks/B3-resolver.md`](../plan/tasks/B3-resolver.md) and [`docs/v1-1/design/v1-1-contracts.md §4–§5`](../design/v1-1-contracts.md#4-resolver-changes).

**Files in scope:**

- `packages/semantics/src/resolver.ts` (rewritten — `ResolutionStep`, `ResolutionAttempt`, six-step chain in `resolveReference`)
- `packages/semantics/src/package-graph.ts` (new — `PackageGraphBuilder`, Tarjan SCC)
- `packages/semantics/src/project-symbols.ts` (extended — `upsertDocument` gains `packageName`; `getByPackage`/`getBySuffix`/`listPackages` added [B2 carry-over])
- `packages/semantics/src/reference-index.ts` (signature extended — `upsertDocument` gains `packageName?`)
- `packages/semantics/src/references.ts` (`pushIdValueAsReference` now recurses into list/object values)
- `packages/semantics/src/validator.ts` (one `tried[]` formatting fix for the new shape)
- `packages/semantics/src/index.ts` (exports `PackageGraphBuilder`, new types)
- `packages/lsp/src/server.ts` (passes `packageName` through to `upsertDocument` and `resolveReference`)
- `packages/semantics/src/__tests__/resolver-v1.1.test.ts` (new, 10 cases)
- `packages/semantics/src/__tests__/resolver.test.ts` (updated — un-skipped the previously-skipped stock-cnc case, updated `tried` field access)
- `tests/integration/src/integration.test.ts` (carry-over B2 fix)

**Verification runs:**

| Command                                           | Result               | Notes |
| ------------------------------------------------- | -------------------- | ----- |
| `pnpm --filter @modeler/semantics test`           | ✅ 71/71             | Unit-level coverage of the new code is green. |
| `pnpm --filter @modeler/parser test`              | ✅ 57/57             | |
| `pnpm --filter @modeler/integration-tests test`   | ❌ **2 failing**     | Same two as B2 review carried forward; B3 was supposed to fix them. Root cause is one missed call site. |
| `pnpm -r typecheck`                               | ✅                   | |

---

## Verdict

**B3 is not ready.** The developer's "complete" claim is over-eager; `STATUS.md` still shows `[ ] B3 resover`, which is honest. The resolver itself is broadly in good shape — the six steps run in the right order, the new `ResolutionAttempt` shape matches contracts §4, and ten new unit tests cover most paths. But two task-list deliverables and two real bugs are still open, and the integration tests that B3 was explicitly chartered to fix remain red.

Specifically:

1. **(missing artifact)** `package-graph.test.ts` was required by B3's Tests-first section with three explicit cases (A→B→C, cycle, self-import). It does not exist. B3.6 has a green typecheck but zero behavior coverage.
2. **(real bug)** `PackageGraphBuilder.getDependents` and `getDependencies` are identical — both walk outbound edges. `getDependents` should walk inbound edges (`edge.to === current`). Will silently return wrong answers the moment a caller relies on it.
3. **(real bug, the B3-vs-integration-tests root cause)** `reference-index.ts:12`'s `enclosingQnameOf` still constructs qnames the v1 way (`[schemaCode, namespace, def.name].filter(s => s !== '').join('.')`). For files without a `namespace` clause (`schema map` + `def er2db_entity X`), this produces `map.X` while the symbol-table now stores `map.er2dbEntity.X` (B2's kind-fallback). The reference-index ends up pointing to qnames that aren't in the symbol-table, which is exactly why the two integration tests still fail.
4. **(test coverage gap)** The "step 6: fully-qualified-but-unique" case in `resolver-v1.1.test.ts:186–205` is mis-labelled — it actually exercises step 2 and asserts `viaStep === 'same-package'`. Step 6 is implemented but not tested.
5. **(spec deviation)** Step 6 implementation has a `ref.parts.length > 1` guard that excludes bare references. Contracts §4.1 explicitly says "when a **bare reference is unique** across the whole project … the resolver allows resolution via step 6." Either fix or amend.
6. **(missing wiring)** B3.7 says "Wire `PackageGraphBuilder` to project-load. Build the package graph once after every full project load; cache it. Invalidate on document change." Nothing in the LSP server constructs a `PackageGraphBuilder`. C2's `modeler/getPackageGraph` would have nothing to read.

The rest is in good shape, including the contract-doc note added to `B4-diagnostics.md` for the §1.4 grammar inconsistency we discussed last round. Nice follow-through there.

---

## Findings

### 1. (Missing artifact) `package-graph.test.ts` was not created

B3 Tests-first, second bullet, requires:

> `packages/semantics/src/__tests__/package-graph.test.ts` — new file. Cases:
> - Three-package project, A→B→C: `build()` returns 3 nodes, 2 edges; `getDependencies('A') === ['B', 'C']` (transitive); `getDependents('C') === ['A', 'B']`.
> - Cycle A→B→A: `findCycles()` returns `[['A', 'B']]`.
> - Self-import (file in package A imports package A): not a cycle; just a `ttr/duplicate-import` candidate.

The file does not exist (`ls packages/semantics/src/__tests__/` shows only `qname`, `resolver`, `resolver-v1.1`, `readme-example`, `semantics`, `symbol-table-extended-kinds`, `symbol-table`, `symbol-table-v1.1`, `validator`). Done-when criterion "package-graph.ts exists, findCycles works on a known-cyclic fixture" cannot be claimed without it.

Without this test file, **finding 2** below would have been caught by the developer rather than the reviewer — the test for `getDependents('C') === ['A', 'B']` fails immediately under the current implementation.

### 2. (Real bug) `getDependents` walks outbound edges instead of inbound

`packages/semantics/src/package-graph.ts:145–159` versus `packages/semantics/src/package-graph.ts:161–175`:

```ts
getDependents(pkg: string): string[] {
  const graph = this.build();
  const dependentSet = new Set<string>();
  const queue: string[] = [pkg];
  while (queue.length > 0) {
    const current = queue.shift()!;
    for (const edge of graph.edges) {
      if (edge.from === current && !dependentSet.has(edge.to)) {   // ← outbound
        dependentSet.add(edge.to);
        queue.push(edge.to);
      }
    }
  }
  return Array.from(dependentSet);
}

getDependencies(pkg: string): string[] {
  // ...identical body, also `edge.from === current` ...
}
```

The two methods are byte-for-byte identical (modulo the variable name `dependentSet` vs `dependencySet`). Per contracts §5:

```ts
/** Returns packages that transitively depend on `pkg`. */
getDependents(pkg: string): string[];

/** Returns packages `pkg` transitively depends on. */
getDependencies(pkg: string): string[];
```

These are opposites. `getDependents(pkg)` must walk *inbound* edges (`edge.to === current`, queue `edge.from`); `getDependencies(pkg)` walks outbound. The current code makes them aliases. Anyone calling `getDependents('cnc')` to ask "which packages import cnc?" gets back "what cnc imports" — wrong.

**Fix.** Change `getDependents`'s inner loop to:

```ts
if (edge.to === current && !dependentSet.has(edge.from)) {
  dependentSet.add(edge.from);
  queue.push(edge.from);
}
```

This is exactly the kind of mistake the missing `package-graph.test.ts` would have caught.

### 3. (Real bug — the integration-test root cause) `enclosingQnameOf` still uses v1 qname shape

`packages/semantics/src/reference-index.ts:5–13`:

```ts
function enclosingQnameOf(def: Definition, schemaCode: string, namespace: string): string | undefined {
  if (
    def.kind === 'entity' || def.kind === 'table' || def.kind === 'view' ||
    def.kind === 'procedure' || def.kind === 'er2dbEntity' || def.kind === 'er2dbRelation' ||
    def.kind === 'relation' || def.kind === 'role' || def.kind === 'er2cncRole'
  ) {
    return [schemaCode, namespace, def.name].filter((s) => s !== '').join('.');
  }
  // ...
}
```

For a file with `schema map` (no namespace) and `def er2db_entity artikl`, this returns `map.artikl`. But the v1.1 symbol-table stores it as `map.er2dbEntity.artikl` (kind-fallback per contracts §3.1). The reference-index then writes "X is referenced by `map.artikl`", but `map.artikl` is not in the symbol-table — so `getSymbolDetail({ qname: 'map.artikl' })` returns null.

That null is exactly what surfaces in the failing integration test:

```
referencedBy kinds: relation, relation, …, relation, unknown, unknown:
  expected [ 'relation', 'relation', …(14) ] to include 'er2dbEntity'
```

The two `unknown`s are the `er2db_entity` referrers that the reference-index recorded under the wrong qname; when the test asks for their kind, they don't resolve.

The `lsp/src/server.ts` diff confirms the developer was aware they had to pipe `packageName` through (line 228 adds it to `projectSymbols.upsertDocument` and `refIndex.upsertDocument` calls). But the fix stops at the call site. `enclosingQnameOf` itself was not updated, and `packageName` isn't threaded into it.

**Fix.** Replace the body of `enclosingQnameOf` with v1.1 qname construction:

```ts
function enclosingQnameOf(
  def: Definition,
  schemaCode: string,
  namespace: string,
  packageName?: string,
): string | undefined {
  if (! /* same kind check as today */) return undefined;
  const nsOrKind = namespace || def.kind;     // matches DocumentSymbolTable.addEntry
  const segments: string[] = [];
  if (packageName) segments.push(packageName);
  segments.push(schemaCode);
  if (nsOrKind) segments.push(nsOrKind);
  segments.push(def.name);
  return segments.join('.');
}
```

And update its single call site at `reference-index.ts:45` to pass `packageName` (already in scope from the new signature).

A second occurrence in `packages/lsp/src/server.ts:419-ish` (`enclosingQnameOf(found.from, ast)`) likely needs the same treatment — verify by grep. Stock-cnc URIs need the same doubled-cnc handling the symbol-table already applies; the cleanest path is to make qname construction live in *one* place (e.g. a small exported helper from `symbol-table.ts`) instead of three.

After this fix the two failing integration tests should turn green. Re-run `pnpm --filter @modeler/integration-tests test` and verify 29/29.

### 4. (Coverage gap) The "step 6" test exercises step 2, not step 6

`packages/semantics/src/__tests__/resolver-v1.1.test.ts:186–205`:

```ts
describe('step 6: fully-qualified-but-unique', () => {
  it('resolves unique bare ref via step 6 when not imported', () => {
    // … project has one billing.app.er.entity.artikl …
    const res = resolver.resolveReference(
      { path: 'artikl', parts: ['artikl'] },
      { schemaCode: 'er', namespace: 'entity', packageName: 'billing.app' }
    );
    expect(res.resolved).toBe(true);
    if (res.resolved) expect(res.viaStep).toBe('same-package');   // ← step 2
  });
});
```

The reference site is in the same package as the target, so step 2 short-circuits. Step 6 never runs. The assertion `viaStep === 'same-package'` is what the test actually verifies — making the `describe` block name misleading and step 6 itself effectively untested.

The B3 spec is explicit about what step 6 needs to look like:

> **Fully-qualified-but-unique (B4 relaxation):** project has exactly one `er.entity.artikl` across all packages; from a file without the import, reference `billing.invoicing.er.entity.artikl` resolves via `viaStep === 'fully-qualified'`.

**Fix.** Rewrite the test fixture so the resolver site is in a *different* package from the target, no `imports`, and the reference is the fully-qualified qname (multi-part):

```ts
table.upsertDocument('billing/invoicing/a.ttr', astTarget, 'er', 'entity', 'billing.invoicing');
// reference site is in package 'app', no import of billing.invoicing
const res = resolver.resolveReference(
  {
    path: 'billing.invoicing.er.entity.artikl',
    parts: ['billing', 'invoicing', 'er', 'entity', 'artikl'],
  },
  { schemaCode: 'er', namespace: 'entity', packageName: 'app' }
);
expect(res.resolved).toBe(true);
if (res.resolved) expect(res.viaStep).toBe('fully-qualified');
```

Add a second case for the bare-but-unique path once finding 5 is decided.

### 5. (Spec deviation) Step 6 implementation excludes bare references

`packages/semantics/src/resolver.ts:146–154`:

```ts
if (ref.parts.length > 1) {
  const uniqueMatches = this.symbols.getBySuffix(ref.path).filter(/* … */);
  if (uniqueMatches.length === 1) {
    tried.push(attempt('fully-qualified', uniqueMatches[0].qname));
    return { resolved: true, symbol: uniqueMatches[0], viaStep: 'fully-qualified' };
  }
}
```

The `ref.parts.length > 1` guard means bare-but-globally-unique references never get to step 6. The contract (§4.1):

> When a bare reference is unique across the whole project (no ambiguity), the resolver allows resolution via step 6 even if the symbol's package is not imported. Practical effect: in small projects the user often won't have to write `import`s at all. The validator still emits `ttr/unimported-reference` at **Info** severity.

You confirmed this guard in an earlier exchange — "to ensure multi-part references (like `cnc.cnc.role.fact`) are required for step 6." But that confuses step 5 (auto-import for cnc) with step 6 (FQN-unique relaxation). Step 5 handles the stock-cnc case; step 6 is *also* supposed to handle bare-unique. They're not the same.

**Two ways to resolve:**

- **(recommended) Drop the guard.** Let `getBySuffix(ref.path)` run for bare refs too. The `uniqueMatches.length === 1` check then determines whether step 6 fires. Add a test for bare unique resolution (a single `er.entity.artikl` in `billing.invoicing`, referenced from `app` without import, resolves via fully-qualified).
- **Amend the contract.** If the team prefers the stricter "step 6 only handles FQN refs", open a contracts §4.1 amendment that says so explicitly, drop the "bare reference is unique" sentence, and add a changelog entry. Don't leave the doc and code disagreeing.

My read: the contract intent is generous (small-project ergonomics), so dropping the guard is more in spirit. But it's the team's call.

### 6. (Missing wiring) B3.7 — `PackageGraphBuilder` is not wired to project-load

B3.7:

> Build the package graph once after every full project load; cache it. Invalidate on document change. The LSP's `modeler/getPackageGraph` method (added in C2) reads from this cache.

`grep -n "PackageGraph" packages/lsp/src/server.ts` returns nothing. There is no construction, no cache, no invalidation. When C2 needs the cache, it'll be missing. The Done-when criterion ("`package-graph.ts` exists, `findCycles` works on a known-cyclic fixture") is met for the unit; the integration is not.

**Fix.** In `packages/lsp/src/server.ts`, add a module-scoped `packageGraph: PackageGraph | null = null` (or `PackageGraphBuilder` instance), rebuild it inside `rebuildValidator()` (which already runs on each `upsertDocument`), and invalidate on `removeDocument`. Don't add the `modeler/getPackageGraph` LSP method here — that's C2. Just make sure the cache is populated.

If a cache is over-engineering at this stage, the absolute minimum is constructing a `new PackageGraphBuilder(projectSymbols, documentsByUri)` lazily on demand. Either way, *some* code path inside `packages/lsp` has to mention `PackageGraphBuilder`.

### 7. (Minor) Dead `legacyCncQname` branch in resolver

`packages/semantics/src/resolver.ts:139–144`:

```ts
const legacyCncQname = `cnc.role.${ref.path}`;
if (legacyCncQname !== cncQname && legacyCncQname !== fullQname && legacyCncQname !== context.enclosingQname) {
  tried.push(attempt('auto-import', legacyCncQname));
  const legacyCncSymbol = this.symbols.get(legacyCncQname);
  if (legacyCncSymbol) return { resolved: true, symbol: legacyCncSymbol, viaStep: 'auto-import' };
}
```

Same pattern at lines 186–191 in `resolveBareId`. B2's URI-based stock-cnc detection (`isStockCnc` in `DocumentSymbolTable`) only produces the doubled `cnc.cnc.role.*` form for files under `stock://`. Non-stock files writing `schema cnc namespace role` would *not* get the doubled form, but the kind-fallback would still produce `cnc.role.X` (because namespace `role` is explicit). So in theory the legacy branch could hit user files. In practice, no test exercises this path and `resolver.test.ts` was explicitly updated to expect the doubled form.

**Recommendation.** Either:

- Add a test asserting the legacy `cnc.role.X` shape resolves (i.e., document the intent), or
- Delete the legacy branches and add a comment in the resolver pointing at `isStockCnc` as the canonical source of truth.

Don't leave dead code that "might" be hit by future fixtures — that's how the cnc handling drifts back out of sync next time.

### 8. (Informational, watch for it) `references.ts` reference traversal got significantly richer

`pushIdValueAsReference` was previously a single-case function (`kind === 'id'`). It now recurses into `list` and `object` values:

```ts
} else if (value.kind === 'list') {
  for (const item of value.items) pushIdValueAsReference(item, out);
} else if (value.kind === 'object') {
  for (const entry of value.entries) pushIdValueAsReference(entry.value, out);
}
```

This is *good* — `def relation r { join: [{ from: …, to: … }] }` couldn't previously have its inner ids tracked as references. But it's an unrelated semantic change rolled into B3. Two consequences worth confirming:

1. All v1 tests still pass at `pnpm --filter @modeler/semantics test`, which is reassuring.
2. Reference counts may now be higher than v1 — the workspace/symbol query="rel" test's "100 symbols" cap might now be a hard limit you're bumping against. Worth checking once finding 3 is fixed whether that test still fails for a *different* reason (volume) rather than the qname mismatch.

This change is positive; just call it out in the commit message so future history-walkers know B3's commit changed reference collection scope.

### 9. (Hygiene) `STATUS.md` not flipped

`STATUS.md` still shows `[ ] B3 resover`. Consistent with the actual state — B3 is not yet done. Once findings 1–6 land, flip to `[x] B3 resolver` (and fix the typo `resover` → `resolver` while you're there).

### 10. (Informational, non-deviation) The new `ResolutionAttempt` types match contracts §4

For the record:

| Type / field                         | Contract §4 location | `resolver.ts` location | Match  |
| ------------------------------------ | -------------------- | ---------------------- | ------ |
| `ResolutionStep` union               | lines 241–247        | lines 5–11             | ✅      |
| `ResolutionAttempt` interface        | lines 249–260        | lines 13–23            | ✅ (+ adds `'ambiguous'` reason, which is fine — used in the wildcard step) |
| `ResolutionResult` discriminated union | lines 262–264        | lines 25–27            | ✅      |

The validator's formatting fix at `validator.ts:155` (`res.tried.map((a) => a.candidate).join(', ')`) correctly adapts to the new shape. Good.

---

## What "done" looks like after the follow-ups

B3 closes when:

1. `package-graph.test.ts` exists and covers the three cases from B3 Tests-first (finding 1).
2. `getDependents` walks inbound edges (finding 2).
3. `enclosingQnameOf` is v1.1-aware and the two failing integration tests turn green (finding 3). Run `pnpm --filter @modeler/integration-tests test` and verify 29/29.
4. The step 6 test actually exercises step 6 (finding 4).
5. Step 6 accepts bare unique references — or contracts §4.1 is amended to say it doesn't (finding 5).
6. The LSP server constructs and caches a `PackageGraphBuilder` instance (finding 6).
7. Either the legacy cnc branches are tested or removed (finding 7).
8. `STATUS.md` flipped to `[x] B3 resolver` (typo fix optional but appreciated).

After that, B4 unblocks — and B4 will need the resolved-via-step-6 info to emit `ttr/unimported-reference` at Info severity, which means the resolver's `ResolutionResult` may need one more piece of data (was it resolved via "relaxation"?) before B4 can be wired. That's B4 scope, not B3, but flag it as you wrap.
