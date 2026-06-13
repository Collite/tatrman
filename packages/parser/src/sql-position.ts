import type { TaggedBlockValue } from './ast.js';

/**
 * Maps a position inside a tagged block's extracted `value` (e.g. an ANTLR SQL
 * token) back to the enclosing `.ttr` file (embedded-sql DESIGN §8).
 *
 * The block's body always starts on its own line (the grammar requires a
 * newline after the tag) and `dedent` strips one uniform `indentWidth` from
 * every line, so the file column is simply the indent re-added — a single
 * additive shift, not a ragged map. This is why DESIGN §8's line-0
 * `valueSource.startColumn` term is 0 for tagged blocks and is omitted here
 * (folding it in would double-count, since `valueSource.column` already equals
 * `indentWidth`). If a future un-formatted file ever has ragged indent, this is
 * the single place to swap in a per-line `indentWidth[]`.
 *
 * Coordinates follow the ANTLR/`SourceLocation` convention: `line` 1-indexed,
 * `column` 0-indexed. ANTLR tokens use the same (`tok.line` 1-indexed,
 * `tok.column` 0-indexed), so they pass through directly.
 */
export interface SqlTokenPos {
  /** 1-indexed line within the SQL `value` (ANTLR `token.line`). */
  line: number;
  /** 0-indexed column within the SQL `value` (ANTLR `token.column`). */
  column: number;
}

export function sqlPosToFile(
  tok: SqlTokenPos,
  block: Pick<TaggedBlockValue, 'valueSource' | 'indentWidth'>,
): { line: number; column: number } {
  const sqlLine = tok.line - 1; // ANTLR line is 1-indexed; DESIGN §8 sqlLine is 0-indexed
  return {
    line: block.valueSource.line + sqlLine,
    column: block.indentWidth + tok.column,
  };
}
