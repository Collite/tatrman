import { describe, it, expect } from 'vitest';
import type { Span } from '@modeler/sql';
import { checkSqlParameters, type SqlParamUsage } from '../sql/param-check.js';

const span = (offset: number): Span => ({ offset, length: 4, line: 1, column: offset });
const use = (name: string, offset = 0): SqlParamUsage => ({ name, span: span(offset) });

describe('SQL parameters cross-check (3.5)', () => {
  it('flags a placeholder used in SQL but not declared (error on its span)', () => {
    const r = checkSqlParameters({
      declared: [{ name: 'id' }],
      placeholders: [use('nazev', 10)],
      nativeParams: [],
      dialect: 'tsql',
    });
    expect(r.diagnostics.map((d) => d.code)).toEqual(['sql-undeclared-param']);
    expect(r.diagnostics[0].severity).toBe('error');
    expect(r.diagnostics[0].span.offset).toBe(10);
    expect(r.diagnostics[0].message).toContain('nazev');
  });

  it('reports a declared param never used as unused', () => {
    const r = checkSqlParameters({
      declared: [{ name: 'since' }, { name: 'id' }],
      placeholders: [use('id')],
      nativeParams: [],
      dialect: 'tsql',
    });
    expect(r.diagnostics).toEqual([]);
    expect(r.unusedParamNames).toEqual(['since']);
  });

  it('treats native bind params (:name / @p) as usages', () => {
    const r = checkSqlParameters({
      declared: [{ name: 'id' }],
      placeholders: [],
      nativeParams: [use('id')], // paramName already strips the sigil
      dialect: 'postgres',
    });
    expect(r.diagnostics).toEqual([]);
    expect(r.unusedParamNames).toEqual([]);
  });

  it('never flags positional params and suppresses unused when one is present', () => {
    const r = checkSqlParameters({
      declared: [{ name: 'a' }, { name: 'b' }],
      placeholders: [],
      nativeParams: [use('1'), use('2')], // $1, $2
      dialect: 'postgres',
    });
    expect(r.diagnostics).toEqual([]); // positional → never "undeclared"
    expect(r.unusedParamNames).toEqual([]); // suppressed by positional presence
  });

  it('folds names per dialect (case-insensitive match)', () => {
    const r = checkSqlParameters({
      declared: [{ name: 'Since' }],
      placeholders: [use('since')],
      nativeParams: [],
      dialect: 'tsql',
    });
    expect(r.diagnostics).toEqual([]);
    expect(r.unusedParamNames).toEqual([]);
  });
});
