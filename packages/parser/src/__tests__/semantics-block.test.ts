import { describe, it, expect } from 'vitest';
import { parseString, DiagnosticCode } from '../index.js';
import type { Definition, TableDef, EntityDef, AttributeDef } from '../index.js';

function getDef<T extends Definition>(src: string, index = 0): T {
  return parseString(src).ast!.definitions[index] as T;
}

describe('semantics block (grammar 4.2)', () => {
  // (a) — attaches on all four attachment kinds and lands on the AST node.
  it('parses on an entity and lands on the node', () => {
    const src = 'model er\ndef entity E { semantics { kind: period_table } }';
    const r = parseString(src);
    expect(r.errors).toHaveLength(0);
    const def = r.ast!.definitions[0] as EntityDef;
    expect(def.semantics?.kind).toBe('semanticsBlock');
    expect(def.semantics?.entries.kind).toBe('period_table');
  });

  it('parses on an inline attribute and lands on the node', () => {
    const src = 'model er\ndef entity E { attributes: [ def attribute a { type: date, semantics { role: period_start } } ] }';
    const def = getDef<EntityDef>(src);
    const attr = def.attributes?.[0] as AttributeDef;
    expect(attr.semantics?.entries.role).toBe('period_start');
  });

  it('parses on a standalone attribute', () => {
    const src = 'model er\ndef attribute a { type: date, semantics { role: due_date } }';
    const def = getDef<AttributeDef>(src);
    expect(def.semantics?.entries.role).toBe('due_date');
  });

  it('parses on a table and its inline column', () => {
    const src = 'model db schema dbo\ndef table t { semantics { kind: poi }, columns: [ def column p { type: text, semantics { role: geo_point } } ] }';
    const r = parseString(src);
    expect(r.errors).toHaveLength(0);
    const def = r.ast!.definitions[0] as TableDef;
    expect(def.semantics?.entries.kind).toBe('poi');
    expect(def.columns?.[0]?.semantics?.entries.role).toBe('geo_point');
  });

  // (b) — entries preserved as raw key→value pairs of the right primitive shape.
  it('captures id values as opaque identifier text, strings unquoted, numbers/bools as primitives', () => {
    const src = [
      'model er',
      'def entity E {',
      '  attributes: [',
      '    def attribute a {',
      '      type: date,',
      '      semantics { role: period_code, code_format: "yyyyMM", period: acme.AccountingPeriod, digits: 6, active: true }',
      '    }',
      '  ]',
      '}',
    ].join('\n');
    const def = getDef<EntityDef>(src);
    const e = (def.attributes?.[0] as AttributeDef).semantics!.entries;
    expect(e.role).toBe('period_code');          // id → text
    expect(e.code_format).toBe('yyyyMM');         // string literal, unquoted
    expect(e.period).toBe('acme.AccountingPeriod'); // dotted id kept opaque (T3 resolves)
    expect(e.digits).toBe(6);                     // number primitive
    expect(e.active).toBe(true);                  // boolean primitive
  });

  // (c) — duplicate key bookkeeping (search-block precedent), last-wins.
  it('records a repeated key in duplicateProperties (last-wins)', () => {
    const src = 'model er\ndef entity E { semantics { role: event_date, role: document_date } }';
    const def = getDef<EntityDef>(src);
    expect(def.semantics?.duplicateProperties).toContain('role');
    expect(def.semantics?.entries.role).toBe('document_date'); // last-wins
  });

  it('a clean block yields no duplicateProperties', () => {
    const src = 'model er\ndef entity E { semantics { kind: fx_rate } }';
    const def = getDef<EntityDef>(src);
    expect(def.semantics?.duplicateProperties ?? []).toHaveLength(0);
  });

  // (d) — trivia survives: a leading comment on the block is preserved in the AST
  // (attached to the enclosing def, the SearchBlock convention — block sources
  // span the `{ … }`, so the comment binds to the nearest node that owns the
  // property slot). The point is that the round-trip never drops it.
  it('preserves a leading comment on the block in the AST', () => {
    const src = [
      'model er',
      'def entity E {',
      '  // grounding role',
      '  semantics { kind: poi }',
      '}',
    ].join('\n');
    const ast = parseString(src).ast!;
    const triviaTexts: string[] = [];
    const walk = (v: unknown): void => {
      if (!v || typeof v !== 'object') return;
      if (Array.isArray(v)) return v.forEach(walk);
      const o = v as Record<string, unknown>;
      for (const t of [...((o.leadingTrivia as { text: string }[]) ?? []), ...((o.trailingTrivia as { text: string }[]) ?? [])]) {
        triviaTexts.push(t.text);
      }
      for (const k in o) if (k !== 'leadingTrivia' && k !== 'trailingTrivia') walk(o[k]);
    };
    walk(ast);
    expect(triviaTexts.some((t) => t.includes('grounding role'))).toBe(true);
  });

  // (e) — source locations present and ANTLR-convention-correct on the block.
  it('has an accurate source location on the block node', () => {
    const src = 'model er\ndef entity E { semantics { kind: poi } }';
    const def = getDef<EntityDef>(src);
    const s = def.semantics!.source;
    expect(s.line).toBe(2);                 // block is on line 2 (1-indexed)
    expect(s.endLine).toBe(2);
    expect(s.column).toBeGreaterThanOrEqual(0);
    expect(s.endColumn).toBeGreaterThan(s.column);
    expect(s.offsetEnd).toBeGreaterThan(s.offsetStart);
    // The span covers the `{ … }` object (the SearchBlock convention), so the
    // formatter re-emits `semantics: { … }` cleanly.
    const text = src.slice(s.offsetStart, s.offsetEnd).trim();
    expect(text.startsWith('{')).toBe(true);
    expect(text.endsWith('}')).toBe(true);
  });

  // (f) — nested object/list values are rejected into a parser diagnostic and dropped.
  it('rejects a nested object/list value with a ttr/semantics-non-scalar diagnostic', () => {
    const src = 'model er\ndef entity E { semantics { role: event_date, bad: { x: 1 }, worse: [1, 2] } }';
    const r = parseString(src);
    const nonScalar = r.errors.filter((e) => e.code === DiagnosticCode.SemanticsNonScalarValue);
    expect(nonScalar).toHaveLength(2);
    const def = r.ast!.definitions[0] as EntityDef;
    // scalar entry kept; non-scalar entries dropped from the flat record.
    expect(def.semantics?.entries.role).toBe('event_date');
    expect(def.semantics?.entries.bad).toBeUndefined();
    expect(def.semantics?.entries.worse).toBeUndefined();
  });
});
