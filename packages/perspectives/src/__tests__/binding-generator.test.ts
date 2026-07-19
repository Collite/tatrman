// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import {
  bindingGenerator, generateBindingRibbon,
  type BindingInput, type BindingMap, type PModelGraph, type BindingRibbon,
} from '../index.js';

// Hero-shaped fixtures (orders_hero binding model). qnames are kept consistent between the
// binding map and the er/db graphs — reconciling live LSP qname formats is the designer
// adapter's job; the pure generator matches exactly.
const er: PModelGraph = {
  nodes: [
    { qname: 'er.entity.Customer', label: 'Customer' },
    { qname: 'er.entity.Order', label: 'Order' },
    { qname: 'er.entity.ActiveCustomer', label: 'Active customer' },
    { qname: 'er.entity.Orphan', label: 'Orphan' },
  ],
};
const db: PModelGraph = {
  nodes: [
    { qname: 'db.dbo.Customer', label: 'Customer master' },
    { qname: 'db.dbo.OrderHeader', label: 'Order header' },
  ],
};

const bindings: BindingMap = {
  entities: [
    { entityQname: 'er.entity.Customer', target: { kind: 'table', tableQname: 'db.dbo.Customer' } },
    { entityQname: 'er.entity.Order', target: { kind: 'table', tableQname: 'db.dbo.OrderHeader' } },
    { entityQname: 'er.entity.ActiveCustomer', target: { kind: 'query', queryQname: 'query.query.active_customers' } },
    // deliberately dangling — its target resolves to nothing (DS-PERSP-002).
    { entityQname: 'er.entity.Orphan', target: { kind: 'unresolved', raw: 'db.dbo.Missing' } },
  ],
  attributes: [
    { attributeQname: 'er.entity.Customer.id', columnQname: 'db.dbo.Customer.CustomerKey' },
    { attributeQname: 'er.entity.Customer.name', columnQname: 'db.dbo.Customer.CustomerName' },
    { attributeQname: 'er.entity.Order.id', columnQname: 'db.dbo.OrderHeader.OrderKey' },
  ],
  queries: [
    { qname: 'query.query.active_customers', predicate: 'Customers active this year', provenance: ['db.dbo.Customer', 'db.dbo.OrderHeader'] },
  ],
};

const baseInput: BindingInput = { er, db, bindings };

describe('binding generator (contracts §4.1, C-2)', () => {
  it('emits exactly one row per bound entity', () => {
    const r = generateBindingRibbon(baseInput);
    expect(r.rows).toHaveLength(4);
    expect(r.rows.map((x) => x.entity.qname)).toEqual([
      'er.entity.Customer', 'er.entity.Order', 'er.entity.ActiveCustomer', 'er.entity.Orphan',
    ]);
  });

  it('table-bound entities are kind:"table" entity→table at rest, labels enriched from the graphs', () => {
    const r = generateBindingRibbon(baseInput);
    const cust = r.rows.find((x) => x.entity.qname === 'er.entity.Customer');
    expect(cust).toMatchObject({
      kind: 'table',
      entity: { qname: 'er.entity.Customer', label: 'Customer' },
      table: { qname: 'db.dbo.Customer', label: 'Customer master' },
    });
  });

  it('a query-bound entity is FIRST-CLASS: kind:"query" with a QueryCard (predicate + base-table provenance)', () => {
    const r = generateBindingRibbon(baseInput);
    const active = r.rows.find((x) => x.entity.qname === 'er.entity.ActiveCustomer');
    expect(active?.kind).toBe('query');
    if (active?.kind !== 'query') throw new Error('expected query row');
    expect(active.query.qname).toBe('query.query.active_customers');
    expect(active.query.predicate).toBe('Customers active this year');
    expect(active.query.provenance.map((t) => t.qname)).toEqual(['db.dbo.Customer', 'db.dbo.OrderHeader']);
    expect(active.query.provenance[0].label).toBe('Customer master'); // enriched
  });

  it('a dangling bind is kept as a DS-PERSP-002 unresolved row — present, never dropped', () => {
    const r = generateBindingRibbon(baseInput);
    const orphan = r.rows.find((x) => x.entity.qname === 'er.entity.Orphan');
    expect(orphan?.kind).toBe('unresolved');
    if (orphan?.kind !== 'unresolved') throw new Error('expected unresolved row');
    expect(orphan.diagnostic).toBe('DS-PERSP-002');
    expect(orphan.detail).toBe('db.dbo.Missing');
  });

  it('selection expands exactly one entity to attribute→column pairs', () => {
    const r = generateBindingRibbon({ ...baseInput, selectedEntity: 'er.entity.Customer' });
    expect(r.expanded?.entity).toBe('er.entity.Customer');
    expect(r.expanded?.pairs).toEqual([
      { attribute: 'id', column: 'CustomerKey' },
      { attribute: 'name', column: 'CustomerName' },
    ]);
    // Order's attribute bindings are not included — exactly one entity expands.
    expect(r.expanded?.pairs.some((p) => p.column === 'OrderKey')).toBe(false);
  });

  it('with no selection there is no expansion', () => {
    expect(generateBindingRibbon(baseInput).expanded).toBeUndefined();
  });

  it('the generator wraps the ribbon in a custom binding-ribbon PerspectiveResult', () => {
    const res = bindingGenerator.generate(baseInput);
    expect(res.kind).toBe('custom');
    if (res.kind !== 'custom') throw new Error('expected custom');
    expect(res.view).toBe('binding-ribbon');
    const ribbon = res.data as BindingRibbon;
    expect(ribbon.rows).toHaveLength(4);
  });
});
