# Section B — Grammar additions + regen

Add the `mapping` keyword, the inline-mapping rules, and relax `targetProperty` to accept a bare id. Regenerate the antlr parser, the TextMate grammar, and the property map.

**Depends on:** Section A (design + grammar-changes doc shipped).

**Spec:** [`docs/v2-1/design/grammar-v2-1-changes.md`](../../design/grammar-v2-1-changes.md) §3.

**Files:**
- `packages/grammar/src/TTR.g4` — canonical grammar.
- `packages/grammar/CHANGELOG.md` — append the 2.1 entry.
- `packages/vscode-ext/scripts/generate-tm-grammar.ts` — add `MAPPING` scope.

All line numbers below are as of the planning snapshot — confirm by reading the surrounding rule before editing.

---

## Tasks

- [ ] **Bump the version marker.** Top of `packages/grammar/src/TTR.g4` (~line 4):
  ```diff
  -// @grammar-version: 2.0
  +// @grammar-version: 2.1
  ```
  The prebuild step will pick this up and regenerate `src/generated/version.ts`.

- [ ] **Add the `MAPPING` lexer token.** Near the other v1.1 property-name tokens (~line 391, alongside `OBJECTS` and `LAYOUT`):
  ```antlr
  MAPPING    : 'mapping' ;    // v2.1
  ```
  Place **before** `IDENT` so the keyword wins on longest-match (it already will, given the existing token ordering — just don't move it below `IDENT`).

- [ ] **Add `MAPPING` to `idPart`.** ~line 376, after the v1.1 graph-body keywords:
  ```antlr
  idPart
    : IDENT
    | ...                                       // existing alternatives unchanged
    | PACKAGE | IMPORT | GRAPH
    | OBJECTS | LAYOUT
    | MAPPING                                   // <-- NEW (v2.1)
    ;
  ```

- [ ] **Relax `targetProperty` to accept a bare id.** ~line 187:
  ```diff
  -targetProperty            : TARGET            propSep? object_ ;
  +targetProperty            : TARGET            propSep? ( object_ | id ) ;
  ```
  This is the *only* existing-rule change. Backward compatible — every existing `target: { ... }` value remains valid.

- [ ] **Add `mappingProperty` to entity/attribute/relation property lists.** ~lines 130/132/134:
  ```diff
  -entityProperty           : descriptionProperty | tagsProperty | labelPluralProperty | nameAttributeProperty | codeAttributeProperty | aliasesProperty | attributesProperty | rolesProperty | displayLabelProperty | searchBlockProperty ;
  +entityProperty           : descriptionProperty | tagsProperty | labelPluralProperty | nameAttributeProperty | codeAttributeProperty | aliasesProperty | attributesProperty | rolesProperty | displayLabelProperty | searchBlockProperty | mappingProperty ;

  -attributeProperty        : descriptionProperty | tagsProperty | typeProperty | isKeyProperty | optionalProperty | valueLabelsProperty | displayLabelProperty | searchBlockProperty ;
  +attributeProperty        : descriptionProperty | tagsProperty | typeProperty | isKeyProperty | optionalProperty | valueLabelsProperty | displayLabelProperty | searchBlockProperty | mappingProperty ;

  -relationProperty         : descriptionProperty | tagsProperty | fromProperty | toProperty | cardinalityProperty | joinProperty | searchBlockProperty ;
  +relationProperty         : descriptionProperty | tagsProperty | fromProperty | toProperty | cardinalityProperty | joinProperty | searchBlockProperty | mappingProperty ;
  ```

- [ ] **Add the `mappingProperty` rule and its sub-rules.** Place near the other property productions, after `searchBlockProperty` (~line 203). Order matters for readability — keep the top-level rule first, then helpers underneath:

  ```antlr
  // v2.1 — inline mapping (syntactic sugar for def er2db_*).
  // The value is either a bare id (defaults: column for attribute, fk for relation,
  // not legal for entity) or a block. Semantic layer enforces context-appropriate
  // keys (`target` + optional `columns:` for entity; `target` for attribute;
  // `fk` for relation).
  mappingProperty       : MAPPING propSep? mappingValue ;

  mappingValue
    : id
    | mappingBlock
    ;

  mappingBlock
    : LBRACE ( mappingBlockProperty ( COMMA? mappingBlockProperty )* COMMA? )? RBRACE
    ;

  mappingBlockProperty
    : targetProperty
    | mappingColumnsProperty
    | fkProperty_                       // reuse the existing er2db_relation FK rule
    ;

  mappingColumnsProperty
    : COLUMNS propSep? mappingColumnMap
    ;

  mappingColumnMap
    : LBRACE ( mappingColumnEntry ( COMMA? mappingColumnEntry )* COMMA? )? RBRACE
    ;

  mappingColumnEntry
    : id propSep? mappingColumnValue
    ;

  mappingColumnValue
    : id
    | LBRACE TARGET propSep? mappingTargetValue RBRACE
    | object_
    ;

  mappingTargetValue
    : id
    | object_
    ;
  ```

  Notes:
  - `fkProperty_` already exists (~line 186) as `FK propSep? id`. Reuse it.
  - Forms (b) and (c) use the dedicated `LBRACE TARGET ...` alternative because `TARGET` is not in `idPart` — it cannot be parsed as a generic `object_`. The `object_` fallback covers any future extension.
  - `COLUMNS` token already exists (~line 420). No new keyword needed for the inner-map key.

- [ ] **Append the 2.1 changelog entry.** `packages/grammar/CHANGELOG.md`, immediately under the `## 2.0` heading (insert a new section above it):
  ```md
  ## 2.1 — 2026-05-27

  Additive: inline mapping shorthand for er2db_* on entity/attribute/relation.
  Backward compatible — every 2.0 file parses unchanged.

  - Added `mapping:` property on `def entity`, `def attribute`, and `def relation`.
  - Added `MAPPING` lexer token; extended `idPart` to keep it usable as an identifier component.
  - Added rules: `mappingProperty`, `mappingValue`, `mappingBlock`,
    `mappingBlockProperty`, `mappingColumnsProperty`, `mappingColumnMap`,
    `mappingColumnEntry`, `mappingColumnValue`.
  - Relaxed `targetProperty` to accept a bare id (`target: <ref>`) in addition to
    the existing object form (`target: { column: <ref> }`).
  - Reuses existing `COLUMNS` and `FK` tokens; no new keywords beyond `mapping`.
  ```

- [ ] **Map `MAPPING` to a TextMate scope.** `packages/vscode-ext/scripts/generate-tm-grammar.ts`. Find the keyword-mapping switch (search for `'SEARCH'`) and add:
  ```ts
  case 'MAPPING': return 'keyword.other.property.ttr';
  ```

- [ ] **Regenerate the antlr parser.**
  ```bash
  cd packages/parser && pnpm run prebuild
  ```
  This rewrites `packages/parser/src/generated/*` via antlr-ng. Confirm:
  ```bash
  grep -l "MappingProperty\|MappingBlock\|MappingColumn" packages/parser/src/generated/
  ```
  Expect hits in `TTRParser.ts` (or whatever the generated parser file is named).

- [ ] **Regenerate the property map and version constant.**
  ```bash
  pnpm --filter @modeler/grammar build
  ```
  The prebuild hook reruns `scripts/extract-property-map.ts`, which:
  1. Re-emits `src/generated/property-map.ts` — should now show `{ name: 'mapping', type: 'unknown' }` (or similar) appended to `entity`, `attribute`, and `relation` entries.
  2. Re-emits `src/generated/version.ts` — should now export `TTR_GRAMMAR_VERSION = '2.1'`.

- [ ] **Add a `mapping` type entry to the property-map typeMap.** `packages/grammar/scripts/extract-property-map.ts`, in the `typeMap` const (~line 37):
  ```ts
  mapping: 'mapping block or reference',
  ```
  Re-run the prebuild after editing.

- [ ] **Regenerate the TextMate grammar.**
  ```bash
  cd packages/vscode-ext && node scripts/generate-tm-grammar.ts
  ```
  Confirm `mapping` appears as a keyword scope in `packages/vscode-ext/syntaxes/ttr.tmLanguage.json`.

- [ ] **Confirm all v1.1 samples still parse.**
  ```bash
  pnpm --filter @modeler/integration-tests test -- v1.1-samples
  ```
  All should pass — this is the backward-compat smoke check.

- [ ] **Confirm the sketched `samples/2.1/er.ttr` does NOT yet parse cleanly.** The user's draft has unbalanced braces; expect parse errors. Section F rewrites it. For now, just verify the parser produces ParseError diagnostics (not crashes).

---

## Verification

- [ ] `pnpm --filter @modeler/grammar build` succeeds.
- [ ] `pnpm --filter @modeler/parser build` succeeds (generated parser typechecks).
- [ ] `pnpm -r typecheck` green. (Some downstream packages may complain about unused `MappingProperty` AST handling — that's expected; Section C wires it up.)
- [ ] `git status` shows `packages/grammar/src/TTR.g4`, `packages/grammar/CHANGELOG.md`, `packages/grammar/scripts/extract-property-map.ts`, `packages/vscode-ext/scripts/generate-tm-grammar.ts`, `packages/vscode-ext/scripts/generate-tm-grammar.js`, and `packages/vscode-ext/syntaxes/ttr.tmLanguage.json`. (`packages/grammar/src/generated/` and `packages/parser/src/generated/` are gitignored — they are regenerated by the build, not committed.)
- [ ] `grep -n MAPPING packages/grammar/src/TTR.g4` returns the lexer token line and the `mappingProperty` rule line.
- [ ] `TTR_GRAMMAR_VERSION` exported from `@modeler/grammar` is `'2.1'`:
  ```bash
  cat packages/grammar/src/generated/version.ts
  ```

## Notes / gotchas

- The grammar is **vendored into `ai-platform`** — do **not** sync yet. Section G owns the sync + Kotlin regen so it happens after all modeler-side changes settle.
- `mappingColumnValue` uses a dedicated `LBRACE TARGET propSep? mappingTargetValue RBRACE` alternative for forms (b) and (c), plus a generic `object_` fallback. `{ target: ... }` cannot be parsed as `object_` because `TARGET` is not in `idPart` — the dedicated alternative is required.
- `targetProperty` relaxation affects **every** existing er2db_* def too (form: `target: db.dbo.SOMECOL` now works in explicit declarations). That's fine — it's symmetric sugar.
- The `entityProperty | attributeProperty | relationProperty` extensions are the only places where `mappingProperty` is reachable from the document grammar. Don't add it to `tableProperty`, `viewProperty`, `columnProperty`, etc. — those are not er2db sources.
- Don't touch the existing `def er2db_entity` / `def er2db_attribute` / `def er2db_relation` rules. They keep working unchanged.
