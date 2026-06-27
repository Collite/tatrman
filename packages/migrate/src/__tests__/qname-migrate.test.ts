import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { ProjectSymbolTable } from '@modeler/semantics';
import { computeKeyMap, newKeyForEntry, rewriteCanonicalKeys } from '../qname-migrate.js';

function symbolsOf(files: { uri: string; src: string; pkg?: string }[]): ProjectSymbolTable {
  const symbols = new ProjectSymbolTable();
  for (const f of files) {
    const ast = parseString(f.src, f.uri).ast!;
    const schemaCode = ast.modelDirective?.modelCode ?? '';
    const namespace = ast.modelDirective?.schema ?? '';
    symbols.upsertDocument(f.uri, ast, schemaCode, namespace, f.pkg ?? '');
  }
  return symbols;
}

describe('qname migrator — old→new canonical key map', () => {
  it('db table gains the kind segment; column too', () => {
    const symbols = symbolsOf([{
      uri: 'file:///db.ttrm',
      pkg: 'shop.sales',
      src: 'model db schema dbo\ndef table Orders { columns: [ def column id { type: int } ] }',
    }]);
    const map = computeKeyMap(symbols);
    expect(map.get('shop.sales.db.dbo.Orders')).toBe('shop.sales.db.dbo.table.Orders');
    expect(map.get('shop.sales.db.dbo.Orders.id')).toBe('shop.sales.db.dbo.table.Orders.id');
  });

  it('db fk/view keep their own kind (not table)', () => {
    const symbols = symbolsOf([{
      uri: 'file:///db.ttrm',
      pkg: 'shop.sales',
      src: 'model db schema dbo\ndef fk fk_a { from: x, to: y }\ndef view V { columns: [] }',
    }]);
    const map = computeKeyMap(symbols);
    expect(map.get('shop.sales.db.dbo.fk_a')).toBe('shop.sales.db.dbo.fk.fk_a');
    expect(map.get('shop.sales.db.dbo.V')).toBe('shop.sales.db.dbo.view.V');
  });

  it('er entity key is unchanged (already uniform)', () => {
    const symbols = symbolsOf([{
      uri: 'file:///er.ttrm',
      pkg: 'shop.core',
      src: 'model er\ndef entity customer { attributes: [def attribute id { type: int }] }',
    }]);
    const map = computeKeyMap(symbols);
    expect(map.has('shop.core.er.entity.customer')).toBe(false);
    expect(map.has('shop.core.er.entity.customer.id')).toBe(false);
  });

  it('stock cnc loses the doubled segment (D15)', () => {
    const symbols = symbolsOf([{
      uri: 'stock://cnc-roles.ttrm',
      pkg: '',
      src: 'model cnc schema role\ndef role fact { }',
    }]);
    const map = computeKeyMap(symbols);
    // stock key cnc.cnc.role.fact → cnc.role.fact
    const entry = symbols.all().find((e) => e.name === 'fact')!;
    expect(newKeyForEntry(entry, symbols)).toBe('cnc.role.fact');
    expect(map.get('cnc.cnc.role.fact')).toBe('cnc.role.fact');
  });

  it('rewriteCanonicalKeys remaps graph objects + layout keys, longest-first', () => {
    const symbols = symbolsOf([{
      uri: 'file:///db.ttrm',
      pkg: 'shop.sales',
      src: 'model db schema dbo\ndef table Orders { columns: [ def column id { type: int } ] }',
    }]);
    const map = computeKeyMap(symbols);
    const ttrg = [
      'graph g {',
      '  model: db,',
      '  objects: [ shop.sales.db.dbo.Orders ],',
      '  layout: { nodes: { shop.sales.db.dbo.Orders: { x: 1, y: 2 } } }',
      '}',
    ].join('\n');
    const out = rewriteCanonicalKeys(ttrg, map);
    expect(out).toContain('shop.sales.db.dbo.table.Orders');
    expect(out).not.toMatch(/dbo\.Orders\b/); // no kind-less db.dbo.Orders left
  });

  it('does not rewrite a non-key substring match', () => {
    const map = new Map([['db.dbo.Orders', 'db.dbo.table.Orders']]);
    // `db.dbo.Orders2` and `x.db.dbo.Orders` must NOT be touched (token boundary).
    const out = rewriteCanonicalKeys('a db.dbo.Orders2 b x.db.dbo.Orders c db.dbo.Orders d', map);
    expect(out).toBe('a db.dbo.Orders2 b x.db.dbo.Orders c db.dbo.table.Orders d');
  });
});
