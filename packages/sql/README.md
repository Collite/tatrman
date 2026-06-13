# @modeler/sql

Embedded-SQL tooling for the TTR modeling language (embedded-sql feature,
Phase 2+). Wraps vendored ANTLR SQL grammars (T-SQL, PostgreSQL) generated to
antlr4ng TypeScript, plus the `maskPlaceholders` pre-pass (2.2), the lexer
service + source map (2.3), and — desktop-only — the parsers and per-dialect
ref adapters (Phase 3).

## Entry points (bundle split, E11 / SPIKE S0.3)

- **`@modeler/sql/lexers`** — dialect **lexers** + `maskPlaceholders`. Browser-
  safe (Web Worker / Designer): ~+155 KB gz for both lexers.
- **`@modeler/sql`** — the above **plus the parsers** + adapters. Desktop (Node)
  only: the parsers add ~+839 KB gz and must not reach the Worker bundle.

## Grammars

Vendored verbatim from `antlr/grammars-v4` at a pinned SHA — see
[`vendor/PINNED.md`](vendor/PINNED.md). Regenerate with:

```bash
pnpm --filter @modeler/sql generate   # prebuild runs this automatically
pnpm --filter @modeler/sql vendor      # re-download at the pinned SHA (rare)
```

`src/generated/**` is **gitignored** — regenerated at build time from `vendor/`,
exactly like `@modeler/parser`.

## Known generation warnings (do not re-triage)

`generate.sh` emits **two benign** PostgreSQL lexer warnings (SPIKE S0.1):

```
warning(79): PostgreSQLLexer.g4: non-fragment lexer rule
  AfterEscapeStringConstantMode_NotContinued can match the empty string
warning(79): PostgreSQLLexer.g4: non-fragment lexer rule
  AfterEscapeStringConstantWithNewlineMode_NotContinued can match the empty string
```

These are upstream artefacts of escape-string-constant handling; the lexer
tokenises correctly regardless. T-SQL generates with **zero** warnings.
