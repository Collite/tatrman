// SPDX-License-Identifier: Apache-2.0
// ProcessingGraph → CanvasGraph mapper for the processing face (contracts §3/§5). canvas-core
// stays dependency-free: the input is the ProcessingGraphSource transport shape (fixture today,
// designer-server later); the output is the kernel model the stage/script skins render.
//
// Invariants held here:
//  • containers → kind 'container-ref' CanvasNode + a collapsed CanvasContainer entry (D-3 β region).
//  • store/display/op stay program-level leaves.
//  • declared ports survive untouched — incl. unconnected err/rejects stubs (D-2); their labels ride
//    slotData.ports for the skin's port strip (CanvasPort itself carries no label).
//  • node-level edges resolve to real port ids: data/transfer attach to flow (out-data → in-data)
//    ports; control edges synthesize cross-axis control ports when an endpoint declares none, so the
//    control edge is geometrically distinguishable from data in both orientations (D-4).

import type {
  CanvasGraph, CanvasNode, CanvasEdge, CanvasContainer, CanvasPort, EdgeRole, PortRole,
} from './types.js';

export interface ProcessingPort {
  id: string;
  direction: 'in' | 'out';
  role: PortRole;
  connected: boolean;
  label?: string;
}

export interface ProcessingNode {
  id: string;
  qname: string;
  /** 'container' collapses to a region; 'store'|'display'|'op' render as leaves */
  kind: 'container' | 'store' | 'display' | 'op' | string;
  label: string;
  bodyText?: string;
  engine?: string;
  dialect?: string;
  fragmentDerived?: boolean; // `"""sql · derived view marking
  collapsed?: boolean; // default true at orchestration level
  ports?: ProcessingPort[];
  slotData?: Record<string, unknown>;
}

export interface ProcessingEdge {
  id: string;
  from: string; // source node id
  to: string; // target node id
  role: EdgeRole; // 'data' | 'control' | 'transfer'
  label?: string;
  /** explicit endpoint ports. When given they are honored verbatim — REQUIRED to disambiguate a
   *  node with multiple same-role ports (two distinct in-data ports fed by two edges). When absent
   *  the mapper falls back to the first role-matching port (fine for single-port / fan-out cases). */
  fromPort?: string;
  toPort?: string;
}

export interface ProcessingGraph {
  id: string;
  face: 'processing';
  nodes: ProcessingNode[];
  edges: ProcessingEdge[];
  /** set on a fragment-derived drill-in (contracts §3) — the whole canvas is read-only + banner. */
  derived?: boolean;
}

const toCanvasPort = (p: ProcessingPort): CanvasPort => ({
  id: p.id,
  direction: p.direction,
  role: p.role,
  connected: p.connected,
});

/** find an existing port matching (direction, role); undefined if none declared. */
function findPort(ports: ProcessingPort[], direction: 'in' | 'out', role: PortRole): ProcessingPort | undefined {
  return ports.find((p) => p.direction === direction && p.role === role);
}

export function processingGraphToCanvas(pg: ProcessingGraph): CanvasGraph {
  // working port maps — declared ports plus any synthesized for edge attachment (D-4 control).
  const portsByNode = new Map<string, ProcessingPort[]>();
  for (const n of pg.nodes) portsByNode.set(n.id, [...(n.ports ?? [])]);

  const ensurePort = (nodeId: string, direction: 'in' | 'out', role: PortRole, explicit?: string): string => {
    const ports = portsByNode.get(nodeId) ?? [];
    // honor an explicitly-named port verbatim if it exists on the node (disambiguates same-role ports).
    if (explicit && ports.some((p) => p.id === explicit)) return explicit;
    const existing = findPort(ports, direction, role);
    if (existing) return existing.id;
    const suffix = role === 'control' ? (direction === 'out' ? 'ctrl-out' : 'ctrl-in') : (direction === 'out' ? 'flow-out' : 'flow-in');
    const id = `${nodeId}::${suffix}`;
    const synth: ProcessingPort = { id, direction, role, connected: true };
    ports.push(synth);
    portsByNode.set(nodeId, ports);
    return id;
  };

  // resolve every node-level edge to concrete port ids (synthesizing where needed).
  const edges: CanvasEdge[] = pg.edges.map((e) => {
    const controlEdge = e.role === 'control';
    const role: PortRole = controlEdge ? 'control' : 'data';
    const fromPort = ensurePort(e.from, 'out', role, e.fromPort);
    const toPort = ensurePort(e.to, 'in', role, e.toPort);
    return {
      id: e.id,
      from: { node: e.from, port: fromPort },
      to: { node: e.to, port: toPort },
      role: e.role,
      label: e.label,
    };
  });

  const containers: CanvasContainer[] = [];
  const nodes: CanvasNode[] = pg.nodes.map((n) => {
    const isContainer = n.kind === 'container';
    if (isContainer) {
      containers.push({
        id: n.id,
        label: n.label,
        engine: n.engine,
        dialect: n.dialect,
        fragmentDerived: n.fragmentDerived,
        collapsed: n.collapsed ?? true,
      });
    }
    const declared = n.ports ?? [];
    return {
      id: n.id,
      qname: n.qname,
      kind: isContainer ? 'container-ref' : n.kind,
      label: n.label,
      bodyText: n.bodyText,
      ports: (portsByNode.get(n.id) ?? []).map(toCanvasPort),
      slotData: {
        ...(n.slotData ?? {}),
        nodeKind: n.kind,
        engine: n.engine,
        dialect: n.dialect,
        fragmentDerived: n.fragmentDerived,
        // labelled ports for the skin's D-2 port strip (declared only — synthesized flow/ctrl
        // handles are edge-routing plumbing, not user-facing ports).
        ports: declared,
      },
    };
  });

  return {
    id: pg.id,
    face: 'processing',
    nodes,
    edges,
    containers,
    derived: pg.derived,
  };
}
