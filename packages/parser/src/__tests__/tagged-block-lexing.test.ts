import { describe, it, expect } from 'vitest';
import { CharStream, CommonTokenStream } from 'antlr4ng';
import { TTRLexer } from '../generated/TTRLexer.js';

/**
 * Lexer-level disambiguation (embedded-sql DESIGN §2.1 table). TAGGED_BLOCK_LITERAL
 * is declared before TRIPLE_STRING_LITERAL, so equal-length matches resolve to the
 * tagged form only when a tag + newline is present on the opener line. Token-type
 * only — independent of (and ahead of) the walker.
 */
function tripleTokenType(src: string): number | null {
  const lexer = new TTRLexer(CharStream.fromString(src));
  const ts = new CommonTokenStream(lexer);
  ts.fill();
  for (const t of ts.getTokens()) {
    if (t.type === TTRLexer.TAGGED_BLOCK_LITERAL || t.type === TTRLexer.TRIPLE_STRING_LITERAL) {
      return t.type;
    }
  }
  return null;
}

describe('embeddedBlock lexer disambiguation (DESIGN §2.1)', () => {
  it('"""sql␊SELECT 1␊""" → TAGGED_BLOCK_LITERAL (tag + newline, declared first)', () => {
    expect(tripleTokenType('x: """sql\nSELECT 1\n"""')).toBe(TTRLexer.TAGGED_BLOCK_LITERAL);
  });

  it('"""␊plain␊""" → TRIPLE_STRING_LITERAL (char after """ is a newline)', () => {
    expect(tripleTokenType('x: """\nplain\n"""')).toBe(TTRLexer.TRIPLE_STRING_LITERAL);
  });

  it('"""a note""" → TRIPLE_STRING_LITERAL (no newline before close)', () => {
    expect(tripleTokenType('x: """a note"""')).toBe(TTRLexer.TRIPLE_STRING_LITERAL);
  });

  it('"""sql""" one line → TRIPLE_STRING_LITERAL (no newline after tag)', () => {
    expect(tripleTokenType('x: """sql"""')).toBe(TTRLexer.TRIPLE_STRING_LITERAL);
  });
});
