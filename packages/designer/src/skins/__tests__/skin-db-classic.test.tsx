import { describe, it, expect, beforeAll } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { dbTableClassic } from '../db-table-classic.js';
import { createSkinRegistry } from '../index.js';
import { CanvasKernel } from '../../canvas/Kernel.js';
import { installBrowserPolyfills, anchors } from '../../canvas/__tests__/test-utils.js';
import type { CanvasGraph, CanvasNode } from '@tatrman/canvas-core';

beforeAll(() => installBrowserPolyfills());

function dbNode(id: string, cols: Array<{ name: string; isKey?: boolean; type?: string }>): CanvasNode {
  return {
    id, qname: `db.dbo.${id}`, kind: 'table', label: id,
    ports: [
      { id: `${id}::out`, direction: 'out', role: 'data', connected: true },
      { id: `${id}::in`, direction: 'in', role: 'data', connected: false },
    ],
    slotData: {
      rows: cols.map((c) => ({ name: c.name, qname: `db.dbo.${id}.${c.name}`, kind: 'column', type: c.type ?? null, isKey: !!c.isKey, optional: false, isNameAttribute: false, isCodeAttribute: false })),
    },
  };
}

describe('db.table-classic skin — column rows, keys, types', () => {
  it('renders table kind mark, label, column rows with key marks AND types', () => {
    const node = dbNode('Customer', [{ name: 'CustomerKey', isKey: true, type: 'int' }, { name: 'Region', type: 'text' }]);
    const Body = dbTableClassic.renderNode;
    render(<Body node={node} state={{ selected: false, focused: false, readOnly: false, derived: false, orphanedLayout: false }} anchors={anchors} theme="ice" />);
    expect(screen.getByTestId('kind-mark')).toHaveTextContent('▦');
    expect(screen.getByTestId('node-label')).toHaveTextContent('Customer');
    const rows = screen.getAllByTestId('card-row');
    expect(within(rows[0]).getByTestId('key-mark')).toHaveTextContent('⚿'); // key column marked
    expect(within(rows[0]).getByTestId('row-type')).toHaveTextContent('int'); // type shown (db shows types)
    expect(within(rows[1]).getByTestId('row-type')).toHaveTextContent('text');
  });

  it('is the registered db default', () => {
    const reg = createSkinRegistry();
    expect(reg.defaultSkin('modeling', 'db')).toBe('db.table-classic');
  });

  it('every port visible incl. an UNCONNECTED port (D-2 — visibility is base)', () => {
    const reg = createSkinRegistry();
    const graph: CanvasGraph = { id: 'db-graph', face: 'modeling', kind: 'db', containers: [], nodes: [dbNode('Customer', [{ name: 'CustomerKey', isKey: true, type: 'int' }])], edges: [] };
    const { container } = render(
      <div style={{ width: 600, height: 400 }}>
        <CanvasKernel graph={graph} registry={reg} skinId="db.table-classic" positions={{ Customer: { x: 0, y: 0 } }} />
      </div>,
    );
    // both the connected out-port and the UNCONNECTED in-port render
    const ports = container.querySelectorAll('[data-testid^="port-"]');
    expect(ports.length).toBe(2);
    expect(container.querySelector('[data-connected="false"]')).not.toBeNull();
  });
});
