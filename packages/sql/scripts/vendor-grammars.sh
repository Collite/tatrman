#!/bin/bash
# Downloads the pinned grammars-v4 SQL grammars verbatim into vendor/.
# Provenance + pinned SHA live in packages/sql/vendor/PINNED.md. Files are NOT
# edited here — lazy patching (DESIGN §12.7) is tracked separately. Re-run only
# to re-vendor at a new pin (then bump PINNED.md).
set -euo pipefail

SHA=923a1a90f3b9e28c8ac08e170e582ceb15d6f0a9
RAW="https://raw.githubusercontent.com/antlr/grammars-v4/$SHA/sql"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR="$SCRIPT_DIR/../vendor"

fetch() { # <url> <dest>
  echo "  $2"
  curl -fsSL "$1" -o "$VENDOR/$2"
}

echo "Vendoring grammars-v4 @${SHA:0:7} → vendor/"

# T-SQL — self-contained (caseInsensitive lexer option, no base class).
fetch "$RAW/tsql/TSqlLexer.g4"  "tsql/TSqlLexer.g4"
fetch "$RAW/tsql/TSqlParser.g4" "tsql/TSqlParser.g4"

# PostgreSQL — needs the Antlr4ng/ base classes + transformGrammar.py (the
# @header injection of those base-class imports; see generate.sh + PINNED.md).
fetch "$RAW/postgresql/PostgreSQLLexer.g4"  "postgresql/PostgreSQLLexer.g4"
fetch "$RAW/postgresql/PostgreSQLParser.g4" "postgresql/PostgreSQLParser.g4"
fetch "$RAW/postgresql/Antlr4ng/PostgreSQLLexerBase.ts"  "postgresql/PostgreSQLLexerBase.ts"
fetch "$RAW/postgresql/Antlr4ng/PostgreSQLParserBase.ts" "postgresql/PostgreSQLParserBase.ts"
fetch "$RAW/postgresql/Antlr4ng/transformGrammar.py"     "postgresql/transformGrammar.py"

echo "Done. Vendored files are committed as-is — do not edit (see vendor/PINNED.md)."
