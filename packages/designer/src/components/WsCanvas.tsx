import { useEffect, useRef, useState } from 'react';
import { ttrmGraphToCyElements } from '../cy/ttrm-adapter';
import type { TtrmGraph } from '../data/ttrm-types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type CytoscapeInstance = any;

// Load cytoscape + extensions once (mirrors Canvas.tsx; WS mode is read-only, so no
// layout persistence, no context menu — a strict subset).
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

/** Read-only Cytoscape canvas for the WS ttrm dependency-graph DTO. */
export function WsCanvas({
  graph,
  onNodeSelect,
}: {
  graph: TtrmGraph | null;
  onNodeSelect: (qname: string | null) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<CytoscapeInstance | null>(null);
  const cyInitRef = useRef(false);
  const onNodeSelectRef = useRef(onNodeSelect);
  useEffect(() => { onNodeSelectRef.current = onNodeSelect; }, [onNodeSelect]);
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
    if (els.length > 0) {
      cy.add(els);
      cy.layout({ name: 'cose-bilkent', randomize: false, animate: false, nodeRepulsion: 8000, idealEdgeLength: 240, gravity: 0.15, padding: 30 }).run();
    }
  }, [graph]);

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
