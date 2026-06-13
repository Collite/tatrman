import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { parseSql, extract } from '@modeler/sql';
import { ProjectSymbolTable } from '../project-symbols.js';
import { resolveSqlReferences } from '../sql/resolve.js';
import { parseSqlConfig, emptySqlConfig, type SqlConfig } from '../sql-config.js';

const DB = `schema db namespace dbo
def table Orders {
  columns: [ def column id { type: int }, def column total { type: int } ]
}
def table Customers {
  columns: [ def column id { type: int }, def column name { type: text } ]
}
`;

function symbols(): ProjectSymbolTable {
  const t = new ProjectSymbolTable();
  t.upsertDocument('file:///p/db.ttr', parseString(DB).ast!, 'db', 'dbo', '');
  return t;
}

// (WH, dbo) ⇄ namespace `dbo`; tsql defaults fill WH.dbo for bare names.
const CONFIG: SqlConfig = parseSqlConfig({
  sql: {
    'default-dialect': 'tsql',
    'namespace-map': [{ namespace: 'dbo', database: 'WH', schema: 'dbo' }],
    defaults: { tsql: { database: 'WH', schema: 'dbo' } },
  },
}).config;

function analyze(sql: string, config: SqlConfig = CONFIG) {
  const { tree } = parseSql(sql, config.defaultDialect);
  const model = extract(tree!, config.defaultDialect);
  return resolveSqlReferences(model, { dialect: config.defaultDialect, config, symbols: symbols() });
}

describe('SQL reference resolution + diagnostics (3.4)', () => {
  it('flags an unknown table (1 diag on the table span)', () => {
    const diags = analyze('SELECT * FROM Nope');
    expect(diags.map((d) => d.code)).toEqual(['sql-unknown-table']);
    expect(diags[0].message).toContain('Nope');
  });

  it('resolves a known table + column with no diagnostics', () => {
    expect(analyze('SELECT id FROM Orders')).toEqual([]);
  });

  it('flags an unknown column (1 diag on the column span)', () => {
    const diags = analyze('SELECT bad FROM Orders');
    expect(diags.map((d) => d.code)).toEqual(['sql-unknown-column']);
    expect(diags[0].message).toContain('bad');
  });

  it('flags a bare column ambiguous across two joined tables', () => {
    const diags = analyze('SELECT id FROM Orders JOIN Customers ON Orders.id = Customers.id');
    expect(diags.map((d) => d.code)).toEqual(['sql-ambiguous-column']);
  });

  it('flags a qualified column not on its alias', () => {
    const diags = analyze('SELECT a.bad FROM Orders a');
    expect(diags.map((d) => d.code)).toEqual(['sql-column-not-on-alias']);
    expect(diags[0].message).toContain('a');
  });

  it('resolves qualified columns on their alias with no diagnostics', () => {
    expect(analyze('SELECT a.id, a.total FROM Orders a')).toEqual([]);
  });

  it('is case-insensitive for tsql (ORDERS == Orders)', () => {
    expect(analyze('SELECT ID FROM ORDERS')).toEqual([]);
  });

  it('skips resolution entirely when no [sql] config is present (FP-safe)', () => {
    expect(analyze('SELECT * FROM Nope', emptySqlConfig())).toEqual([]);
  });

  it('skips a fully-qualified name whose (database, schema) is unmapped', () => {
    // other.Thing → (WH, other) not in the namespace map → skip, no diagnostic.
    expect(analyze('SELECT * FROM other.Thing')).toEqual([]);
  });
});
