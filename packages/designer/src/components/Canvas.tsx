import { useEffect, useRef, useState } from 'react';
import type { ModelGraph, DisplayMode, Cardinality, RenderableSchemaCode, ViewportState } from '@tatrman/lsp';
import type { WorkspaceEdit } from 'vscode-languageserver-types';
import type { LspClient } from '../lsp-client';
import { modelGraphToCyElements } from '../cy/adapter';
import { glyphFor } from '../cy/glyph-renderer';
import { buildLayout, applyPositions } from '../cy/save-layout';
import { debounce } from '../util/debounce';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type CytoscapeInstance = any;

// Module-scope promise: loads cytoscape + extensions once, registers extensions globally.
const cytoscapeReadyPromise = Promise.all([
  import('cytoscape'),
  import('cytoscape-cose-bilkent'),
  import('cytoscape-node-html-label'),
]).then(([cyMod, coseMod, nlMod]) => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const cytoscape = (cyMod as any).default ?? cyMod;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const cose = (coseMod as any).default ?? coseMod;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const nl = (nlMod as any).default ?? nlMod;

  cytoscape.use(cose);
  nl(cytoscape);
  return cytoscape;
});

interface CanvasProps {
  graph: ModelGraph | null;
  displayMode: DisplayMode;
  activeSchema: RenderableSchemaCode;
  viewports: Record<RenderableSchemaCode, ViewportState>;
  nodePositions: Record<string, { x: number; y: number }>;
  lspClient: LspClient | null;
  projectRoot: string | null;
  onNodeSelect: (qname: string | null) => void;
  currentViewport: ViewportState | null;
  onRemoveNode: (qname: string) => void;
  // Applies the WorkspaceEdit returned by modeler/setLayout so the dragged
  // layout is written back to the .ttrg (canonical text). Without this the
  // layout only lives in cy and is lost on export / graph reopen.
  onLayoutPersist?: (edit: WorkspaceEdit) => void;
  // FO-31: false in the Studio Viewer build — suppresses the right-click
  // "Remove from graph" edit affordance. Render + view persistence are
  // unaffected. Defaults to editor.
  canEdit?: boolean;
}

interface ContextMenuState {
  visible: boolean;
  x: number;
  y: number;
  qname: string;
}

export function Canvas({ graph, displayMode, activeSchema, viewports, nodePositions, lspClient, projectRoot, onNodeSelect, currentViewport, onRemoveNode, onLayoutPersist, canEdit = true }: CanvasProps) {
  void activeSchema;
  void viewports;
  const containerRef = useRef<HTMLDivElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<CytoscapeInstance | null>(null);
  const canEditRef = useRef(canEdit);
  const displayModeRef = useRef<DisplayMode>(displayMode);
  const graphRef = useRef<ModelGraph | null>(graph);
  const onNodeSelectRef = useRef(onNodeSelect);
  const lspClientRef = useRef<LspClient | null>(null);
  const projectRootRef = useRef<string | null>(null);
  const nodePositionsRef = useRef(nodePositions);
  const currentViewportRef = useRef<ViewportState | null>(null);
  const cyInitRef = useRef(false);
  const rafRef = useRef<number | null>(null);
const [cyReady, setCyReady] = useState(false);
  const [contextMenu, setContextMenu] = useState<ContextMenuState>({ visible: false, x: 0, y: 0, qname: '' });
  const onRemoveNodeRef = useRef(onRemoveNode);
  const onLayoutPersistRef = useRef(onLayoutPersist);

  useEffect(() => { onNodeSelectRef.current = onNodeSelect; }, [onNodeSelect]);
  useEffect(() => { onRemoveNodeRef.current = onRemoveNode; }, [onRemoveNode]);
  useEffect(() => { canEditRef.current = canEdit; }, [canEdit]);
  useEffect(() => { onLayoutPersistRef.current = onLayoutPersist; }, [onLayoutPersist]);
  useEffect(() => {
    displayModeRef.current = displayMode;
    const client = lspClientRef.current;
    const graphUri = projectRootRef.current;
    const cy = cyRef.current;
    if (!client || !graphUri || !cy) return;
    // Persist the just-selected display mode. buildLayout composes the live
    // pan/zoom with this displayMode, so we must NOT use currentViewportRef
    // here (it lags by one render and would write the previous mode).
    const { nodes, viewport } = buildLayout(cy, currentViewportRef.current, displayMode);
    client.setLayout(graphUri, { version: 1 as const, viewport, nodes, edges: {} })
      .then((edit) => { if (edit) onLayoutPersistRef.current?.(edit); })
      .catch((_err: unknown) => {});
  }, [displayMode]);
  useEffect(() => { graphRef.current = graph; }, [graph]);
  useEffect(() => { lspClientRef.current = lspClient; }, [lspClient]);
  useEffect(() => { projectRootRef.current = projectRoot; }, [projectRoot]);
  useEffect(() => { nodePositionsRef.current = nodePositions; }, [nodePositions]);
  useEffect(() => { currentViewportRef.current = currentViewport; }, [currentViewport]);

  useEffect(() => {
    if (cyInitRef.current || !containerRef.current) return;
    cyInitRef.current = true;

    cytoscapeReadyPromise.then((cytoscape) => {
      if (!containerRef.current) return;

      const cy = cytoscape({
        container: containerRef.current,
        style: [
          {
            selector: 'node',
            style: {
              shape: 'round-rectangle',
              'background-color': '#ffffff',
              'background-opacity': 1,
              'border-width': 1,
              'border-color': '#64748b',
              width: 220,
              height: 'data(h)',
              'text-opacity': 0,
            },
          },
          {
            selector: 'node[kind = "table"]',
            style: { 'border-color': '#3b82f6' },
          },
          {
            selector: 'node[kind = "view"]',
            style: { 'border-color': '#8b5cf6' },
          },
          {
            selector: 'node[kind = "entity"]',
            style: { 'border-color': '#10b981' },
          },
          {
            selector: 'node:selected',
            style: { 'border-width': 2, 'border-color': '#0ea5e9' },
          },
          {
            selector: 'edge',
            style: {
              width: 1.5,
              'line-color': '#3b82f6',
              'target-arrow-color': '#3b82f6',
              'target-arrow-shape': 'triangle',
              'curve-style': 'bezier',
            },
          },
          {
            selector: 'edge[kind = "fk"]',
            style: { 'line-color': '#3b82f6', 'target-arrow-color': '#3b82f6', 'target-arrow-shape': 'triangle' },
          },
          {
            selector: 'edge[kind = "relation"]',
            style: { 'line-color': '#10b981', 'target-arrow-color': '#10b981', 'target-arrow-shape': 'none' },
          },
        ],
      });

      cy.nodeHtmlLabel([
        {
          query: 'node',
          halign: 'center',
          valign: 'center',
          tpl: (data: Record<string, unknown>) => (data['labelHtml'] as string) ?? '',
        },
      ]);

      function saveLayout() {
        const client = lspClientRef.current;
        const graphUri = projectRootRef.current;
        const cy = cyRef.current;
        if (!client || !graphUri || !cy) return;
        const { nodes, viewport } = buildLayout(cy, currentViewportRef.current, displayModeRef.current);
        client.setLayout(graphUri, { version: 1 as const, viewport, nodes, edges: {} })
          .then((edit) => { if (edit) onLayoutPersistRef.current?.(edit); })
          .catch((_err: unknown) => {});
      }

      const debouncedSaveLayout = debounce(saveLayout, 500);
      cy.on('dragfreeon', debouncedSaveLayout);
      cy.on('viewport', debounce(saveLayout, 750));
      cy.on('layoutstop', saveLayout);

      cy.on('tap', 'node, edge', (evt: CytoscapeInstance) => {
        const data = evt.target.data();
        onNodeSelectRef.current(data['qname'] as string);
      });
      cy.on('tap', (evt: CytoscapeInstance) => {
        if (evt.target === cy) onNodeSelectRef.current(null);
      });
      cy.on('cxttap', 'node', (evt: CytoscapeInstance) => {
        // FO-31: the Viewer build has no edit affordances — no remove menu.
        if (!canEditRef.current) return;
        const data = evt.target.data();
        const pos = evt.renderedPosition();
        setContextMenu({ visible: true, x: pos.x, y: pos.y, qname: data['qname'] as string });
      });
      cy.on('tap', () => {
        setContextMenu((prev) => prev.visible ? { ...prev, visible: false } : prev);
      });

      cyRef.current = cy;
      // Dev-only handle for the headless-browser harness (and manual debugging):
      // lets tooling read cy.edges()/nodes() and rendered positions. Stripped
      // from production builds.
      if (import.meta.env.DEV) {
        (window as unknown as { __cy?: CytoscapeInstance }).__cy = cy;
      }
      setCyReady(true);
    });

    return () => {
      if (cyRef.current) {
        cyRef.current.destroy();
        cyRef.current = null;
      }
      cyInitRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (!contextMenu.visible) return;
    const handlePointerDown = (e: PointerEvent) => {
      const menu = document.querySelector('[data-context-menu]');
      if (menu && !menu.contains(e.target as Node)) {
        setContextMenu((prev) => prev.visible ? { ...prev, visible: false } : prev);
      }
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setContextMenu((prev) => prev.visible ? { ...prev, visible: false } : prev);
      }
    };
    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [contextMenu.visible]);

  useEffect(() => {
    if (!cyRef.current) return;
    const cy = cyRef.current;

    if (graph === null) {
      cy.elements().remove();
      return;
    }

    const els = modelGraphToCyElements(graph, displayModeRef.current);
    cy.elements().remove();
    if (els.length > 0) {
      cy.add(els);
    }

    const positions = nodePositionsRef.current;
    const hasPositions = Object.keys(positions).length > 0;

    if (hasPositions) {
      applyPositions(cy, positions);
    }

    if (!hasPositions) {
      cy.layout({
        name: 'cose-bilkent',
        randomize: false,
        animate: false,
        nodeRepulsion: 8000,
        idealEdgeLength: 280,
        edgeElasticity: 0.45,
        gravity: 0.15,
        padding: 30,
      }).run();
    }
  }, [graph]);

  useEffect(() => {
    if (!cyRef.current || !graphRef.current) return;
    const cy = cyRef.current;
    const mode = displayModeRef.current;

    const els = modelGraphToCyElements(graphRef.current, mode);
    cy.nodes().forEach((node: CytoscapeInstance) => {
      const qname = node.data('qname');
      const el = els.find((e) => e.group === 'nodes' && e.data['qname'] === qname);
      if (el) {
        node.data('labelHtml', el.data['labelHtml']);
      }
    });
    (cy as unknown as { nodeHtmlLabel: (opts: string) => void }).nodeHtmlLabel('update');
  }, [displayMode]);

  useEffect(() => {
    const cy = cyRef.current;
    const overlayEl = overlayRef.current;
    if (!cy || !overlayEl) return;

    function renderOverlay() {
      const overlay = overlayEl;
      if (!overlay) return;
      const relationEdges = cy.edges('[kind = "relation"]');
      const zoom = cy.zoom();

      const svgParts: string[] = [];
      for (const edge of relationEdges) {
        const fromCard = edge.data('fromCardinality') as string | null;
        const toCard = edge.data('toCardinality') as string | null;

        const sEnd = edge.renderedSourceEndpoint() as { x: number; y: number };
        const tEnd = edge.renderedTargetEndpoint() as { x: number; y: number };
        const sx = sEnd.x;
        const sy = sEnd.y;
        const tx = tEnd.x;
        const ty = tEnd.y;

        const dx = tx - sx;
        const dy = ty - sy;
        const length = Math.sqrt(dx * dx + dy * dy);
        if (length === 0) continue;

        const angle = Math.atan2(dy, dx) * (180 / Math.PI);

        const fromGlyph = fromCard ? glyphFor(fromCard as Cardinality) : '';
        const toGlyph = toCard ? glyphFor(toCard as Cardinality) : '';

        if (fromGlyph) {
          svgParts.push(
            `<g transform="translate(${sx},${sy}) rotate(${angle}) scale(${zoom})">${fromGlyph}</g>`
          );
        }
        if (toGlyph) {
          svgParts.push(
            `<g transform="translate(${tx},${ty}) rotate(${angle + 180}) scale(${zoom})">${toGlyph}</g>`
          );
        }
      }

      overlay.innerHTML = svgParts.length > 0
        ? `<svg style="position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;overflow:visible">${svgParts.join('')}</svg>`
        : '';
    }

    function scheduleRender() {
      if (rafRef.current !== null) return;
      rafRef.current = requestAnimationFrame(() => {
        rafRef.current = null;
        renderOverlay();
      });
    }

    cy.on('render zoom pan', scheduleRender);
    renderOverlay();

    return () => {
      cy.off('render zoom pan', scheduleRender);
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = null;
      }
    };
  }, [cyReady]);

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>
      <div
        ref={containerRef}
        className="w-full h-full bg-white"
        style={{ minHeight: '400px' }}
      />
      <div
        ref={overlayRef}
        style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}
      />
      {contextMenu.visible && (
        <div
          data-context-menu
          className="absolute bg-white border border-slate-300 rounded-lg shadow-lg py-1 z-50"
          style={{ left: contextMenu.x, top: contextMenu.y, minWidth: '160px' }}
        >
          <button
            onClick={() => {
              onRemoveNodeRef.current(contextMenu.qname);
              setContextMenu((prev) => ({ ...prev, visible: false }));
            }}
            className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50"
          >
            Remove from graph
          </button>
        </div>
      )}
    </div>
  );
}