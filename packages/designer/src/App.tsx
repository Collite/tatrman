import { useEffect, useMemo, useRef, useState } from 'react';
import { useReducer } from 'react';
import { Header } from './components/Header';
import { Canvas } from './components/Canvas';
import { GraphPicker } from './components/GraphPicker';
import { InspectorPanel } from './components/InspectorPanel';
import { NlPane } from './components/NlPane';
import { ErrorBoundary } from './components/ErrorBoundary';
import { createLspClient } from './lsp-client';
import type { LspClient } from './lsp-client';
import { designerReducer } from './state/designer-reducer';
import { initialDesignerState } from './state/designer-state';
import { loadProjectViaFileSystemAccessAPI, type ProjectFiles } from './fs/file-system';
import { loadDemoFiles } from './fs/demo-loader';
import { getGraphResponseToModelGraph } from './cy/adapter';
import type { DisplayMode } from '@tatrman/lsp';
import { applyWorkspaceEdit } from './lsp/apply-workspace-edit';
import type { WorkspaceEdit } from 'vscode-languageserver-types';
import { WsModeApp } from './WsModeApp';
import { VelesModeApp } from './VelesModeApp';
import { selectBackend, BackendSelectionError } from './data/select-data-source';

/** Files the TTR language server understands. Non-TTR files (e.g. modeler.toml)
 *  must not be opened as `ttr` documents — they'd be parsed as TTR and emit
 *  spurious parse errors. */
function isModelFile(relativePath: string): boolean {
  return relativePath.endsWith('.ttrm') || relativePath.endsWith('.ttrg');
}

function LandingCard({ onLoadProject, onOpenDemo }: { onLoadProject: () => void; onOpenDemo: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center flex-1 gap-8 p-12">
      <div className="bg-white border border-slate-300 rounded-xl shadow-lg p-10 max-w-md text-center">
        <h2 className="text-2xl font-bold text-gray-800 mb-3">TTR Modeler Designer</h2>
        <p className="text-gray-500 mb-8">
          Visual schema designer for Tatrman models. Load a local project folder or explore a demo.
        </p>
        <div className="flex flex-col gap-3">
          <button
            onClick={onLoadProject}
            className="px-6 py-3 bg-sky-500 text-white rounded-lg hover:bg-sky-600 font-medium transition-colors"
          >
            Load Project Folder
          </button>
          <button
            onClick={onOpenDemo}
            className="px-6 py-3 bg-slate-100 text-gray-700 border border-slate-300 rounded-lg hover:bg-slate-200 font-medium transition-colors"
          >
            Open Demo (v1.1-mini)
          </button>
        </div>
      </div>
    </div>
  );
}

/** Explicit backend selection (P2, never sniffed). WS mode and Veles mode are
 *  separate views; the worker path below is unchanged. Computed once from the URL. */
function resolveBackend():
  | { kind: 'worker'; demo: string | null }
  | { kind: 'ws'; origin: string }
  | { kind: 'veles'; base: string }
  | { kind: 'error'; message: string } {
  try {
    return selectBackend(window.location.search);
  } catch (err) {
    if (err instanceof BackendSelectionError) return { kind: 'error', message: err.message };
    throw err;
  }
}

/** FO-31 view-mode flag (FO-P0.S1). `?viewer=1` selects the read-only Studio
 *  Viewer surface: render + view persistence, no edit affordances. This is the
 *  runtime substrate; FO-P0.S2 makes it a build in which the edit code is absent
 *  rather than merely gated. Veles is already read-only; WS mode gates per-view. */
function isViewerMode(): boolean {
  return new URLSearchParams(window.location.search).get('viewer') === '1';
}

function App() {
  const backend = useMemo(resolveBackend, []);
  const viewer = useMemo(isViewerMode, []);
  if (backend.kind === 'ws') return <WsModeApp origin={backend.origin} />;
  if (backend.kind === 'veles') return <VelesModeApp base={backend.base} />;
  if (backend.kind === 'error') {
    return (
      <div className="flex flex-col items-center justify-center h-screen gap-4 p-12 bg-gray-50">
        <div className="bg-white border border-red-300 rounded-xl shadow-lg p-8 max-w-md text-center">
          <h2 className="text-xl font-bold text-red-700 mb-2">Invalid backend selection</h2>
          <p className="text-sm text-slate-600 break-words">{backend.message}</p>
        </div>
      </div>
    );
  }
  return <WorkerApp viewer={viewer} />;
}

// FO-21 (FO-P0.S2.T4): the Studio Viewer's Worker-mode app — render + view
// persistence only. The add/remove object handlers, the create-graph wizard, and
// the object-picker / missing-objects drawer moved to `tatrman-platform`'s
// authoring extension and re-enter via the extension surface (FO-P0.S4). What
// stays: project/demo load, graph render, and the layout write-back
// (`persistLayoutEdit` → modeler/setLayout), which is view-persistence (FO-31).
function WorkerApp({ viewer = false }: { viewer?: boolean }) {
  const [state, dispatch] = useReducer(designerReducer, initialDesignerState);
  const [nlPaneOpen, setNlPaneOpen] = useState(false);
  const [clientReady, setClientReady] = useState(false);
  const [transportKind, setTransportKind] = useState<'node' | 'browser' | null>(null);
  const clientRef = useRef<LspClient | null>(null);
  const demoLoadingRef = useRef(false);
  const docTextCache = useRef<Map<string, string>>(new Map());

  const getText = (uri: string) => docTextCache.current.get(uri);

  const openDoc = async (uri: string, content: string) => {
    docTextCache.current.set(uri, content);
    await clientRef.current!.openDocument(uri, content);
  };

  useEffect(() => {
    let cancelled = false;
    createLspClient().then((client: LspClient) => {
      if (cancelled) {
        client.dispose();
        return;
      }
      client.onDiagnostics((_uri, messages) => {
        dispatch({ type: 'setError', message: messages.length === 0 ? null : messages.join(', ') });
      });
      clientRef.current = client;
      setTransportKind(client.transportKind);
      setClientReady(true);
    });
    return () => {
      cancelled = true;
      clientRef.current?.dispose();
      clientRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!clientReady) return;
    const params = new URLSearchParams(window.location.search);
    const demo = params.get('demo');
    if (!demo || demoLoadingRef.current) return;
    demoLoadingRef.current = true;
    loadDemoFiles(demo).then(async (files) => {
      if (!clientRef.current) return;
      // Declare the project root before opening docs so package inference is
      // relative to it (the browser worker has no workspace folder).
      await clientRef.current.setProjectRoot(`file:///${demo}`);
      await Promise.all(
        Array.from(files.files.entries())
          .filter(([relativePath]) => isModelFile(relativePath))
          .map(([relativePath, content]) =>
            openDoc(`file:///${demo}/${relativePath}`, content)
          )
      );
      dispatch({ type: 'loadProject', projectUri: `file:///${demo}` });
      const result = await clientRef.current.listGraphs(`file:///${demo}`);
      dispatch({ type: 'storeGraphList', graphs: result.graphs });
    }).catch((err: unknown) => {
      dispatch({ type: 'setError', message: `Failed to load demo: ${err}` });
    });
  }, [clientReady]);

  const handleFileLoad = async (files: ProjectFiles) => {
    const client = clientRef.current;
    if (!client) return;
    await client.setProjectRoot(`file:///${files.rootName}`);
    await Promise.all(
      Array.from(files.files.entries())
        .filter(([relativePath]) => isModelFile(relativePath))
        .map(([relativePath, content]) =>
          openDoc(`file:///${files.rootName}/${relativePath}`, content)
        )
    );
    dispatch({ type: 'loadProject', projectUri: `file:///${files.rootName}` });
    const result = await client.listGraphs(`file:///${files.rootName}`);
    dispatch({ type: 'storeGraphList', graphs: result.graphs });
  };

  const handleDirPick = async () => {
    const files = await loadProjectViaFileSystemAccessAPI();
    if (files) handleFileLoad(files);
  };

  const handleOpenDemo = () => {
    const params = new URLSearchParams(window.location.search);
    params.set('demo', 'v1.1-mini');
    window.location.search = params.toString();
  };

  const handleOpenTtrg = async () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.ttrg';
    input.onchange = async () => {
      const file = input.files?.[0];
      if (!file) return;
      const content = await file.text();
      const uri = `file:///${file.name}`;
      if (!clientRef.current) return;
      await openDoc(uri, content);
      dispatch({ type: 'loadProject', projectUri: uri });
      dispatch({ type: 'storeGraphList', graphs: [] });
      await handleSelectGraph(uri);
    };
    input.click();
  };

  const handleSelectGraph = async (graphUri: string) => {
    dispatch({ type: 'openGraph', graphUri });
    const client = clientRef.current;
    if (!client) return;
    const graph = await client.getGraph(graphUri);
    if (graph) {
      dispatch({ type: 'storeGraph', graph });
      if (graph.layout?.nodes && Object.keys(graph.layout.nodes).length > 0) {
        dispatch({ type: 'loadLayout', layout: graph.layout });
      }
    }
  };

  const handleNodeSelect = (qname: string | null) => {
    dispatch({ type: 'selectSymbol', qname });
  };

  // Write a layout edit (from modeler/setLayout) back into the .ttrg text so the
  // dragged positions / display mode survive export and graph reopen. The graph
  // is not refetched — cy already shows these positions, and currentGraph is
  // unchanged, so this does not trigger a Canvas rebuild.
  const persistLayoutEdit = (edit: WorkspaceEdit) => {
    void applyWorkspaceEdit(edit, getText, openDoc).catch(() => {});
  };

  useEffect(() => {
    const qname = state.selectedSymbol?.qname;
    if (!qname) return;
    if (state.symbolDetails[qname]) return;
    const client = clientRef.current;
    if (!client) return;
    let cancelled = false;
    client.getSymbolDetail(qname).then((detail) => {
      if (cancelled || !detail) return;
      dispatch({ type: 'storeSymbolDetail', detail });
    }).catch((err) => {
      if (cancelled) return;
      dispatch({ type: 'setError', message: String(err) });
    });
    return () => { cancelled = true; };
  }, [state.selectedSymbol?.qname, state.symbolDetails]);

  // Stable graph identity: rebuild the cy-facing ModelGraph only when the
  // underlying graph response changes — NOT on every render. Without this, a
  // selection click (which only changes selectedSymbol) handed Canvas a fresh
  // graph object, re-triggering its rebuild effect and snapping dragged nodes
  // back to their original layout positions.
  const modelGraph = useMemo(
    () => (state.currentGraph ? getGraphResponseToModelGraph(state.currentGraph) : null),
    [state.currentGraph]
  );

  const hasProject = state.projectUri !== null;
  const hasGraph = state.currentGraphUri !== null;
  const showPicker = hasProject && !hasGraph;
  const graphName = state.currentGraphUri
    ? (state.availableGraphs.find((g) => g.uri === state.currentGraphUri)?.name ?? state.currentGraphUri.split('/').pop() ?? null)
    : null;

  return (
    <div className="flex flex-col h-screen bg-gray-50" data-viewer={viewer ? '1' : undefined}>
      <Header
        graphName={graphName}
        missingObjectsCount={state.currentGraph?.missingObjects?.length ?? 0}
        displayMode={state.currentViewport?.displayMode ?? 'just-names'}
        projectUri={state.projectUri}
        transportKind={transportKind}
        onFileLoad={handleFileLoad}
        onDisplayModeChange={(mode: DisplayMode) => dispatch({ type: 'setDisplayMode', mode })}
        onToggleNlPane={() => setNlPaneOpen((v) => !v)}
        onDirPick={handleDirPick}
        onBack={() => dispatch({ type: 'closeGraph' })}
        onOpenFile={handleOpenTtrg}
        onDownloadLayout={async () => {
          const client = clientRef.current;
          const uri = state.currentGraphUri;
          if (!client || !uri) return;
          const layout = await client.exportLayout(uri);
          const blob = new Blob([JSON.stringify(layout, null, 2)], { type: 'application/json' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'layout.json';
          a.click();
          URL.revokeObjectURL(url);
        }}
      />
      {state.error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-2">
          {state.error}
        </div>
      )}
      {!hasProject ? (
        <LandingCard onLoadProject={handleDirPick} onOpenDemo={handleOpenDemo} />
      ) : showPicker ? (
        <GraphPicker
          graphs={state.availableGraphs}
          onSelect={handleSelectGraph}
        />
      ) : hasGraph ? (
        <div className="flex flex-1 overflow-hidden">
          <div className="flex-1 relative">
            <ErrorBoundary
              label={state.currentGraph?.schema ?? 'graph'}
              resetKey={state.currentGraphUri ?? 'none'}
            >
              <Canvas
                graph={modelGraph}
                displayMode={state.currentViewport?.displayMode ?? 'just-names'}
                activeSchema={'er'}
                viewports={{ er: state.currentViewport ?? { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' }, db: { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' } }}
                nodePositions={state.nodePositions}
                lspClient={clientRef.current}
                projectRoot={state.currentGraphUri}
                onNodeSelect={handleNodeSelect}
                currentViewport={state.currentViewport}
                onLayoutPersist={persistLayoutEdit}
              />
            </ErrorBoundary>
          </div>
          <InspectorPanel
            selectedSymbol={state.selectedSymbol}
            symbolDetails={state.symbolDetails}
            onSelect={handleNodeSelect}
          />
        </div>
      ) : null}
      <NlPane open={nlPaneOpen} onToggle={() => setNlPaneOpen((v) => !v)} />
    </div>
  );
}

export default App;