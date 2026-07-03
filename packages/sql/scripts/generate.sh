#!/bin/bash
# Generates antlr4ng TypeScript lexers/parsers from the pinned vendor/ grammars
# into src/generated/<dialect>/ (gitignored — regenerated at build time, like
# @modeler/parser). vendor/ stays pristine: the Postgres @header base-class
# import is injected into a THROWAWAY copy (replicating grammars-v4's
# Antlr4ng/transformGrammar.py), never into vendor/. SPIKE S0.1.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG="$SCRIPT_DIR/.."
VENDOR="$PKG/vendor"
OUT="$PKG/src/generated"
ANTLR_NG=(npx antlr-ng -l -v -Dlanguage=TypeScript)

gen() { # <dialect> <out-subdir> <work-dir> <Lexer.g4> <Parser.g4>
  local dialect="$1" out="$2" work="$3" lexer="$4" parser="$5"
  echo "Generating $dialect → src/generated/$dialect"
  rm -rf "$out"
  mkdir -p "$out"
  "${ANTLR_NG[@]}" -o "$out" --lib "$work" -- "$lexer" "$parser"
}

# ---- T-SQL — self-contained (no base class, caseInsensitive lexer option) ----
gen tsql "$OUT/tsql" "$VENDOR/tsql" \
  "$VENDOR/tsql/TSqlLexer.g4" "$VENDOR/tsql/TSqlParser.g4"

# ---- PostgreSQL — needs the @header base-class import injected ----
# Replicate transformGrammar.py against a throwaway copy so vendor/ stays clean.
PG_WORK="$(mktemp -d)"
trap 'rm -rf "$PG_WORK"' EXIT
cp "$VENDOR/postgresql/PostgreSQLLexer.g4" "$VENDOR/postgresql/PostgreSQLParser.g4" "$PG_WORK/"
# The .g4 carry the markers `// Insert here @header for C++ {lexer,parser}.`;
# swap each for the antlr4ng base-class import (matches transformGrammar.py).
sed -i.bak 's#// Insert here @header for C++ lexer\.#@header {import { PostgreSQLLexerBase } from "./PostgreSQLLexerBase.js"}#' "$PG_WORK/PostgreSQLLexer.g4"
sed -i.bak 's#// Insert here @header for C++ parser\.#@header {import { PostgreSQLParserBase } from "./PostgreSQLParserBase.js"}#' "$PG_WORK/PostgreSQLParser.g4"
rm -f "$PG_WORK"/*.bak

gen postgresql "$OUT/postgresql" "$PG_WORK" \
  "$PG_WORK/PostgreSQLLexer.g4" "$PG_WORK/PostgreSQLParser.g4"
# The generated parser/lexer @header-import `./PostgreSQL*Base.js`; ship the base
# classes beside the generated output so the import resolves (tsc + runtime).
cp "$VENDOR/postgresql/PostgreSQLLexerBase.ts" "$VENDOR/postgresql/PostgreSQLParserBase.ts" "$OUT/postgresql/"

echo "Generated lexers/parsers to src/generated/{tsql,postgresql}"
