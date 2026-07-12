// SPDX-License-Identifier: Apache-2.0
// The closed β edit vocabulary (C1-d-i) as a discriminated union — the type IS the
// contract. Everything else is a text edit (C1-d-iv: δ node/edge surface form stays
// internal; the canvas only ever emits these γ-hybrid ops).

export interface PortRef {
  /** `container.port`, or `container` for its default port. */
  ref: string;
}

export type GraphEdit =
  | { op: 'addNode'; canvas: string; kind: string; name?: string; afterZeta?: string }
  | { op: 'removeNode'; zeta: string }
  | { op: 'connect'; from: string; to: string }
  | { op: 'disconnect'; from: string; to: string }
  | { op: 'addControlEdge'; controlKind: 'after' | 'with'; a: string; b: string }
  | { op: 'createContainer'; name: string; target: string; dialect?: string }
  | { op: 'deleteContainer'; path: string }
  | { op: 'assignTarget'; path: string; target: string }
  | { op: 'bindContainerPorts'; path: string; ports: { in: string[]; out: string[]; err?: string[] } }
  | { op: 'renameVariable'; zeta: string; newName: string }
  | { op: 'setProperty'; zeta: string; property: string; valueText: string };

export const edit = {
  addNode: (canvas: string, kind: string, name?: string, afterZeta?: string): GraphEdit => ({ op: 'addNode', canvas, kind, name, afterZeta }),
  removeNode: (zeta: string): GraphEdit => ({ op: 'removeNode', zeta }),
  connect: (from: string, to: string): GraphEdit => ({ op: 'connect', from, to }),
  disconnect: (from: string, to: string): GraphEdit => ({ op: 'disconnect', from, to }),
  addControlEdge: (controlKind: 'after' | 'with', a: string, b: string): GraphEdit => ({ op: 'addControlEdge', controlKind, a, b }),
  createContainer: (name: string, target: string, dialect?: string): GraphEdit => ({ op: 'createContainer', name, target, dialect }),
  deleteContainer: (path: string): GraphEdit => ({ op: 'deleteContainer', path }),
  assignTarget: (path: string, target: string): GraphEdit => ({ op: 'assignTarget', path, target }),
  bindContainerPorts: (path: string, ports: { in: string[]; out: string[]; err?: string[] }): GraphEdit => ({ op: 'bindContainerPorts', path, ports }),
  renameVariable: (zeta: string, newName: string): GraphEdit => ({ op: 'renameVariable', zeta, newName }),
  setProperty: (zeta: string, property: string, valueText: string): GraphEdit => ({ op: 'setProperty', zeta, property, valueText }),
};
