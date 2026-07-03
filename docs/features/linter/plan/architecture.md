# Linter / Formatter / Autofix — Architecture

Companion to [`../design.md`](../design.md) (approved). This document fixes the solution
architecture, the component and flow diagrams, the tech stack, and — critically — the mapping from
**existing code** to **target packages**. Read the design doc first; this assumes its decisions.

> **Brownfield, not greenfield.** A working formatter and four autofix quick-fixes already exist
> inside `@modeler/lsp`. The plan **extracts and extends** them; it does not rebuild them. See §4.

---

## 1. Tech stack

| Concern | Choice | Already in repo |
|---|---|---|
| Language / modules | TypeScript, ESM, Node16 resolution, strict, ES2022 (`tsconfig.base.json`) | yes |
| Monorepo | pnpm workspaces (`packages/*`), `workspace:*` deps | yes |
| Parser runtime | `antlr4ng@^3.0.0` (TS port of ANTLR4) | yes (`packages/parser`) |
| Grammar gen | `antlr-ng` via `packages/grammar/scripts/generate-typescript-parser.sh` | yes |
| TOML config | `smol-toml` | yes (`packages/semantics/manifest.ts`) |
| CLI | `commander@^13` | yes (`packages/migrate/cli.ts`) |
| Tests | Vitest (`src/__tests__/*.test.ts`) | yes |
| LSP | `vscode-languageserver` (Node16 deep import per CLAUDE.md) | yes (`packages/lsp`) |
| Formatter IR | in-house Prettier-style Doc IR (`formatter/ir.ts`, `render.ts`) | yes (in `lsp`, to extract) |

No new third-party runtime dependencies are introduced. New packages are `@modeler/format` and
`@modeler/lint`.

---

## 2. Target package graph

```
grammar ──► parser(+CST) ──► semantics ──┬─► lint ─┐
                               │          │         ├─► lsp ──► vscode-ext
                               ├─► edit ──┘         │
                               └─► format ──────────┘            designer
```

Edges are one-way (CLAUDE.md invariant). New/changed nodes:

- **`@modeler/parser` (changed):** add a lossless CST + trivia layer. Semantic AST shape is
  unchanged; trivia is additive (`leadingTrivia?`, `trailingTrivia?`).
- **`@modeler/format` (new):** the formatter. Depends only on `parser`. No `semantics` dep — that
  is what keeps `ttr fmt` unconditionally safe. Ships `ttr fmt`.
- **`@modeler/lint` (new):** rule registry, runner, config, suppression, autofix. Depends on
  `semantics`, `parser`, and `edit`. Ships `ttr lint`.
- **`@modeler/edit` (changed):** gains structured-edit ops the autofixes need (most already exist
  as ad-hoc quick-fixes; they move here / into lint rules).
- **`@modeler/lsp` (changed):** depends on `lint` + `format`; deletes `Validator` calls and the
  in-host formatter/quick-fix code, re-wiring to the new packages.

---

## 3. Component view

### 3.1 `@modeler/parser` — CST + trivia

```
parseString(src, uri)
   │  builds CommonTokenStream (walker.ts:162) — comments now on HIDDEN channel
   ▼
ASTWalker ──► Document (unchanged semantic shape)
   │
   └─► TriviaAttacher ──► reads hidden-channel tokens from the token stream,
                          attaches leading/trailing Trivia[] to AST nodes
```

New files: `packages/parser/src/cst/trivia.ts` (types), `cst/attach.ts` (attacher). The token
stream is already constructed in `walker.ts`; the attacher consumes it after the walk.

### 3.2 `@modeler/format` — formatter

```
format(src, config)
   │  parseString → ast (+trivia)
   ▼
printer (CST/AST walk) ──► Doc IR (ir.ts) ──► render(width) ──► string
        ▲
        └── trivia-aware: emits leading/trailing comments around nodes
```

Extracted from `packages/lsp/src/formatter/{format,ir,render}.ts`. The Doc IR and canonical
property ordering are reused as-is; the printer gains comment emission.

### 3.3 `@modeler/lint` — registry / runner / config / suppression / fix

```
                       ┌─────────── registry.ts (RULES) ───────────┐
                       │ rules/{structure,references,imports,...}   │
                       └───────────────────┬────────────────────────┘
loadLintConfig(.ttrlint.toml) ─► config    │
buildSuppressionIndex(cst trivia) ─► supp.  ▼
                          runner.ts: for each enabled rule → check(ctx) → report
                                     → filter(suppression) → stamp severity(config)
                                     → Diagnostic[] (+ optional Fix)
                                                   │
                          fix.ts: collect safe fixes → @modeler/edit → WorkspaceEdit
```

### 3.4 `@modeler/lsp` — thin host (after rewire)

```
publishDiagnostics ─► lint.lintDocument + cached lint.lintProject
onDocumentFormatting ─► format.format
onCodeAction ─► lint rule fixes (safe + suggestion)
watch .ttrlint.toml ─► re-lint open docs
```

---

## 4. Existing-code inventory (what we extract / extend)

Discovered in the current tree; the plan **builds on these**, it does not recreate them.

| Existing | Location | Disposition |
|---|---|---|
| `Validator` (11 `validate*` methods, 26 codes) | `packages/semantics/src/validator.ts` | Port each check to a `Rule` in `@modeler/lint`; delete the class at end of P2. |
| `DiagnosticCode` enum | `packages/parser/src/diagnostics.ts` | Keep; rules map id→code. |
| Doc-IR formatter (`formatDocument`, `DEFAULT_FORMAT_CONFIG`, `FormatConfig`) | `packages/lsp/src/formatter/format.ts` | **Extract** to `@modeler/format`. Currently comment-unaware (slices verbatim spans, fixed canonical order, idempotent). P1 makes it trivia-aware. |
| Doc IR + renderer | `packages/lsp/src/formatter/ir.ts`, `render.ts` | Extract with the formatter. |
| Formatter tests (incl. idempotency) | `packages/lsp/src/__tests__/formatter*.test.ts` | Move to `@modeler/format`; extend with comment cases. |
| Autofix quick-fixes: `quickFixUnusedImport`, `quickFixMissingPackageDeclaration`, `quickFixPackageDeclarationMismatch`, `quickFixUnimportedReference`, `refactorExtractDefToNewFile` | `packages/lsp/src/code-actions.ts` | Re-home as `Rule.fix` (safe) / suggestions in `@modeler/lint`; `onCodeAction` rewires to rule fixes. |
| `onDocumentFormatting`, `onCodeAction` handlers; `loadFormatConfig` | `packages/lsp/src/server.ts:931,954,1008` | Re-wire to new packages; keep the "don't format on parse error" guard. |
| LSP already advertises `documentFormattingProvider`, `codeActionProvider` | `server.ts:366-367` | No capability change needed. |
| Validator tests | `packages/semantics/src/__tests__/{validator,duplicate-mapping,diagnostics-v1.1}.test.ts` | Port to per-rule tests in `@modeler/lint`; keep as golden corpus. |
| Manifest `[lint]` (`strict`, `requireDescriptions`) | `packages/semantics/src/manifest.ts` | Keep returning `lint:{...}`; lint reads as fallback (back-compat, §6.4 design). |
| TextMate comment scopes | `packages/vscode-ext/syntaxes/ttr.tmLanguage.json` | Already highlights comments; unaffected by the channel change. |

The key insight from the inventory: the existing formatter proves the Doc-IR approach works and is
idempotent, **but it drops comments** because they are skipped at lex time. P0 (CST/trivia) is the
foundation that lets P1 preserve them; everything downstream already has a working skeleton.

---

## 5. Flow diagrams

### 5.1 Live editing (LSP)

```
didChange ─► publishDiagnostics
              ├─ parseString → ast (+trivia)
              ├─ lintDocument(uri, ast, cst, deps, config)
              │     └─ rules → reports → suppression filter → severity stamp
              ├─ lintProject (cached) filtered to uri
              └─ sendDiagnostics
format-on-save ─► onDocumentFormatting ─► format.format(src, cfg) ─► TextEdit[]
lightbulb ─► onCodeAction ─► for each diag with fix → CodeAction(edit)
edit .ttrlint.toml ─► invalidate config ─► re-lint open docs
```

### 5.2 CI (CLI)

```
ttr lint <root> [--fix]            ttr fmt <path> [--check|--write]
   ├─ load project + config           ├─ for each .ttr/.ttrg: format(src)
   ├─ lintProject + lintDocument*     ├─ --check: diff → exit 1 if any unformatted
   ├─ --fix: apply safe fixes to      └─ --write: rewrite in place
   │         fixpoint, re-report
   ├─ print (pretty|json)
   └─ exit 0 (clean) / 1 (findings ≥ fail-on) / 2 (operational)
```

---

## 6. Cross-cutting constraints (from CLAUDE.md, enforced in tasks)

- **Text is canonical.** Autofix produces `WorkspaceEdit`s the host applies; the LSP re-parses. No
  independent model state.
- **One LSP across hosts.** No per-host language logic; formatter/linter live in their packages.
- **Parser stays mechanical.** The CST/trivia work is mechanical token-stream reading; no
  resolution logic enters `parser`.
- **SourceLocation is ANTLR-style** (1-indexed line, 0-indexed col, exclusive `offsetEnd`;
  multi-token spans use `stopToken.column + stopTokenLength`). Trivia spans obey the same rules;
  re-check `makeSourceLocation` in `walker.ts` when touching it.
- **`vscode-languageserver` deep import** (`vscode-languageserver/lib/node/main.js`) in source;
  tests may use `/node`.
- **ESLint forbids `any`** outside `generated/**`.
- **Grammar is vendored to ai-platform.** The `skip`→`channel(HIDDEN)` change is parse-equivalent;
  still requires regenerate + `sync-to-ai-platform.sh` (Appendix A of design).

---

## 7. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Trivia attachment drops or misplaces comments | Round-trip identity test (`print∘parse === src`) gates P0 before any formatting logic. |
| Formatter regression during extraction | Move existing tests first (TDD); they must stay green through the move, before comment-awareness is added. |
| Grammar change breaks ai-platform | Change is parse-equivalent; verify with `check-sync.sh` and a parser round-trip test; coordinate the sync commit. |
| Autofix edits overlap / corrupt text | Fixes are minimal ranges; runner merges non-overlapping only, drops overlaps, iterates to fixpoint (eslint model). Each fix has a unit test asserting the resulting text. |
| Behaviour drift when replacing `Validator` | Golden corpus test: lint output byte-identical to `Validator` under `recommended` preset before deleting the class. |
| `endColumn` span bug recurs (CLAUDE.md history) | Trivia/fix span tests assert exact ranges, not lengths. |
