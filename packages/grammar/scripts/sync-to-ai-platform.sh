#!/bin/bash
set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <ai-platform-path>"
  echo "Copies TTR.g4 to ai-platform/shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated/"
  exit 1
fi

GRAMMAR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRAMMAR_FILE="$GRAMMAR_DIR/../src/TTR.g4"
TARGET_DIR="$1/shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated"
TARGET_FILE="$TARGET_DIR/TTR.g4"

if [ ! -f "$GRAMMAR_FILE" ]; then
  echo "Grammar file not found: $GRAMMAR_FILE"
  exit 1
fi

COMMIT_HASH=$(cd "$GRAMMAR_DIR/../.." && git rev-parse HEAD 2>/dev/null || echo "unknown")
HEADER_COMMENT="// Vendored from modeler@$COMMIT_HASH — DO NOT EDIT DIRECTLY"

mkdir -p "$TARGET_DIR"

cat > "$TARGET_FILE" << 'HEADER'
HEADER
echo "$HEADER_COMMENT" >> "$TARGET_FILE"
echo "" >> "$TARGET_FILE"
cat "$GRAMMAR_FILE" >> "$TARGET_FILE"

echo "Synced TTR.g4 to $TARGET_FILE"