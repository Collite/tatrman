import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';
import { parseManifest, resolveManifest, effectiveSchemaId } from '../manifest.js';

// qname-redesign D8 / [defaults].schema / D10 scoped unique-match — the manifest
// is wired into production resolution: the slot-filling engine consumes the
// package default-schema + project default, and symbol keys are populated through
// `effectiveSchemaId`. These tests pin that the wire actually changes behaviour
// (not just that the manifest parses).

const TOML = `
[schemas.dbo]
db-schema = "dbo"
dialect   = "tsql"

[schemas.sales]
db-schema = "sales"
dialect   = "tsql"

[packages."shop.sales"]
default-schema = "sales"

[defaults]
schema = "dbo"
`;

const manifest = resolveManifest(parseManifest(TOML), '/proj');

/**
 * Build a project the way the LSP/lint loaders do — the db schema slot for a
 * directive-less file is filled from the manifest via `effectiveSchemaId`.
 */
function project(docs: Array<{ uri: string; src: string; pkg: string }>): ProjectSymbolTable {
  const table = new ProjectSymbolTable();
  for (const d of docs) {
    const ast = parseString(d.src, d.uri).ast!;
    const schemaCode = ast.modelDirective?.modelCode ?? '';
    const namespace = effectiveSchemaId(ast.modelDirective?.schema, d.pkg, manifest);
    table.upsertDocument(d.uri, ast, schemaCode, namespace, d.pkg);
  }
  return table;
}

describe('effectiveSchemaId', () => {
  it('file `schema` directive wins over the manifest', () => {
    expect(effectiveSchemaId('explicit', 'shop.sales', manifest)).toBe('explicit');
  });
  it('falls back to the package default-schema (D8)', () => {
    expect(effectiveSchemaId(undefined, 'shop.sales', manifest)).toBe('sales');
  });
  it('falls back to [defaults].schema when the package sets none', () => {
    expect(effectiveSchemaId(undefined, 'shop.catalog', manifest)).toBe('dbo');
  });
  it('is empty when there is no manifest at all', () => {
    expect(effectiveSchemaId(undefined, 'shop.sales', undefined)).toBe('');
  });
});

describe('manifest-driven resolution (D8)', () => {
  it("keys a directive-less db def under the package's default schema", () => {
    const table = project([
      { uri: 'sales.ttrm', pkg: 'shop.sales', src: 'def table Orders { columns: [def column id { type: int }] }' },
    ]);
    // D8: package shop.sales → default-schema sales, so the symbol lives under
    // db.sales (not the conventional dbo).
    expect(table.get('shop.sales.db.sales.table.Orders')).toBeDefined();
    expect(table.get('shop.sales.db.dbo.table.Orders')).toBeUndefined();
  });

  it('resolves a bare db ref against the manifest-resolved schema', () => {
    const table = project([
      { uri: 'sales.ttrm', pkg: 'shop.sales', src: 'def table Orders { columns: [def column id { type: int }] }' },
    ]);
    const resolver = new Resolver(table, '', manifest);
    const res = resolver.resolveReference(
      { path: 'Orders', parts: ['Orders'] },
      { schemaCode: 'db', namespace: 'sales', packageName: 'shop.sales' },
    );
    expect(res.resolved).toBe(true);
    if (res.resolved) expect(res.symbol.qname).toBe('shop.sales.db.sales.table.Orders');
  });
});

describe('scoped unique-match + ambiguity (D10) via the slot engine', () => {
  const docs = [
    { uri: 'sales.ttrm', pkg: 'shop.sales', src: 'def table Orders { columns: [def column id { type: int }] }' },
    { uri: 'arch.ttrm', pkg: 'shop.archive', src: 'def table Orders { columns: [def column id { type: int }] }' },
  ];

  it('resolves a name that is ambiguous project-wide but unique within the file package', () => {
    const table = project(docs);
    const resolver = new Resolver(table, '', manifest);
    const res = resolver.resolveReference(
      { path: 'Orders', parts: ['Orders'] },
      { schemaCode: 'db', namespace: 'sales', packageName: 'shop.sales' },
    );
    expect(res.resolved).toBe(true);
    if (res.resolved) expect(res.symbol.qname).toBe('shop.sales.db.sales.table.Orders');
  });

  it('reports ambiguous when the name matches several symbols and no package scope disambiguates', () => {
    const table = project(docs);
    const resolver = new Resolver(table, '', manifest);
    const res = resolver.resolveReference(
      { path: 'Orders', parts: ['Orders'] },
      { schemaCode: 'db', namespace: 'dbo', packageName: 'shop.other' },
    );
    expect(res.resolved).toBe(false);
    if (!res.resolved) {
      expect(res.reason).toBe('ambiguous');
      expect((res.candidates ?? []).length).toBe(2);
    }
  });
});
