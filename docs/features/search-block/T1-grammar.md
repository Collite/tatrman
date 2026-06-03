# T1 — Grammar + regeneration

Edit the canonical grammar, then regenerate the antlr parser and the TextMate
grammar. All line numbers are as of the planning snapshot — confirm by reading
the surrounding rule before editing.

File: `packages/grammar/src/TTR.g4`

## Tasks

- [ ] **Move `searchable` off the leaf properties.** In the per-kind property
  rules, remove `searchableProperty` from `columnProperty` (~line 112) and
  `attributeProperty` (~line 124). Leave the `searchableProperty` *production*
  (~line 158) untouched — it is reused inside the search block now.

  - `columnProperty` becomes:
    ```antlr
    columnProperty : descriptionProperty | tagsProperty | typeProperty | optionalProperty | isKeyProperty | indexedProperty | searchBlockProperty ;
    ```
  - `attributeProperty` becomes (drop `searchableProperty`, keep `searchBlockProperty`):
    ```antlr
    attributeProperty : descriptionProperty | tagsProperty | typeProperty | isKeyProperty | optionalProperty | valueLabelsProperty | displayLabelProperty | searchBlockProperty ;
    ```

- [ ] **Widen `searchBlockProperty` to the remaining data-bearing kinds.** Add
  `searchBlockProperty` to:
  - `tableProperty` (~line 108)
  - `viewProperty` (~line 110)
  - `columnProperty` (~line 112 — added in the step above)
  - `relationProperty` (~line 126)

  (`entityProperty`, `attributeProperty`, `queryProperty`, `roleProperty` already
  have it — leave them.)

- [ ] **Add the `fuzzy` production.** Near the other search productions
  (~line 199, after `examplesProperty`):
  ```antlr
  fuzzyProperty : FUZZY propSep? BOOLEAN_LITERAL ;
  ```

- [ ] **Add `searchable` and `fuzzy` to the block body.** Extend
  `searchSubProperty` (~lines 315–321):
  ```antlr
  searchSubProperty
    : keywordsProperty
    | patternsProperty
    | descriptionsProperty
    | examplesProperty
    | aliasesProperty
    | searchableProperty
    | fuzzyProperty
    ;
  ```
  Update the block comment (~line 194 and ~line 442) to list `searchable` and
  `fuzzy` among the sub-properties.

- [ ] **Add the `FUZZY` lexer token.** In the search-feature lexer group
  (~lines 445–448, near `SEARCH`/`KEYWORDS`):
  ```antlr
  FUZZY : 'fuzzy' ;
  ```
  Keep `SEARCHABLE : 'searchable' ;` (~line 419) — still used inside the block.

- [ ] **Map `FUZZY` to a TextMate scope.** In
  `packages/vscode-ext/scripts/generate-tm-grammar.ts`, add a case alongside the
  existing `SEARCH`/`SEARCHABLE` cases (~line 98/118):
  ```ts
  case 'FUZZY': return 'keyword.other.property.ttr';
  ```

- [ ] **Regenerate the antlr parser.**
  ```bash
  cd packages/parser && pnpm run prebuild
  ```
  This rewrites `packages/parser/src/generated/*` via `antlr-ng`. Confirm a
  `FuzzyPropertyContext` (and the widened `*Property` contexts) appear in the
  generated `TTRParser` types.

- [ ] **Regenerate the TextMate grammar, then verify build.**
  ```bash
  cd packages/vscode-ext && node scripts/generate-tm-grammar.ts
  cd ../.. && pnpm --filter @modeler/parser build && pnpm --filter @modeler/parser typecheck
  ```

## Verification

- [ ] `pnpm --filter @modeler/parser build` succeeds (the `prebuild` hook reruns
  generation cleanly).
- [ ] `git status` shows regenerated files under
  `packages/parser/src/generated/` and the VS Code TextMate grammar — stage them
  in the same commit as the `.g4` edit.
- [ ] Grep the generated parser for `Fuzzy` and `searchBlock` to confirm the new
  rules landed: `grep -rl Fuzzy packages/parser/src/generated`.

## Notes / gotchas

- `descriptions` (list) vs `description` (single) disambiguation relies on ANTLR
  longest-match — don't reorder lexer rules around `SEARCH`/`SEARCHABLE`.
- The grammar is **vendored into `ai-platform`**; do not sync yet — T5 owns the
  sync + Kotlin regen so it happens together with the loader change.
