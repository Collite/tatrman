import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom';

// Cytoscape is heavy + canvas-based; stub it (same pattern as Canvas.test.tsx).
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

const FIXTURES = join(dirname(fileURLToPath(import.meta.url)), '..', 'data', '__tests__', 'fixtures', 'ttrm');
const resultOf = (name: string) => JSON.parse(readFileSync(join(FIXTURES, name), 'utf8')).result;

// A FakeWebSocket that auto-answers each ttrm/* request from the canned fixtures.
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
    };
    const file = map[req.method];
    if (file) queueMicrotask(() => this.receive({ jsonrpc: '2.0', id: req.id, result: resultOf(file) }));
  }
}

let lastSocket: AutoWs | null = null;
beforeEach(() => {
  lastSocket = null;
  // WsDesignerServerDataSource uses the global WebSocket; override it with our fake.
  (globalThis as unknown as { WebSocket: unknown }).WebSocket = class {
    constructor(url: string) {
      lastSocket = new AutoWs(url);
      return lastSocket as unknown as object;
    }
  };
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('WsModeApp', () => {
  it('renders the model index (schemas + packages)', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('db')).toBeInTheDocument());
    expect(screen.getByText('er')).toBeInTheDocument();
    expect(screen.getByText('acme.erp')).toBeInTheDocument();
  });

  it('renders the graph (2 nodes / 1 edge) when a schema is selected', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('db')).toBeInTheDocument());
    fireEvent.click(screen.getByText('db'));
    await waitFor(() => expect(screen.getByTestId('ws-canvas-stats')).toHaveTextContent('2/1'));
  });

  it('search shows a hit and selecting it loads object detail', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('db')).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText('Search model'), { target: { value: 'cust' } });
    await waitFor(() => expect(screen.getByText('acme.erp.db.customers')).toBeInTheDocument());
    fireEvent.click(screen.getByText('acme.erp.db.customers'));
    // Object detail panel shows the qname.
    await waitFor(() => {
      const dds = screen.getAllByText('acme.erp.db.customers');
      expect(dds.length).toBeGreaterThan(0);
    });
  });

  it('a modelChanged notification triggers a re-fetch + toast', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('db')).toBeInTheDocument());
    lastSocket!.receive({ jsonrpc: '2.0', method: 'ttrm/modelChanged', params: { modelVersion: 'm-4b01' } });
    await waitFor(() => expect(screen.getByText(/model reloaded \(m-4b01\)/)).toBeInTheDocument());
  });

  it('shows an error card when the server cannot be reached', async () => {
    // Override with a socket that closes immediately instead of opening.
    (globalThis as unknown as { WebSocket: unknown }).WebSocket = class {
      onopen: (() => void) | null = null;
      onclose: (() => void) | null = null;
      onerror: ((e: unknown) => void) | null = null;
      onmessage: (() => void) | null = null;
      constructor() {
        queueMicrotask(() => this.onerror?.(new Error('refused')));
      }
      send() {}
      close() {}
    };
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('Cannot connect to server')).toBeInTheDocument());
  });
});
