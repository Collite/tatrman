import { useEffect, useRef, useState } from 'react';
import { WsCanvas } from './components/WsCanvas';
import { SearchBox } from './components/SearchBox';
import { ToastContainer, makeToast, type ToastMessage } from './Toast';
import { WsDesignerServerDataSource } from './data/ws-designer-server-data-source';
import type { ModelIndex, ModelGraphPayload, ObjectDetail } from './data/model-data-source';

// WS (server) mode: read-only view backed by ttr-designer-server (M3.1). No landing
// card / File System Access flow — the server owns the repo. No edit affordances
// (capabilities.edit === false): there is no "+ Add object", no create-graph wizard,
// no missing-objects apply-actions, and dragging persists nothing (this canvas has
// no setLayout path at all). This whole component imports zero edit machinery.

function ObjectDetailPanel({ detail }: { detail: ObjectDetail | null }) {
  return (
    <div className="w-80 border-l border-slate-200 bg-white overflow-auto p-4">
      <h3 className="text-xs text-gray-500 uppercase tracking-wide mb-2">Object</h3>
      {!detail ? (
        <p className="text-sm text-slate-400">Select a node to inspect it.</p>
      ) : (
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
      )}
    </div>
  );
}

export function WsModeApp({ origin }: { origin: string }) {
  const [index, setIndex] = useState<ModelIndex | null>(null);
  const [graph, setGraph] = useState<ModelGraphPayload | null>(null);
  const [activeSchema, setActiveSchema] = useState<string | null>(null);
  const [detail, setDetail] = useState<ObjectDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const sourceRef = useRef<WsDesignerServerDataSource | null>(null);
  const activeSchemaRef = useRef<string | null>(null);
  useEffect(() => { activeSchemaRef.current = activeSchema; }, [activeSchema]);
  // Monotonic generation counters: an out-of-order async completion (select A then B,
  // B resolves first) must not bind stale data to the current selection. Graph and
  // detail are independent, so they get separate counters.
  const graphSeq = useRef(0);
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
      .then(async () => {
        const idx = await source.getModelIndex();
        if (disposed) return;
        setIndex(idx);
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
    const schema = activeSchemaRef.current;
    if (schema) {
      const seq = ++graphSeq.current;
      const g = await source.getModelGraph({ schema });
      if (seq === graphSeq.current) setGraph(g);
    }
    addToast(`model reloaded (${version})`);
  };

  const selectSchema = async (schema: string) => {
    const source = sourceRef.current;
    if (!source) return;
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

  return (
    <div className="flex flex-col h-screen bg-gray-50">
      <header className="flex items-center gap-4 px-4 py-2 border-b border-slate-200 bg-white">
        <span className="font-semibold text-slate-800">TTR Designer</span>
        <span className="text-xs px-2 py-0.5 rounded bg-amber-100 text-amber-700">read-only (server)</span>
        <div className="flex-1" />
        <SearchBox onSearch={runSearch} onSelectHit={(qname) => void selectNode(qname)} />
      </header>
      <div className="flex flex-1 overflow-hidden">
        <nav className="w-56 border-r border-slate-200 bg-white overflow-auto p-3" aria-label="Model index">
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
        <div className="flex-1 relative">
          <WsCanvas graph={graph} onNodeSelect={(qname) => void selectNode(qname)} />
        </div>
        <ObjectDetailPanel detail={detail} />
      </div>
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
    </div>
  );
}
