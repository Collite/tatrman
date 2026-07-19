// Shared helpers for kernel/skin component tests. React Flow needs ResizeObserver +
// matchMedia (absent in jsdom) — polyfilled here. Also small hero-shaped CanvasGraphs.

import type { CanvasGraph, CanvasNode, NodeBaseState, AnchorDeclaration } from '@tatrman/canvas-core';

class RO {
  observe() {}
  unobserve() {}
  disconnect() {}
}
export function installBrowserPolyfills() {
  (globalThis as unknown as { ResizeObserver: typeof RO }).ResizeObserver = RO;
  if (!window.matchMedia) {
    window.matchMedia = ((q: string) => ({
      matches: false, media: q, onchange: null,
      addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false; },
    })) as unknown as typeof window.matchMedia;
  }
}

export const anchors: AnchorDeclaration = {
  chrome: { x: 12, y: 4, align: 'tl' },
  status: { x: 180, y: 4, align: 'tr' },
  diagnostics: { x: 4, y: 4, align: 'tl' },
};

export const fullState: NodeBaseState = {
  selected: true, focused: false, readOnly: true, derived: false, orphanedLayout: true,
  runStatus: 'failed', diagnostics: { errorCount: 2, warnCount: 1 },
};

export function erNode(id: string, rows: Array<{ name: string; isKey?: boolean; type?: string | null }> = []): CanvasNode {
  return {
    id, qname: `er.${id}`, kind: 'entity', label: id,
    ports: [
      { id: `${id}::out`, direction: 'out', role: 'data', connected: true },
      { id: `${id}::in`, direction: 'in', role: 'data', connected: true },
    ],
    slotData: {
      rows: rows.map((r) => ({
        name: r.name, qname: `er.${id}.${r.name}`, kind: 'attribute', type: r.type ?? null,
        isKey: !!r.isKey, optional: false, isNameAttribute: false, isCodeAttribute: false,
      })),
    },
  };
}

export function heroErGraph(): CanvasGraph {
  return {
    id: 'er-graph', face: 'modeling', kind: 'er', containers: [],
    nodes: [
      erNode('Customer', [{ name: 'id', isKey: true, type: 'int' }, { name: 'name', type: 'text' }]),
      erNode('Order', [{ name: 'id', isKey: true, type: 'int' }]),
    ],
    edges: [
      {
        id: 'r1', from: { node: 'Customer', port: 'Customer::out' }, to: { node: 'Order', port: 'Order::in' },
        role: 'data', label: 'customer_orders', cardinality: { from: '1', to: '0..*' },
      },
    ],
  };
}
