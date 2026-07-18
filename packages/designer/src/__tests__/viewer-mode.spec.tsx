// FO-P0.S1.T1 — Studio Viewer smoke test (the FO-31 regression tripwire).
//
// The FO-21 move carves the designer's edit surface out to `tatrman-platform`,
// leaving a core-only "Studio Viewer" build in `tatrman`. FO-31's carve-out:
// view persistence (saved layouts / positions / viewport — the `.ttrl`-shaped
// presentation prefs) is READ-HALF and STAYS in the Viewer. This test pins that
// contract at the App level via the `?viewer=1` view-mode flag (the S1 substrate;
// S2 replaces "edit code gated off" with "edit code absent from the build"):
//
//   (a) the render engine is up and read-only (a graph opens, view controls work);
//   (b) saved-views UX is present AND functional against the mock prefs store
//       (LSP `getLayout`/`setLayout`) — the one open-tier feature the move could
//       silently break;
//   (c) no edit affordance is reachable — no Add-object, no Remove-from-graph.
//
// Worker mode is the subject: per the S1 inventory it is the only host with NO
// read-only flag today (WS mode gates per-view; Veles is hard read-only but has
// no layout persistence to guard). Harness mirrors layout-persistence-v1.1.
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

/** Drive Worker mode (with `?viewer=1` already on the URL) into an open graph.
 *  Unlike the editor harness, viewer mode never shows "+ Add object", so we
 *  wait on the back-to-picker control (present iff a graph is open) instead. */
async function openGraphInViewer() {
  render(<App />);
  await screen.findByText('Export Layout');
  fireEvent.click(screen.getByTitle(/open project folder/i));
  fireEvent.click(await screen.findByText('OrderModel'));
  await screen.findByTitle(/back to graph picker/i);
}

beforeEach(() => {
  window.history.replaceState(null, '', '/?viewer=1');
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
});

afterEach(() => {
  cleanup();
  window.history.replaceState(null, '', '/');
});

describe('FO-P0.S1 — Studio Viewer smoke (FO-31 guard)', () => {
  describe('(a) the render engine is up and read-only', () => {
    it('opens a graph and renders the view controls (display-mode pills)', async () => {
      await openGraphInViewer();
      expect(screen.getByText('OrderModel')).toBeInTheDocument();
      // Read-side view control is present and interactive once a graph is open.
      expect(screen.getByRole('button', { name: 'just names' })).toBeEnabled();
      expect(screen.getByRole('button', { name: 'with types' })).toBeEnabled();
    });
  });

  describe('(b) saved-views persistence works against the mock prefs store (FO-31)', () => {
    it('exposes the Export Layout affordance (view/prefs surface)', async () => {
      await openGraphInViewer();
      expect(screen.getByText('Export Layout')).toBeInTheDocument();
    });

    it('a restored viewport from a saved layout drives the header (views LOAD)', async () => {
      h.getGraph.mockImplementation(async (uri: string) => ({
        ...graphFromText(h.workerStore.get(uri) ?? '', uri),
        layout: {
          nodes: { 'p.er.entity.Existing': { x: 320, y: 180 } },
          edges: {},
          viewport: { zoom: 2, panX: 10, panY: 20, displayMode: 'with-types' },
        },
      }));
      await openGraphInViewer();
      expect(screen.getByRole('button', { name: 'with types' })).toHaveClass('text-sky-500');
    });

    it('a drag persists positions via setLayout — the prefs write survives without edit code (views SAVE)', async () => {
      await openGraphInViewer();
      h.setLayout.mockClear();

      await waitFor(() => expect(h.cyHandlers.find((c) => c.event === 'dragfreeon')).toBeTruthy());
      const dragfreeon = h.cyHandlers.find((c) => c.event === 'dragfreeon')!.handler;
      act(() => { dragfreeon({ type: 'dragfreeon' }); });

      await waitFor(() => expect(h.setLayout).toHaveBeenCalled());
      expect(h.setLayout.mock.calls.at(-1)?.[0]).toBe(GRAPH_URI);
    });

    it('a display-mode change persists via setLayout (viewport prefs)', async () => {
      await openGraphInViewer();
      h.setLayout.mockClear();

      fireEvent.click(screen.getByRole('button', { name: 'with types' }));

      await waitFor(() => expect(h.setLayout).toHaveBeenCalled());
      const payload = h.setLayout.mock.calls.at(-1)?.[1] as { viewport?: { displayMode: string } };
      expect(payload.viewport?.displayMode).toBe('with-types');
    });
  });

  describe('(c) no edit affordance is reachable', () => {
    it('the Header exposes no "+ Add object" button', async () => {
      await openGraphInViewer();
      expect(screen.queryByText('+ Add object')).not.toBeInTheDocument();
    });

    it('registers no right-click context menu (no "Remove from graph" affordance)', async () => {
      await openGraphInViewer();
      // The canvas wires its cytoscape handlers (node/background tap is always present)…
      await waitFor(() => expect(h.cyHandlers.find((c) => c.event === 'tap')).toBeTruthy());
      // …but the Viewer build wires NO cxttap handler: the remove-from-graph context
      // menu moved to the authoring extension (FO-P0.S2.T4), so it cannot even fire.
      expect(h.cyHandlers.find((c) => c.event === 'cxttap')).toBeUndefined();
      expect(screen.queryByText('Remove from graph')).not.toBeInTheDocument();
      expect(document.querySelector('[data-context-menu]')).toBeNull();
    });

    it('no add-object mutation RPC is reachable from the viewer UI', async () => {
      await openGraphInViewer();
      // With no Add-object entry point rendered, the picker never mounts and the
      // graph-mutation RPC is never callable.
      expect(screen.queryByPlaceholderText(/qname to add/i)).not.toBeInTheDocument();
      expect(h.createGraph).not.toHaveBeenCalled();
      expect(h.applyGraphEdit).not.toHaveBeenCalled();
    });
  });
});
