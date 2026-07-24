// SPDX-License-Identifier: Apache-2.0
// PL-P1.S9.T2 — render the STATIC manifest program graph (from `manifestToProgramGraph`, S9.T1) on the
// shared ProcessingCanvas. Maps the read-only `ProgramGraph` onto canvas-core's `ProcessingGraph`:
// islands → `op` leaves labeled engine/executor, transfers → `transfer` edges, `onFailureOf` → a
// `control` edge. Ports are synthesized (one in/out data + a control pair) so edges have anchors — the
// canvas's port fallback picks the role-matching port. This view is READ-ONLY (no edit doors).

import type { ProcessingGraph, ProcessingNode, ProcessingEdge, ProcessingPort } from '@tatrman/canvas-core';
import type { ProgramGraph, ProgramGraphNode, ProgramGraphEdge } from './manifest-program-graph.js';

function portsFor(nodeId: string): ProcessingPort[] {
  return [
    { id: `${nodeId}.in`, direction: 'in', role: 'data', connected: true, label: 'in' },
    { id: `${nodeId}.out`, direction: 'out', role: 'data', connected: true, label: 'out' },
    { id: `${nodeId}.in_err`, direction: 'in', role: 'control', connected: false, label: 'on failure' },
    { id: `${nodeId}.out_err`, direction: 'out', role: 'control', connected: false, label: 'on failure' },
  ];
}

function toNode(n: ProgramGraphNode): ProcessingNode {
  return {
    id: n.id,
    qname: n.id,
    kind: 'op',
    label: n.label,
    engine: `${n.engine}/${n.executor}`,
    collapsed: true,
    ports: portsFor(n.id),
  };
}

function toEdge(e: ProgramGraphEdge): ProcessingEdge {
  if (e.kind === 'error') {
    return { id: e.id, from: e.from, to: e.to, role: 'control', label: 'on failure', fromPort: `${e.from}.out_err`, toPort: `${e.to}.in_err` };
  }
  return { id: e.id, from: e.from, to: e.to, role: 'transfer', label: e.via, fromPort: `${e.from}.out`, toPort: `${e.to}.in` };
}

/** Map a static [ProgramGraph] to the canvas-core [ProcessingGraph] the (read-only) ProcessingCanvas renders. */
export function programGraphToProcessing(graph: ProgramGraph, id = 'program'): ProcessingGraph {
  return {
    id,
    face: 'processing',
    nodes: graph.nodes.map(toNode),
    edges: graph.edges.map(toEdge),
    derived: true, // read-only view — the canvas shows the derived/read-only banner
  };
}
