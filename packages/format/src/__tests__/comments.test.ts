import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { format } from '../index.js';

/** Count `//` and `/*` comment openers, ignoring those inside string literals is
 *  unnecessary here — the fixtures contain no `//`/`/*` inside strings. */
function commentCount(src: string): number {
  return (src.match(/\/\//g)?.length ?? 0) + (src.match(/\/\*/g)?.length ?? 0);
}

/** Deep-strip `source`/trivia so two ASTs can be compared for semantic equality. */
function stripMeta(value: unknown): unknown {
  if (value === null || typeof value !== 'object') return value;
  if (Array.isArray(value)) return value.map(stripMeta);
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
    if (k === 'source' || k === 'leadingTrivia' || k === 'trailingTrivia') continue;
    out[k] = stripMeta(v);
  }
  return out;
}

describe('formatter — comment preservation', () => {
  it('keeps a leading line comment above its def', () => {
    const src = `schema db namespace dbo

// the users table
def table users {
    columns: [
        def column id { type: int }
    ]
}
`;
    const out = format(src, 'a.ttrm');
    expect(out).toContain('// the users table\ndef table users {');
  });

  it('keeps a trailing comment on a property line', () => {
    const src = `def model m {
    description: "hello" // greeting
}
`;
    const out = format(src, 'b.ttrm');
    expect(out).toContain('description: "hello" // greeting');
  });

  it('keeps a block comment', () => {
    const src = `/* note */
def model m {
    description: "hi"
}
`;
    const out = format(src, 'c.ttrm');
    expect(out).toContain('/* note */');
  });

  it('keeps a trailing comment on a list item after the comma', () => {
    const src = `def table t {
    columns: [
        def column id { type: int }, // pk
        def column name { type: text }
    ]
}
`;
    const out = format(src, 'd.ttrm');
    expect(out).toContain('// pk');
    expect(commentCount(out)).toBe(1);
  });

  it('never duplicates or drops a comment', () => {
    const src = `schema db namespace dbo

// lead one
// lead two
def table t {
    columns: [
        def column id { type: int }, // a
        def column name { type: text } // b
    ]
}
`;
    const out = format(src, 'e.ttrm');
    expect(commentCount(out)).toBe(commentCount(src));
  });

  it('is idempotent with comments present', () => {
    const src = `schema db namespace dbo

// lead
def table t {
    columns: [
        def column id { type: int }, // pk
        def column name { type: text }
    ]
}

// trailing model
def model m {
    description: "x" // d
}
`;
    const once = format(src, 'f.ttrm');
    expect(format(once, 'f.ttrm')).toBe(once);
  });

  it('preserves semantics (AST equal modulo source/trivia)', () => {
    const src = `schema db namespace dbo

// lead
def table t {
    columns: [
        def column id { type: int }, // pk
        def column name { type: text }
    ]
}
`;
    const out = format(src, 'g.ttrm');
    const a = parseString(src, 'g.ttrm');
    const b = parseString(out, 'g.ttrm');
    expect(b.errors.filter((e) => e.severity === 'error')).toEqual([]);
    expect(stripMeta(b.ast)).toEqual(stripMeta(a.ast));
  });
});
