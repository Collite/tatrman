# 1.1.B.2 — Package-aware symbol table

**Goal:** every `SymbolEntry` carries `packageName` and `schemaCode`; every qname in the `ProjectSymbolTable` is package-prefixed (`<package>.<schema>.<ns-or-kind>.<defName>`). Add `getByPackage`, `getBySuffix`, `listPackages` accessors.

**Reads:** [contracts §3 (symbol-table changes)](../../design/v1-1-contracts.md#3-symbol-table-changes), `packages/semantics/src/symbol-table.ts`, `packages/semantics/src/project-symbols.ts`.
**Blocked by:** 1.1.B.1.
**Blocks:** B3, B4, C1, F, H, I.
**Estimated time:** 1.5–2 days.

## Tests-first

- [ ] `packages/semantics/src/__tests__/symbol-table-v1.1.test.ts` — new file. Cases:
  - Document with `package billing.invoicing` and `def entity artikl` produces a single `SymbolEntry` with `qname === 'billing.invoicing.er.entity.artikl'`, `packageName === 'billing.invoicing'`, `schemaCode === 'er'`, `name === 'artikl'`.
  - Document with no `package` produces `SymbolEntry.qname === 'er.entity.artikl'` (v1 shape) and `packageName === ''`.
  - Two documents both in `billing.invoicing` and one in `accounting`: `ProjectSymbolTable.getByPackage('billing.invoicing')` returns symbols from both billing files; `listPackages()` returns `['', 'accounting', 'billing.invoicing']` (sorted, empty string sentinel for default package).
  - `getBySuffix('er.entity.artikl')` returns the matching symbol from any package; if two packages both contain an `er.entity.artikl`, returns both.
  - Stock cnc loader: per [contracts §3.1](../../design/v1-1-contracts.md#31-qname-construction-rule), the cnc-stock-roles file produces `cnc.cnc.role.fact` (doubled cnc form, per open-question #10 resolution). Test asserts this.

## Library reference

No external libraries. Existing `DocumentSymbolTable` / `ProjectSymbolTable` classes are the template.

## Implementation tasks

- [ ] **B2.1 — Extend `SymbolEntry`.** Add `packageName: string` and `schemaCode: string` per [contracts §3](../../design/v1-1-contracts.md#3-symbol-table-changes). Both required (default `''` for default-package files / no-namespace files).
- [ ] **B2.2 — Update `DocumentSymbolTable` to take packageName at construction.** The symbol-table builder reads `ast.packageDecl?.name ?? ''` and passes it into the constructor. Every `SymbolEntry` it produces carries that `packageName`.
- [ ] **B2.3 — Update qname construction.** In the symbol-table builder, when assembling the qname for a definition, prepend `${packageName}.` (with the trailing dot) when `packageName !== ''`. Per [contracts §3.1](../../design/v1-1-contracts.md#31-qname-construction-rule). The stock cnc file's qnames get the doubled `cnc.cnc.role.*` form for v1.1 — accept it.
- [ ] **B2.4 — Add `ProjectSymbolTable.getByPackage(packageName)`.** Iterate the underlying map; return matching entries. O(n) is fine for v1.1; optimise only if profiling shows a problem.
- [ ] **B2.5 — Add `ProjectSymbolTable.getBySuffix(suffix)`.** Returns `SymbolEntry[]` whose `qname` ends with `.${suffix}` or `=== suffix`. Used by the resolver's step-6 "fully-qualified-but-unique" relaxation (per [contracts §4.1](../../design/v1-1-contracts.md#41-fully-qualified-but-unique-relaxation-decision-from-open-question-4)).
- [ ] **B2.6 — Add `ProjectSymbolTable.listPackages()`.** Returns the sorted distinct package names. Used by `modeler/getProjectInfo`'s `packages` field.
- [ ] **B2.7 — Update existing semantics tests.** Tests that asserted `qname === 'er.entity.artikl'` for files that will get a package now need to either (a) declare a package in the fixture, or (b) accept the empty-package qname. Migrate test fixtures to use the v1.1 shape where the test is exercising v1.1 behaviour; leave v1-shape fixtures alone (they continue to test the default-package path).

## Verify by running

```bash
pnpm --filter @modeler/semantics test
pnpm --filter @modeler/semantics typecheck
```

The new `symbol-table-v1.1.test.ts` cases pass; existing semantics tests pass.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Every `SymbolEntry` produced by the symbol-table builder carries `packageName` and `schemaCode`.
- [ ] `ProjectSymbolTable` exposes the three new accessor methods.
- [ ] Stock cnc qnames are `cnc.cnc.role.*` (doubled) — this is the v1.1 transitional shape per open-question #10; a TODO comment in `symbol-table.ts` flags it for revisit when the conceptual model lands.
- [ ] No resolver changes yet — that's B3. The resolver still uses the v1 two-step chain; many tests will still pass because the project-symbol-table lookup at step 2 just sees the new qname shape. Some will fail; that's the point — B3 fixes them.
