import { useEffect, useRef, useState } from 'react';
import { ttrmGraphToCyElements } from '../cy/ttrm-adapter';
import type { TtrmGraph } from '../data/ttrm-types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type CytoscapeInstance = any;

// Load cytoscape + extensions once (mirrors Canvas.tsx). Layout persistence landed
// in T4 (TP-5, gated by `editable`); still no context menu — a strict subset.
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

/**
 * Cytoscape canvas for the WS ttrm dependency-graph DTO. Edit-capable as of T4
 * (TP-5) when [editable] and a specific `.ttrg` graph is in view: manual
 * positions from `ttrm/getLayout` (passed in as [positions]) are honored
 * instead of always auto-laying-out, and [onNodeDragEnd] fires so the caller
 * can persist via `ttrm/setLayout`. The generic package/schema browse view
 * (no `positions`) is unaffected — it still auto-lays-out every time, exactly
 * as before T4.
 */
export function WsCanvas({
  graph,
  positions,
  editable = false,
  onNodeSelect,
  onNodeDragEnd,
}: {
  graph: TtrmGraph | null;
  positions?: Record<string, { x: number; y: number }> | null;
  editable?: boolean;
  onNodeSelect: (qname: string | null) => void;
  onNodeDragEnd?: (qname: string, x: number, y: number) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<CytoscapeInstance | null>(null);
  const cyInitRef = useRef(false);
  const onNodeSelectRef = useRef(onNodeSelect);
  useEffect(() => { onNodeSelectRef.current = onNodeSelect; }, [onNodeSelect]);
  const onNodeDragEndRef = useRef(onNodeDragEnd);
  useEffect(() => { onNodeDragEndRef.current = onNodeDragEnd; }, [onNodeDragEnd]);
  const editableRef = useRef(editable);
  useEffect(() => { editableRef.current = editable; }, [editable]);
  const [, setReady] = useState(false);

  useEffect(() => {
    if (cyInitRef.current || !containerRef.current) return;
    cyInitRef.current = true;
    cytoscapeReadyPromise.then((cytoscape) => {
      if (!containerRef.current) return;
      const cy = cytoscape({
        container: containerRef.current,
        style: [
          { selector: 'node', style: { shape: 'round-rectangle', 'background-color': '#ffffff', 'border-width': 1, 'border-color': '#64748b', width: 200, height: 'data(h)', 'text-opacity': 0 } },
          { selector: 'node[kind = "table"]', style: { 'border-color': '#3b82f6' } },
          { selector: 'node[kind = "view"]', style: { 'border-color': '#8b5cf6' } },
          { selector: 'node:selected', style: { 'border-width': 2, 'border-color': '#0ea5e9' } },
          { selector: 'edge', style: { width: 1.5, 'line-color': '#3b82f6', 'target-arrow-color': '#3b82f6', 'target-arrow-shape': 'triangle', 'curve-style': 'bezier' } },
        ],
      });
      cy.nodeHtmlLabel([
        { query: 'node', halign: 'center', valign: 'center', tpl: (data: Record<string, unknown>) => (data['labelHtml'] as string) ?? '' },
      ]);
      cy.on('tap', 'node', (evt: CytoscapeInstance) => onNodeSelectRef.current(evt.target.data('qname') as string));
      cy.on('tap', (evt: CytoscapeInstance) => { if (evt.target === cy) onNodeSelectRef.current(null); });
      // Drag-persist (T4, TP-5): only fires when the caller marked this view editable
      // (a specific `.ttrg` graph, not the ad-hoc package/schema browse view, which
      // has no `.ttrg` file to persist positions to).
      cy.on('dragfree', 'node', (evt: CytoscapeInstance) => {
        if (!editableRef.current) return;
        const pos = evt.target.position() as { x: number; y: number };
        onNodeDragEndRef.current?.(evt.target.data('qname') as string, pos.x, pos.y);
      });
      cyRef.current = cy;
      setReady(true);
    });
    return () => {
      if (cyRef.current) { cyRef.current.destroy(); cyRef.current = null; }
      cyInitRef.current = false;
    };
  }, []);

  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) return;
    cy.elements().remove();
    if (!graph) return;
    const els = ttrmGraphToCyElements(graph);
    if (els.length === 0) return;
    cy.add(els);
    const hasManualPositions = positions != null && Object.keys(positions).length > 0;
    if (hasManualPositions) {
      // Manual mode (C1-c "mode: manual"): apply whatever positions exist; a node
      // with no recorded position yet (newly added via addObjectToGraph) falls
      // back to cytoscape's default placement rather than re-running auto-layout
      // over the whole canvas and disturbing everyone else's saved positions.
      cy.nodes().forEach((n: CytoscapeInstance) => {
        const p = positions![n.data('qname') as string];
        if (p) n.position(p);
      });
    } else {
      cy.layout({ name: 'cose-bilkent', randomize: false, animate: false, nodeRepulsion: 8000, idealEdgeLength: 240, gravity: 0.15, padding: 30 }).run();
    }
  }, [graph, positions]);

  const nodeCount = graph?.nodes.length ?? 0;
  const edgeCount = graph?.edges.length ?? 0;

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>
      <div ref={containerRef} className="w-full h-full bg-white" style={{ minHeight: '400px' }} />
      {/* Test/observability hook — the graph element counts, independent of the cy instance. */}
      <div data-testid="ws-canvas-stats" hidden>{`${nodeCount}/${edgeCount}`}</div>
    </div>
  );
}
