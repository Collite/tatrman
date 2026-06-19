# PD2 — `.ttrd` grammar + `DomainBlock` AST

**Goal:** add the `domain` file kind to the grammar (`DOMAIN`/`PACKAGES`/`ENTITIES` tokens + `domainBlock` rules), the `DomainBlock` AST node, and the `.ttrd` file-kind dispatch — mirroring how `.ttrg`/`graphBlock` were done in v1.1.A/C1.

**Reads:** [contracts §13.3](../../design/v1-1-contracts.md#133-ttrd-grammar--domainblock-ast), [grammar-changes §9.3](../../design/grammar-v1-1-changes.md#93-new-ttrd-domain-file-editor-only--ai-platform-does-not-load-it), the shipped [`A-grammar.md`](../tasks/A-grammar.md) and [`C1-ttrg-parsing.md`](../tasks/C1-ttrg-parsing.md) as the pattern to copy.
**Blocked by:** v1.1 A (grammar) merged. (Independent of PD1 — can run in parallel.)
**Blocks:** PD3.
**Estimated time:** 2–3 days.

## Tests-first

- [ ] `packages/parser/src/__tests__/ttrd-grammar.test.ts`:
  - `parseString('package domains\ndomain accounting { packages: [ucetnictvi, obchodni_doklady] }')` → `ast.domain?.name === 'accounting'`; `ast.domain?.packages` = `['ucetnictvi','obchodni_doklady']`; `ast.domain?.entities` = `[]`.
  - domain with `description`, `tags`, and `entities: [artikl.er.entity.artikl]` parses; fields populated.
  - nested package member `prodeje.regional` parses as a single dotted member string (not split).
  - trailing commas tolerated (mirror `graphBlock`).
  - a `.ttr`-style file with both `domain` block and `def` → parses (grammar permissive); the file-kind error is a semantics concern (asserted in PD3, but add a parser-level smoke that both nodes are present).
- [ ] `packages/grammar/scripts/__tests__/textmate-output.test.ts` (extend) — assert scopes for `keyword.declaration.domain.ttr`, `keyword.other.packages.ttr`, `keyword.other.entities.ttr`.

## Library reference

Run Context7 before editing the grammar:

```
mcp__context7__resolve-library-id { libraryName: "antlr4ng", query: "TypeScript runtime, lexer rule ordering" }
mcp__context7__query-docs         { libraryId: "<id>", query: "keyword vs identifier longest-match, optional list rules" }
```

The working regeneration pattern is in `A-grammar.md`. You are only editing `TTR.g4` and re-running `pnpm --filter @modeler/parser run prebuild`. Do not hand-edit generated files.

## Implementation tasks

- [ ] **PD2.1 — Lexer tokens.** In `packages/grammar/src/TTR.g4`, after `LAYOUT`, add `DOMAIN : 'domain' ;`, `PACKAGES : 'packages' ;`, `ENTITIES : 'entities' ;`. Keep them before `IDENT`.
- [ ] **PD2.2 — `document` rule.** Extend to `packageDecl? importDecl* (schemaDirective | graphBlock | domainBlock)? definition* EOF` (add the `domainBlock` alternative alongside `graphBlock`).
- [ ] **PD2.3 — Domain parser rules.** Add `domainBlock`, `domainProperty`, `domainPackagesProperty`, `domainEntitiesProperty` per contracts §13.3 / grammar-changes §9.3. Reuse `descriptionProperty`/`tagsProperty` from the `graphProperty` set.
- [ ] **PD2.4 — `idPart` extension.** Add `DOMAIN | PACKAGES | ENTITIES` to `idPart` (consistent with how `OBJECTS`/`LAYOUT` were added) so the new keywords remain usable as reference fragments.
- [ ] **PD2.5 — Regenerate + version note.** Run `pnpm --filter @modeler/parser run prebuild`; commit generated output. No grammar **major** bump needed beyond the existing `2.0.0` (these are additive within the v2 line) — append a one-line note to `TTR.g4`'s header comment recording the `.ttrd` addition. Confirm with the grammar maintainer whether a minor bump (`2.1.0`) is warranted; default to `2.1.0`.
- [ ] **PD2.6 — `DomainBlock` AST + walker.** Add the `DomainBlock` interface (contracts §13.3) to `packages/parser/src/ast.ts`; add `domain?: DomainBlock` to `Document`. Extend the AST walker to populate it (mirror the `GraphBlock` walker). Carry `source: SourceLocation` on the node and each member (members need locations for go-to-def in PD3).
- [ ] **PD2.7 — TextMate generator.** Update `packages/vscode-ext/scripts/generate-tm-grammar.ts` for the three new keywords; regenerate. (Full `.ttrd` language registration — file icon, extension binding — is a small follow-up; note it for PD5/VS Code but a minimal scope addition is enough here.)

## Verify by running

```bash
pnpm --filter @modeler/grammar test
pnpm --filter @modeler/parser test
pnpm --filter @modeler/vscode-ext test
pnpm -r build && pnpm -r typecheck
```

All exit 0. New `ttrd-grammar.test.ts` cases pass; every existing parser test still passes (additive change).

## DONE when

- [ ] Every checkbox ticked.
- [ ] `TTR.g4` declares `DOMAIN`/`PACKAGES`/`ENTITIES` and the `domainBlock` rule set; `document` accepts a `domainBlock`.
- [ ] `Document.domain?: DomainBlock` is populated by the walker, with source locations on members.
- [ ] All v1/v1.1 samples still parse. No semantics yet — `DomainTable`, recursion, and file-kind enforcement are PD3.
