import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type { Document, TableDef } from '../ast.js';
import type { Trivia } from '../cst/trivia.js';

function allTrivia(ast: Document): Array<{ where: 'leading' | 'trailing'; trivia: Trivia }> {
  const out: Array<{ where: 'leading' | 'trailing'; trivia: Trivia }> = [];
  const visit = (v: unknown): void => {
    if (v === null || typeof v !== 'object') return;
    if (Array.isArray(v)) {
      for (const i of v) visit(i);
      return;
    }
    const obj = v as Record<string, unknown>;
    for (const t of (obj.leadingTrivia as Trivia[] | undefined) ?? []) out.push({ where: 'leading', trivia: t });
    for (const t of (obj.trailingTrivia as Trivia[] | undefined) ?? []) out.push({ where: 'trailing', trivia: t });
    for (const key in obj) {
      if (key === 'source' || key === 'leadingTrivia' || key === 'trailingTrivia') continue;
      visit(obj[key]);
    }
  };
  visit(ast);
  return out;
}

describe('trivia attachment', () => {
  it('attaches a leading line comment to the def with an exact SourceLocation', () => {
    const src = `// lead
def table x {
    columns: [
        def column id { type: int }
    ]
}
`;
    const result = parseString(src, 'x.ttrm');
    expect(result.errors).toHaveLength(0);
    const table = result.ast!.definitions[0] as TableDef;
    expect(table.kind).toBe('table');
    expect(table.leadingTrivia).toBeDefined();
    expect(table.leadingTrivia).toHaveLength(1);

    const lead = table.leadingTrivia![0];
    expect(lead.kind).toBe('line-comment');
    expect(lead.text).toBe('// lead');
    // "// lead" is exactly the first 7 chars of the file.
    expect(lead.source).toEqual({
      file: 'x.ttrm',
      line: 1,
      column: 0,
      endLine: 1,
      endColumn: 7,
      offsetStart: 0,
      offsetEnd: 7,
    });
  });

  it('attaches a trailing line comment to a node on the same line', () => {
    const src = `def table x {
    columns: [
        def column id { type: int } // pk
    ]
}
`;
    const result = parseString(src, 'y.ttrm');
    expect(result.errors).toHaveLength(0);
    const trailing = allTrivia(result.ast!).filter((t) => t.where === 'trailing');
    const pk = trailing.find((t) => t.trivia.text === '// pk');
    expect(pk).toBeDefined();
    expect(pk!.trivia.kind).toBe('line-comment');
    // Trailing comment sits on line 3 in the fixture.
    expect(pk!.trivia.source.line).toBe(3);
  });

  it('classifies a block comment as block-comment', () => {
    const src = `/* hello */
def table x {
    columns: [
        def column a { type: int }
    ]
}
`;
    const result = parseString(src, 'z.ttrm');
    expect(result.errors).toHaveLength(0);
    const block = allTrivia(result.ast!).find((t) => t.trivia.kind === 'block-comment');
    expect(block).toBeDefined();
    expect(block!.trivia.text).toBe('/* hello */');
    expect(block!.trivia.source.offsetStart).toBe(0);
    expect(block!.trivia.source.offsetEnd).toBe('/* hello */'.length);
  });
});
