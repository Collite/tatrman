# Vendored SQL grammars — provenance

Source: [`antlr/grammars-v4`](https://github.com/antlr/grammars-v4)
**Pinned commit:** `923a1a90f3b9e28c8ac08e170e582ceb15d6f0a9` (master, fetched 2026-06-11)

Downloaded **verbatim** by [`../scripts/vendor-grammars.sh`](../scripts/vendor-grammars.sh).
No edits are made to the files here — lazy patching (DESIGN §12.7) happens only
when a concrete corpus query fails, and is then tracked as a patch. The
canonical provenance/notes for the feature are in
[`docs/features/embedded-sql/PINNED.md`](../../../docs/features/embedded-sql/PINNED.md).

## Files

| Dialect | File | Upstream path |
|---|---|---|
| tsql | `tsql/TSqlLexer.g4` | `sql/tsql/TSqlLexer.g4` |
| tsql | `tsql/TSqlParser.g4` | `sql/tsql/TSqlParser.g4` |
| postgresql | `postgresql/PostgreSQLLexer.g4` | `sql/postgresql/PostgreSQLLexer.g4` |
| postgresql | `postgresql/PostgreSQLParser.g4` | `sql/postgresql/PostgreSQLParser.g4` |
| postgresql | `postgresql/PostgreSQLLexerBase.ts` | `sql/postgresql/Antlr4ng/PostgreSQLLexerBase.ts` |
| postgresql | `postgresql/PostgreSQLParserBase.ts` | `sql/postgresql/Antlr4ng/PostgreSQLParserBase.ts` |
| postgresql | `postgresql/transformGrammar.py` | `sql/postgresql/Antlr4ng/transformGrammar.py` (reference) |

## Notes

- **T-SQL** is self-contained: `TSqlLexer.g4` declares `options { caseInsensitive = true; }`
  and has no `superClass`/base-class dependency.
- **PostgreSQL** declares `superClass = PostgreSQL{Lexer,Parser}Base` and uses
  semantic predicates implemented in the base classes. The grammars carry the
  markers `// Insert here @header for C++ {lexer,parser}.`; grammars-v4's
  `Antlr4ng/transformGrammar.py` swaps each for an `@header` import of the
  antlr4ng base class. [`../scripts/generate.sh`](../scripts/generate.sh)
  replicates that injection with `sed` against a **throwaway** copy (so these
  files stay pristine) and copies the base `.ts` next to the generated output
  (the `@header` import is relative: `./PostgreSQL*Base.js`).

`transformGrammar.py` is vendored for reference only; `generate.sh` does the
injection itself and does not run it.
