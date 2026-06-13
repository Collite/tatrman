import { describe, it, expect } from 'vitest';
import {
  loadSqlConfig,
  emptySqlConfig,
  namespaceFor,
  sqlNameFor,
  defaultsFor,
} from '../sql-config.js';

const SAMPLE = `
[sql]
default-dialect = "postgres"

[[sql.namespace-map]]
namespace = "sales"
database  = "WH"
schema    = "dbo"

[[sql.namespace-map]]
namespace = "public_core"
database  = "core"
schema    = "public"

[sql.defaults.tsql]
database = "WH"
schema   = "dbo"
[sql.defaults.postgres]
database = "core"
schema   = "public"
`;

describe('modeler.toml [sql] config (3.3)', () => {
  it('parses default-dialect, namespace-map, and per-dialect defaults', () => {
    const { config, diagnostics } = loadSqlConfig(SAMPLE);
    expect(diagnostics).toEqual([]);
    expect(config.defaultDialect).toBe('postgres');
    expect(config.namespaceMap).toHaveLength(2);
    expect(config.namespaceMap[0]).toEqual({ namespace: 'sales', database: 'WH', schema: 'dbo' });
    expect(config.defaults.tsql).toEqual({ database: 'WH', schema: 'dbo' });
    expect(config.defaults.postgres).toEqual({ database: 'core', schema: 'public' });
  });

  it('builds both lookup directions', () => {
    const { config } = loadSqlConfig(SAMPLE);
    expect(namespaceFor(config, 'WH', 'dbo')).toBe('sales');
    expect(namespaceFor(config, 'core', 'public')).toBe('public_core');
    expect(namespaceFor(config, 'nope', 'nope')).toBeUndefined();
    expect(sqlNameFor(config, 'sales')).toEqual({ database: 'WH', schema: 'dbo' });
    expect(sqlNameFor(config, 'missing')).toBeUndefined();
    expect(defaultsFor(config, 'postgres')).toEqual({ database: 'core', schema: 'public' });
    expect(defaultsFor(config, 'duckdb')).toBeUndefined();
  });

  it('flags a duplicate namespace (bijectivity)', () => {
    const toml = `
[[sql.namespace-map]]
namespace = "dup"
database = "A"
schema = "a"
[[sql.namespace-map]]
namespace = "dup"
database = "B"
schema = "b"
`;
    const { diagnostics } = loadSqlConfig(toml);
    expect(diagnostics.some((d) => /duplicate namespace 'dup'/.test(d.message))).toBe(true);
  });

  it('flags a duplicate (database, schema) pair (bijectivity)', () => {
    const toml = `
[[sql.namespace-map]]
namespace = "one"
database = "WH"
schema = "dbo"
[[sql.namespace-map]]
namespace = "two"
database = "WH"
schema = "dbo"
`;
    const { diagnostics } = loadSqlConfig(toml);
    expect(diagnostics.some((d) => /duplicate \(database, schema\) 'WH\.dbo'/.test(d.message))).toBe(true);
  });

  it('absent [sql] section → tsql default, empty map, no crash', () => {
    const { config, diagnostics } = loadSqlConfig('[project]\nname = "x"\n');
    expect(diagnostics).toEqual([]);
    expect(config).toEqual(emptySqlConfig());
    expect(config.defaultDialect).toBe('tsql');
    expect(config.namespaceMap).toEqual([]);
  });

  it('invalid default-dialect falls back to tsql with a diagnostic', () => {
    const { config, diagnostics } = loadSqlConfig('[sql]\ndefault-dialect = "oracle"\n');
    expect(config.defaultDialect).toBe('tsql');
    expect(diagnostics.some((d) => /not a known dialect/.test(d.message))).toBe(true);
  });
});
