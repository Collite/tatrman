// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { processingGraphToCanvas, type ProcessingGraph } from '../processing-map.js';

// DS-P5.S1 — ProcessingGraph (source shape) → CanvasGraph (kernel model, contracts §3).
// Containers become collapsed REGIONS (kind 'container-ref' + a CanvasContainer entry, D-3 β);
// store/display are program-level leaves; node-level edges resolve to real port ids; a control
// edge synthesizes cross-axis control ports when the endpoints declare none (D-4); declared
// ports (incl. the unconnected rejects stub) survive untouched (D-2).

const HERO: ProcessingGraph = {
  id: 'monthly_sales',
  face: 'processing',
  nodes: [
    {
      id: 'extract', qname: 'monthly_sales.extract', kind: 'container', label: 'extract',
      engine: 'sql @ mssql', fragmentDerived: true, collapsed: true, bodyText: '"""sql\n  select …',
      ports: [
        { id: 'extract.orders', direction: 'out', role: 'data', connected: true, label: 'orders' },
        { id: 'extract.lines', direction: 'out', role: 'data', connected: true, label: 'lines' },
      ],
    },
    {
      id: 'crunch', qname: 'monthly_sales.crunch', kind: 'container', label: 'crunch',
      engine: 'polars', collapsed: true, bodyText: 'join + aggregate',
      ports: [
        { id: 'crunch.in_orders', direction: 'in', role: 'data', connected: true, label: 'orders' },
        { id: 'crunch.in_lines', direction: 'in', role: 'data', connected: true, label: 'lines' },
        { id: 'crunch.out', direction: 'out', role: 'data', connected: true, label: 'monthly_sales' },
        { id: 'crunch.rejects', direction: 'out', role: 'rejects', connected: false, label: 'rejects ∅' },
      ],
    },
    {
      id: 'store', qname: 'monthly_sales.store', kind: 'store', label: 'store monthly_sales',
      ports: [{ id: 'store.in', direction: 'in', role: 'data', connected: true }],
    },
    {
      id: 'display', qname: 'monthly_sales.display', kind: 'display', label: 'display top_customers',
      slotData: { previewRows: 5 },
      ports: [{ id: 'display.in', direction: 'in', role: 'data', connected: true }],
    },
  ],
  edges: [
    { id: 'e_transfer', from: 'extract', to: 'crunch', role: 'transfer', label: '⇄ transfer' },
    { id: 'e_store', from: 'crunch', to: 'store', role: 'data' },
    { id: 'e_display', from: 'crunch', to: 'display', role: 'data' },
    { id: 'e_after', from: 'store', to: 'display', role: 'control', label: 'after' },
  ],
};

describe('processingGraphToCanvas', () => {
  const cg = processingGraphToCanvas(HERO);

  it('produces a processing-face CanvasGraph keyed by the program id', () => {
    expect(cg.face).toBe('processing');
    expect(cg.id).toBe('monthly_sales');
    expect(cg.nodes).toHaveLength(4);
  });

  it('maps container nodes to kind "container-ref" + a collapsed CanvasContainer (region, D-3 β)', () => {
    const extract = cg.nodes.find((n) => n.id === 'extract')!;
    expect(extract.kind).toBe('container-ref');
    const region = cg.containers.find((c) => c.id === 'extract')!;
    expect(region).toBeDefined();
    expect(region.collapsed).toBe(true);
    expect(region.engine).toBe('sql @ mssql');
    expect(region.fragmentDerived).toBe(true);
    // engine/dialect/fragment metadata rides slotData for the skin's region render too
    expect(extract.slotData.engine).toBe('sql @ mssql');
    expect(extract.slotData.fragmentDerived).toBe(true);
  });

  it('keeps store/display as program-level leaves (not containers)', () => {
    expect(cg.nodes.find((n) => n.id === 'store')!.kind).toBe('store');
    expect(cg.nodes.find((n) => n.id === 'display')!.kind).toBe('display');
    expect(cg.containers.map((c) => c.id)).toEqual(['extract', 'crunch']);
  });

  it('preserves declared ports incl. the unconnected rejects stub (D-2)', () => {
    const crunch = cg.nodes.find((n) => n.id === 'crunch')!;
    const rejects = crunch.ports.find((p) => p.role === 'rejects')!;
    expect(rejects).toBeDefined();
    expect(rejects.connected).toBe(false);
    // port labels survive for the skin's port strip
    expect((crunch.slotData.ports as Array<{ id: string; label?: string }>).some((p) => p.label === 'rejects ∅')).toBe(true);
  });

  it('resolves node-level data/transfer edges to real out→in data port ids', () => {
    const transfer = cg.edges.find((e) => e.id === 'e_transfer')!;
    expect(transfer.role).toBe('transfer');
    expect(transfer.from.node).toBe('extract');
    expect(transfer.from.port).toBe('extract.orders'); // first out/data
    expect(transfer.to.node).toBe('crunch');
    expect(transfer.to.port).toBe('crunch.in_orders'); // first in/data
    const store = cg.edges.find((e) => e.id === 'e_store')!;
    expect(store.from.port).toBe('crunch.out');
    expect(store.to.port).toBe('store.in');
  });

  it('synthesizes cross-axis control ports when a control edge endpoint declares none (D-4)', () => {
    const after = cg.edges.find((e) => e.id === 'e_after')!;
    expect(after.role).toBe('control');
    expect(after.from.port).toBe('store::ctrl-out');
    expect(after.to.port).toBe('display::ctrl-in');
    // the synthesized ports exist on the nodes so the kernel draws handles for them
    const store = cg.nodes.find((n) => n.id === 'store')!;
    expect(store.ports.some((p) => p.id === 'store::ctrl-out' && p.role === 'control' && p.direction === 'out')).toBe(true);
    const display = cg.nodes.find((n) => n.id === 'display')!;
    expect(display.ports.some((p) => p.id === 'display::ctrl-in' && p.role === 'control' && p.direction === 'in')).toBe(true);
  });

  it('honors explicit fromPort/toPort so distinct same-role ports are not collapsed', () => {
    // two edges into a node with TWO in-data ports — without explicit ports both would collapse
    // onto the first (crunch.in_orders); with them each edge routes to its own port.
    const g = processingGraphToCanvas({
      id: 'p', face: 'processing',
      nodes: [
        { id: 'a', qname: 'a', kind: 'op', label: 'a', ports: [{ id: 'a.out', direction: 'out', role: 'data', connected: true }] },
        { id: 'b', qname: 'b', kind: 'op', label: 'b', ports: [{ id: 'b.out', direction: 'out', role: 'data', connected: true }] },
        { id: 'c', qname: 'c', kind: 'op', label: 'c', ports: [
          { id: 'c.in_orders', direction: 'in', role: 'data', connected: true },
          { id: 'c.in_lines', direction: 'in', role: 'data', connected: true },
        ] },
      ],
      edges: [
        { id: 'e1', from: 'a', to: 'c', role: 'data', toPort: 'c.in_orders' },
        { id: 'e2', from: 'b', to: 'c', role: 'data', toPort: 'c.in_lines' },
      ],
    });
    expect(g.edges.find((e) => e.id === 'e1')!.to.port).toBe('c.in_orders');
    expect(g.edges.find((e) => e.id === 'e2')!.to.port).toBe('c.in_lines'); // NOT collapsed onto in_orders
  });

  it('carries a derived flag through when the source graph is derived (fragment drill-in)', () => {
    const derived = processingGraphToCanvas({ ...HERO, derived: true });
    expect(derived.nodes.length).toBeGreaterThan(0);
    // the mapper marks the graph; the canvas host reads it to render read-only + DS-CANV-002
    expect(processingGraphToCanvas({ ...HERO, derived: true }).derived).toBe(true);
    expect(cg.derived).toBeFalsy();
  });
});
