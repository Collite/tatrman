import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { buildModelGraph } from '../model-graph.js';

// DS-P3.S1.T3 — cnc (conceptual) extraction. There is no `concept` def kind in TTR.g4 today
// (a logged Q-5 gap): conceptual concepts are `def entity` and labeled conceptual relations are
// `def relation`, so cnc extraction reuses the er entity/relation node+edge builders outright.
// The concept's attributes ARE its properties (the bubbles/cards skins read them from the rows).
const CNC = `
package orders_hero
model cnc

def entity Customer { description: "A party that places orders",
  attributes: [ def attribute name { type: text } ]
}
def entity Order { description: "A commercial order",
  attributes: [ def attribute placed_on { type: date } ]
}
def entity Product { description: "A sellable product",
  attributes: [ def attribute title { type: text } ]
}

def relation places { description: "Customer places Order",
  from: cnc.entity.Customer, to: cnc.entity.Order,
  cardinality: { from: "1", to: "0..*" }
}
def relation contains { description: "Order contains Product",
  from: cnc.entity.Order, to: cnc.entity.Product,
  cardinality: { from: "1", to: "0..*" }
}
`;

function graph() {
  const parsed = parseString(CNC, 'file:///cnc.ttrm');
  expect(parsed.errors.filter((e) => e.severity === 'error'), 'cnc fixture parses clean').toEqual([]);
  return buildModelGraph(parsed.ast!, 'cnc');
}

describe('buildModelGraph (cnc schema)', () => {
  it('reports schemaCode cnc', () => {
    expect(graph().schemaCode).toBe('cnc');
  });

  it('concepts are nodes carrying their properties (attributes) as rows', () => {
    const g = graph();
    expect(g.nodes.map((n) => n.name).sort()).toEqual(['Customer', 'Order', 'Product']);
    for (const n of g.nodes) expect(n.kind).toBe('entity'); // concepts modeled as entities (Q-5 gap)
    const customer = g.nodes.find((n) => n.name === 'Customer')!;
    expect(customer.rows.map((r) => r.name)).toEqual(['name']);
    expect(customer.rows[0].kind).toBe('attribute');
  });

  it('labeled directed relations become edges with the relation name + cardinality', () => {
    const g = graph();
    expect(g.edges).toHaveLength(2);
    const byName = (n: string) => g.nodes.find((x) => x.name === n)!.qname;

    const places = g.edges.find((e) => e.qname.endsWith('places'))!;
    expect(places.kind).toBe('relation');
    expect(places.fromNode).toBe(byName('Customer'));
    expect(places.toNode).toBe(byName('Order'));
    expect(places.fromCardinality).toBe('one');
    expect(places.toCardinality).toBe('many');

    const contains = g.edges.find((e) => e.qname.endsWith('contains'))!;
    expect(contains.fromNode).toBe(byName('Order'));
    expect(contains.toNode).toBe(byName('Product'));
  });

  it('nodes and edges carry accurate source locations (edit-synth invariant)', () => {
    const g = graph();
    for (const n of g.nodes) expect(n.sourceLocation.line).toBeGreaterThan(0);
    for (const e of g.edges) expect(e.sourceLocation.line).toBeGreaterThan(0);
  });
});
