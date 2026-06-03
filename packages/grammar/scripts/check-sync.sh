#!/bin/bash
set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <ai-platform-path>"
  exit 1
fi

GRAMMAR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_FILE="$GRAMMAR_DIR/../src/TTR.g4"
REMOTE_FILE="$1/shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated/TTR.g4"

if [ ! -f "$REMOTE_FILE" ]; then
  echo "Remote grammar not found: $REMOTE_FILE"
  exit 1
fi

LOCAL_HASH=$(md5 -q "$LOCAL_FILE" 2>/dev/null || md5sum "$LOCAL_FILE" 2>/dev/null | awk '{print $1}')
REMOTE_HASH=$(md5 -q "$REMOTE_FILE" 2>/dev/null || md5sum "$REMOTE_FILE" 2>/dev/null | awk '{print $1}')

if [ "$LOCAL_HASH" != "$REMOTE_HASH" ]; then
  echo "Grammar mismatch detected!"
  echo "Local:  $LOCAL_HASH"
  echo "Remote: $REMOTE_HASH"
  diff "$LOCAL_FILE" "$REMOTE_FILE" || true
  exit 1
fi

echo "Grammar files match."