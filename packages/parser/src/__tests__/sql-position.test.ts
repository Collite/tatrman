import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import { sqlPosToFile } from '../sql-position.js';
import type { QueryDef, TaggedBlockValue } from '../ast.js';

/**
 * embedded-sql §8 source map: a SQL token at (line, col) inside a tagged block's
 * `value` maps back to the `.ttr` file via a uniform additive shift
 * (valueSource.line + uniform indentWidth). Coordinates are ANTLR/SourceLocation
 * convention: line 1-indexed, column 0-indexed.
 */
function blockOf(src: string): TaggedBlockValue {
  const r = parseString(src);
  expect(r.errors.filter((e) => e.severity === 'error')).toHaveLength(0);
  const q = r.ast!.definitions[0] as QueryDef;
  return q.sourceText as TaggedBlockValue;
}

describe('sqlPosToFile (§8 source map)', () => {
  it('maps a line-1 token (uniform indent re-added)', () => {
    // `  SELECT u.email FROM users u` indented 2 → value dedents to col 0.
    const block = blockOf(
      'def query q {\n  sourceText: """sql\n  SELECT u.email FROM users u\n  """\n}',
    );
    expect(block.indentWidth).toBe(2);
    // `email` is at value line 1, col 9 (S=0 … u=7 .=8 e=9). File line is the
    // body's first line; file col = indentWidth(2) + 9 = 11.
    const pos = sqlPosToFile({ line: 1, column: 9 }, block);
    expect(pos).toEqual({ line: block.valueSource.line, column: 11 });
  });

  it('maps a line-2 token (column from 0, same uniform indent)', () => {
    const block = blockOf(
      'def query q {\n  sourceText: """sql\n  SELECT 1\n  FROM t\n  """\n}',
    );
    expect(block.indentWidth).toBe(2);
    // `FROM` is on value line 2, col 0 → file col = indentWidth(2) + 0 = 2,
    // file line = valueSource.line + 1.
    const pos = sqlPosToFile({ line: 2, column: 0 }, block);
    expect(pos).toEqual({ line: block.valueSource.line + 1, column: 2 });
    // `t` on value line 2 col 5 → file col 7.
    expect(sqlPosToFile({ line: 2, column: 5 }, block).column).toBe(7);
  });

  it('zero indent: file column equals the token column', () => {
    const block = blockOf('def query q {\n  sourceText: """sql\nSELECT 1\n"""\n}');
    expect(block.indentWidth).toBe(0);
    expect(sqlPosToFile({ line: 1, column: 7 }, block)).toEqual({
      line: block.valueSource.line,
      column: 7,
    });
  });
});
