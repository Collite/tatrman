# PD2 — `.ttrd` grammar + `DomainBlock` AST

**Goal:** add the `domain` file kind to the grammar (`DOMAIN`/`PACKAGES`/`ENTITIES` tokens + `domainBlock` rules), the `DomainBlock` AST node, and the `.ttrd` file-kind dispatch — mirroring how `.ttrg`/`graphBlock` were done in v1.1.A/C1.

**Reads:** [contracts §13.3](../../v1-1/design/v1-1-contracts.md#133-ttrd-grammar--domainblock-ast), [grammar-changes §9.3](../../v1-1/design/grammar-v1-1-changes.md#93-new-ttrd-domain-file-editor-only--ai-platform-does-not-load-it), the shipped [`A-grammar.md`](../../v1-1/plan/tasks/A-grammar.md) and [`C1-ttrg-parsing.md`](../../v1-1/plan/tasks/C1-ttrg-parsing.md) as the pattern to copy.
**Blocked by:** v1.1 A (grammar) merged. (Independent of PD1 — can run in parallel.)
**Blocks:** PD3.
**Estimated time:** 2–3 days.

## Tests-first

- [x] `packages/parser/src/__tests__/ttrd-grammar.test.ts`:
  - `parseString('package domains\ndomain accounting { packages: [ucetnictvi, obchodni_doklady] }')` → `ast.domain?.name === 'accounting'`; `ast.domain?.packages` = `['ucetnictvi','obchodni_doklady']`; `ast.domain?.entities` = `[]`.
  - domain with `description`, `tags`, and `entities: [artikl.er.entity.artikl]` parses; fields populated.
  - nested package member `prodeje.regional` parses as a single dotted member string (not split).
  - trailing commas tolerated (mirror `graphBlock`).
  - a `.ttr`-style file with both `domain` block and `def` → parses (grammar permissive); both nodes present (file-kind error is PD3).
  - member source locations carried (for PD3 go-to-def); `domain`/`packages`/`entities` still usable as identifier fragments.
- [x] TextMate scopes — extended `packages/vscode-ext/scripts/__tests__/generate-tm-grammar.test.ts` (the actual generator test; the path in the task draft, `packages/grammar/scripts/__tests__/`, does not exist) to assert `keyword.declaration.domain.ttr`, `keyword.other.packages.ttr`, `keyword.other.entities.ttr`.

## Library reference

Run Context7 before editing the grammar:

```
mcp__context7__resolve-library-id { libraryName: "antlr4ng", query: "TypeScript runtime, lexer rule ordering" }
mcp__context7__query-docs         { libraryId: "<id>", query: "keyword vs identifier longest-match, optional list rules" }
```

The working regeneration pattern is in `A-grammar.md`. You are only editing `TTR.g4` and re-running `pnpm --filter @modeler/parser run prebuild`. Do not hand-edit generated files.

## Implementation tasks

- [x] **PD2.1 — Lexer tokens.** `DOMAIN`/`PACKAGES`/`ENTITIES` added to `TTR.g4` (after `MAPPING`, before `IDENT`). `PACKAGES` ('packages') coexists with `PACKAGE` ('package') via longest-match.
- [x] **PD2.2 — `document` rule.** Now `packageDecl? importDecl* (schemaDirective | graphBlock | domainBlock)? definition* EOF`.
- [x] **PD2.3 — Domain parser rules.** `domainBlock`, `domainProperty`, `domainPackagesProperty`, `domainEntitiesProperty` added; reuse `descriptionProperty`/`tagsProperty`.
- [x] **PD2.4 — `idPart` extension.** `DOMAIN | PACKAGES | ENTITIES` added to `idPart`.
- [x] **PD2.5 — Regenerate + version note.** Regenerated via prebuild. Grammar marker `2.2 → 2.3` (additive; the grammar had already advanced past the draft's `2.0.0`/`2.1.0` to `2.2` for drill_map). Header note + CHANGELOG entry added (also backfilled the missing 2.2 entry).
- [x] **PD2.6 — `DomainBlock` AST + walker.** `DomainBlock` added to `ast.ts` + `Document.domain?`; `walkDomainBlock` mirrors `walkGraphBlock`. Source locations on the node and each member (`packageSources`/`entitySources`).
- [x] **PD2.7 — TextMate generator.** `tokenToScope` + the hardcoded `keywordScopes`/`scopeMap` lists updated for the three keywords; `ttr.tmLanguage.json` regenerated. Full `.ttrd` language registration (icon, extension binding, dedicated `.ttrd` grammar) deferred to PD5/VS Code follow-up.

## Verify by running

```bash
pnpm --filter @modeler/grammar test
pnpm --filter @modeler/parser test
pnpm --filter @modeler/vscode-ext test
pnpm -r build && pnpm -r typecheck
```

All exit 0. New `ttrd-grammar.test.ts` cases pass; every existing parser test still passes (additive change).

## DONE when

- [x] Every checkbox ticked.
- [x] `TTR.g4` declares `DOMAIN`/`PACKAGES`/`ENTITIES` and the `domainBlock` rule set; `document` accepts a `domainBlock`.
- [x] `Document.domain?: DomainBlock` is populated by the walker, with source locations on members.
- [x] All v1/v1.1 samples still parse (parser 177, full `-r test` green). No semantics yet — `DomainTable`, recursion, and file-kind enforcement are PD3.
