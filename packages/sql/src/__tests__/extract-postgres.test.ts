import { describe, it, expect } from 'vitest';
import { CharStream, CommonTokenStream } from 'antlr4ng';
import { PostgreSQLLexer } from '../generated/postgresql/PostgreSQLLexer.js';
import { PostgreSQLParser } from '../generated/postgresql/PostgreSQLParser.js';
import { maskPlaceholders } from '../mask.js';
import { extract } from '../adapters/index.js';
import type { SqlRefModel } from '../refmodel.js';

/**
 * embedded-sql 3.1 (RED) — the PostgreSQL extraction contract. Same surface as
 * the T-SQL spec, Postgres syntax (`"quoted"` ids, `schema.table`, `$1`/`:name`
 * params). Red until 3.2's `PostgresAdapter`.
 */
function model(sql: string): SqlRefModel {
  const masked = maskPlaceholders(sql).masked;
  const lexer = new PostgreSQLLexer(CharStream.fromString(masked));
  lexer.removeErrorListeners();
  const parser = new PostgreSQLParser(new CommonTokenStream(lexer));
  parser.removeErrorListeners();
  return extract(parser.root(), 'postgres');
}

const names = (m: SqlRefModel) => m.tables.map((t) => t.name.join('.')).sort();
const cols = (m: SqlRefModel) => m.columns.map((c) => (c.qualifier ? `${c.qualifier}.${c.name}` : c.name)).sort();

describe('PostgreSQL extraction → SqlRefModel (3.1)', () => {
  it('simple FROM with a schema-qualified name + alias', () => {
    const m = model('SELECT a, b FROM public.orders o');
    expect(m.tables).toHaveLength(1);
    expect(m.tables[0]!.name).toEqual(['public', 'orders']);
    expect(m.tables[0]!.alias).toBe('o');
    expect(m.tables[0]!.origin).toBe('base');
    expect(cols(m)).toEqual(['a', 'b']);
  });

  it('double-quoted identifiers are unwrapped', () => {
    const m = model('SELECT 1 FROM "public"."Order Items"');
    expect(m.tables[0]!.name).toEqual(['public', 'Order Items']);
  });

  it('JOIN + aliases, qualified columns', () => {
    const m = model('SELECT o.id, c.name FROM orders o JOIN customers c ON o.cid = c.id');
    expect(names(m)).toEqual(['customers', 'orders']);
    expect(cols(m)).toEqual(['c.id', 'c.name', 'o.cid', 'o.id']);
    expect(m.rootScope.tables).toHaveLength(2);
  });

  it('CTE (WITH) is recorded; reference marked origin=cte', () => {
    const m = model('WITH cte AS (SELECT id FROM orders) SELECT id FROM cte');
    expect(m.ctes.map((c) => c.name)).toContain('cte');
    expect(m.tables.find((t) => t.name.join('.') === 'cte')?.origin).toBe('cte');
  });

  it('derived table (subquery alias) → origin=derived', () => {
    const m = model('SELECT d.x FROM (SELECT x FROM orders) d');
    expect(m.tables.find((t) => t.alias === 'd')?.origin).toBe('derived');
  });

  it('$1 / :name / masked {param} surface as params', () => {
    const m = model('SELECT 1 FROM orders WHERE id = $1 AND code = {kod_artiklu}');
    expect(m.params.map((p) => p.name)).toEqual(expect.arrayContaining(['kod_artiklu']));
  });

  it('error tolerance: partial query still yields the table + parseErrors', () => {
    const m = model('SELECT id FROM users )))garbage(((');
    expect(m.tables.some((t) => t.name.join('.') === 'users')).toBe(true);
    expect(m.parseErrors.length).toBeGreaterThan(0);
  });

  it('scope: a JOIN lists both tables in rootScope', () => {
    const m = model('SELECT name FROM a JOIN b ON a.id = b.id');
    expect(m.rootScope.tables.map((t) => t.name.join('.')).sort()).toEqual(['a', 'b']);
  });
});
