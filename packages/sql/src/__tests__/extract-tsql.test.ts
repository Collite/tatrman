// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { CharStream, CommonTokenStream } from 'antlr4ng';
import { TSqlLexer } from '../generated/tsql/TSqlLexer.js';
import { TSqlParser } from '../generated/tsql/TSqlParser.js';
import { maskPlaceholders } from '../mask.js';
import { extract } from '../adapters/index.js';
import type { SqlRefModel } from '../refmodel.js';

/**
 * embedded-sql 3.1 (RED) — the T-SQL extraction contract (DESIGN §12.3 "80%").
 * Parses each fixture and asserts the dialect-agnostic SqlRefModel the Phase 3.2
 * `TsqlAdapter` must produce. Red until 3.2 implements `extract`; the parse
 * itself succeeds, so a failure here is the missing adapter, not a spec typo.
 */
function model(sql: string): SqlRefModel {
  const masked = maskPlaceholders(sql).masked;
  const lexer = new TSqlLexer(CharStream.fromString(masked));
  lexer.removeErrorListeners();
  const parser = new TSqlParser(new CommonTokenStream(lexer));
  parser.removeErrorListeners();
  return extract(parser.tsql_file(), 'tsql');
}

const names = (m: SqlRefModel) => m.tables.map((t) => t.name.join('.')).sort();
const cols = (m: SqlRefModel) => m.columns.map((c) => (c.qualifier ? `${c.qualifier}.${c.name}` : c.name)).sort();

describe('T-SQL extraction → SqlRefModel (3.1)', () => {
  it('simple FROM with a 2-part name + alias', () => {
    const m = model('SELECT a, b FROM dbo.Orders o');
    expect(m.tables).toHaveLength(1);
    expect(m.tables[0]!.name).toEqual(['dbo', 'Orders']);
    expect(m.tables[0]!.alias).toBe('o');
    expect(m.tables[0]!.origin).toBe('base');
    expect(cols(m)).toEqual(['a', 'b']);
    expect(m.rootScope.tables).toHaveLength(1);
  });

  it('bracketed name parts are unwrapped', () => {
    const m = model('SELECT 1 FROM [dbo].[Order Items]');
    expect(m.tables[0]!.name).toEqual(['dbo', 'Order Items']);
  });

  it('JOIN + aliases, qualified columns', () => {
    const m = model('SELECT o.id, c.name FROM Orders o JOIN Customers c ON o.cid = c.id');
    expect(names(m)).toEqual(['Customers', 'Orders']);
    expect(m.tables.find((t) => t.alias === 'o')?.name).toEqual(['Orders']);
    expect(cols(m)).toEqual(['c.id', 'c.name', 'o.cid', 'o.id']);
    expect(m.rootScope.tables).toHaveLength(2);
  });

  it('CTE (WITH) is recorded and marked origin=cte when referenced', () => {
    const m = model('WITH cte AS (SELECT id FROM Orders) SELECT id FROM cte');
    expect(m.ctes.map((c) => c.name)).toContain('cte');
    expect(m.tables.find((t) => t.name.join('.') === 'cte')?.origin).toBe('cte');
    expect(m.tables.some((t) => t.name.join('.') === 'Orders')).toBe(true);
  });

  it('derived table (subquery alias) → origin=derived', () => {
    const m = model('SELECT d.x FROM (SELECT x FROM Orders) d');
    const derived = m.tables.find((t) => t.alias === 'd');
    expect(derived?.origin).toBe('derived');
  });

  it('native @param surfaces as a param (masked {param} is a column at this level)', () => {
    // `extract` sees only the tree; a masked `{kod_artiklu}` reads as a bare
    // column here — the `{param}` overlay happens where the MaskResult is known
    // (resolver, stages 3.4/3.5). So only the native `@p` is a param.
    const m = model('SELECT 1 FROM Orders WHERE id = @p AND code = {kod_artiklu}');
    expect(m.params.map((p) => p.name)).toContain('p');
    expect(m.columns.map((c) => c.name)).toContain('kod_artiklu');
  });

  it('error tolerance: a partly-broken query still yields the table + records parseErrors', () => {
    const m = model('SELECT id FROM users )))garbage(((');
    expect(m.tables.some((t) => t.name.join('.') === 'users')).toBe(true);
    expect(m.parseErrors.length).toBeGreaterThan(0);
  });

  it('scope: a JOIN lists both tables in rootScope so the resolver can decide ambiguity', () => {
    const m = model('SELECT name FROM A JOIN B ON A.id = B.id');
    expect(m.rootScope.tables.map((t) => t.name.join('.')).sort()).toEqual(['A', 'B']);
  });
});
