import { useEffect, useRef, useState } from 'react';
import { WsCanvas } from './components/WsCanvas';
import { SearchBox } from './components/SearchBox';
import { ToastContainer, makeToast, type ToastMessage } from './Toast';
import { WsDesignerServerDataSource } from './data/ws-designer-server-data-source';
import type { ModelIndex, ModelGraphPayload, ObjectDetail } from './data/model-data-source';
import type { TtrmGraphMetadata, TtrmGetGraphResponse } from './data/ttrm-types';

// WS (server) mode: view backed by ttr-designer-server (M3.1). No landing card /
// File System Access flow — the server owns the repo, and (unlike Worker mode)
// there's no local filesystem to grant access to.
//
// Two views, mutually exclusive (`activeSchema` XOR `activeGraphUri`):
//   - Package/schema browse (`ttrm/getModelGraph`) — always read-only. There is no
//     `.ttrg` file backing this view, so there's nothing to persist positions to;
//     WsCanvas gets no `positions`/`editable` here and keeps auto-laying-out, same
//     as before T4.
//   - A specific `.ttrg` graph (`ttrm/getGraph` + `ttrm/getLayout`, T3/T4) — editable:
//     drag-persist (`ttrm/setLayout`), "+ Add object" / remove
//     (`ttrm/addObjectToGraph`/`removeObjectFromGraph`), and a create-graph form
//     (`ttrm/createGraph`) all live here.

function ObjectDetailPanel({
  detail,
  onRemove,
}: {
  detail: ObjectDetail | null;
  /** Present only when a specific `.ttrg` graph is in view (T4) — removes from that graph. */
  onRemove?: (qname: string) => void;
}) {
  return (
    <div className="w-80 border-l border-slate-200 bg-white overflow-auto p-4">
      <h3 className="text-xs text-gray-500 uppercase tracking-wide mb-2">Object</h3>
      {!detail ? (
        <p className="text-sm text-slate-400">Select a node to inspect it.</p>
      ) : (
        <>
          <dl className="space-y-3 text-sm">
            <div>
              <dt className="text-xs text-gray-400">qname</dt>
              <dd className="font-mono break-all">{detail.object.qname}</dd>
            </div>
            <div>
              <dt className="text-xs text-gray-400">kind</dt>
              <dd>{detail.object.kind}</dd>
            </div>
            <div>
              <dt className="text-xs text-gray-400">schema</dt>
              <dd>{detail.object.schema || '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-gray-400">package</dt>
              <dd>{detail.object.pkg || '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-gray-400">source</dt>
              <dd className="font-mono text-xs break-all">
                {typeof detail.sourceLocation === 'string'
                  ? detail.sourceLocation
                  : `${detail.sourceLocation.file}:${detail.sourceLocation.line}`}
              </dd>
            </div>
          </dl>
          {onRemove && (
            <button
              type="button"
              onClick={() => onRemove(detail.object.qname)}
              className="mt-4 w-full px-2 py-1 rounded text-sm text-red-700 border border-red-200 hover:bg-red-50"
            >
              Remove from graph
            </button>
          )}
        </>
      )}
    </div>
  );
}

export function WsModeApp({ origin }: { origin: string }) {
  const [index, setIndex] = useState<ModelIndex | null>(null);
  const [graph, setGraph] = useState<ModelGraphPayload | null>(null);
  const [activeSchema, setActiveSchema] = useState<string | null>(null);
  const [graphs, setGraphs] = useState<TtrmGraphMetadata[] | null>(null);
  const [activeGraphUri, setActiveGraphUri] = useState<string | null>(null);
  const [graphView, setGraphView] = useState<TtrmGetGraphResponse | null>(null);
  const [layoutPositions, setLayoutPositions] = useState<Record<string, { x: number; y: number }>>({});
  const [repoRoot, setRepoRoot] = useState<string | null>(null);
  const [newGraphOpen, setNewGraphOpen] = useState(false);
  const [addObjectValue, setAddObjectValue] = useState('');
  const [detail, setDetail] = useState<ObjectDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const sourceRef = useRef<WsDesignerServerDataSource | null>(null);
  const activeSchemaRef = useRef<string | null>(null);
  useEffect(() => { activeSchemaRef.current = activeSchema; }, [activeSchema]);
  const activeGraphUriRef = useRef<string | null>(null);
  useEffect(() => { activeGraphUriRef.current = activeGraphUri; }, [activeGraphUri]);
  // Monotonic generation counters: an out-of-order async completion (select A then B,
  // B resolves first) must not bind stale data to the current selection. Graph,
  // graph-view, and detail are independent, so they get separate counters.
  const graphSeq = useRef(0);
  const graphViewSeq = useRef(0);
  const detailSeq = useRef(0);

  const addToast = (message: string, kind: 'error' | 'info' = 'info') =>
    setToasts((prev) => [...prev, makeToast(message, kind)]);
  const dismissToast = (id: string) => setToasts((prev) => prev.filter((t) => t.id !== id));

  useEffect(() => {
    let disposed = false;
    const source = new WsDesignerServerDataSource(origin, {
      onClose: () => {
        // Surface an involuntary server drop instead of failing silently per-action
        // (P2: no auto-reconnect in v1 — the user re-opens the page).
        if (!disposed) setError('Disconnected from server (connection closed)');
      },
    });
    sourceRef.current = source;
    source
      .connect()
      .then(async (status) => {
        if (disposed) return;
        setRepoRoot(status.repoRoot);
        const idx = await source.getModelIndex();
        if (disposed) return;
        setIndex(idx);
        const gs = await source.listGraphs();
        if (disposed) return;
        setGraphs(gs);
      })
      .catch((err: unknown) => {
        if (!disposed) setError(String(err instanceof Error ? err.message : err));
      });

    const sub = source.onModelChanged((version) => {
      void refresh(version);
    });

    return () => {
      disposed = true;
      sub.dispose();
      source.dispose();
      sourceRef.current = null;
    };
  }, [origin]);

  const refresh = async (version: string) => {
    const source = sourceRef.current;
    if (!source) return;
    const idx = await source.getModelIndex();
    setIndex(idx);
    setGraphs(await source.listGraphs());
    const schema = activeSchemaRef.current;
    if (schema) {
      const seq = ++graphSeq.current;
      const g = await source.getModelGraph({ schema });
      if (seq === graphSeq.current) setGraph(g);
    }
    const uri = activeGraphUriRef.current;
    if (uri) await loadGraphView(uri);
    addToast(`model reloaded (${version})`);
  };

  const loadGraphView = async (uri: string) => {
    const source = sourceRef.current;
    if (!source) return;
    const seq = ++graphViewSeq.current;
    const [gv, layout] = await Promise.all([source.getGraph(uri), source.getLayout(uri)]);
    if (seq !== graphViewSeq.current) return;
    setGraphView(gv);
    const positions: Record<string, { x: number; y: number }> = {};
    for (const canvas of layout.canvases) {
      for (const n of canvas.nodes) positions[n.qname] = { x: n.x, y: n.y };
    }
    setLayoutPositions(positions);
    if (layout.orphaned.length > 0) {
      addToast(`${layout.orphaned.length} saved position(s) reference objects no longer in the model`, 'error');
    }
  };

  const selectGraphView = async (uri: string) => {
    setActiveSchema(null);
    setGraph(null);
    setDetail(null);
    setActiveGraphUri(uri);
    await loadGraphView(uri);
  };

  const persistLayout = async (positions: Record<string, { x: number; y: number }>) => {
    const source = sourceRef.current;
    const uri = activeGraphUriRef.current;
    const g = graphs?.find((x) => x.uri === uri);
    if (!source || !uri || !g) return;
    await source.setLayout(uri, [
      {
        key: g.name,
        skin: null,
        mode: 'manual',
        nodes: Object.entries(positions).map(([qname, p]) => ({ qname, x: p.x, y: p.y })),
        collapsed: [],
      },
    ]);
  };

  const handleNodeDragEnd = (qname: string, x: number, y: number) => {
    setLayoutPositions((prev) => {
      const next = { ...prev, [qname]: { x, y } };
      void persistLayout(next).catch((err: unknown) =>
        addToast(`Failed to save layout: ${err instanceof Error ? err.message : err}`, 'error'),
      );
      return next;
    });
  };

  const handleAddObject = async () => {
    const source = sourceRef.current;
    const uri = activeGraphUriRef.current;
    const qname = addObjectValue.trim();
    if (!source || !uri || !qname) return;
    try {
      const r = await source.addObjectToGraph(uri, qname, true);
      if (!r.ok) throw new Error(r.reason ?? 'add failed');
      setAddObjectValue('');
      await loadGraphView(uri);
      setGraphs(await source.listGraphs());
    } catch (err) {
      addToast(`Failed to add ${qname}: ${err instanceof Error ? err.message : err}`, 'error');
    }
  };

  const handleRemoveObject = async (qname: string) => {
    const source = sourceRef.current;
    const uri = activeGraphUriRef.current;
    if (!source || !uri) return;
    try {
      const r = await source.removeObjectFromGraph(uri, qname, true);
      if (!r.ok) throw new Error(r.reason ?? 'remove failed');
      setDetail((d) => (d?.object.qname === qname ? null : d));
      await loadGraphView(uri);
      setGraphs(await source.listGraphs());
    } catch (err) {
      addToast(`Failed to remove ${qname}: ${err instanceof Error ? err.message : err}`, 'error');
    }
  };

  const handleCreateGraph = async (name: string, schema: string) => {
    const source = sourceRef.current;
    if (!source || !repoRoot || !name.trim()) return;
    const uri = `file://${repoRoot.replace(/\/$/, '')}/graphs/${name.trim()}.ttrg`;
    try {
      const r = await source.createGraph({ uri, name: name.trim(), schema });
      if (!r.ok) throw new Error(r.reason ?? 'create failed');
      setNewGraphOpen(false);
      setGraphs(await source.listGraphs());
      await selectGraphView(uri);
    } catch (err) {
      addToast(`Failed to create graph "${name}": ${err instanceof Error ? err.message : err}`, 'error');
    }
  };

  const selectSchema = async (schema: string) => {
    const source = sourceRef.current;
    if (!source) return;
    setActiveGraphUri(null);
    setGraphView(null);
    setActiveSchema(schema);
    setDetail(null);
    const seq = ++graphSeq.current;
    const g = await source.getModelGraph({ schema });
    if (seq === graphSeq.current) setGraph(g);
  };

  const selectNode = async (qname: string | null) => {
    const source = sourceRef.current;
    if (!source || !qname) {
      setDetail(null);
      return;
    }
    const seq = ++detailSeq.current;
    try {
      const d = await source.getObject(qname);
      if (seq === detailSeq.current) setDetail(d);
    } catch (err) {
      if (seq === detailSeq.current) {
        addToast(`Failed to load ${qname}: ${err instanceof Error ? err.message : err}`, 'error');
      }
    }
  };

  const runSearch = async (query: string) => {
    const source = sourceRef.current;
    if (!source) return [];
    return source.search({ query });
  };

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-screen gap-4 p-12 bg-gray-50">
        <div className="bg-white border border-red-300 rounded-xl shadow-lg p-8 max-w-md text-center">
          <h2 className="text-xl font-bold text-red-700 mb-2">Cannot connect to server</h2>
          <p className="text-sm text-slate-600 break-words">{error}</p>
          <p className="text-xs text-slate-400 mt-4">{origin}/ttrm</p>
        </div>
      </div>
    );
  }

  const inGraphView = activeGraphUri != null;

  return (
    <div className="flex flex-col h-screen bg-gray-50">
      <header className="flex items-center gap-4 px-4 py-2 border-b border-slate-200 bg-white">
        <span className="font-semibold text-slate-800">TTR Designer</span>
        <span
          className={`text-xs px-2 py-0.5 rounded ${
            inGraphView ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
          }`}
        >
          {inGraphView ? 'editing (server)' : 'read-only (server)'}
        </span>
        <div className="flex-1" />
        <SearchBox onSearch={runSearch} onSelectHit={(qname) => void selectNode(qname)} />
      </header>
      <div className="flex flex-1 overflow-hidden">
        <nav className="w-56 border-r border-slate-200 bg-white overflow-auto p-3" aria-label="Model index">
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-xs text-gray-500 uppercase tracking-wide">Graphs</h3>
            <button
              type="button"
              onClick={() => setNewGraphOpen((v) => !v)}
              className="text-xs px-1.5 py-0.5 rounded border border-slate-300 hover:bg-slate-100"
              aria-label="New graph"
            >
              + New
            </button>
          </div>
          {newGraphOpen && <NewGraphForm onCreate={(name, schema) => void handleCreateGraph(name, schema)} />}
          <ul className="space-y-1 mb-4">
            {(graphs ?? []).map((g) => (
              <li key={g.uri}>
                <button
                  type="button"
                  onClick={() => void selectGraphView(g.uri)}
                  className={`w-full text-left px-2 py-1 rounded text-sm ${
                    activeGraphUri === g.uri ? 'bg-emerald-100 text-emerald-800' : 'hover:bg-slate-100'
                  }`}
                >
                  {g.name}
                  {g.missingObjectCount > 0 && (
                    <span className="ml-1 text-xs text-amber-600">({g.missingObjectCount} missing)</span>
                  )}
                </button>
              </li>
            ))}
            {graphs != null && graphs.length === 0 && <li className="text-xs text-slate-400 px-2">No .ttrg graphs yet.</li>}
          </ul>

          <h3 className="text-xs text-gray-500 uppercase tracking-wide mb-2">Schemas</h3>
          <ul className="space-y-1">
            {(index?.schemas ?? []).map((schema) => (
              <li key={schema}>
                <button
                  type="button"
                  onClick={() => void selectSchema(schema)}
                  className={`w-full text-left px-2 py-1 rounded text-sm ${
                    activeSchema === schema ? 'bg-sky-100 text-sky-800' : 'hover:bg-slate-100'
                  }`}
                >
                  {schema}
                </button>
              </li>
            ))}
          </ul>
          {index && index.packages.length > 0 && (
            <>
              <h3 className="text-xs text-gray-500 uppercase tracking-wide mt-4 mb-2">Packages</h3>
              <ul className="space-y-1 text-sm text-slate-600">
                {index.packages.map((pkg) => (
                  <li key={pkg} className="px-2 py-1 font-mono text-xs">{pkg}</li>
                ))}
              </ul>
            </>
          )}
        </nav>
        <div className="flex-1 relative flex flex-col">
          {inGraphView && (
            <div className="flex items-center gap-2 px-3 py-2 border-b border-slate-200 bg-white">
              <input
                type="text"
                value={addObjectValue}
                onChange={(e) => setAddObjectValue(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleAddObject();
                }}
                placeholder="qname to add…"
                className="flex-1 text-sm border border-slate-300 rounded px-2 py-1"
              />
              <button
                type="button"
                onClick={() => void handleAddObject()}
                className="text-sm px-2 py-1 rounded bg-emerald-600 text-white hover:bg-emerald-700"
              >
                + Add object
              </button>
            </div>
          )}
          <div className="flex-1 relative">
            <WsCanvas
              graph={inGraphView ? (graphView ? { nodes: graphView.nodes, edges: graphView.edges } : null) : graph}
              positions={inGraphView ? layoutPositions : null}
              editable={inGraphView}
              onNodeSelect={(qname) => void selectNode(qname)}
              onNodeDragEnd={handleNodeDragEnd}
            />
          </div>
        </div>
        <ObjectDetailPanel detail={detail} onRemove={inGraphView ? (qname) => void handleRemoveObject(qname) : undefined} />
      </div>
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
    </div>
  );
}

function NewGraphForm({ onCreate }: { onCreate: (name: string, schema: string) => void }) {
  const [name, setName] = useState('');
  const [schema, setSchema] = useState('er');
  return (
    <div className="mb-3 p-2 border border-slate-200 rounded bg-slate-50 space-y-2">
      <input
        type="text"
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="graph name"
        className="w-full text-sm border border-slate-300 rounded px-2 py-1"
      />
      <select
        value={schema}
        onChange={(e) => setSchema(e.target.value)}
        className="w-full text-sm border border-slate-300 rounded px-2 py-1"
      >
        <option value="er">er</option>
        <option value="db">db</option>
        <option value="binding">binding</option>
        <option value="query">query</option>
        <option value="cnc">cnc</option>
      </select>
      <button
        type="button"
        onClick={() => name.trim() && onCreate(name, schema)}
        className="w-full text-sm px-2 py-1 rounded bg-emerald-600 text-white hover:bg-emerald-700"
      >
        Create
      </button>
    </div>
  );
}
