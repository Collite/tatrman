// SPDX-License-Identifier: Apache-2.0
// The C1-d structured graph-op vocabulary (DS-P6 / D-6). The three insertion doors (palette,
// on-edge "+", ⌘K insert) all emit sequences of THESE ops through one builder — so the same
// insertion is byte-identical regardless of which door triggered it (the one-vocabulary
// guarantee). The ops are consumed by the edit-apply seam (`modeler/applyGraphEdit`); v1 gates
// application (the LSP stub returns ok:false) but emission is fully defined and tested here.

export interface PortRef {
  node: string;
  port: string;
}

export type EdgeRole = 'data' | 'control' | 'transfer';

export type GraphOp =
  | { op: 'insert-node'; nodeKind: string; id: string; label?: string }
  | { op: 'connect'; from: PortRef; to: PortRef; role: EdgeRole }
  | { op: 'disconnect'; edgeId: string }
  | { op: 'set-arg'; nodeId: string; key: string; value: string }
  // whole-node source-text replacement (the drawer's content edit, A-3 β). Distinct from set-arg
  // (a NAMED arg) — the apply seam replaces the node's source span. `nodeRef` is the node's qname
  // (the drawer's id space); the apply seam reconciles it to the graph node (see PL G-2).
  | { op: 'set-source'; nodeRef: string; text: string }
  // remove an object (a stale/missing ref OR a def) from the graph (FO-A1 W3, TP-5 T4.1.5).
  // Emission cleans the object's reference site; removal REFUSES (A1-EDIT-002) when the object
  // still has dependents — the refusal names them. See buildRemoveObjectWithConsequences.
  | { op: 'remove-object'; qname: string };

export interface EdgeInsertionTarget {
  edgeId: string;
  from: PortRef;
  to: PortRef;
  role: EdgeRole;
}

/** Deterministic synthesized id for a node inserted onto a given edge with a given kind. Pure —
 *  no counters/random — so every door computes the SAME id for the same insertion. */
export function insertionNodeId(target: EdgeInsertionTarget, nodeKind: string): string {
  return `${nodeKind}@${target.edgeId}`;
}

/**
 * Insert a new node onto an existing edge: split the edge and wire the new node between the
 * endpoints. Fixed op order (insert → disconnect → connect-before → connect-after) and a
 * deterministic node id make the sequence identical across the three doors. The split edge's
 * role is preserved on both new connections.
 */
export function buildInsertNodeOnEdge(target: EdgeInsertionTarget, nodeKind: string, label?: string): GraphOp[] {
  const id = insertionNodeId(target, nodeKind);
  return [
    { op: 'insert-node', nodeKind, id, ...(label ? { label } : {}) },
    { op: 'disconnect', edgeId: target.edgeId },
    { op: 'connect', from: target.from, to: { node: id, port: `${id}::in` }, role: target.role },
    { op: 'connect', from: { node: id, port: `${id}::out` }, to: target.to, role: target.role },
  ];
}

/** A set-arg op for the property panel (C1-d wall — node args/expressions are textual edits). */
export function buildSetArgOp(nodeId: string, key: string, value: string): GraphOp {
  return { op: 'set-arg', nodeId, key, value };
}

/** A set-source op for the drawer's content edit (whole-node source-text replacement, A-3 β). */
export function buildSetSourceOp(nodeRef: string, text: string): GraphOp {
  return { op: 'set-source', nodeRef, text };
}

/** A remove-object op (FO-A1 W3): drop an object (stale ref or def) from the graph. The
 *  consequence check + text emission is buildRemoveObjectWithConsequences (graph-edits). */
export function buildRemoveObjectOp(qname: string): GraphOp {
  return { op: 'remove-object', qname };
}
