# Vendored SQL grammars — provenance

Source: [`antlr/grammars-v4`](https://github.com/antlr/grammars-v4)
**Pinned commit:** `923a1a90f3b9e28c8ac08e170e582ceb15d6f0a9` (master, fetched 2026-06-11)

Files are downloaded **verbatim** by `scripts/vendor-grammars.sh`. No edits are
made to the `.g4` files in `vendor/` — lazy patching (DESIGN §12.7) happens only
when a concrete corpus query fails, and is then tracked as a patch.

## Files

| Dialect | File | Upstream path |
|---|---|---|
| tsql | `tsql/TSqlLexer.g4` | `sql/tsql/TSqlLexer.g4` |
| tsql | `tsql/TSqlParser.g4` | `sql/tsql/TSqlParser.g4` |
| postgresql | `postgresql/PostgreSQLLexer.g4` | `sql/postgresql/PostgreSQLLexer.g4` |
| postgresql | `postgresql/PostgreSQLParser.g4` | `sql/postgresql/PostgreSQLParser.g4` |
| postgresql | `postgresql/PostgreSQLLexerBase.ts` | `sql/postgresql/Antlr4ng/PostgreSQLLexerBase.ts` |
| postgresql | `postgresql/PostgreSQLParserBase.ts` | `sql/postgresql/Antlr4ng/PostgreSQLParserBase.ts` |

## Notes

- **T-SQL** is self-contained: `TSqlLexer.g4` declares `options { caseInsensitive = true; }`
  and has no `superClass`/base-class dependency.
- **PostgreSQL** declares `superClass = PostgreSQL{Lexer,Parser}Base` and uses
  semantic predicates implemented in the base classes. grammars-v4 ships an
  `Antlr4ng/` target directory with base classes written **for the `antlr4ng`
  runtime** (exactly the runtime this repo uses) plus `transformGrammar.py`,
  which injects an `@header` import of those base classes into the `.g4` files.
  `scripts/generate.sh` replicates that injection with `sed` against a throwaway
  copy so `vendor/` stays pristine, and copies the base `.ts` files next to the
  generated output (the `@header` import is relative: `./PostgreSQLLexerBase.js`).

## Generation command

```
npx antlr-ng -l -v -Dlanguage=TypeScript -o <out> --lib <griddir> -- <Lexer.g4> <Parser.g4>
```

Run via `pnpm --filter @modeler/sql-spike generate` (wraps `scripts/generate.sh`).
