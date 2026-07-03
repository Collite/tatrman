import { describe, it, expect } from 'vitest';
import type { ObjectValue, Reference } from '@modeler/parser';
import { er2dbTargetDescription } from '../model-graph.js';

const LOC = { file: 'f', line: 1, column: 0, endLine: 1, endColumn: 0, offsetStart: 0, offsetEnd: 0 };

function obj(key: string, path: string): ObjectValue {
  return {
    kind: 'object',
    source: LOC,
    entries: [
      {
        key,
        source: LOC,
        value: { kind: 'id', path, parts: path.split('.'), source: LOC },
      },
    ],
  };
}

describe('er2dbTargetDescription', () => {
  it('renders a table target', () => {
    expect(er2dbTargetDescription(obj('table', 'db.dbo.QXXUKAZMUHOD'))).toBe('table:db.dbo.QXXUKAZMUHOD');
  });

  it('renders a view target', () => {
    expect(er2dbTargetDescription(obj('view', 'db.dbo.V_HODNOTY'))).toBe('view:db.dbo.V_HODNOTY');
  });

  it('renders a query target with the new key (renamed from sqlQuery)', () => {
    expect(er2dbTargetDescription(obj('query', 'query.query.sales_filter'))).toBe('query:query.query.sales_filter');
  });

  it('treats a bare reference target as a table', () => {
    const ref: Reference = { path: 'db.dbo.X', parts: ['db', 'dbo', 'X'], source: LOC };
    expect(er2dbTargetDescription(ref)).toBe('table:db.dbo.X');
  });

  it('returns empty string for an undefined or unrecognized target', () => {
    expect(er2dbTargetDescription(undefined)).toBe('');
    expect(er2dbTargetDescription(obj('bogus', 'whatever'))).toBe('');
  });
});
