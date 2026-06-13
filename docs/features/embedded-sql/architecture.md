# Embedded SQL — architecture

**Status:** Architecture v2, 2026-06-11 (post-spike). Normative companion to
[`contracts.md`](contracts.md) and [`plan.md`](plan.md). The design rationale and
all decisions live in
[`embedded-language-blocks.md`](embedded-language-blocks.md) (referenced here as
**DESIGN**); the Phase 0 findings that shaped this build view are in
[`spike-report.md`](spike-report.md) (**SPIKE**). This document is the
build-facing view.

## 1. Goal

Syntactically (and, on desktop, semantically) analyse SQL embedded in TTR
`query.sourceText` and `view.definitionSql`: syntax highlighting in all hosts,
and table/column resolution against the TTR `db` schema with go-to-definition,
hover, and diagnostics on the desktop hosts.

The runtime target of every query is the **real database** it will execute
against (SQL Server, PostgreSQL, DuckDB) — never Calcite — so the editor
approximates each engine's own grammar. Imperfect parse/lex is acceptable
(DESIGN §12.1).

## 2. Tech stack

| Concern | Choice | Notes |
|---|---|---|
| TTR grammar/parser | ANTLR via `antlr-ng` → `antlr4ng` (TS) | existing; `packages/parser` |
| Tagged-block carrier | `"""<tag>` triple-string (DESIGN §2) | new lexer token + `embeddedBlock` rule |
| SQL grammars | ANTLR grammars-v4 `tsql`, `postgresql`; DuckDB = postgres-derived | vendored-pinned `@923a1a9`, lazily patched (SPIKE S0.1; DESIGN §12.1/§12.7) |
| SQL parser runtime | `antlr4ng` (same as TTR) | generated lexers/parsers per dialect; case-insensitivity via native `caseInsensitive` option (SPIKE S0.1) |
| SQL parser approach | **lexer-first**; best-effort parser later | DESIGN §12.3 |
| Placeholder carrier | `maskPlaceholders` span-preserving pre-pass | **required**: TTR `{param}` placeholders break raw SQL lexing (SPIKE S0.2) |
| Multi-dialect normalisation | `SqlRefModel` extraction model | DESIGN §12.4; not full-AST unification |
| SQL package | new `@modeler/sql` | vendored grammars + generated lexers/parsers + mask + lexer/parser services + adapters; subpath exports isolate lexer-only for the browser |
| Kotlin side | `packages/kotlin/ttr-parser` | tagged-block value contract only — no SQL analysis |
| Project config | `modeler.toml` | namespace map + per-dialect defaults (contracts §5) |

## 3. Component view

```
                         .ttr source
                              │
                ┌─────────────▼─────────────┐
                │  @modeler/parser (TTR)     │   TAGGED_BLOCK_LITERAL token
                │  antlr4ng TTRLexer/Parser  │   → embeddedBlock production
                │  walker → TaggedBlockValue │   { tag, language, dialect,
                └─────────────┬─────────────┘     value, tagSource, valueSource,
                              │                    indentWidth }
        ┌─────────────────────┼─────────────────────────┐
        │ (highlight: all hosts)         (semantics: desktop only)
        ▼                                                ▼
┌───────────────────────┐                  ┌────────────────────────────────┐
│ SQL LEXER (per dialect)│                  │ SQL PARSER (per dialect)        │
│ antlr4ng TSqlLexer /   │                  │ antlr4ng TSqlParser / PgParser  │
│ PostgreSqlLexer        │                  │ error-tolerant (default strategy)│
│ → token stream         │                  │ → parse tree (may hold errors)  │
└───────────┬───────────┘                  └───────────────┬────────────────┘
            │ map via source map (§8 DESIGN)               │ per-dialect adapter
            ▼                                              ▼
┌───────────────────────┐                  ┌────────────────────────────────┐
│ LSP semantic tokens    │                  │ SqlRefModel (dialect-agnostic)  │
│ (modeler/* → host)     │                  │ tables/columns/ctes/params/scope│
└───────────────────────┘                  └───────────────┬────────────────┘
                                                            │ resolver
                                            ┌───────────────▼────────────────┐
                                            │ TTR db symbol table (semantics) │
                                            │ + modeler.toml namespace map    │
                                            │ → diagnostics, hover, go-to-def │
                                            └─────────────────────────────────┘
```

All new logic lives in `parser` / `semantics` / `lsp` — hosts stay thin (the
"one LSP across hosts" invariant). The Designer and VS Code/IntelliJ get features
purely by what the shared LSP emits.

## 4. Pipeline (request flow)

1. **Parse `.ttr`.** `@modeler/parser` produces `TaggedBlockValue` for each
   `sourceText`/`definitionSql` (tag-peel → dedent → trailing-newline strip;
   DESIGN §4). Untagged triple-strings stay raw text.
2. **Select dialect** from the tag via the registry (contracts §3); bare `sql`
   → `modeler.toml` default.
3. **Mask placeholders.** Run `maskPlaceholders` over `TaggedBlockValue.value`:
   blank each TTR `{param}` placeholder's two brace chars to spaces (length
   preserved → the §8 uniform source map is untouched) and return the masked
   placeholder spans. **Required** — without it T-SQL raw-lexes at only ~41%
   (every `{`/`}` is a lex error; SPIKE S0.2). The masked spans feed both the
   `parameter` semantic-token colouring (step 4) and the Phase 3 `parameters`
   cross-check.
4. **Highlight (all hosts).** Run the dialect's SQL *lexer* over the masked text;
   map each token's span back into the `.ttr` file (base `valueSource` + uniform
   `indentWidth`, DESIGN §8); emit LSP semantic tokens, re-colouring the masked
   spans as `parameter`.
5. **Semantics (desktop only).** Run the dialect's SQL *parser* (error-tolerant)
   over the same masked text; the per-dialect adapter walks the tree into
   `SqlRefModel`; the resolver maps its table/column refs onto TTR `db` symbols
   (via the namespace map + dialect identifier folding) and emits diagnostics,
   hover, go-to-definition, etc.

## 5. Host split (bundle-driven, DESIGN §12.5)

| Host | LSP entry | SQL capability |
|---|---|---|
| VS Code / IntelliJ | `server-stdio.ts` (Node) | lexers + parsers + best-effort semantics |
| Designer | `server-browser.ts` (Worker) | **lexers only** (highlighting) — parsers not bundled (size) |

The generated SQL **lexers** are portable TS and bundle into the browser Worker;
the **parsers** are not, to keep the Worker small. "Browser is worse" =
highlighting without semantics, by deliberate choice.

**Measured (SPIKE S0.3, gzipped, marginal over the shared `antlr4ng` runtime):**
both lexers **+155 KB** (tsql 107 + postgres 48), both parsers **+839 KB**
(~5.4× the lexers). Today's Worker is 316 KB gz; **+ both lexers → ~471 KB
(+49%)**, accepted; + parsers → ~1,155 KB (+265%), rejected for the browser.
DuckDB reuses the Postgres lexer, so per-dialect growth is sub-linear. The
browser bundles `@modeler/sql`'s **lexer-only subpath export**; the desktop
imports the full package.

## 6. Module ownership

| Module | Adds |
|---|---|
| `@modeler/grammar` | `TAGGED_BLOCK_LITERAL` + `embeddedBlock`; tag registry |
| `@modeler/parser` | `TaggedBlockValue` walker; source map helper |
| **`@modeler/sql`** *(new)* | vendored SQL grammars (pinned `@923a1a9`) + generated lexers/parsers; `maskPlaceholders`; SQL lexer/parser services; `SqlRefModel` per-dialect adapters. **Subpath exports:** `@modeler/sql/lexers` (browser) vs full (desktop). No TTR-symbol knowledge. |
| `@modeler/semantics` | SQL resolver over `SqlRefModel`; identifier folding; diagnostics; `modeler.toml` SQL config; `parameters` cross-check |
| `@modeler/lsp` | embedded semantic tokens; SQL hover/def/refs/completion (desktop) |
| `@modeler/vscode-ext` | optional TextMate fallback; semantic-token legend registration |
| `packages/kotlin/ttr-parser` | `TaggedBlockValue` model + value contract only (no SQL analysis) |

### 6.1 SQL grammar generation (SPIKE S0.1)

Vendored verbatim from `antlr/grammars-v4 @923a1a9` (see
[`PINNED.md`](PINNED.md)); generated with `antlr-ng` (`-Dlanguage=TypeScript`).
T-SQL needs no base class. **PostgreSQL** declares
`superClass = PostgreSQLParserBase` / `PostgreSQLLexerBase` with semantic
predicates; grammars-v4 ships an `Antlr4ng/` target directory with base classes
written for **this exact runtime** plus a `transformGrammar.py` that injects the
`@header` import. The generation script replicates that injection against a
throwaway copy (vendor stays pristine) and copies the base `.ts` beside the
output — **no hand-forking.** Two benign empty-string lexer warnings on the
Postgres escape-string rules are expected and harmless.

## 7. Invariants honoured

- **Text canonical** — SQL is read-only analysis over the parsed value; no model
  state owned outside text.
- **One LSP across hosts** — all SQL features flow from the shared server; host
  differences are bundle/config only.
- **Parser stays mechanical** — `@modeler/parser` only carries the tagged-block
  value + raw SQL token/parse trees; resolution lives in `@modeler/semantics`.
- **SourceLocation discipline** — the SQL source map is a uniform additive shift
  (DESIGN §8), not a ragged segment map; conformance-tested.
- **Cross-repo** — only the tagged-block value contract crosses to ai-platform
  (DESIGN §10); SQL analysis is editor-only and never ships to ai-platform.
