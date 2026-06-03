# 1.1.B.3 — Resolver chain + PackageGraph

**Goal:** implement the six-step resolution chain (lexical → same-package → named-import → wildcard-import → auto-import → fully-qualified-but-unique) and ship the `PackageGraph` module.

**Reads:** [contracts §4 (resolver changes)](../../design/v1-1-contracts.md#4-resolver-changes), [contracts §5 (PackageGraph)](../../design/v1-1-contracts.md#5-packagegraph-module), `packages/semantics/src/resolver.ts`, `packages/semantics/src/project-symbols.ts`.
**Blocked by:** 1.1.B.2.
**Blocks:** B4 (validator uses resolver output), C1, F, H, I.
**Estimated time:** 2–2.5 days.

## Tests-first

- [ ] `packages/semantics/src/__tests__/resolver-v1.1.test.ts` — new file. One case per resolution step:
  - **Lexical:** inside `def entity artikl { attributes: [def attribute id { ... }], nameAttribute: id }`, `nameAttribute: id` resolves to `<pkg>.er.entity.artikl.id` via `viaStep === 'lexical'`.
  - **Same-package:** file A (`package billing.invoicing`, defines `er.entity.artikl`) and file B (`package billing.invoicing`, defines `er.relation.r { from: er.entity.artikl }`) — the `from:` reference resolves via `viaStep === 'same-package'` with no `import` statement.
  - **Named import:** `import billing.products.er.entity.produkt` makes a bare `er.entity.produkt` resolve via `viaStep === 'named-import'`.
  - **Wildcard import:** `import billing.products.*` makes a bare `er.entity.produkt` resolve via `viaStep === 'wildcard-import'`.
  - **Wildcard non-recursion:** `import billing.products.*` does NOT expose defs in `billing.products.subordinates`; reference to such a def returns `resolved: false` with `tried[]` showing the wildcard step with `reason: 'wildcard-non-recursive'`.
  - **Auto-import (cnc):** bare `cnc.role.fact` resolves via `viaStep === 'auto-import'` from any file with no explicit import. (Underlying qname is `cnc.cnc.role.fact` per B2 — the resolver maps the bare lookup.)
  - **Fully-qualified-but-unique (B4 relaxation):** project has exactly one `er.entity.artikl` across all packages; from a file without the import, reference `billing.invoicing.er.entity.artikl` resolves via `viaStep === 'fully-qualified'`. The validator emits `ttr/unimported-reference` at Info severity (verified in B4).
  - **Ambiguity:** two wildcards expose conflicting bare names → `resolved: false`, `reason: 'ambiguous'`, `candidates: SymbolEntry[]` populated with both matches.
- [ ] `packages/semantics/src/__tests__/package-graph.test.ts` — new file. Cases:
  - Three-package project, A→B→C: `build()` returns 3 nodes, 2 edges; `getDependencies('A') === ['B', 'C']` (transitive); `getDependents('C') === ['A', 'B']`.
  - Cycle A→B→A: `findCycles()` returns `[['A', 'B']]`.
  - Self-import (file in package A imports package A): not a cycle; just a `ttr/duplicate-import` candidate.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "graphlib", query: "directed graph SCC detection" }
mcp__context7__query-docs         { libraryId: "<id>", query: "Tarjan strongly connected components" }
```

We don't need a graph library — Tarjan's SCC is ~30 lines of pure TS; implement it inline to avoid the dep. Cycle detection only runs on `getPackageGraph` requests and during validation, both bounded by the number of packages (small).

## Implementation tasks

- [ ] **B3.1 — Add `ResolutionStep`, `ResolutionAttempt`, updated `ResolutionResult` types.** Edit `packages/semantics/src/resolver.ts` per [contracts §4](../../design/v1-1-contracts.md#4-resolver-changes). The result's `tried[]` becomes `ResolutionAttempt[]` (not `string[]` as in v1). Any callers that read `tried[]` need to adapt — search for `\.tried` and update.
- [ ] **B3.2 — Implement step-2 (same-package).** In `Resolver.resolveReference`, after the lexical attempt, look up the reference's bare-or-short-dotted form against `projectSymbols.getByPackage(contextPackageName)`. If found, return `{ resolved: true, symbol, viaStep: 'same-package' }`.
- [ ] **B3.3 — Implement step-3 (named imports) and step-4 (wildcard imports).** Pass the current document's `imports: ImportDecl[]` into `resolveReference` via the `ResolutionContext`. Iterate named first, wildcards second. For wildcards, do NOT recurse into sub-packages (assert in test).
- [ ] **B3.4 — Implement step-5 (auto-import cnc).** The `cnc` package is always in scope; lookup any bare `cnc.<role>.<name>` reference against the symbol table. Record `viaStep: 'auto-import'`.
- [ ] **B3.5 — Implement step-6 (fully-qualified-but-unique relaxation).** When the reference's path is a fully-qualified qname (matches `projectSymbols.get(qname)` directly), return resolved. Additionally, when a bare-or-short reference is unique across the whole project per `getBySuffix`, accept it too — and **flag for the validator** (via a separate return path) so the validator can emit `ttr/unimported-reference` at Info severity.
- [ ] **B3.6 — Create `packages/semantics/src/package-graph.ts`.** Implement `PackageNode`, `PackageEdge`, `PackageGraph` types and `PackageGraphBuilder` class per [contracts §5](../../design/v1-1-contracts.md#5-packagegraph-module). `findCycles` uses Tarjan's SCC. Export the module from `packages/semantics/src/index.ts`.
- [ ] **B3.7 — Wire `PackageGraphBuilder` to project-load.** Build the package graph once after every full project load; cache it. Invalidate on document change. The LSP's `modeler/getPackageGraph` method (added in C2) reads from this cache.
- [ ] **B3.8 — Update existing resolver tests.** Tests that asserted `tried: string[]` need to update to the new `ResolutionAttempt[]` shape. Update them; do not regress to the v1 shape.

## Verify by running

```bash
pnpm --filter @modeler/semantics test
pnpm --filter @modeler/semantics typecheck
```

Both new test files pass; existing semantics tests pass (with the `tried[]` shape update).

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] `Resolver.resolveReference` implements all six steps in order.
- [ ] `package-graph.ts` exists, `findCycles` works on a known-cyclic fixture.
- [ ] Every resolution success carries the `viaStep` field; every failure carries `tried: ResolutionAttempt[]` with a `reason` per attempt.
- [ ] No validator changes yet — that's B4. The new diagnostic codes don't fire yet because nothing calls the resolver in "lint" mode. The resolver just produces the data; B4 emits the diagnostics.
