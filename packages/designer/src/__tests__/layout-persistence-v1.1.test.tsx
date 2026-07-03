import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act, cleanup } from '@testing-library/react';

const h = vi.hoisted(() => ({
  workerStore: new Map<string, string>(),
  cyHandlers: [] as Array<{ event: string; selector?: string; handler: (evt: unknown) => void }>,
  openDocument: vi.fn(),
  listGraphs: vi.fn(),
  getGraph: vi.fn(),
  getLayout: vi.fn(),
  setLayout: vi.fn(),
  listSymbols: vi.fn(),
  exportLayout: vi.fn(),
  getSymbolDetail: vi.fn().mockResolvedValue(null),
  getModelGraph: vi.fn(),
  getPackageGraph: vi.fn(),
  createGraph: vi.fn(),
  applyGraphEdit: vi.fn(),
  onDiagnostics: vi.fn(),
  dispose: vi.fn(),
}));

vi.mock('cytoscape', () => {
  const mockUse = vi.fn();
  const mockDefault = vi.fn((_opts: unknown) => ({
    elements: vi.fn(() => ({ remove: vi.fn().mockReturnThis() })),
    add: vi.fn().mockReturnThis(),
    layout: vi.fn(() => ({ run: vi.fn() })),
    nodes: vi.fn(() => ({
      forEach: (cb: (n: { position: () => { x: number; y: number }; data: (k: string) => string | undefined }) => void) =>
        cb({ position: () => ({ x: 100, y: 200 }), data: (k: string) => (k === 'qname' ? 'p.er.entity.Existing' : undefined) }),
    })),
    on: vi.fn((event: string, arg2: unknown, arg3: unknown) => {
      if (typeof arg2 === 'function') {
        h.cyHandlers.push({ event, handler: arg2 as (evt: unknown) => void });
      } else {
        h.cyHandlers.push({ event, selector: arg2 as string, handler: arg3 as (evt: unknown) => void });
      }
    }),
    off: vi.fn(),
    nodeHtmlLabel: vi.fn(),
    destroy: vi.fn(),
    pan: vi.fn(() => ({ x: 0, y: 0 })),
    zoom: vi.fn(() => 1),
    edges: vi.fn(() => []),
    render: vi.fn(),
    getElementById: vi.fn(() => ({ length: 0, position: vi.fn() })),
  }));
  (mockDefault as unknown as Record<string, unknown>).use = mockUse;
  return { default: mockDefault, use: mockUse };
});
vi.mock('cytoscape-cose-bilkent', () => ({ default: vi.fn() }));
vi.mock('cytoscape-node-html-label', () => ({ default: vi.fn() }));

vi.mock('../lsp-client.ts', () => ({
  createLspClient: vi.fn().mockResolvedValue({
    transportKind: 'browser',
    openDocument: h.openDocument,
    setProjectRoot: vi.fn().mockResolvedValue({ projectRoot: '' }),
    listGraphs: h.listGraphs,
    getGraph: h.getGraph,
    getLayout: h.getLayout,
    setLayout: h.setLayout,
    listSymbols: h.listSymbols,
    exportLayout: h.exportLayout,
    getSymbolDetail: h.getSymbolDetail,
    getModelGraph: h.getModelGraph,
    getPackageGraph: h.getPackageGraph,
    createGraph: h.createGraph,
    applyGraphEdit: h.applyGraphEdit,
    onDiagnostics: h.onDiagnostics,
    dispose: h.dispose,
  }),
}));

vi.mock('../fs/file-system', () => ({
  loadProjectViaFileSystemAccessAPI: vi.fn().mockImplementation(async () => PROJECT_FILES()),
  loadProjectViaUpload: vi.fn(),
  downloadFile: vi.fn(),
}));

import App from '../App';

const GRAPH_URI = 'file:///proj/graphs/order.ttrg';
const V0 = 'package p\n\ngraph OrderModel {\n  schema: er\n  objects {\n    p.er.entity.Existing\n  }\n}\n';

const GRAPH_META = {
  uri: GRAPH_URI,
  name: 'OrderModel',
  schema: 'er' as const,
  tags: [],
  objectCount: 1,
  missingObjectCount: 0,
};

const LAYOUT_WITH_NODES = {
  version: 1,
  viewport: { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' },
  nodes: { 'p.er.entity.Alpha': { x: 100, y: 200 } },
  edges: {},
};

const PROJECT_FILES = () => ({
  rootName: 'proj',
  files: new Map([
    ['graphs/order.ttrg', V0],
    ['p.ttrm', 'package p\n'],
  ]),
});

function graphFromText(text: string, uri: string, missingObjects: string[] = []) {
  const openIdx = text.indexOf('{', text.indexOf('objects'));
  const closeIdx = text.indexOf('}', openIdx);
  const inner = closeIdx > openIdx ? text.slice(openIdx + 1, closeIdx) : '';
  const qnames = inner.split(/\s+/).map((s) => s.trim()).filter((s) => s.includes('.'));
  const nodes = qnames.map((q) => ({
    qname: q,
    kind: 'entity' as const,
    name: q.split('.').pop()!,
    label: q.split('.').pop()!,
    sourceUri: uri,
    sourceLocation: { line: 1, column: 0 },
    rows: [],
  }));
  return { schema: 'er' as const, nodes, edges: [], layout: { nodes: {}, edges: {} }, missingObjects, imports: ['p'] };
}

async function openGraph() {
  render(<App />);
  await screen.findByText('Export Layout');
  fireEvent.click(screen.getByTitle(/open project folder/i));
  fireEvent.click(await screen.findByText('OrderModel'));
  await screen.findByText('+ Add object');
}

beforeEach(() => {
  h.workerStore.clear();
  h.cyHandlers.length = 0;
  h.openDocument.mockReset().mockImplementation(async (uri: string, content: string) => {
    h.workerStore.set(uri, content);
  });
  h.listGraphs.mockReset().mockResolvedValue({ graphs: [GRAPH_META] });
  h.getGraph.mockReset().mockImplementation(async (uri: string) => graphFromText(h.workerStore.get(uri) ?? '', uri));
  h.getLayout.mockReset().mockResolvedValue(LAYOUT_WITH_NODES);
  h.setLayout.mockReset().mockResolvedValue({ ok: true });
  h.listSymbols.mockReset().mockResolvedValue([]);
  window.history.replaceState(null, '', '/');
});

afterEach(() => {
  cleanup();
});

describe('E4.8 — Layout persistence v1.1 (per-graph, wire-path tests)', () => {
  describe('H2.1 — Layout loaded via getGraph, not via getLayout', () => {
    it('getLayout is NOT called on open (layout comes from getGraph.graph.layout)', async () => {
      h.getLayout.mockClear();
      await openGraph();
      expect(h.getLayout).not.toHaveBeenCalled();
    });

    it('setLayout is NOT called during initial open (layout comes from getGraph.layout)', async () => {
      h.setLayout.mockClear();
      await openGraph();
      expect(h.setLayout).not.toHaveBeenCalled();
    });

    it('a saved layout from getGraph.layout is RESTORED on open (viewport displayMode applied)', async () => {
      // getGraph returns a layout block with saved positions + a non-default viewport.
      h.getGraph.mockImplementation(async (uri: string) => ({
        ...graphFromText(h.workerStore.get(uri) ?? '', uri),
        layout: {
          nodes: { 'p.er.entity.Existing': { x: 320, y: 180 } },
          edges: {},
          viewport: { zoom: 2, panX: 10, panY: 20, displayMode: 'with-types' },
        },
      }));
      h.getLayout.mockClear();
      await openGraph();

      // Load came from getGraph (no separate getLayout call) ...
      expect(h.getLayout).not.toHaveBeenCalled();
      // ... and the restored viewport's displayMode drives the header's active pill.
      expect(screen.getByRole('button', { name: 'with types' })).toHaveClass('text-sky-500');
    });
  });

  describe('H2.2 — Drag → setLayout called with graphUri', () => {
    it('fires dragfreeon and asserts setLayout was called with the graphUri', async () => {
      await openGraph();
      h.setLayout.mockClear();

      await waitFor(() => expect(h.cyHandlers.find((c) => c.event === 'dragfreeon')).toBeTruthy());
      const dragfreeon = h.cyHandlers.find((c) => c.event === 'dragfreeon')!.handler;
      act(() => {
        dragfreeon({ type: 'dragfreeon' });
      });

      await waitFor(() => {
        expect(h.setLayout).toHaveBeenCalled();
      });
      const lastCall = h.setLayout.mock.calls.at(-1);
      expect(lastCall?.[0]).toBe(GRAPH_URI);
      const payload = lastCall?.[1] as Record<string, unknown>;
      expect(payload).toHaveProperty('nodes');
      expect(payload).toHaveProperty('version');
    });

    it('applies the setLayout WorkspaceEdit, writing the dragged layout back to the .ttrg', async () => {
      await openGraph();
      // setLayout returns an edit that rewrites the whole .ttrg with a layout block.
      const PATCHED = V0.replace(/\}\n$/, '  layout {\n    nodes { p.er.entity.Existing { x: 100, y: 200 } }\n  }\n}\n');
      h.setLayout.mockReset().mockResolvedValue({
        documentChanges: [{
          textDocument: { uri: GRAPH_URI, version: null },
          edits: [{ range: { start: { line: 0, character: 0 }, end: { line: 9999, character: 0 } }, newText: PATCHED }],
        }],
      });
      h.openDocument.mockClear();

      await waitFor(() => expect(h.cyHandlers.find((c) => c.event === 'dragfreeon')).toBeTruthy());
      const dragfreeon = h.cyHandlers.find((c) => c.event === 'dragfreeon')!.handler;
      act(() => { dragfreeon({ type: 'dragfreeon' }); });

      await waitFor(() => expect(h.setLayout).toHaveBeenCalled());
      // The returned edit must be applied — the .ttrg now carries the layout block.
      await waitFor(() => expect(h.openDocument).toHaveBeenCalledWith(GRAPH_URI, PATCHED));
      expect(h.workerStore.get(GRAPH_URI)).toBe(PATCHED);
    });

    it('H1 regression: display-mode toggle persists the dragged node positions (read from cy, not stale state)', async () => {
      await openGraph();
      h.setLayout.mockClear();

      // Drag → save reads live cy positions ({ Existing: 100,200 } from the cy mock).
      await waitFor(() => expect(h.cyHandlers.find((c) => c.event === 'dragfreeon')).toBeTruthy());
      const dragfreeon = h.cyHandlers.find((c) => c.event === 'dragfreeon')!.handler;
      act(() => { dragfreeon({ type: 'dragfreeon' }); });
      await waitFor(() => expect(h.setLayout).toHaveBeenCalled());
      const dragCallCount = h.setLayout.mock.calls.length;

      // Toggle display mode → must persist the SAME node positions, not stale ones.
      fireEvent.click(screen.getByRole('button', { name: 'with types' }));
      await waitFor(() => expect(h.setLayout.mock.calls.length).toBe(dragCallCount + 1));

      const payload = h.setLayout.mock.calls.at(-1)?.[1] as { nodes: Record<string, { x: number; y: number }> };
      expect(payload.nodes).toEqual({ 'p.er.entity.Existing': { x: 100, y: 200 } });
    });
  });

  describe('H2.3 — Display-mode change persists per-graph via setLayout', () => {
    it('changing display mode calls setLayout with the current graphUri and the NEW displayMode', async () => {
      await openGraph();
      h.setLayout.mockClear();

      fireEvent.click(screen.getByRole('button', { name: 'with types' }));

      await waitFor(() => {
        expect(h.setLayout).toHaveBeenCalled();
      });
      const lastCall = h.setLayout.mock.calls.at(-1);
      expect(lastCall?.[0]).toBe(GRAPH_URI);
      // G1 regression: the persisted viewport must carry the just-selected mode,
      // not the previous one (off-by-one) or undefined.
      const payload = lastCall?.[1] as { viewport?: { displayMode: string } };
      expect(payload.viewport?.displayMode).toBe('with-types');
    });

    it('setLayout is called to the current graph URI, not a project-wide store', async () => {
      await openGraph();
      h.setLayout.mockClear();

      fireEvent.click(screen.getByRole('button', { name: 'with types' }));

      await waitFor(() => {
        expect(h.setLayout).toHaveBeenCalled();
      });
      for (const call of h.setLayout.mock.calls) {
        expect(call[0]).toBe(GRAPH_URI);
      }
    });
  });
});