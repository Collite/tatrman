import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, cleanup, act } from '@testing-library/react';
import '@testing-library/jest-dom';

// Capture the cytoscape instance's registered 'dragfree'/'tap' handlers so
// tests can fire them directly — the mocked canvas renders no real DOM text
// for node labels (same technique as Canvas.test.tsx's drag tests).
let dragfreeHandler: ((evt: unknown) => void) | null = null;
let tapNodeHandler: ((evt: unknown) => void) | null = null;
vi.mock('cytoscape', () => {
  const mockUse = vi.fn();
  const mockDefault = vi.fn(() => ({
    elements: vi.fn(() => ({ remove: vi.fn().mockReturnThis() })),
    add: vi.fn().mockReturnThis(),
    layout: vi.fn(() => ({ run: vi.fn() })),
    nodes: vi.fn(() => ({ forEach: vi.fn() })),
    on: vi.fn((event: string, selectorOrHandler: unknown, maybeHandler?: unknown) => {
      if (event === 'dragfree') dragfreeHandler = (maybeHandler ?? selectorOrHandler) as (evt: unknown) => void;
      if (event === 'tap' && typeof selectorOrHandler === 'string' && selectorOrHandler === 'node') {
        tapNodeHandler = maybeHandler as (evt: unknown) => void;
      }
    }),
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

const sent: Array<{ method: string; params: unknown }> = [];
const lastParamsFor = (method: string): unknown => [...sent].reverse().find((r) => r.method === method)?.params;

class AutoWs extends FakeWebSocket {
  send(data: string): void {
    super.send(data);
    const req = JSON.parse(data) as { id: number; method: string; params?: unknown };
    sent.push({ method: req.method, params: req.params });
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
    if (req.method === 'ttrm/setLayout') {
      queueMicrotask(() => this.receive({ jsonrpc: '2.0', id: req.id, result: { ok: true } }));
      return;
    }
    if (req.method === 'ttrm/addObjectToGraph') {
      queueMicrotask(() => this.receive({ jsonrpc: '2.0', id: req.id, result: { ok: true, objectCount: 2 } }));
      return;
    }
    if (req.method === 'ttrm/removeObjectFromGraph') {
      queueMicrotask(() => this.receive({ jsonrpc: '2.0', id: req.id, result: { ok: true, objectCount: 0 } }));
      return;
    }
    if (req.method === 'ttrm/createGraph') {
      queueMicrotask(() => this.receive({ jsonrpc: '2.0', id: req.id, result: { ok: true, uri: 'file:///work/fixture-repo/graphs/new_one.ttrg' } }));
      return;
    }
    const file = map[req.method];
    if (file) queueMicrotask(() => this.receive({ jsonrpc: '2.0', id: req.id, result: resultOf(file) }));
  }
}

beforeEach(() => {
  dragfreeHandler = null;
  tapNodeHandler = null;
  sent.length = 0;
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

async function enterGraphView() {
  render(<WsModeApp origin="ws://127.0.0.1:7270" />);
  await waitFor(() => expect(screen.getByText('all_er')).toBeInTheDocument());
  fireEvent.click(screen.getByText('all_er'));
  await waitFor(() => expect(screen.getByText(/editing \(server\)/i)).toBeInTheDocument());
}

describe('WsModeApp edit flows (T4, TP-5)', () => {
  it('drag → dragfree calls ttrm/setLayout with the dragged position', async () => {
    await enterGraphView();
    await waitFor(() => expect(dragfreeHandler).not.toBeNull());

    act(() => {
      dragfreeHandler!({ target: { data: () => 'acme.erp.db.customers', position: () => ({ x: 42, y: 7 }) } });
    });

    await waitFor(() => expect(sent.some((r) => r.method === 'ttrm/setLayout')).toBe(true));
    const params = lastParamsFor('ttrm/setLayout') as {
      uri: string;
      canvases: Array<{ nodes: Array<{ qname: string; x: number; y: number }> }>;
    };
    expect(params.uri).toBe('file:///proj/graphs/all_er.ttrg');
    expect(params.canvases[0].nodes).toContainEqual({ qname: 'acme.erp.db.customers', x: 42, y: 7 });
  });

  it('"+ Add object" calls ttrm/addObjectToGraph with the typed qname', async () => {
    await enterGraphView();
    fireEvent.change(screen.getByPlaceholderText(/qname to add/i), { target: { value: 'acme.erp.db.new_table' } });
    fireEvent.click(screen.getByText('+ Add object'));

    await waitFor(() => expect(sent.some((r) => r.method === 'ttrm/addObjectToGraph')).toBe(true));
    const params = lastParamsFor('ttrm/addObjectToGraph') as { uri: string; qname: string };
    expect(params.qname).toBe('acme.erp.db.new_table');
  });

  it('selecting a node then "Remove from graph" calls ttrm/removeObjectFromGraph', async () => {
    await enterGraphView();
    await waitFor(() => expect(tapNodeHandler).not.toBeNull());
    act(() => {
      tapNodeHandler!({ target: { data: (key: string) => (key === 'qname' ? 'acme.erp.db.customers' : undefined) } });
    });
    await waitFor(() => expect(screen.getByText('Remove from graph')).toBeInTheDocument());

    fireEvent.click(screen.getByText('Remove from graph'));
    await waitFor(() => expect(sent.some((r) => r.method === 'ttrm/removeObjectFromGraph')).toBe(true));
    const params = lastParamsFor('ttrm/removeObjectFromGraph') as { qname: string };
    expect(params.qname).toBe('acme.erp.db.customers');
  });

  it('the create-graph form calls ttrm/createGraph and switches into the new graph', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('db')).toBeInTheDocument());

    fireEvent.click(screen.getByLabelText('New graph'));
    fireEvent.change(screen.getByPlaceholderText('graph name'), { target: { value: 'new_one' } });
    fireEvent.click(screen.getByText('Create'));

    await waitFor(() => expect(sent.some((r) => r.method === 'ttrm/createGraph')).toBe(true));
    const params = lastParamsFor('ttrm/createGraph') as { uri: string; name: string; schema: string };
    expect(params.uri).toBe('file:///work/fixture-repo/graphs/new_one.ttrg');
    expect(params.name).toBe('new_one');
    expect(params.schema).toBe('er');

    // Switches into the editing view for the freshly created graph.
    await waitFor(() => expect(screen.getByText(/editing \(server\)/i)).toBeInTheDocument());
  });
});
