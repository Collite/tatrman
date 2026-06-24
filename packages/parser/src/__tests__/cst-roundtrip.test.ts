import { describe, it, expect } from 'vitest';
import { CharStream, CommonTokenStream, Token } from 'antlr4ng';
import { parseString } from '../index.js';
import { TTRLexer } from '../generated/TTRLexer.js';
import type { Document } from '../ast.js';
import type { Trivia } from '../cst/trivia.js';

/**
 * Identity printer (test-only). Reconstructs the source from significant tokens
 * (re-lexed) plus the comment trivia attached to the AST. Whitespace gaps
 * between adjacent items are taken from the source but asserted to be
 * whitespace-only — so a dropped comment (which would leave non-whitespace in a
 * gap) fails the assertion, and a duplicated comment breaks reconstruction.
 */
function identityPrint(src: string, ast: Document): string {
  const lexer = new TTRLexer(CharStream.fromString(src));
  const stream = new CommonTokenStream(lexer);
  stream.fill();

  interface Span {
    start: number;
    end: number; // exclusive
    text: string;
  }
  const items: Span[] = [];
  for (const t of stream.getTokens()) {
    if (t.channel === Token.DEFAULT_CHANNEL && t.type !== Token.EOF) {
      items.push({ start: t.start, end: t.stop + 1, text: src.slice(t.start, t.stop + 1) });
    }
  }
  for (const tr of collectTrivia(ast)) {
    items.push({ start: tr.source.offsetStart, end: tr.source.offsetEnd, text: tr.text });
  }
  items.sort((a, b) => a.start - b.start || a.end - b.end);

  let out = '';
  let cursor = 0;
  for (const item of items) {
    const gap = src.slice(cursor, item.start);
    // The only thing allowed between recognised items is whitespace.
    expect(gap).toMatch(/^\s*$/);
    out += gap + item.text;
    cursor = item.end;
  }
  out += src.slice(cursor);
  return out;
}

function collectTrivia(ast: Document): Trivia[] {
  const out: Trivia[] = [];
  const seen = new Set<unknown>();
  const visit = (v: unknown): void => {
    if (v === null || typeof v !== 'object') return;
    if (seen.has(v)) return;
    seen.add(v);
    if (Array.isArray(v)) {
      for (const i of v) visit(i);
      return;
    }
    const obj = v as Record<string, unknown>;
    for (const key of ['leadingTrivia', 'trailingTrivia']) {
      const arr = obj[key];
      if (Array.isArray(arr)) out.push(...(arr as Trivia[]));
    }
    for (const key in obj) {
      if (key === 'source' || key === 'leadingTrivia' || key === 'trailingTrivia') continue;
      visit(obj[key]);
    }
  };
  visit(ast);
  return out;
}

const CORPUS: Array<{ name: string; uri: string; src: string }> = [
  {
    name: 'leading line comment above a def',
    uri: 'a.ttrm',
    src: `schema db namespace dbo

// the users table
def table users {
    columns: [
        def column id { type: int }
    ]
}
`,
  },
  {
    name: 'trailing line comment on a property line',
    uri: 'b.ttrm',
    src: `schema db namespace dbo

def table users {
    columns: [
        def column id { type: int } // primary key
    ]
}
`,
  },
  {
    name: 'block comment and blank lines',
    uri: 'c.ttrm',
    src: `schema db namespace dbo

/* a block
   comment */

def table t {
    columns: [
        def column a { type: int }
    ]
}
`,
  },
  {
    name: 'comment at EOF',
    uri: 'd.ttrm',
    src: `schema db namespace dbo

def table t {
    columns: [
        def column a { type: int }
    ]
}
// trailing file comment
`,
  },
  {
    name: 'dangling comment before closing brace',
    uri: 'e.ttrm',
    src: `schema db namespace dbo

def table t {
    columns: [
        def column a { type: int }
        // nothing after me
    ]
}
`,
  },
  {
    name: 'multiple leading comments',
    uri: 'f.ttrm',
    src: `schema db namespace dbo

// one
// two
def table t {
    columns: [
        def column a { type: int }
    ]
}
`,
  },
];

describe('CST round-trip (identity printer)', () => {
  for (const entry of CORPUS) {
    it(`reproduces "${entry.name}" byte-for-byte`, () => {
      const result = parseString(entry.src, entry.uri);
      expect(result.errors).toHaveLength(0);
      expect(result.ast).toBeDefined();
      expect(identityPrint(entry.src, result.ast!)).toBe(entry.src);
    });
  }

  it('attaches every comment exactly once across the corpus', () => {
    for (const entry of CORPUS) {
      const result = parseString(entry.src, entry.uri);
      const trivia = collectTrivia(result.ast!);
      const commentCount = (entry.src.match(/\/\//g)?.length ?? 0) + (entry.src.match(/\/\*/g)?.length ?? 0);
      expect(trivia.length).toBe(commentCount);
    }
  });
});
