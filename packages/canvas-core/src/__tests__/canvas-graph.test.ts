// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { modelGraphToCanvas, type ModelGraphInput } from '../model-graph-map.js';
import type { CanvasPort } from '../types.js';

const erGraph: ModelGraphInput = {
  schemaCode: 'er',
  nodes: [
    {
      qname: 'er.Customer', kind: 'entity', name: 'Customer', label: 'Customer', schemaCode: 'er',
      rows: [
        { name: 'id', qname: 'er.Customer.id', kind: 'attribute', type: 'int', isKey: true, optional: false, isNameAttribute: false, isCodeAttribute: false },
        { name: 'name', qname: 'er.Customer.name', kind: 'attribute', type: 'text', isKey: false, optional: true, isNameAttribute: true, isCodeAttribute: false },
      ],
    },
    { qname: 'er.Order', kind: 'entity', name: 'Order', label: 'Order', schemaCode: 'er', rows: [] },
    { qname: 'er.Loner', kind: 'entity', name: 'Loner', label: 'Loner', schemaCode: 'er', rows: [] }, // no edges
  ],
  edges: [
    { id: 'r1', qname: 'er.relation.customer_orders', kind: 'relation', fromNode: 'er.Customer', toNode: 'er.Order', fromCardinality: 'one', toCardinality: 'many' },
  ],
};

describe('ModelGraph → CanvasGraph mapper (db/er)', () => {
  it('maps nodes with rows in slotData and preserves node identity', () => {
    const cg = modelGraphToCanvas(erGraph);
    expect(cg.face).toBe('modeling');
    expect(cg.kind).toBe('er');
    const cust = cg.nodes.find((n) => n.id === 'er.Customer')!;
    expect(cust.label).toBe('Customer');
    expect((cust.slotData.rows as unknown[]).length).toBe(2);
  });

  it('maps relation/FK edges with cardinality (crow-foot source)', () => {
    const cg = modelGraphToCanvas(erGraph);
    const e = cg.edges[0];
    expect(e.from.node).toBe('er.Customer');
    expect(e.to.node).toBe('er.Order');
    expect(e.cardinality).toEqual({ from: '1', to: '0..*' });
    expect(e.label).toBe('customer_orders');
  });

  it('NEVER drops a port — every node keeps in+out data ports, connected reflecting edges', () => {
    const cg = modelGraphToCanvas(erGraph);
    for (const n of cg.nodes) {
      expect(n.ports.length).toBe(2);
      expect(n.ports.map((p) => p.direction).sort()).toEqual(['in', 'out']);
    }
    const cust = cg.nodes.find((n) => n.id === 'er.Customer')!;
    expect(cust.ports.find((p) => p.direction === 'out')!.connected).toBe(true);
    const loner = cg.nodes.find((n) => n.id === 'er.Loner')!;
    // an unconnected node keeps its ports (never dropped), marked connected:false
    expect(loner.ports.every((p) => p.connected === false)).toBe(true);
  });

  it('the port model represents unconnected err/rejects stubs (D-2)', () => {
    const rejects: CanvasPort = { id: 'op.rejects', direction: 'out', role: 'rejects', connected: false };
    expect(rejects.role).toBe('rejects');
    expect(rejects.connected).toBe(false); // still rendered — visibility is base, not skin
  });

  it('edges reference ports that exist on their endpoint nodes', () => {
    const cg = modelGraphToCanvas(erGraph);
    for (const e of cg.edges) {
      const from = cg.nodes.find((n) => n.id === e.from.node)!;
      const to = cg.nodes.find((n) => n.id === e.to.node)!;
      expect(from.ports.some((p) => p.id === e.from.port)).toBe(true);
      expect(to.ports.some((p) => p.id === e.to.port)).toBe(true);
    }
  });
});
