# Stage AA — inline `mapping:` keyword → `binding:`

Goal: rename the **inline mapping property** (`mapping:` on `def entity` / `def attribute` /
`def relation`, the v2.1 sugar for `def er2db_*`) to **`binding:`**, so the whole cross-model
vocabulary is consistent with the `schema map → binding` rename (Stage A).

This is a sibling of Stage A and **shares files with it** (the `BINDING` token, `default-schema.ts`,
the `mapping-*.ts` semantics modules). Stage A renamed the *schema code*; AA renames the *inline
property keyword*. Do AA **right after** A (A is already in development and has added the `BINDING`
token, which AA reuses).

Verified footprint (`packages/grammar/src/TTR.g4` + TS):
- Grammar: `MAPPING : 'mapping'` token (≈ line 537), `MAPPING` in `idPart` (≈ line 519);
  `mappingProperty : MAPPING propSep? mappingValue` (≈ line 284) referenced by `entityProperty`
  (187), `attributeProperty` (189), `relationProperty` (191); sub-rules `mappingValue`,
  `mappingBlock`, `mappingBlockProperty`, `mappingColumnsProperty`, `mappingColumnMap`,
  `mappingColumnEntry`, `mappingColumnValue`, `mappingTargetValue` (≈ 286–319). `BINDING : 'binding'`
  already exists (≈ line 544, added by Stage A).
- Parser: AST nodes `MappingProperty`, `MappingPropertyBareId`, `MappingPropertyBlock`
  (`packages/parser/src/index.ts` 63–65, `ast.ts`, `walker.ts`).
- Diagnostics: `DuplicateMapping = 'ttr/duplicate-mapping'` (`packages/parser/src/diagnostics.ts` 33).
- Semantics: `mapping-synthesizer.ts` (`entity.mapping`, `attr.mapping`, `rel.mapping`),
  `mapping-references.ts` (`collectMappingReferences`, `MappingReference`), `reference-index.ts`,
  `default-schema.ts` (qname namespace already `binding.er2dbEntity.…`).
- Host: `format/src/printer.ts`, `vscode-ext/scripts/generate-tm-grammar.ts` (`case 'MAPPING'`),
  `completion-property.ts` (property suggestions).

Prereq: Stage A merged (or coordinate on the same branch — they touch the same files). TDD: AA1 first.

---

- [ ] **AA1 — Tests first (red).**
  - Update `packages/semantics/src/__tests__/mapping-references.test.ts` and
    `mapping-synthesizer.test.ts` fixtures to use `binding:` (entity block, bare-id attribute, and
    relation `fk` forms).
  - Update/clone the conformance fixtures that exercise inline mapping to `binding:`; add a negative
    fixture asserting `mapping:` is now an unknown property under `schema er`.
  - Add a test asserting the renamed diagnostic code `ttr/duplicate-binding`.

- [ ] **AA2 — Grammar.** In `TTR.g4`:
  - Rename `mappingProperty → bindingProperty` using the existing `BINDING` token; rename the
    sub-rules `mapping* → binding*` (`bindingValue`, `bindingBlock`, `bindingBlockProperty`,
    `bindingColumnsProperty`, `bindingColumnMap`, `bindingColumnEntry`, `bindingColumnValue`,
    `bindingTargetValue`). Update the `entityProperty` / `attributeProperty` / `relationProperty`
    alternations to reference `bindingProperty`.
  - **Remove** the `MAPPING` token and its `idPart` entry. Update the `// v2.1 — inline mapping`
    comment to note the v3.0 rename.

- [ ] **AA3 — Regenerate + parser AST/walker.**
  - Run both regen steps (parser prebuild, vscode-ext tm-grammar).
  - Rename the AST nodes `MappingProperty*` → `BindingProperty*` in `ast.ts`, `walker.ts`, and the
    `packages/parser/src/index.ts` exports. Keep source locations intact.
  - `diagnostics.ts`: `DuplicateMapping = 'ttr/duplicate-mapping'` → `DuplicateBinding =
    'ttr/duplicate-binding'`. (Breaking diagnostic-code change — acceptable in the 3.0 window;
    record it in the CHANGELOG via Stage D.)

- [ ] **AA4 — Semantics.**
  - `mapping-synthesizer.ts`: rename the `entity.mapping` / `attr.mapping` / `rel.mapping` field
    reads to `.binding` (matching the renamed AST). Synthesis still emits `er2db_*` — only the inline
    property name changed.
  - `mapping-references.ts` / `reference-index.ts`: update for the renamed nodes. Rename the public
    symbols `collectMappingReferences`/`MappingReference` → `collectBindingReferences`/
    `BindingReference` for consistency (update `semantics/src/index.ts` exports + consumers in
    `lsp`). **Optional/your call:** renaming the *files* `mapping-*.ts` → `binding-*.ts` is cosmetic
    churn — symbols + keyword are what matter; decide and be consistent.

- [ ] **AA5 — Host: format + highlight + completion.**
  - `format/src/printer.ts`: print `binding:` for the inline property.
  - `generate-tm-grammar.ts`: the `MAPPING` case is gone; ensure `BINDING` highlights as a property
    keyword in the inline context (it already exists for the schema keyword). Re-run the generator.
  - `completion-property.ts`: offer `binding` (not `mapping`) on entity/attribute/relation.

- [ ] **AA6 — Migrate modeler fixtures + docs.**
  - Rewrite inline `mapping:` → `binding:` in every modeler fixture
    (`rg -l 'mapping\s*:' tests --glob='!**/node_modules/**'`; exclude the `mappings`/schema forms).
  - Update docs that describe "inline mapping (v2.1)" → `binding` (e.g. `docs/features/yaml-converter-inline-split/`,
    `CLAUDE.md` parser section). (ai-models content is handled in Stage E.)

- [ ] **AA7 — Verify.**
  - `pnpm --filter @modeler/parser test && pnpm --filter @modeler/semantics test && pnpm --filter @modeler/format test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build`
  - `rg -n '\bmapping\s*:' --glob='!**/node_modules/**' --glob='!**/.vscode-test/**'` returns no
    results (the inline keyword is gone; `mappings`/other words unaffected).

- [ ] **AA8 — Commit.** `Section Phase0-AA: rename inline mapping: → binding:`. Coordinate the merge
  with Stage A (shared files); do not tag a grammar release yet (Stage D).
