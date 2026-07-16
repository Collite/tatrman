// SPDX-License-Identifier: Apache-2.0
// The pluggable model data source (contracts §6, MD6).
//
// Two implementations back it:
//   - WorkerLspDataSource         — the existing browser-worker LSP path (edit: true)
//   - WsDesignerServerDataSource  — the ttrm/* WS JSON-RPC path to ttr-designer-server (edit: false)
//
// §6 covers the READ contract only. Graph-list / edit operations stay on
// `LspClient` and are reachable only when `capabilities.edit` is true (the gating
// mechanism — see App.tsx read-only gating, T3.2.6). This is a recorded contracts
// delta (contracts.md §v1.3 changelog), not a silent divergence.
//
// `getLayout` is the one exception (TP-5 T1, C1-f): it's a genuine read (viewing
// positions, not editing them) now that `ttr-designer-server` speaks it — so it
// lives on this interface, optional, rather than behind `capabilities.edit`.
// `WorkerLspDataSource` doesn't implement it (the worker path already has its own
// separate `LspClient.getLayout` against the in-file block, untouched until T5).

import type { TtrmIndex, TtrmGraph, TtrmObjectDetail, TtrmSearchHit, TtrmLayoutPayload } from './ttrm-types.js';

export type ModelIndex = TtrmIndex;
export type ModelGraphPayload = TtrmGraph;
export type ObjectDetail = TtrmObjectDetail;
export type SearchHit = TtrmSearchHit;
export type LayoutPayload = TtrmLayoutPayload;

/** Scope for `getModelGraph`. `schema` selects a schema view; `package` narrows to a package. */
export interface GraphScope {
  schema?: string;
  package?: string;
  edgeTypes?: string[];
}

export interface SearchParams {
  query: string;
  algorithm?: string;
  limit?: number;
}

export interface Disposable {
  dispose(): void;
}

export interface ModelDataSource {
  getModelIndex(): Promise<ModelIndex>;
  getModelGraph(scope?: GraphScope): Promise<ModelGraphPayload>;
  getObject(qname: string): Promise<ObjectDetail>;
  search(q: SearchParams): Promise<SearchHit[]>;
  onModelChanged(cb: (version: string) => void): Disposable;
  readonly capabilities: { edit: boolean };
  /** `.ttrl` sidecar read for a `.ttrg` document (TP-5 T1). Optional — see file header. */
  getLayout?(uri: string): Promise<LayoutPayload>;
}
