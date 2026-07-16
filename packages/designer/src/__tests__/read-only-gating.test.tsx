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

// Pre-T4, this file asserted WS mode was UNCONDITIONALLY read-only — that was
// the deliberate M3.2 scope boundary (contracts.md §v1.4(a): richer/edit
// capability "deferred to the C1-f arc"). T4 IS that arc: `ttr-designer-server`
// can now write (T3), so WS mode gained real edit affordances. What's still
// true, and what this file now asserts instead: editing is PER-VIEW, not
// global — the ad-hoc package/schema browse view (no specific `.ttrg` file
// backing it, so nothing to persist positions/membership to) stays read-only;
// only a selected `.ttrg` graph view is editable.
describe('WS mode edit-affordance gating (T4, TP-5)', () => {
  it('the WS data source advertises edit === true (T3 gave it real write RPCs)', () => {
    expect(new WsDesignerServerDataSource('ws://127.0.0.1:7270').capabilities.edit).toBe(true);
  });

  it('the schema/package browse view (no graph selected) stays read-only', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('db')).toBeInTheDocument());
    fireEvent.click(screen.getByText('db'));
    await waitFor(() => expect(screen.getByTestId('ws-canvas-stats')).toHaveTextContent('2/1'));

    // No add-object input, no remove-from-graph button, no editing badge —
    // this view has no `.ttrg` file to persist mutations to.
    expect(screen.queryByPlaceholderText(/qname to add/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/remove from graph/i)).not.toBeInTheDocument();
    expect(screen.getByText(/read-only \(server\)/i)).toBeInTheDocument();
  });

  it('the "+ New" graph affordance is always available (not gated on a selected graph)', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('db')).toBeInTheDocument());
    expect(screen.getByLabelText('New graph')).toBeInTheDocument();
  });

  it('selecting a specific .ttrg graph switches to the editing view', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('all_er')).toBeInTheDocument());
    fireEvent.click(screen.getByText('all_er'));

    await waitFor(() => expect(screen.getByText(/editing \(server\)/i)).toBeInTheDocument());
    expect(screen.getByPlaceholderText(/qname to add/i)).toBeInTheDocument();
  });
});
