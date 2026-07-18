// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { layoutAuto, LayoutController, type LayoutInput } from '../layout.js';
import type { CanvasGraph, CanvasNode } from '../types.js';
import type { CanvasViewState } from '../viewstate.js';

// small hero-er-shaped graph (3 entities, 2 relations)
const heroEr: CanvasGraph = {
  id: 'er-graph', face: 'modeling', kind: 'er', containers: [],
  nodes: [
    { id: 'Customer', qname: 'er.Customer', kind: 'entity', label: 'Customer', ports: [], slotData: {} },
    { id: 'Order', qname: 'er.Order', kind: 'entity', label: 'Order', ports: [], slotData: {} },
    { id: 'OrderLine', qname: 'er.OrderLine', kind: 'entity', label: 'OrderLine', ports: [], slotData: {} },
  ],
  edges: [
    { id: 'e1', from: { node: 'Customer', port: 'o' }, to: { node: 'Order', port: 'i' }, role: 'data' },
    { id: 'e2', from: { node: 'Order', port: 'o' }, to: { node: 'OrderLine', port: 'i' }, role: 'data' },
  ],
};

const input: LayoutInput = {
  orientation: 'LR',
  sizeOf: (_n: CanvasNode) => ({ width: 160, height: 72 }),
};

describe('layout seam (contracts §3 / C1-b wall)', () => {
  it('auto-layout is deterministic — same graph + input ⇒ byte-identical positions', async () => {
    const a = await layoutAuto(heroEr, input);
    const b = await layoutAuto(heroEr, input);
    expect(JSON.stringify(a)).toBe(JSON.stringify(b));
    expect(Object.keys(a).sort()).toEqual(['Customer', 'Order', 'OrderLine']);
  });

  it('a custom skin layout bypasses ELK and is used verbatim', async () => {
    const custom = await layoutAuto(heroEr, {
      ...input,
      params: { custom: () => ({ Customer: { x: 1, y: 2 }, Order: { x: 3, y: 4 }, OrderLine: { x: 5, y: 6 } }) },
    });
    expect(custom.Order).toEqual({ x: 3, y: 4 });
  });

  it('LayoutController starts in auto and exposes the auto positions', async () => {
    const auto = await layoutAuto(heroEr, input);
    const ctl = new LayoutController(auto);
    expect(ctl.mode).toBe('auto');
    expect(ctl.positions).toEqual(auto);
  });

  it('a node drag flips to manual and preserves the dragged position', async () => {
    const auto = await layoutAuto(heroEr, input);
    const ctl = new LayoutController(auto);
    ctl.nodeDragged('Order', { x: 999, y: 888 });
    expect(ctl.mode).toBe('manual');
    expect(ctl.positions.Order).toEqual({ x: 999, y: 888 });
    expect(ctl.positions.Customer).toEqual(auto.Customer); // untouched nodes keep auto
    expect(ctl.manualPositions).toEqual({ Order: { x: 999, y: 888 } });
  });

  it('resetToAuto returns to the deterministic result and clears manual', async () => {
    const auto = await layoutAuto(heroEr, input);
    const ctl = new LayoutController(auto);
    ctl.nodeDragged('Order', { x: 1, y: 1 });
    ctl.resetToAuto();
    expect(ctl.mode).toBe('auto');
    expect(ctl.positions).toEqual(auto);
    expect(ctl.manualPositions).toEqual({});
  });

  it('CanvasViewState has no viewport field — viewport is never persisted (invariant 6)', () => {
    const vs: CanvasViewState = { skin: 'er.crow', mode: 'manual', nodes: { Order: { x: 1, y: 2 } }, collapsed: [] };
    expect('viewport' in vs).toBe(false);
    // type-level: assigning a viewport key would be a compile error (asserted by construction)
    expect(Object.keys(vs).sort()).toEqual(['collapsed', 'mode', 'nodes', 'skin']);
  });
});
