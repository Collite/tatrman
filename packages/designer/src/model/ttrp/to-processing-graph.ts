// SPDX-License-Identifier: Apache-2.0
// DM-P4.S4: the shape adapter mapping the live `ttrp/getGraph` wire (GraphView/ContainerView/NodeView/
// EdgeView) onto the DS `ProcessingGraph` the merged ProcessingCanvas renders (via canvas-core's
// processingGraphToCanvas). Program level = collapsed container regions + program leaves; a container
// drill = that container's own nodes+edges. Node-level edges carry their declared ports. Orchestration
// `transfers` (island-level, port-less) are NOT mapped in v1 — a documented fidelity gap; the wire's
// `graph.edges` carry the container/leaf connectivity, and the fixtures keep the fully-connected hero.
import type { ProcessingGraph, ProcessingNode, ProcessingEdge, ProcessingPort } from '@tatrman/canvas-core';
import type { GetGraphResult, NodeView, EdgeView, ContainerView, PortView } from './types.js';

const PROGRAM = 'program';

function portFrom(nodeId: string, p: PortView): ProcessingPort {
  const role: ProcessingPort['role'] = p.kind === 'control' ? 'control' : p.name === 'rejects' ? 'rejects' : 'data';
  return { id: `${nodeId}.${p.name}`, direction: p.direction, role, connected: true, label: p.name };
}

function leafKind(kind: string): ProcessingNode['kind'] {
  const k = kind.toLowerCase();
  if (k.includes('store')) return 'store';
  if (k.includes('display')) return 'display';
  return 'op';
}

function nodeFrom(n: NodeView): ProcessingNode {
  return {
    id: n.zeta,
    qname: n.zeta,
    kind: leafKind(n.kind),
    label: n.label,
    ports: n.ports.map((p) => portFrom(n.zeta, p)),
  };
}

function containerNode(c: ContainerView): ProcessingNode {
  const ports: ProcessingPort[] = [];
  for (const [dir, names] of Object.entries(c.ports)) {
    for (const name of names) {
      const direction: 'in' | 'out' = dir === 'in' ? 'in' : 'out';
      const role: ProcessingPort['role'] = dir === 'err' ? 'rejects' : 'data';
      ports.push({ id: `${c.path}.${name}`, direction, role, connected: true, label: name });
    }
  }
  return {
    id: c.path,
    qname: c.path,
    kind: 'container',
    label: c.path,
    engine: c.target,
    fragmentDerived: !!c.fragment,
    collapsed: true,
    ports,
  };
}

function edgeRole(type: EdgeView['type']): ProcessingEdge['role'] {
  return type === 'data' ? 'data' : 'control';
}

function edgeFrom(e: EdgeView): ProcessingEdge {
  return {
    id: `${e.from}.${e.fromPort}->${e.to}.${e.toPort}`,
    from: e.from,
    to: e.to,
    role: edgeRole(e.type),
    fromPort: `${e.from}.${e.fromPort}`,
    toPort: `${e.to}.${e.toPort}`,
    ...(e.via ? { label: e.via } : {}),
  };
}

/** Map a `ttrp/getGraph` result to a DS ProcessingGraph for the given canvas (`'program'` or a
 *  container path). Unknown container ⇒ an empty graph (no throw), matching the fixture source. */
export function ttrpToProcessingGraph(result: GetGraphResult, canvasKey: string): ProcessingGraph {
  const g = result.graph;
  const atProgram = canvasKey === PROGRAM || canvasKey === g.program || canvasKey === '';
  if (atProgram) {
    return {
      id: g.program,
      face: 'processing',
      nodes: [...g.containers.map(containerNode), ...g.leaves.map(nodeFrom)],
      edges: g.edges.map(edgeFrom),
    };
  }
  const container = g.containers.find((c) => c.path === canvasKey || c.path.split('/').pop() === canvasKey);
  if (!container) return { id: canvasKey, face: 'processing', nodes: [], edges: [] };
  return {
    id: container.path,
    face: 'processing',
    derived: container.derived || !!container.fragment,
    nodes: container.nodes.map(nodeFrom),
    edges: container.edges.map(edgeFrom),
  };
}
