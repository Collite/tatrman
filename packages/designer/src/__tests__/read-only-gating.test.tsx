import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
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

describe('read-only gating (WS mode)', () => {
  it('the WS data source advertises edit === false', () => {
    expect(new WsDesignerServerDataSource('ws://127.0.0.1:7270').capabilities.edit).toBe(false);
  });

  it('WS mode renders no edit affordances', async () => {
    render(<WsModeApp origin="ws://127.0.0.1:7270" />);
    await waitFor(() => expect(screen.getByText('db')).toBeInTheDocument());

    // No "+ Add object" button, no create-graph wizard, no layout-download.
    expect(screen.queryByText(/add object/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/create graph/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/download layout/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/new graph/i)).not.toBeInTheDocument();

    // The read-only badge is present (the affirmative signal).
    expect(screen.getByText(/read-only/i)).toBeInTheDocument();
  });
});
