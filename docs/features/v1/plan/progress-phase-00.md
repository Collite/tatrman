# Phase 0 — Progress

**Started:** 2026-05-14
**Status:** In review — see review-001.md

## Section A — Monorepo scaffold
- [x] Create branch `feat/phase-00-thin-slice` from `main` (already on v0)
- [x] Create `docs/plan/progress-phase-00.md`
- [x] Confirm Node 20+ and pnpm 9+ (Node 24.11.0, pnpm 11.1.1)
- [x] Confirm Java 21+ available (OpenJDK 21.0.11)
- [x] Read architecture.md
- [x] Read TTR.g4 and samples

## Section B — @modeler/grammar
- [x] Create `packages/grammar/` with package.json, tsconfig.json
- [x] Move `grammar/TTR.g4` to `packages/grammar/src/TTR.g4`
- [x] Update repo root README.md reference
- [x] Add `generate-typescript-parser.sh`, `sync-to-ai-platform.sh`, `check-sync.sh`
- [x] Add `index.ts` exposing grammarFile path
- [x] Add README documenting scripts and canonical-source policy

**Review-001 fixes applied:**
- Removed `options { language = TypeScript; }` from `TTR.g4` (target-neutral; vendored to ai-platform Kotlin parser)
- Added `-Dlanguage=TypeScript` CLI flag to `generate-typescript-parser.sh`
- Grammar file is now target-neutral; TypeScript target selected via CLI only

## Section C — @modeler/parser
- [x] Create `packages/parser/` with package.json, tsconfig.json
- [x] antlr-ng for TypeScript parser generation
- [x] `parseString(content, fileLabel?)` and `parseFile(path)` APIs
- [x] AST types: Document, Definition (discriminated union — Review-001 P1 Task 14), SchemaDirective, SourceLocation, ParseError, ParseResult
- [x] walker.ts with DiagnosticErrorListener for syntax errors
- [x] Tests: empty doc, schema directive, entity def, syntax error, parseFile samples
- [x] README documenting API

**Review-001 fixes applied:**
- `offsetStart`/`offsetEnd` now use token `start`/`stop` indices from ANTLR (not hardcoded to 0)
- `endColumn` uses last-token's length (not span length) — fixed in review-002 Task 1; restored strict test `endColumn === 20` for 20-char input
- JSDoc added to `SourceLocation` documenting ANTLR 1-indexed lines / 0-indexed columns convention
- Parser tsconfig `exclude` removed (decorative, caused confusion)
- `generate-typescript-parser.sh` prebuild hook runs correctly
- `Definition` is now a proper discriminated union with 17 per-kind interfaces (`ModelDef`, `TableDef`, … `Er2CncRoleDef`), each with `kind: '<kind>'` discriminant. `walker.ts` returns typed variants via `satisfies` casts.

## Section D — @modeler/semantics
- [x] Create `packages/semantics/` with package.json, tsconfig.json
- [x] Placeholder interfaces: SymbolTable, Resolver, Validator (TODO for Phase 2)
- [x] noop() function
- [x] Test verifying package builds

## Section E — @modeler/edit
- [x] Create `packages/edit/` with package.json, tsconfig.json
- [x] Re-exports WorkspaceEdit from vscode-languageserver-types

## Section F — @modeler/lsp
- [x] Create `packages/lsp/` with package.json, tsconfig.json
- [x] server-stdio.ts (Node child process entry)
- [x] server-browser.ts (Web Worker entry)
- [x] server.ts with createServerConnection() factory
- [x] Implement: initialize, initialized, shutdown, exit lifecycle
- [x] Implement: textDocument/didOpen, didChange, didClose, didSave full sync
- [x] Implement: textDocument/publishDiagnostics on document changes
- [x] Implement: custom modeler/getModelGraph returning stub graph
- [x] esbuild bundling for stdio (~504kb) and browser (~964kb)
- [x] README documenting public surface

**Review-001 fixes applied:**
- `server-browser.ts` uses ESM top-level import of `parseString` (not `require()`)
- `--external:@modeler/parser` and `--external:antlr4ng` removed; browser bundle embeds parser + ANTLR runtime
- `server.ts` exports `createServerConnection(connection: Connection)` for testability
- All `as any` workarounds resolved; `TextDocuments(TextDocument)` typed properly
- `runServer()` uses `createConnection(ProposedFeatures.all)` (review-002 Task 4 — no more `as never`)

## Section G — @modeler/vscode-ext
- [x] Create `packages/vscode-ext/` with package.json, tsconfig.json
- [x] extension.ts with LanguageClient wiring
- [x] Language registration for .ttr and .ttrl
- [x] TextMate grammar auto-generated from TTR.g4
- [x] Language configuration (bracket pairs, comments, auto-closing pairs)
- [x] Command: modeler.openInDesigner (placeholder)
- [x] .vscode/launch.json for Extension Development Host
- [x] README documenting dev workflow

**Review-001 fixes applied:**
- `copy-server` script in `package.json` copies `server-stdio.js` from `../lsp/dist/` to extension `dist/`
- `language-configuration.json` created with bracket pairs, comments, auto-closing pairs, surrounding pairs
- `.vscode/launch.json` created with "Run Extension" configuration
- `tsconfig.json` uses `paths` to redirect `vscode-languageclient` resolution to correct `./lib/node/main.d.ts` (fixes TS2724)
- `extension.ts` uses `NodeModule` with both `run` and `debug` for `ServerOptions`
- `generate-tm-grammar.ts` tokens audited against TTR.g4 — removed tokens not present as lexer rules

**Note:** VS Code smoke test (`@vscode/test-electron`) deferred to Phase 1.

## Section H — @modeler/designer
- [x] Create `packages/designer/` with React 19 + Vite + TypeScript scaffold
- [x] Tailwind CSS configuration
- [x] Cytoscape canvas component for node rendering
- [x] Header with file picker (.ttr loading)
- [x] InspectorPanel scaffold
- [x] LSP-driven node loading (Review-001 Task 4) — NOT the regex parser from v0
- [x] README documenting dev workflow

**Review-001 fixes applied:**
- `lsp-client.ts` created with `createLspClient()` using `BrowserMessageReader/Writer` + `createProtocolConnection` over a Worker
- `App.tsx` uses `client.openDocument()` and `client.getModelGraph()` (not regex)
- `vscode-languageserver-protocol` and `vscode-jsonrpc` added as dependencies

**Note:** Playwright smoke test and full LSP integration deferred to Phase 3.

## Section I — Cross-package integration tests
- [x] Create `tests/integration/` with package.json
- [x] Tests parseFile for all .ttr files in samples/ (no errors)
- [x] Tests that `samples/v1-metadata/er.ttr` returns >0 entity definitions
- [x] LSP integration tests: boots server in-process via `MessageConnection`, sends `didOpen` for each sample, asserts no diagnostics errors, calls `modeler/getModelGraph` on er.ttr and validates node shape

**Review-001 fixes applied:**
- `tests/integration/package.json` now includes `@modeler/lsp` as a `workspace:*` devDependency
- Integration test suite: 4 tests (2 parser + 2 LSP)
- Current LSP tests open only `er.ttr` via LSP (not every sample). Tighten in Phase 1 §K when broken-fixture infrastructure lands.

## Section J — Documentation
- [x] Update repo root README.md with developing locally section
- [x] Add CONTRIBUTING.md with workspace structure, package conventions, test conventions

## Section K — Progress tracking
- [x] (this file)

## Deferred to Later Phases

| Item | Deferred to |
|------|-------------|
| Full AST with all Definition properties | Phase 2.A |
| Symbol table, reference resolver | Phase 2.B/C |
| Validator, per-kind checks | Phase 2.D |
| Go-to-definition, find-references, hover | Phase 2 |
| LSP semantic tokens | Phase 1 |
| Designer edit mode | v1.1 |
| Designer detail panel content | Phase 3 |
| Designer schema/detail toggles | Phase 3 |
| Layout persistence | Phase 3 |
| IntelliJ plugin | Phase 4 |
| ai-platform CI integration | Phase 1 |
| VS Code smoke test (`@vscode/test-electron`) | Phase 1 |
| TextMate grammar structural rebuild | Phase 1 (full coverage) |

## Carried into Phase 1

- [ ] VS Code smoke test (`@vscode/test-electron`) — extension host must open `samples/v1-metadata/er.ttr`, show highlighting, show diagnostics on broken variant, confirm no error popup
- [ ] TextMate grammar covers every lexer rule in TTR.g4 (structural rebuild, not audit)

## Known Issues

~~1. **LSP vscode-languageserver API strictness** — Workaround used `(createConnection as any)()` pattern.~~ Resolved in Review-001 Task 6 (ProposedFeatures.all pattern, no more casts).

1. **TextMate grammar coverage** — Minimal in Phase 0 (keywords, strings, numbers, comments only). Full coverage in Phase 1.

2. **Designer LSP integration** — LSP-in-Web-Worker wired in Review-001 Task 4. Full integration and er/db schema rendering deferred to Phase 3.

3. **Parser tests path** — Tests use relative path `../../../../samples` due to Vitest running in package directory. Address as part of Phase 1 §K (broken-fixture infrastructure) by introducing a shared `_fixtures.ts` helper.

## Test Results

```
packages/parser:  9 tests passed  (added endColumn regression tests in review-002)
packages/semantics: 1 test passed
packages/lsp:    3 tests passed  (initialize, diagnostics, getModelGraph)
tests/integration: 4 tests passed (2 parser + 2 LSP)
pnpm -r build:   exits 0
pnpm -r lint:    exits 0 (MODULE_TYPELESS_PACKAGE_JSON warning resolved by adding "type": "module" to root package.json)
pnpm -r typecheck: exits 0
```