import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act, cleanup } from '@testing-library/react';

// ---------------------------------------------------------------------------
// Shared mock state. `workerStore` stands in for the LSP worker's in-memory
// documents: openDocument writes it, and addObjectToGraph reads it to build its
// edit (exactly like the real server, which builds edits from documents.get()).
// `cyHandlers` captures the event handlers Canvas registers on the cytoscape
// instance so the context-menu (cxttap) path can be driven directly.
// ---------------------------------------------------------------------------
const h = vi.hoisted(() => ({
  workerStore: new Map<string, string>(),
  cyHandlers: [] as Array<{ event: string; selector?: string; handler: (evt: unknown) => void }>,
  openDocument: vi.fn(),
  listGraphs: vi.fn(),
  getGraph: vi.fn(),
  getLayout: vi.fn(),
  setLayout: vi.fn(),
  addObjectToGraph: vi.fn(),
  removeObjectFromGraph: vi.fn(),
  listSymbols: vi.fn(),
}));

vi.mock('cytoscape', () => {
  const mockUse = vi.fn();
  const mockDefault = vi.fn((_opts: unknown) => ({
    elements: vi.fn(() => ({ remove: vi.fn().mockReturnThis() })),
    add: vi.fn().mockReturnThis(),
    layout: vi.fn(() => ({ run: vi.fn() })),
    nodes: vi.fn(() => ({ forEach: vi.fn() })),
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
    addObjectToGraph: h.addObjectToGraph,
    removeObjectFromGraph: h.removeObjectFromGraph,
    listSymbols: h.listSymbols,
    exportLayout: vi.fn(),
    getSymbolDetail: vi.fn().mockResolvedValue(null),
    getModelGraph: vi.fn(),
    getPackageGraph: vi.fn(),
    createGraph: vi.fn(),
    applyGraphEdit: vi.fn(),
    onDiagnostics: vi.fn(),
    dispose: vi.fn(),
  }),
}));

const PROJECT_FILES = () => ({
  rootName: 'proj',
  files: new Map([
    ['graphs/order.ttrg', V0],
    ['p.ttrm', 'package p\n'],
  ]),
});

vi.mock('../fs/file-system', () => ({
  loadProjectViaFileSystemAccessAPI: vi.fn().mockImplementation(async () => PROJECT_FILES()),
  loadProjectViaUpload: vi.fn(),
  downloadFile: vi.fn(),
}));

import App from '../App';
import { Canvas } from '../components/Canvas';
import type { ModelGraph } from '@tatrman/lsp';

// ---------------------------------------------------------------------------
// Fixtures + a tiny edit/graph model that mirrors the real server behaviour.
// ---------------------------------------------------------------------------
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

const SYMBOLS = [
  { qname: 'p.er.entity.Alpha', kind: 'entity', name: 'Alpha', packageName: 'p' },
  { qname: 'p.er.entity.Beta', kind: 'entity', name: 'Beta', packageName: 'p' },
  { qname: 'other.er.entity.Gamma', kind: 'entity', name: 'Gamma', packageName: 'other' },
];

const LAYOUT = {
  version: 1,
  viewports: {
    db: { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' },
    er: { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' },
  },
  nodes: {},
  edges: {},
};

function offsetToPos(text: string, offset: number) {
  let line = 0;
  let lastNl = -1;
  for (let i = 0; i < offset; i++) {
    if (text[i] === '\n') { line++; lastNl = i; }
  }
  return { line, character: offset - (lastNl + 1) };
}

/** Inserts `qname` on its own line just before the objects-block closing brace. */
function insertObjectEdit(text: string, uri: string, qname: string) {
  const openIdx = text.indexOf('{', text.indexOf('objects'));
  const closeIdx = text.indexOf('}', openIdx);
  const pos = offsetToPos(text, closeIdx);
  return {
    documentChanges: [
      {
        textDocument: { uri, version: null },
        edits: [{ range: { start: pos, end: pos }, newText: `${qname}\n  ` }],
      },
    ],
  };
}

/** Builds a GetGraphResponse by reading the objects block out of the document. */
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

// Walks the standard entry flow: load project folder → open the graph.
async function openGraph() {
  render(<App />);
  await screen.findByText('Export Layout'); // appears once the (browser) client is ready
  fireEvent.click(screen.getByTitle(/open project folder/i));
  fireEvent.click(await screen.findByText('OrderModel'));
  await screen.findByText('+ Add object');
}

async function addObjectViaPicker(symbolName: string, { autoImportOff = false } = {}) {
  fireEvent.click(screen.getByText('+ Add object'));
  await screen.findByText(symbolName);
  if (autoImportOff) {
    fireEvent.click(screen.getByRole('checkbox', { name: /auto-import/i }));
  }
  fireEvent.click(screen.getByText(symbolName).closest('button')!);
}

beforeEach(() => {
  h.workerStore.clear();
  h.cyHandlers.length = 0;
  h.openDocument.mockReset().mockImplementation(async (uri: string, content: string) => {
    h.workerStore.set(uri, content);
  });
  h.listGraphs.mockReset().mockResolvedValue({ graphs: [GRAPH_META] });
  h.getGraph.mockReset().mockImplementation(async (uri: string) => graphFromText(h.workerStore.get(uri) ?? '', uri));
  h.getLayout.mockReset().mockResolvedValue(LAYOUT);
  h.setLayout.mockReset().mockResolvedValue({ ok: true });
  h.listSymbols.mockReset().mockResolvedValue(SYMBOLS);
  h.removeObjectFromGraph.mockReset().mockResolvedValue({ documentChanges: [] });
  h.addObjectToGraph.mockReset().mockImplementation(async (uri: string, qname: string, autoImport: boolean) => {
    if (!autoImport) return { documentChanges: [] }; // server can't place an out-of-scope object without an import
    return insertObjectEdit(h.workerStore.get(uri) ?? '', uri, qname);
  });
  window.history.replaceState(null, '', '/');
});

afterEach(() => {
  cleanup();
});

describe('G2 — add object round-trip through App', () => {
  it('adding an in-scope object calls addObjectToGraph(uri, qname, true) and applies the edit', async () => {
    await openGraph();
    await addObjectViaPicker('Alpha');

    await waitFor(() =>
      expect(h.addObjectToGraph).toHaveBeenCalledWith(GRAPH_URI, 'p.er.entity.Alpha', true),
    );
    // The edit was applied: the document now contains the new object, and a
    // refetch happened (getGraph called for the initial open and again after).
    await waitFor(() => expect(h.workerStore.get(GRAPH_URI)).toContain('p.er.entity.Alpha'));
    expect(h.getGraph.mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('two consecutive adds compose without corrupting the document (stale-cache regression)', async () => {
    await openGraph();

    await addObjectViaPicker('Alpha');
    await waitFor(() => expect(h.workerStore.get(GRAPH_URI)).toContain('p.er.entity.Alpha'));

    await addObjectViaPicker('Beta');
    await waitFor(() => expect(h.workerStore.get(GRAPH_URI)).toContain('p.er.entity.Beta'));

    const finalText = h.workerStore.get(GRAPH_URI)!;
    // Both adds survived — the second did not clobber the first.
    expect(finalText).toContain('p.er.entity.Existing');
    expect(finalText).toContain('p.er.entity.Alpha');
    expect(finalText).toContain('p.er.entity.Beta');
    // Document stays well-formed: exactly one objects block, balanced braces.
    expect((finalText.match(/}/g) ?? []).length).toBe(2);
    expect(h.addObjectToGraph).toHaveBeenCalledWith(GRAPH_URI, 'p.er.entity.Beta', true);
  });
});

describe('G4 — failure toast on out-of-scope add with auto-import off', () => {
  it('shows a toast when addObjectToGraph returns no edit', async () => {
    await openGraph();
    await addObjectViaPicker('Gamma', { autoImportOff: true });

    await waitFor(() =>
      expect(h.addObjectToGraph).toHaveBeenCalledWith(GRAPH_URI, 'other.er.entity.Gamma', false),
    );
    expect(await screen.findByText(/out of scope and auto-import is off/i)).toBeInTheDocument();
    // Nothing was written to the document.
    expect(h.workerStore.get(GRAPH_URI)).not.toContain('Gamma');
  });
});

describe('G3 — remove object', () => {
  it('the missing-objects drawer removes a stale entry via removeObjectFromGraph(uri, qname, true)', async () => {
    h.getGraph.mockImplementation(async (uri: string) =>
      graphFromText(h.workerStore.get(uri) ?? '', uri, ['p.er.entity.Stale']),
    );
    await openGraph();

    fireEvent.click(await screen.findByText(/1 stale/i));
    fireEvent.click(await screen.findByRole('button', { name: /remove/i }));

    await waitFor(() =>
      expect(h.removeObjectFromGraph).toHaveBeenCalledWith(GRAPH_URI, 'p.er.entity.Stale', true),
    );
  });

  it('the Canvas context menu (cxttap → "Remove from graph") calls onRemoveNode', async () => {
    const onRemoveNode = vi.fn();
    const graph: ModelGraph = {
      schemaCode: 'er',
      nodes: [{
        qname: 'p.er.entity.Foo', kind: 'entity', name: 'Foo', schemaCode: 'er', label: 'Foo',
        sourceUri: GRAPH_URI, sourceLocation: { line: 1, column: 0 }, rows: [],
      }],
      edges: [],
    };
    h.cyHandlers.length = 0;
    render(
      <Canvas
        graph={graph}
        displayMode="just-names"
        activeSchema="er"
        viewports={{ er: { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' }, db: { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' } }}
        nodePositions={{}}
        lspClient={null}
        projectRoot={GRAPH_URI}
        onNodeSelect={vi.fn()}
        currentViewport={{ zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' }}
        onRemoveNode={onRemoveNode}
      />,
    );

    // Wait for Canvas to register its cytoscape handlers, then fire cxttap on a node.
    await waitFor(() => expect(h.cyHandlers.find((c) => c.event === 'cxttap')).toBeTruthy());
    const cxttap = h.cyHandlers.find((c) => c.event === 'cxttap')!.handler;
    act(() => {
      cxttap({ target: { data: () => ({ qname: 'p.er.entity.Foo' }) }, renderedPosition: () => ({ x: 10, y: 20 }) });
    });

    fireEvent.click(await screen.findByRole('button', { name: /remove from graph/i }));
    expect(onRemoveNode).toHaveBeenCalledWith('p.er.entity.Foo');
  });
});
