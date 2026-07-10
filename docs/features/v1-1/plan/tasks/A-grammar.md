# 1.1.A — Grammar additions

**Goal:** extend `TTR.g4` with the three new top-level constructs (`package`, `import`, `graph`) and the two `.ttrg` body keywords (`objects`, `layout`) and the wildcard star token (`*`), without breaking any existing v1 sample.

**Reads:** [contracts §1 (grammar tokens and parser rules added)](../../design/v1-1-contracts.md#1-grammar-tokens-and-parser-rules-added), [grammar diff doc](../../design/grammar-v1-1-changes.md) §3.
**Blocked by:** v1 (Phase 5) shipped.
**Blocks:** every other sub-phase in v1.1.
**Estimated time:** 3–5 days.

## Tests-first

- [ ] `packages/parser/src/__tests__/grammar-v2.test.ts` — new test file. Cases:
  - `parseString('package billing.invoicing\nschema er namespace entity\ndef entity X {}')` succeeds; `ast.packageDecl?.name === 'billing.invoicing'`; `ast.imports.length === 0`; `ast.definitions.length === 1`.
  - `parseString('package a.b\nimport x.y.*\nimport p.q.r.S\nschema er namespace entity\n')` succeeds; `ast.imports.length === 2`; first import `wildcard === true`, second `wildcard === false`.
  - `parseString('package a.b\ngraph my_view { schema: er, objects: [a.b.er.entity.X] }')` succeeds; `ast.graph?.name === 'my_view'`; `ast.graph?.schema === 'er'`; `ast.graph?.objects.length === 1`.
  - Every existing v1 sample (`samples/v1-metadata/*.ttr`, `samples/v1-mini/*.ttr`, `samples/builtin/*.ttr`) re-parses cleanly with `errors.length === 0`.
- [ ] `packages/grammar/scripts/__tests__/textmate-output.test.ts` (extend existing) — assert the generator emits scopes for `keyword.control.package.ttr`, `keyword.control.import.ttr`, `keyword.declaration.graph.ttr`.

## Library reference

Run Context7 before coding:

```
mcp__context7__resolve-library-id { libraryName: "antlr4ng", query: "TypeScript runtime, parser rules, error recovery" }
mcp__context7__query-docs         { libraryId: "<id>", query: "lexer rule ordering, longest-match disambiguation, idPart equivalent" }
```

Existing Phase-1 code shows the working `antlr-ng` regeneration pattern — that pattern stays; you're only editing `TTR.g4` and re-running `pnpm --filter @modeler/parser run prebuild`.

## Implementation tasks

- [ ] **A.1 — Add lexer tokens.** Edit `packages/grammar/src/TTR.g4`. After the `NAMESPACE` token, add `PACKAGE`, `IMPORT`, `GRAPH`, `OBJECTS`, `LAYOUT` in that order. After the `DOT` token, add `STAR : '*' ;`. Match the lexeme strings from [contracts §1.1](../../design/v1-1-contracts.md#11-new-lexer-tokens) exactly.
- [ ] **A.2 — Add parser rules.** In the same file, add `packageDecl`, `importDecl`, `graphBlock`, `graphProperty`, `graphSchemaProperty`, `graphObjectsProperty`, `graphLayoutProperty`, and `qualifiedName` per [contracts §1.2](../../design/v1-1-contracts.md#12-new-parser-rules). Place them right after the existing `schemaDirective` rule.
- [ ] **A.3 — Update the `document` rule.** Replace `document : schemaDirective? definition* EOF ;` with `document : packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF ;`.
- [ ] **A.4 — Extend `idPart`.** Add `PACKAGE | IMPORT | GRAPH | OBJECTS | LAYOUT` to the `idPart` alternatives per [contracts §1.3](../../design/v1-1-contracts.md#13-idpart-extension). This keeps the new keywords usable as cross-reference fragments (consistent with how `entity`, `table`, etc. are handled today).
- [ ] **A.5 — Regenerate the TypeScript parser.** Run `pnpm --filter @modeler/parser run prebuild`. Check the generated files in `packages/parser/src/generated/` for the new productions. Do **not** hand-edit generated files. Commit them.
- [ ] **A.6 — Update the TextMate-grammar generator.** Edit `packages/vscode-ext/scripts/generate-tm-grammar.ts` to recognise the three new top-level keywords (`package`, `import`, `graph`) and the two body keywords (`objects`, `layout`). Run `node scripts/generate-tm-grammar.ts`. Verify `packages/vscode-ext/syntaxes/ttr.tmGrammar.json` now contains the new scope names.
- [ ] **A.7 — Bump grammar version.** Update `packages/grammar/package.json`'s `version` to `2.0.0`. Update the `// TTR (Tatrman) vN grammar — ...` header comment at the top of `TTR.g4` to `v2`.
- [ ] **A.8 — Verify backward compatibility.** Run the grammar-v2 test file and the existing parser tests. Every test must pass.

## Verify by running

```bash
pnpm --filter @modeler/grammar test
pnpm --filter @modeler/parser test
pnpm --filter @modeler/vscode-ext test
pnpm -r build
pnpm -r typecheck
```

All exit 0. The new `grammar-v2.test.ts` file's cases pass; every existing parser test still passes.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] `TTR.g4` declares all six new tokens and all eight new parser rule alternatives.
- [ ] `pnpm --filter @modeler/parser run prebuild` produces clean generated output committed to the repo.
- [ ] All v1 samples still parse without errors.
- [ ] `packages/grammar/package.json` version is `2.0.0`.
- [ ] No AST changes yet — that's 1.1.B.1. The parser produces parse trees for the new constructs, but the AST walker doesn't know about them; the `Document` interface is unchanged. This is intentional and expected — 1.1.B.1 fills the AST in.
