import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom';

vi.mock('cytoscape', () => {
  const mockUse = vi.fn();
  const mockDefault = vi.fn(() => ({
    elements: vi.fn(() => ({ remove: vi.fn().mockReturnThis() })),
    add: vi.fn().mockReturnThis(),
    layout: vi.fn(() => ({ run: vi.fn() })),
    nodes: vi.fn(() => ({ forEach: vi.fn() })),
    on: vi.fn(),
    nodeHtmlLabel: vi.fn(),
    destroy: vi.fn(),
  }));
  (mockDefault as unknown as Record<string, unknown>).use = mockUse;
  return { default: mockDefault, use: mockUse };
});
vi.mock('cytoscape-cose-bilkent', () => ({ default: vi.fn() }));
vi.mock('cytoscape-node-html-label', () => ({ default: vi.fn() }));

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { FakeWebSocket } from '../data/__tests__/fake-websocket.js';
import { WsModeApp } from '../WsModeApp.js';
import { WsDesignerServerDataSource } from '../data/ws-designer-server-data-source.js';

const FIXTURES = join(dirname(fileURLToPath(import.meta.url)), '..', 'data', '__tests__', 'fixtures', 'ttrm');
const resultOf = (name: string) => JSON.parse(readFileSync(join(FIXTURES, name), 'utf8')).result;

class AutoWs extends FakeWebSocket {
  send(data: string): void {
    super.send(data);
    const req = JSON.parse(data) as { id: number; method: string };
    const map: Record<string, string> = {
      'ttrm/getStatus': 'get-status.json',
      'ttrm/getModelIndex': 'get-model-index.json',
      'ttrm/getModelGraph': 'get-model-graph.json',
      'ttrm/getObject': 'get-object.json',
      'ttrm/search': 'search.json',
      'ttrm/listGraphs': 'list-graphs.json',
      'ttrm/getGraph': 'get-graph.json',
      'ttrm/getLayout': 'get-layout-absent.json',
    };
    const file = map[req.method];
    if (file) queueMicrotask(() => this.receive({ jsonrpc: '2.0', id: req.id, result: resultOf(file) }));
  }
}

beforeEach(() => {
  (globalThis as unknown as { WebSocket: unknown }).WebSocket = class {
    constructor(url: string) {
      return new AutoWs(url) as unknown as object;
    }
  };
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// History: this file has tracked the WS host's edit story through three regimes.
// Pre-T4 it asserted WS mode was unconditionally read-only (M3.2 scope boundary);
// the T4/TP-5 arc gave `ttr-designer-server` real write RPCs and this file briefly
// asserted per-view editing. FO-21 (FO-P0.S2.T4) reverses that for the OPEN build:
// the Studio Viewer's WS mode is READ + view-persistence only. The model-mutating
// affordances ("+ Add object" / remove / "+ New" graph) moved to the authoring
// extension, and their `ttrm/*` write routes split into `ttr-designer-edit-server`
// (FO-P0.S2.T5). Drag-persist of a selected graph's layout STAYS (FO-31). This
// file now pins the read-only contract; the edit round-trips re-enter as an
// authoring-extension integration test in FO-P0.S4.
describe('WS mode is read-only in the Studio Viewer build (FO-21, FO-P0.S2.T4)', () => {
  it('the WS data source advertises edit === false (mutation RPCs moved to the platform edit server)', () => {
    expect(new WsDesignerServerDataSource('ws://127.0.0.1:7270').capabilities.edit).toBe(false);
  });

  it('the schema/package browse view exposes no edit affordances', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('db')).toBeInTheDocument());
    fireEvent.click(screen.getByText('db'));
    await waitFor(() => expect(screen.getByTestId('ws-canvas-stats')).toHaveTextContent('2/1'));

    expect(screen.queryByPlaceholderText(/qname to add/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/remove from graph/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText('New graph')).not.toBeInTheDocument();
    expect(screen.getByText(/read-only \(server\)/i)).toBeInTheDocument();
  });

  it('selecting a specific .ttrg graph renders it read-only (view + layout persistence, no model-edit UI)', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('all_er')).toBeInTheDocument());
    fireEvent.click(screen.getByText('all_er'));

    // The graph view still renders; there is just no add-object bar or edit badge.
    await waitFor(() => expect(screen.getByTestId('ws-canvas-stats')).toBeInTheDocument());
    expect(screen.queryByPlaceholderText(/qname to add/i)).not.toBeInTheDocument();
    expect(screen.getByText(/read-only \(server\)/i)).toBeInTheDocument();
  });
});
