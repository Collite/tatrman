// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import {
  qnameToKey,
  modelForKind,
  modelHasSchema,
  classifyReference,
  resolveReference,
  type Vocab,
  type SymbolIndex,
  type Qname,
} from '../qname.js';

const VOCAB: Vocab = {
  models: new Set(['db', 'er', 'md', 'binding', 'query', 'cnc']),
  packages: new Set(['shop', 'shop.sales', 'shop.core', 'shop.fin']),
  schemas: new Set(['dbo', 'sales']),
  kinds: new Set(['table', 'view', 'column', 'entity', 'attribute', 'relation', 'dimension']),
};

describe('qnameToKey — uniform, package-first', () => {
  it('db table includes the kind segment', () => {
    const q: Qname = { package: 'shop.sales', model: 'db', schema: 'dbo', kind: 'table', parts: ['Orders'] };
    expect(qnameToKey(q)).toBe('shop.sales.db.dbo.table.Orders');
  });
  it('er entity has no schema slot', () => {
    const q: Qname = { package: 'shop.core', model: 'er', kind: 'entity', parts: ['customer'] };
    expect(qnameToKey(q)).toBe('shop.core.er.entity.customer');
  });
  it('cnc role is schema-less (no namespace echo, D15)', () => {
    const q: Qname = { package: '', model: 'cnc', kind: 'role', parts: ['fact'] };
    expect(qnameToKey(q)).toBe('cnc.role.fact');
  });
  it('sub-objects extend parts', () => {
    const q: Qname = { package: 'shop.sales', model: 'db', schema: 'dbo', kind: 'table', parts: ['Orders', 'id'] };
    expect(qnameToKey(q)).toBe('shop.sales.db.dbo.table.Orders.id');
  });
});

describe('modelForKind — single source (D14/D15)', () => {
  it('query and drillMap fold into db', () => {
    expect(modelForKind('query')).toBe('db');
    expect(modelForKind('drillMap')).toBe('db');
  });
  it('role/er2cncRole → cnc (schema-less)', () => {
    expect(modelForKind('role')).toBe('cnc');
    expect(modelForKind('er2cncRole')).toBe('cnc');
    expect(modelHasSchema('cnc')).toBe(false);
  });
  it('entity → er; table → db (has schema)', () => {
    expect(modelForKind('entity')).toBe('er');
    expect(modelForKind('table')).toBe('db');
    expect(modelHasSchema('db')).toBe(true);
  });
  it('er2db_*/md2* → binding', () => {
    expect(modelForKind('er2dbEntity')).toBe('binding');
    expect(modelForKind('md2dbCubelet')).toBe('binding');
  });
});

describe('classifyReference — vocabulary classification (pure, total)', () => {
  it('package + name (everything else derived)', () => {
    expect(classifyReference('shop.sales.Orders', VOCAB)).toEqual({ package: 'shop.sales', parts: ['Orders'] });
  });
  it('longest dotted package match', () => {
    expect(classifyReference('shop.sales.Orders', VOCAB).package).toBe('shop.sales');
    expect(classifyReference('shop.X', VOCAB)).toEqual({ package: 'shop', parts: ['X'] });
  });
  it('explicit model + schema escape hatch', () => {
    expect(classifyReference('db.dbo.Orders', VOCAB)).toEqual({ model: 'db', schema: 'dbo', parts: ['Orders'] });
  });
  it('package then model then name', () => {
    expect(classifyReference('shop.core.er.customer', VOCAB)).toEqual({ package: 'shop.core', model: 'er', parts: ['customer'] });
  });
  it('package then model then schema then name (cross-package db column ref)', () => {
    // `pkg.db.dbo.Table.Col` — the schema (`dbo`) follows the post-package model
    // and must still be stripped, leaving the trailing name path.
    expect(classifyReference('shop.sales.db.dbo.Orders.id', VOCAB))
      .toEqual({ package: 'shop.sales', model: 'db', schema: 'dbo', parts: ['Orders', 'id'] });
  });
  it('leading kind keyword', () => {
    expect(classifyReference('shop.sales.table.Orders', VOCAB)).toEqual({ package: 'shop.sales', kind: 'table', parts: ['Orders'] });
  });
  it('bare name → all parts', () => {
    expect(classifyReference('Orders', VOCAB)).toEqual({ parts: ['Orders'] });
  });
  it('cnc.role.fact — model + kind + name', () => {
    expect(classifyReference('cnc.role.fact', { ...VOCAB, kinds: new Set([...VOCAB.kinds, 'role']) }))
      .toEqual({ model: 'cnc', kind: 'role', parts: ['fact'] });
  });
});

/** A trivial in-memory index over a fixed set of uniform canonical keys → kind. */
function indexOf(keys: Record<string, string>): SymbolIndex {
  return {
    has: (k) => k in keys,
    kindOf: (k) => keys[k],
    candidates: (parts, scope) => {
      const tail = '.' + parts.join('.');
      return Object.keys(keys).filter((k) => {
        if (!k.endsWith(tail) && k !== parts.join('.')) return false;
        if (scope.package !== undefined && scope.package !== '' && !k.startsWith(scope.package + '.')) return false;
        if (scope.model !== undefined && !k.split('.').includes(scope.model)) return false;
        if (scope.schema !== undefined && !k.split('.').includes(scope.schema)) return false;
        return true;
      });
    },
  };
}

describe('resolveReference — slot filling (architecture §5)', () => {
  const site = { filePackage: 'shop.sales', headerSchema: 'dbo', projectDefaultSchema: 'dbo' };

  it('full elision: bare Orders in a db file → exact canonical key', () => {
    const idx = indexOf({ 'shop.sales.db.dbo.table.Orders': 'table' });
    const r = resolveReference(classifyReference('Orders', VOCAB), { ...site, expectedKind: 'table' }, VOCAB, idx);
    expect(r.resolved).toBe(true);
    if (r.resolved) {
      expect(r.key).toBe('shop.sales.db.dbo.table.Orders');
      expect(r.qname.model).toBe('db');
      expect(r.viaUniqueMatch).toBe(false);
    }
  });

  it('context kind: target table fills model db + schema from default', () => {
    const idx = indexOf({ 'shop.sales.db.dbo.table.Customers': 'table' });
    const r = resolveReference(classifyReference('Customers', VOCAB), { ...site, expectedKind: 'table' }, VOCAB, idx);
    expect(r.resolved).toBe(true);
    if (r.resolved) expect(r.key).toBe('shop.sales.db.dbo.table.Customers');
  });

  it('er has no schema slot', () => {
    const idx = indexOf({ 'shop.core.er.entity.customer': 'entity' });
    const r = resolveReference(
      classifyReference('shop.core.customer', VOCAB),
      { filePackage: 'shop.core', expectedKind: 'entity' },
      VOCAB,
      idx,
    );
    expect(r.resolved).toBe(true);
    if (r.resolved) {
      expect(r.key).toBe('shop.core.er.entity.customer');
      expect(r.qname.schema).toBeUndefined();
    }
  });

  it('explicit escape hatch db.dbo.Orders resolves regardless of file package', () => {
    const idx = indexOf({ 'shop.sales.db.dbo.table.Orders': 'table' });
    const r = resolveReference(
      classifyReference('db.dbo.Orders', VOCAB),
      { filePackage: 'other.pkg', expectedKind: 'table' },
      VOCAB,
      idx,
    );
    expect(r.resolved).toBe(true);
    if (r.resolved) expect(r.key).toBe('shop.sales.db.dbo.table.Orders');
  });

  it('ambiguous: same name in two packages with no scoping → AmbiguousReference', () => {
    const idx = indexOf({
      'a.db.dbo.table.Orders': 'table',
      'b.db.dbo.table.Orders': 'table',
    });
    const r = resolveReference(
      classifyReference('Orders', VOCAB),
      { filePackage: 'c', expectedKind: 'table' },
      VOCAB,
      idx,
    );
    expect(r.resolved).toBe(false);
    if (!r.resolved) expect(r.code).toBe('AmbiguousReference');
  });

  it('scoped unique-match wins over a cross-package duplicate', () => {
    const idx = indexOf({
      'shop.sales.db.dbo.table.Orders': 'table',
      'other.db.dbo.table.Orders': 'table',
    });
    const r = resolveReference(classifyReference('Orders', VOCAB), { ...site, expectedKind: 'table' }, VOCAB, idx);
    expect(r.resolved).toBe(true);
    if (r.resolved) expect(r.key).toBe('shop.sales.db.dbo.table.Orders');
  });

  it('cross-schema unique-match is flagged viaUniqueMatch (lintable, D10)', () => {
    const idx = indexOf({ 'far.db.dbo.table.Widget': 'table' });
    const r = resolveReference(
      classifyReference('Widget', VOCAB),
      { filePackage: 'here', expectedKind: 'table' },
      VOCAB,
      idx,
    );
    expect(r.resolved).toBe(true);
    if (r.resolved) expect(r.viaUniqueMatch).toBe(true);
  });

  it('unresolved name → UnresolvedReference', () => {
    const idx = indexOf({ 'shop.sales.db.dbo.table.Orders': 'table' });
    const r = resolveReference(classifyReference('Nonexistent', VOCAB), { ...site, expectedKind: 'table' }, VOCAB, idx);
    expect(r.resolved).toBe(false);
    if (!r.resolved) expect(r.code).toBe('UnresolvedReference');
  });
});
