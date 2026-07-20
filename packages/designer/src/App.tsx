// SPDX-License-Identifier: Apache-2.0
// The Studio Viewer entry (Designer Merge, DM-P2.S4). Selects a backend (P2, never sniffed),
// builds the ModelDataSource + its ViewStateStore, loads the catalog, and mounts the DS ShellFrame.
// The OPEN build ships NO edit code: `editContext` is undefined everywhere here, so the shell is
// edit-absent (FO-21); the FO-P0.S4 license loader supplies an authoring context on the commercial
// build (DM-P3). Cytoscape and the flat SV designer are retired (DM-⚑a) — React Flow is the engine.

import { useEffect, useMemo, useRef, useState } from 'react';
import type { DisplayMode } from '@tatrman/lsp';
import type { ViewStateStore } from '@tatrman/canvas-core';
import { ShellFrame, type ActiveSubject } from './shell/ShellFrame';
import { buildCatalog } from './shell/catalog';
import type { CatalogGroup } from './shell/types';
import type { ModelDataSource } from './data/model-data-source';
import { WorkerLspDataSource } from './data/worker-lsp-data-source';
import { WsDesignerServerDataSource } from './data/ws-designer-server-data-source';
import { VelesReadApiDataSource } from './data/veles-read-api-data-source';
import { VelesTtrmDataSource } from './data/veles-ttrm-data-source';
import { makeViewStateStore, type ViewStateStoreIO } from './data/view-state-store-factory';
import type { PrefsRecord } from './data/view-state-store';
import { selectBackend, BackendSelectionError, type BackendSelection } from './data/select-data-source';
import { useAuthoringContext } from './ext/use-authoring-context.js';
import { createLspClient, type LspClient } from './lsp-client';
import { loadDemoFiles } from './fs/demo-loader';
import { loadProjectViaFileSystemAccessAPI, type ProjectFiles } from './fs/file-system';
import { applyWorkspaceEdit } from './lsp/apply-workspace-edit';
import { ErrorBoundary } from './components/ErrorBoundary';
import { ToastContainer, makeToast, type ToastMessage } from './Toast';

function isModelFile(relativePath: string): boolean {
  return relativePath.endsWith('.ttrm') || relativePath.endsWith('.ttrg');
}

function resolveBackend(): BackendSelection | { kind: 'error'; message: string } {
  try {
    return selectBackend(window.location.search);
  } catch (err) {
    if (err instanceof BackendSelectionError) return { kind: 'error', message: err.message };
    throw err;
  }
}

/** The server-backed selections (everything except worker). */
type ServerSelection = Extract<BackendSelection, { kind: 'ws' | 'veles' }>;

function BackendErrorScreen({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center h-screen gap-4 p-12 bg-gray-50">
      <div className="bg-white border border-red-300 rounded-xl shadow-lg p-8 max-w-md text-center">
        <h2 className="text-xl font-bold text-red-700 mb-2">Invalid backend selection</h2>
        <p className="text-sm text-slate-600 break-words">{message}</p>
      </div>
    </div>
  );
}

/** A ready Studio: the DS shell over a resolved data source + view-state store. Edit-absent in the
 *  OPEN build (no authoring loader registered → `editContext` undefined, FO-21); the COMMERCIAL build
 *  registers a loader that resolves a `ShellEditContext` here (FO-P0.S4). */
function Studio({
  dataSource, viewState, catalog, files, workspace,
}: {
  dataSource: ModelDataSource;
  viewState?: ViewStateStore;
  catalog: CatalogGroup[];
  files: string[];
  workspace: string;
}) {
  const editContext = useAuthoringContext(dataSource);
  const [displayMode, setDisplayMode] = useState<DisplayMode>('just-names');
  const [active, setActive] = useState<ActiveSubject | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const addToast = (message: string, kind: 'error' | 'info' = 'error') => setToasts((p) => [...p, makeToast(message, kind)]);
  const dismissToast = (id: string) => setToasts((p) => p.filter((t) => t.id !== id));

  // apply a saved subject's display mode once, when a different subject becomes active.
  const displayModeRef = useRef<string | null>(null);
  const onActiveChange = (a: ActiveSubject | null) => {
    setActive(a);
    if (a?.viewportDisplayMode && a.ref !== displayModeRef.current) {
      displayModeRef.current = a.ref;
      setDisplayMode(a.viewportDisplayMode);
    }
  };

  return (
    <div className="flex flex-col h-screen bg-gray-50">
      <header className="flex items-center gap-4 px-4 py-2 border-b border-slate-200 bg-white" data-testid="studio-header">
        <span className="font-semibold text-slate-800">{active?.label ?? workspace}</span>
        <div className="ml-auto flex items-center gap-2 text-xs">
          <span className="uppercase tracking-wide text-slate-400">Detail</span>
          <select
            data-testid="display-mode"
            value={displayMode}
            onChange={(e) => setDisplayMode(e.target.value as DisplayMode)}
            className="border border-slate-300 rounded px-2 py-1"
          >
            <option value="just-names">Names</option>
            <option value="with-types">With types</option>
          </select>
        </div>
      </header>
      <ErrorBoundary label="shell" resetKey={workspace}>
        <ShellFrame
          dataSource={dataSource}
          workspace={workspace}
          catalog={catalog}
          files={files}
          displayMode={displayMode}
          viewState={viewState}
          onActiveChange={onActiveChange}
          onError={addToast}
          editContext={editContext ?? undefined}
        />
      </ErrorBoundary>
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
    </div>
  );
}

/** A loading / landing shell while a backend is being established. */
function Splash({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center h-screen gap-6 p-12 bg-gray-50">
      <div className="bg-white border border-slate-300 rounded-xl shadow-lg p-10 max-w-md text-center">
        <h2 className="text-2xl font-bold text-gray-800 mb-3">Studio Viewer</h2>
        {children}
      </div>
    </div>
  );
}

/** Worker backend: local LSP over a Web Worker; load a project/demo, then mount the shell. */
function WorkerStudio({ demo }: { demo: string | null }) {
  const [ready, setReady] = useState<{ dataSource: ModelDataSource; viewState: ViewStateStore; catalog: CatalogGroup[]; files: string[]; workspace: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(demo !== null);
  const clientRef = useRef<LspClient | null>(null);
  const docTextCache = useRef<Map<string, string>>(new Map());
  const prefsMap = useRef<Map<string, PrefsRecord>>(new Map());
  const startedRef = useRef(false);

  const getText = (uri: string) => docTextCache.current.get(uri);
  const openDoc = async (uri: string, content: string) => {
    docTextCache.current.set(uri, content);
    await clientRef.current!.openDocument(uri, content);
  };

  const buildStudio = async (client: LspClient, projectRoot: string, workspace: string, files: string[]) => {
    const dataSource = new WorkerLspDataSource(client, { projectRoot });
    const io: ViewStateStoreIO = {
      kind: 'in-file',
      layout: {
        getLayout: (key) => client.getLayout(key),
        setLayout: async (key, layout) => {
          const edit = await client.setLayout(key, layout);
          if (edit) await applyWorkspaceEdit(edit, getText, openDoc);
        },
      },
      prefs: {
        get: (key) => prefsMap.current.get(key),
        set: (key, v) => prefsMap.current.set(key, { ...prefsMap.current.get(key), ...v }),
      },
    };
    const viewState = makeViewStateStore('in-file', io);
    const { graphs, symbols } = await dataSource.listCatalog();
    setReady({ dataSource, viewState, catalog: buildCatalog(graphs, symbols), files, workspace });
  };

  // establish the worker LSP once
  useEffect(() => {
    let cancelled = false;
    createLspClient().then((client) => {
      if (cancelled) { client.dispose(); return; }
      client.onDiagnostics((_uri, messages) => { if (messages.length) setError(messages.join(', ')); });
      clientRef.current = client;
    });
    return () => { cancelled = true; clientRef.current?.dispose(); clientRef.current = null; };
  }, []);

  // auto-load a ?demo= project
  useEffect(() => {
    if (!demo || startedRef.current) return;
    const client = clientRef.current;
    if (!client) return; // wait for the client effect
    startedRef.current = true;
    (async () => {
      try {
        const files = await loadDemoFiles(demo);
        await client.setProjectRoot(`file:///${demo}`);
        const modelPaths = Array.from(files.files.keys()).filter(isModelFile);
        await Promise.all(modelPaths.map((p) => openDoc(`file:///${demo}/${p}`, files.files.get(p)!)));
        await buildStudio(client, `file:///${demo}`, demo, modelPaths);
      } catch (e) {
        setError(`Failed to load demo: ${e}`);
      } finally {
        setLoading(false);
      }
    })();
    // re-run when the client arrives
  });

  const loadFolder = async (files: ProjectFiles) => {
    const client = clientRef.current;
    if (!client) return;
    setLoading(true);
    try {
      await client.setProjectRoot(`file:///${files.rootName}`);
      const modelPaths = Array.from(files.files.keys()).filter(isModelFile);
      await Promise.all(modelPaths.map((p) => openDoc(`file:///${files.rootName}/${p}`, files.files.get(p)!)));
      await buildStudio(client, `file:///${files.rootName}`, files.rootName, modelPaths);
    } catch (e) {
      setError(`Failed to load project: ${e}`);
    } finally {
      setLoading(false);
    }
  };

  if (error && !ready) return <BackendErrorScreen message={error} />;
  if (ready) return <Studio {...ready} />;
  if (loading) return <Splash><p className="text-gray-500">Loading…</p></Splash>;
  return (
    <Splash>
      <p className="text-gray-500 mb-8">Open a local project folder or explore a demo.</p>
      <div className="flex flex-col gap-3">
        <button
          onClick={async () => { const f = await loadProjectViaFileSystemAccessAPI(); if (f) await loadFolder(f); }}
          className="px-6 py-3 bg-sky-500 text-white rounded-lg hover:bg-sky-600 font-medium transition-colors"
        >
          Load Project Folder
        </button>
        <button
          onClick={() => { const p = new URLSearchParams(window.location.search); p.set('demo', 'v1.1-mini'); window.location.search = p.toString(); }}
          className="px-6 py-3 bg-slate-100 text-gray-700 border border-slate-300 rounded-lg hover:bg-slate-200 font-medium transition-colors"
        >
          Open Demo (v1.1-mini)
        </button>
      </div>
    </Splash>
  );
}

/** Server backends (WS / Veles): read-only deployed catalogs; connect, list, mount. */
function ServerStudio(props: ServerSelection) {
  const [ready, setReady] = useState<{ dataSource: ModelDataSource; viewState: ViewStateStore; catalog: CatalogGroup[] } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let disposed = false;
    (async () => {
      try {
        let dataSource: ModelDataSource;
        let io: ViewStateStoreIO;
        if (props.kind === 'ws') {
          const ws = new WsDesignerServerDataSource(props.origin);
          await ws.connect();
          dataSource = ws;
          io = { kind: 'sidecar', layout: { getLayout: (uri) => ws.getLayout(uri), setLayout: async (uri, canvases) => { await ws.setLayout(uri, canvases); } } };
        } else if (props.transport === 'ttrm') {
          // Platform Veles: WS ttrm/* + bearer on the handshake (VS-2). No sidecar layout.
          const v = new VelesTtrmDataSource(props.origin, { token: props.token ?? undefined });
          await v.connect();
          dataSource = v;
          io = { kind: 'none' };
        } else {
          dataSource = new VelesReadApiDataSource(props.base);
          io = { kind: 'none' };
        }
        const { graphs, symbols } = await dataSource.listCatalog();
        if (disposed) return;
        setReady({ dataSource, viewState: makeViewStateStore(io.kind, io), catalog: buildCatalog(graphs, symbols) });
      } catch (e) {
        if (!disposed) setError(`Failed to reach the ${props.kind} backend: ${e}`);
      }
    })();
    return () => { disposed = true; };
  }, []);

  const label = props.kind === 'ws' ? props.origin : props.transport === 'ttrm' ? props.origin : props.base;
  if (error) return <BackendErrorScreen message={error} />;
  if (!ready) return <Splash><p className="text-gray-500">Connecting to the {props.kind} backend…</p></Splash>;
  return <Studio dataSource={ready.dataSource} viewState={ready.viewState} catalog={ready.catalog} files={[]} workspace={label} />;
}

function App() {
  const backend = useMemo(resolveBackend, []);
  if (backend.kind === 'error') return <BackendErrorScreen message={backend.message} />;
  if (backend.kind === 'ws' || backend.kind === 'veles') return <ServerStudio {...backend} />;
  return <WorkerStudio demo={backend.demo} />;
}

export default App;
