import { describe, it, expect } from 'vitest';
import type { BindingMapData } from '@tatrman/lsp';
import { buildBindingHints, shortTarget } from '../binding-adapter.js';

// DS-P4.S1 — the binding adapter's qname→short-label heuristic is the most bug-prone surface in
// the model layer (it splits canonical qnames by convention). These tests pin its behavior on the
// canonical shape AND on the awkward inputs the review flagged (a segment literally named "table",
// missing markers, leaf-only refs).

describe('shortTarget (binding-adapter qname heuristic)', () => {
  it('canonical db table → schema.Name', () => {
    expect(shortTarget('orders_hero.db.dbo.table.OrderLine')).toBe('dbo.OrderLine');
  });

  it('uses the LAST "table" segment so an earlier literal "table" cannot hijack the split', () => {
    // a package/schema segment literally named "table" must not steal the split
    expect(shortTarget('table.db.dbo.table.OrderLine')).toBe('dbo.OrderLine');
  });

  it('multi-segment table name after the marker is preserved', () => {
    expect(shortTarget('pkg.db.dbo.table.Sales.Fact')).toBe('dbo.Sales.Fact');
  });

  it('no "table" marker (query / entity leaf) → the leaf segment', () => {
    expect(shortTarget('pkg.query.ActiveCustomer')).toBe('ActiveCustomer');
    expect(shortTarget('Customer')).toBe('Customer');
  });
});

describe('buildBindingHints', () => {
  const data: BindingMapData = {
    entities: [
      { entityQname: 'orders_hero.er.entity.Customer', target: { kind: 'table', tableQname: 'orders_hero.db.dbo.table.Customer' } },
      { entityQname: 'orders_hero.er.entity.ActiveCustomer', target: { kind: 'query', queryQname: 'orders_hero.query.ActiveCustomer' } },
      { entityQname: 'orders_hero.er.entity.Orphan', target: { kind: 'unresolved', raw: 'db.dbo.table.Missing' } },
      { entityQname: 'orders_hero.er.entity.Void', target: { kind: 'unresolved' } },
    ],
    attributes: [],
    queries: [],
  };

  it('maps each entity qname to a ghost chip with the right kind + shortened target', () => {
    const hints = buildBindingHints(data);
    expect(hints['orders_hero.er.entity.Customer']).toEqual({ target: 'dbo.Customer', kind: 'table' });
    expect(hints['orders_hero.er.entity.ActiveCustomer']).toEqual({ target: 'ActiveCustomer', kind: 'query' });
    expect(hints['orders_hero.er.entity.Orphan']).toEqual({ target: 'dbo.Missing', kind: 'unresolved' }); // raw canonical form shortened like a table
    expect(hints['orders_hero.er.entity.Void']).toEqual({ target: 'unresolved', kind: 'unresolved' });
  });

  it('is keyed by entity qname (the er CanvasGraph node id) so hints land on the right node', () => {
    const hints = buildBindingHints(data);
    expect(Object.keys(hints)).toEqual([
      'orders_hero.er.entity.Customer',
      'orders_hero.er.entity.ActiveCustomer',
      'orders_hero.er.entity.Orphan',
      'orders_hero.er.entity.Void',
    ]);
  });
});
