import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type { TaggedBlockValue, QueryDef, StringValue, TripleStringValue } from '../ast.js';

/**
 * Phase 1 (embedded-sql) — RED. These assert the tagged-block value contract
 * (DESIGN §9 golden cases C1–C11; contracts §2). They fail until the grammar
 * (1.2) and the TS walker (1.3) produce a `TaggedBlockValue` — the failure is the
 * missing production type/behaviour, not a spec bug.
 *
 * `␊` = newline, `·` = significant space in the source forms below.
 */
function sourceTextOf(src: string): StringValue | TripleStringValue | TaggedBlockValue {
  const r = parseString(src);
  expect(r.errors, `parse errors: ${JSON.stringify(r.errors)}`).toHaveLength(0);
  const q = r.ast!.definitions[0] as QueryDef;
  return q.sourceText!;
}

function tagged(src: string): TaggedBlockValue {
  const v = sourceTextOf(src);
  expect(v.kind).toBe('taggedBlock');
  return v as TaggedBlockValue;
}

describe('tagged-block value contract (embedded-sql Phase 1)', () => {
  it('C1 — bare `sql` tag, clean body', () => {
    const v = tagged('def query c1 {\n  sourceText: """sql\nSELECT 1\n"""\n}');
    expect(v.tag).toBe('sql');
    expect(v.language).toBe('SQL');
    expect(v.dialect).toBeNull(); // bare sql → modeler.toml default
    expect(v.value).toBe('SELECT 1');
  });

  it('C2 — `ms-sql` resolves dialect tsql', () => {
    const v = tagged('def query c2 {\n  sourceText: """ms-sql\nSELECT 1\n"""\n}');
    expect(v.tag).toBe('ms-sql');
    expect(v.dialect).toBe('tsql');
    expect(v.value).toBe('SELECT 1');
  });

  it('C3 — uniform 2-indent + trailing hspace after tag', () => {
    const v = tagged('def query c3 {\n  sourceText: """sql  \n  SELECT 1\n  """\n}');
    expect(v.tag).toBe('sql');
    expect(v.value).toBe('SELECT 1');
    expect(v.indentWidth).toBe(2);
  });

  it('C4 — ragged indent (2 vs 6): common 2 stripped', () => {
    const v = tagged('def query c4 {\n  sourceText: """sql\n  SELECT a,\n      b\n  """\n}');
    expect(v.value).toBe('SELECT a,\n    b');
  });

  it('C7 — `"""sql"""` one line is a plain string, NOT a tagged block', () => {
    const v = sourceTextOf('def query c7 {\n  sourceText: """sql"""\n}');
    expect(v.kind).not.toBe('taggedBlock');
    expect(v.value).toBe('sql');
  });

  it('C9 — empty body', () => {
    const v = tagged('def query c9 {\n  sourceText: """sql\n"""\n}');
    expect(v.value).toBe('');
  });

  it('C10 — backtick-quoted id survives the """ fence', () => {
    const v = tagged('def query c10 {\n  sourceText: """mysql\nSELECT `id`\n"""\n}');
    expect(v.dialect).toBe('mysql');
    expect(v.value).toBe('SELECT `id`');
  });

  it('C11 — internal blank line kept; only the close-fence newline stripped', () => {
    const v = tagged('def query c11 {\n  sourceText: """sql\nSELECT 1\n\nFROM t\n"""\n}');
    expect(v.value).toBe('SELECT 1\n\nFROM t');
  });
});
