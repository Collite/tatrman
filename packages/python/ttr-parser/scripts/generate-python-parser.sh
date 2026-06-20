#!/usr/bin/env bash
# Generate the ANTLR Python3 parser/lexer/visitor/listener from the canonical
# `packages/grammar/src/TTR.g4` into `src/ttr_parser/_generated/`.
#
# Mirrors `packages/grammar/scripts/generate-typescript-parser.sh` for the Python
# target (D1: reference ANTLR tool jar 4.13.2, NOT `antlr-ng` which is TS-only).
# Output is gitignored (D4) and bundled into the wheel via Hatchling
# `force-include`.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRAMMAR="$HERE/../../grammar/src/TTR.g4"
OUT="$HERE/src/ttr_parser/_generated"
ANTLR_VERSION="4.13.2"
JAR="${ANTLR_JAR:-$HOME/.cache/antlr/antlr-${ANTLR_VERSION}-complete.jar}"

if [ ! -f "$GRAMMAR" ]; then
  echo "Grammar file not found: $GRAMMAR" >&2
  exit 1
fi

mkdir -p "$(dirname "$JAR")" "$OUT"

if [ ! -f "$JAR" ]; then
  echo "Downloading ANTLR $ANTLR_VERSION reference jar to $JAR"
  curl -fsSL -o "$JAR" \
    "https://www.antlr.org/download/antlr-${ANTLR_VERSION}-complete.jar"
fi

# Clear any stale generated sources so a removed rule does not leave a ghost.
rm -f "$OUT"/TTRLexer.py "$OUT"/TTRParser.py "$OUT"/TTRListener.py \
      "$OUT"/TTRVisitor.py "$OUT"/*.tokens "$OUT"/*.interp 2>/dev/null || true

# `-visitor` for parity with TS/Kotlin generation args; the walker uses direct
# context traversal (top-down), so the visitor base is generated but unused.
# `-long-messages` for readable syntax errors during development.
java -jar "$JAR" -Dlanguage=Python3 -visitor -long-messages \
  -o "$OUT" "$GRAMMAR"

touch "$OUT/__init__.py"

echo "ANTLR Python parser generated to $OUT"

# Copy the canonical stock CNC vocab into the package as build data (D4: single
# source of truth in @modeler/semantics, no committed duplicate — gitignored and
# shipped in the wheel via the `artifacts` pattern). StockLoader reads it via
# importlib.resources at runtime, so the installed wheel needs no JVM.
STOCK_SRC="$HERE/../../semantics/src/stock/cnc-roles.ttr"
STOCK_DST="$HERE/src/ttr_parser/semantics/stock/cnc-roles.ttr"
if [ ! -f "$STOCK_SRC" ]; then
  echo "Stock vocab not found: $STOCK_SRC" >&2
  exit 1
fi
mkdir -p "$(dirname "$STOCK_DST")"
cp "$STOCK_SRC" "$STOCK_DST"
echo "Stock CNC vocab copied to $STOCK_DST"
