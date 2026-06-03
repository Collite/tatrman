# T3 — Semantics validation (+ validator tests)

Depends on T2 (the `SearchBlock` AST must carry `searchable`, `fuzzy`,
`duplicateProperties`). Two new diagnostics:

- **warning** — `fuzzy: true` but `searchable` is not `true` (false or omitted).
- **error** — a sub-property appears more than once inside one `search` block.

TDD: write the validator tests first.

Files:
- `DiagnosticCode` enum in `@modeler/parser` (exported from the package;
  `validator.test.ts` imports it as `import { DiagnosticCode } from '@modeler/parser'`).
  Locate it: `grep -rn "enum DiagnosticCode" packages/parser/src`.
- `packages/semantics/src/validator.ts`
- test: extend `packages/semantics/src/__tests__/validator.test.ts` (or add
  `search-validation.test.ts` alongside it).

## Tasks

- [ ] **Write the failing validator tests first.** Mirror the existing pattern in
  `validator.test.ts` (`parseString` → build the validator → `validateProject()`
  → assert on `diags.find(d => d.code === …)`). Cases:
  1. `attribute` with `search { fuzzy: true }` (no `searchable`) ⇒ a diagnostic
     with the new `FuzzyWithoutSearchable` code, severity `warning`.
  2. `search { searchable: true, fuzzy: true }` ⇒ **no** `FuzzyWithoutSearchable`
     diagnostic.
  3. `search { searchable: false, fuzzy: true }` ⇒ warning present.
  4. `search { keywords {...}, keywords {...} }` ⇒ a `DuplicateSearchProperty`
     error.
  5. A clean `search` block on a `table`/`column` ⇒ neither new diagnostic.

  Run `pnpm --filter @modeler/semantics test` and confirm failures (codes don't
  exist yet).

- [ ] **Add the two diagnostic codes** to the `DiagnosticCode` enum in
  `@modeler/parser`:
  ```ts
  FuzzyWithoutSearchable = 'fuzzy-without-searchable',
  DuplicateSearchProperty = 'duplicate-search-property',
  ```
  (Match the existing value style — kebab-case string values like
  `'duplicate-import'`.) Rebuild parser so semantics sees the new members:
  `pnpm --filter @modeler/parser build`.

- [ ] **Add a search-block validation pass** in `validator.ts`. Inside
  `validateProject()` add a loop over `ast.definitions` (or extend the existing
  `for (const def of ast.definitions)` at ~line 29). For every def, collect its
  own `search` block **and** the `search` blocks of nested members
  (`table.columns[]`, `view.columns[]`, `entity.attributes[]`). A small helper:
  ```ts
  function* searchBlocksOf(def: Definition): Iterable<SearchBlock> {
    if ('search' in def && def.search) yield def.search;
    const nested =
      def.kind === 'table' ? def.columns :
      def.kind === 'view'  ? def.columns :
      def.kind === 'entity'? def.attributes : undefined;
    for (const m of nested ?? []) if (m.search) yield m.search;
  }
  ```
  Then, per block:
  ```ts
  for (const sb of searchBlocksOf(def)) {
    if (sb.fuzzy === true && sb.searchable !== true) {
      diagnostics.push({
        code: DiagnosticCode.FuzzyWithoutSearchable,
        severity: 'warning',
        message: 'fuzzy search is enabled but the element is not marked searchable; set searchable: true',
        source: sb.source,
      });
    }
    for (const dup of sb.duplicateProperties ?? []) {
      diagnostics.push({
        code: DiagnosticCode.DuplicateSearchProperty,
        severity: 'error',
        message: `Duplicate '${dup}' in search block`,
        source: sb.source,
      });
    }
  }
  ```
  Use the `ValidationDiagnostic` shape already defined at the top of the file
  (~line 12). `sb.source` is the block's own `SourceLocation` (good enough for
  v1; refine to the sub-property span later if desired).

- [ ] **Confirm the nested-member kinds are right.** Only `table`/`view`
  (columns) and `entity` (attributes) hold members that can carry their own
  `search` block. `relation`, `query`, `role` carry only a top-level `search`.
  Inline column/attribute defs reuse the same `ColumnDef`/`AttributeDef` shape,
  so the helper covers them.

- [ ] **Run validator tests to green and full semantics suite.**
  ```bash
  pnpm --filter @modeler/semantics test
  pnpm --filter @modeler/semantics typecheck
  ```

## Verification

- [ ] New tests pass; existing semantics tests unaffected.
- [ ] `pnpm -r build && pnpm -r typecheck && pnpm -r test` all green.
- [ ] (Optional but recommended, per the repo's integration-test convention)
  add one scenario to `tests/integration/` that opens a `.ttr` with a bad
  `search` block and asserts the diagnostics surface through the LSP
  `textDocument/publishDiagnostics` path — use the existing PassThrough-paired
  harness as the template.
