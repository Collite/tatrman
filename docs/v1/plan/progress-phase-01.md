# Phase 1 — Progress

**Started:** 2026-05-14
**Status:** In progress

## Section A — Carryover from review-001 P1/P2
- [x] Confirm review-001 P1/P2 items are reflected on disk (all verified)

## Section B — TextMate grammar full coverage
- [x] Audit pass — tokens verified against TTR.g4 (107 lexer rules)
- [x] Rebuilt generator — parses TTR.g4 via regex, maps tokens to 14 scope categories
- [x] Generated file: `packages/vscode-ext/syntaxes/ttr.tmLanguage.json` (commit-ready)
- [x] `regen-tmgrammar` script added to vscode-ext
- [x] Unit tests for generator: 6 tests in `scripts/__tests__/generate-tm-grammar.test.ts`
- [x] CI guard in `ci.yml`: `git diff --exit-code` on grammar regeneration

## Section C — Full language configuration
- [x] `wordPattern`: `[a-zA-ZÀ-ɏ_][a-zA-Z0-9_À-ɏ]*` (Czech/Latin Extended)
- [x] `indentationRules`: increaseIndentPattern for `{`, decreaseIndentPattern for `}`
- [x] `onEnterRules`: for empty `{}` and `[]` blocks

## Section D — .ttrl layout sidecar support
- [x] Language registration: id `ttrl`, extensions `.ttrl`, mimetype `application/ttrl+json`
- [x] `language-configuration-ttrl.json` for JSON-style behavior
- [x] JSON schema: `packages/vscode-ext/schemas/ttrl.schema.json` (version, viewports, nodes, edges)
- [x] `contributes.jsonValidation` in package.json
- [x] LSP gates `.ttrl` parse: `if (uri.endsWith('.ttrl')) return;`
- [x] Unit test: `.ttrl` file produces no diagnostics

## Section E — Diagnostic taxonomy
- [x] `DiagnosticCode` enum in `packages/parser/src/diagnostics.ts`:
  - `ParseError = 'ttr/parse-error'`
  - `UnknownProperty = 'ttr/unknown-property'` (reserved for Phase 2; not emitted in Phase 1)
- [x] `ParseError.code` field added to `ParseError` interface
- [x] `DiagnosticErrorListener.syntaxError` now sets `code: DiagnosticCode.ParseError`
- [x] LSP propagates `code` and `source: 'modeler'` on every Diagnostic
- [x] Tests: LSP test for diagnostic code + source on malformed input
- [x] `docs/design/diagnostics.md` created with full code catalog (note: `ttr/unknown-property` reserved for Phase 2; `ttr/parse-recovery-info` code removed — never emitted in Phase 1)

## Section F — Parser error recovery
- [x] 10 recovery fixtures in `packages/parser/src/__tests__/recovery-fixtures.ts`
- [x] ANTLR `DiagnosticErrorListener` emits `ttr/parse-error` for syntax errors
- [x] ANTLR's built-in error recovery produces partial ASTs
- [x] 20 recovery tests (2 per fixture — error check + def count check)
- [ ] `ttr/parse-recovery-info` diagnostic code **defined but not yet emitted** —
  requires `DefaultErrorStrategy` subclass in walker.ts (Phase 2 work)
- [ ] 5 of 10 fixtures are "permissive grammar" cases (grammar accepts these;
  `expectErrors: false`); only 5 produce actual parse errors

**Status:** Partial — `ttr/parse-recovery-info` emission deferred to Phase 2.

## Section G — Semantic tokens via LSP
- [ ] Deferred — see review-003 §1G. `vscode-languageserver@9`'s
  `ServerCapabilities.semanticTokensProvider` type is workable but the
  partial work was rolled back. Move to Phase 1.1 or Phase 2.A.
  `textDocument/semanticTokens/full` handler not in LSP server; no unit test
  for semantic tokens exists.

**Status:** Deferred — see review-003 §1G. Move to Phase 1.1 or Phase 2.A.

## Section H — File icons
- [x] `packages/vscode-ext/icons/ttr.svg` — document icon (blue rect with text lines)
- [x] `packages/vscode-ext/icons/ttrl.svg` — layout icon (grey rect with grid)
- [x] Language registration includes icon paths (light/dark)

## Section I — ai-platform sync CI integration
- [x] `.github/workflows/grammar-sync.yml` — triggers on TTR.g4 changes, runs check-sync.sh
- [x] Graceful skip when AI_PLATFORM_PATH not set
- [x] `sync-ai-platform` script at root `package.json`

## Section J — VS Code smoke test
- [ ] Deferred — see review-003 §1J. Scaffold removed (placeholder test + broken
  runner script). Move to Phase 1.1.

**Status:** Deferred — see review-003 §1J. Move to Phase 1.1.

## Section K — Broken-sample fixtures
- [x] `samples/broken/` directory created with 5 fixtures (not the planned 6;
  `query-bad-language-value.ttr` missing — defer to Phase 2 if useful)
- [x] Integration tests consume broken fixtures (`tests/integration/src/integration.test.ts`)
- [x] Fixture names ↔ contents ↔ README aligned (review-003 Task 14):
  `db-trailing-comma.ttr` renamed to `db-unterminated-bracket.ttr`;
  `db-missing-comma.ttr` content matches its name

## Section L — Documentation + progress
- [x] `docs/design/diagnostics.md` created; example for `ttr/parse-error` shows
  a real syntax error (unmatched `{`); `ttr/unknown-property` annotated as
  reserved for Phase 2 (review-003 Tasks 8, 9)
- [x] `docs/plan/progress-phase-01.md` updated as work lands (this file)
- [ ] `packages/vscode-ext/README.md` with v1 feature list — not updated

## Test Results
```
packages/parser:  19 tests passed (9 original + 10 recovery — 5 fixtures × 2 tests)
packages/semantics: 1 test passed
packages/lsp:    4 tests passed (3 original + 1 ttrl gating)
packages/vscode-ext: 6 tests passed (generator)
tests/integration: 5 tests passed (broken fixtures integration)
pnpm -r build:   exits 0
pnpm -r lint:    exits 0
pnpm -r typecheck: exits 0
```

## Deferred to Later Phases
| Item | Target |
|------|--------|
| Semantic tokens (textDocument/semanticTokens/full) | Phase 1.1 or Phase 2.A |
| VS Code smoke test | Phase 1.1 |