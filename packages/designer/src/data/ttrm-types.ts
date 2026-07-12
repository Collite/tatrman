// SPDX-License-Identifier: Apache-2.0
// TypeScript types for the `ttrm/*` WS JSON-RPC wire DTOs.
//
// These mirror the Kotlin `ttr-designer-server` handlers (M3.1) as pinned in
// `docs/ttr-metadata/architecture/contracts.md` §4 (+ the §v1.3 changelog).
// The canned fixtures under `__tests__/fixtures/ttrm/` are the contract record:
// if the server ever disagrees, reconcile via contracts.md — never ad hoc.

/** `ttrm/getStatus` — the handshake. Answers even before a model is loaded. */
export interface TtrmStatus {
  protocolVersion: number;
  modelVersion: string | null;
  loadedAt: string | null;
  repoRoot: string;
  issues: Array<{ message: string; file: string | null }>;
}

/** `ttrm/getModelIndex`. */
export interface TtrmIndex {
  packages: string[];
  schemas: string[];
  areas: string[];
  counts: { objects: number; schemas: number; areas: number };
  modelVersion: string;
}

export type TtrmEdgeType = 'DEFINES' | 'REFERENCES' | 'MAPS_TO' | 'USES';

export interface TtrmNode {
  qname: string;
  kind: string;
  label: string;
  schema: string;
  pkg: string;
}

export interface TtrmEdge {
  from: string;
  to: string;
  type: TtrmEdgeType;
}

/** `ttrm/getModelGraph` — the *dependency-graph* payload (not the canvas ModelGraph). */
export interface TtrmGraph {
  nodes: TtrmNode[];
  edges: TtrmEdge[];
}

/** `ttrm/getObject`. */
export interface TtrmObjectDetail {
  object: TtrmNode;
  sourceLocation: string | { file: string; line: number; column: number };
  references: string[];
}

/** `ttrm/search` hit element. */
export interface TtrmSearchHit {
  qname: string;
  score: number;
  matchedField: string;
}

/** Resolved world (`ttrm/getWorld` with a qname). */
export interface TtrmWorld {
  qname: string;
  fingerprint: string;
  engines: string[];
  executors: string[];
  storages: string[];
  staging: string | null;
}
