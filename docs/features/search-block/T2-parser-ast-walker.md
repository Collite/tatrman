# T2 — Parser AST + walker (+ parser tests)

Depends on T1 (regenerated parser contexts must exist). TDD: write the parser
tests first, watch them fail, then implement.

Files:
- `packages/parser/src/ast.ts`
- `packages/parser/src/walker.ts`
- new test: `packages/parser/src/__tests__/search-block.test.ts`

## Tasks

- [ ] **Write the failing parser tests first.** Create
  `packages/parser/src/__tests__/search-block.test.ts`. Use `parseString` from
  the package entry (`import { parseString } from '../index.js'`; match the
  import style already used in `parser.test.ts`). Cover:
  1. A `search { searchable: true, fuzzy: true }` block parses with **0 errors**
     on each newly-allowed kind: `table`, `column` (inline in a table),
     `view`, `relation`. Assert the resulting def's `search.searchable === true`
     and `search.fuzzy === true`.
  2. The same on the already-allowed kinds (`entity`, `attribute`, `query`,
     `role`) still works.
  3. **Negative:** top-level `def column c { type: varchar, searchable: true }`
     now produces a parse error (`result.errors.length > 0`). Same for
     `def attribute`.
  4. **Duplicate bookkeeping:** `search { keywords {...}, keywords {...} }`
     yields `search.duplicateProperties` containing `"keywords"`; a clean block
     yields `duplicateProperties` empty/undefined.
  5. A block mixing the localized + boolean + list sub-properties parses and maps
     each field correctly (`keywords`, `patterns`, `searchable`, `fuzzy`).

  Run `pnpm --filter @modeler/parser test -- search-block` and confirm they fail.

- [ ] **Extend the `SearchBlock` AST type** (`ast.ts`, ~lines 137–145):
  ```ts
  export interface SearchBlock {
    kind: 'searchBlock';
    keywords?: LocalizedStringList;
    patterns?: string[];
    descriptions?: LocalizedStringList;
    examples?: string[];
    aliases?: string[];
    searchable?: boolean;          // moved in from ColumnDef/AttributeDef
    fuzzy?: boolean;               // new
    duplicateProperties?: string[]; // sub-property names seen more than once
    source: SourceLocation;
  }
  ```

- [ ] **Remove the leaf `searchable` field and add `search`.** In `ast.ts`:
  - `ColumnDef` (~lines 206–217): delete `searchable?: boolean;`, add
    `search?: SearchBlock;`.
  - `AttributeDef` (~lines 273–286): delete `searchable?: boolean;`
    (it already has `search?: SearchBlock;`).
  - `TableDef` (~lines 184–194): add `search?: SearchBlock;`.
  - `ViewDef` (~lines 196–204): add `search?: SearchBlock;`.
  - `RelationDef` (~lines 288–298): add `search?: SearchBlock;`.

- [ ] **Update `walkSearchBlock`** (`walker.ts`, ~lines 1421–1447) to read the
  two new booleans and record duplicates:
  ```ts
  function walkSearchBlock(ctx: SearchBlockContext, file: string): SearchBlock {
    let keywords, patterns, descriptions, examples, aliases;
    let searchable: boolean | undefined;
    let fuzzy: boolean | undefined;
    const seen = new Map<string, number>();
    const bump = (k: string) => seen.set(k, (seen.get(k) ?? 0) + 1);

    for (const p of ctx.searchSubProperty()) {
      if (p.keywordsProperty())     { bump('keywords');     keywords = walkLocalizedStringList(p.keywordsProperty()!.localizedStringList()!, file); }
      if (p.patternsProperty())     { bump('patterns');     patterns = walkListOfStrings(p.patternsProperty()!.listOfStrings()!, file); }
      if (p.descriptionsProperty()) { bump('descriptions'); descriptions = walkLocalizedStringList(p.descriptionsProperty()!.localizedStringList()!, file); }
      if (p.examplesProperty())     { bump('examples');     examples = walkListOfStrings(p.examplesProperty()!.listOfStrings()!, file); }
      if (p.aliasesProperty())      { bump('aliases');      aliases = walkListOfStrings(p.aliasesProperty()!.listOfStrings()!, file); }
      if (p.searchableProperty())   { bump('searchable');   searchable = p.searchableProperty()!.BOOLEAN_LITERAL()!.getText() === 'true'; }
      if (p.fuzzyProperty())        { bump('fuzzy');        fuzzy = p.fuzzyProperty()!.BOOLEAN_LITERAL()!.getText() === 'true'; }
    }

    const duplicateProperties = [...seen.entries()].filter(([, n]) => n > 1).map(([k]) => k);
    return { kind: 'searchBlock', keywords, patterns, descriptions, examples, aliases, searchable, fuzzy, duplicateProperties, source: makeSourceLocation(ctx, file) };
  }
  ```
  (Exact accessor names — `fuzzyProperty()`, `searchableProperty()` — come from
  the regenerated `SearchSubPropertyContext`; adjust if antlr-ng pluralizes
  differently.)

- [ ] **Drop the leaf `searchable` reads and wire `search` into the new
  walkers:**
  - Column walkers: the block-form builder returning `kind: 'column'` and the
    inline-list builder (`walkColumnDefList`) both currently read
    `p.searchableProperty()` and pass `searchable`. Remove those, and add
    `if (p.searchBlockProperty()) { search = walkSearchBlock(p.searchBlockProperty()!.searchBlock()!, file); }`, then include `search` in the returned object.
  - Attribute walkers (block + inline): remove the `p.searchableProperty()`
    reads and drop `searchable` from the returned objects (the `search` wiring is
    already present).
  - Table walker (returns at ~walker.ts:625): add a `search` local, read
    `p.searchBlockProperty()`, include `search` in the return.
  - View walker (returns at ~:654): same.
  - Relation walker (returns at ~:957): same.

- [ ] **Run the parser tests to green.**
  ```bash
  pnpm --filter @modeler/parser test
  pnpm --filter @modeler/parser typecheck
  ```

## Verification

- [ ] New `search-block.test.ts` passes; pre-existing parser tests still pass.
- [ ] `pnpm --filter @modeler/parser typecheck` is clean — the removed
  `searchable` field will surface any stray references at compile time (fix
  them; expect hits in `@modeler/lsp` graph mapping or `@modeler/semantics` if
  anything read `column.searchable`/`attribute.searchable`).
- [ ] `grep -rn "\.searchable" packages/*/src --include=*.ts` returns only the
  intended `search.searchable` accesses (no leftover `column.searchable` /
  `attribute.searchable`).
