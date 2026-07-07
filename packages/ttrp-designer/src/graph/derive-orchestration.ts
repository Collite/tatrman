import type { EdgeView, GetGraphResult, NodeView } from './types.js';

/** A canvas node the Cytoscape adapter renders (container island, op, or program leaf). */
export interface DesignerNode {
  zeta: string;
  kind: string;
  label: string;
  /** Owning container path (drill-in canvases); undefined at the orchestration level. */
  containerPath?: string;
  isContainer: boolean;
  derived: boolean;
  synthesized: boolean;
  range: NodeView['range'];
  provenance?: NodeView['provenance'];
}

export interface DesignerEdge {
  id: string;
  from: string;
  to: string;
  fromPort: string;
  toPort: string;
  type: string;
  via?: string | null;
}

export interface CanvasElements {
  nodes: DesignerNode[];
  edges: DesignerEdge[];
}

const PROGRAM = 'program';

function nodeFrom(n: NodeView, isContainer: boolean, containerPath?: string): DesignerNode {
  return {
    zeta: n.zeta,
    kind: n.kind,
    label: n.label,
    containerPath,
    isContainer,
    derived: false,
    synthesized: n.synthesized ?? false,
    range: n.range,
    provenance: n.provenance,
  };
}

function edgeFrom(e: EdgeView, i: number): DesignerEdge {
  return {
    id: `e${i}:${e.from}.${e.fromPort}->${e.to}.${e.toPort}`,
    from: e.from,
    to: e.to,
    fromPort: e.fromPort,
    toPort: e.toPort,
    type: e.type,
    via: e.via ?? null,
  };
}

/**
 * The orchestration (top-level) element set (C1-a β consequence 1): collapsed containers
 * + program-level leaves (movement/store/display) + cross-container data edges (with their
 * synthesized transfer id) + control edges — exactly the derived execution-layer graph the
 * author sees as waves. NO inner op nodes appear at this level.
 */
export function deriveOrchestration(result: GetGraphResult): CanvasElements {
  const { graph } = result;
  const nodes: DesignerNode[] = [
    ...graph.containers.map((c) => ({
      zeta: c.path,
      kind: 'Container',
      label: c.path,
      isContainer: true,
      derived: c.derived,
      synthesized: false,
      range: null,
    })),
    ...graph.leaves.map((l) => nodeFrom(l, false)),
  ];
  const edges = graph.edges.map((e, i) => edgeFrom(e, i));
  return { nodes, edges };
}

/**
 * A container drill-in element set (C1-a consequence 4): the container's own nodes + internal
 * edges, with any CHILD containers collapsed (the same rule at every level — recursion via
 * this function). A fragment/derived container renders its decomposed sub-graph read-only.
 */
export function deriveContainer(result: GetGraphResult, path: string): CanvasElements {
  const container = result.graph.containers.find((c) => c.path === path);
  if (!container) return { nodes: [], edges: [] };
  const nodes = container.nodes.map((n) => nodeFrom(n, false, path));
  const edges = container.edges.map((e, i) => edgeFrom(e, i));
  return { nodes, edges };
}

/** The element set for any canvas key (`program` → orchestration, else drill-in). */
export function deriveCanvas(result: GetGraphResult, canvasKey: string): CanvasElements {
  return canvasKey === PROGRAM ? deriveOrchestration(result) : deriveContainer(result, canvasKey);
}
