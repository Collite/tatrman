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
import type { RenderableSchemaCode, BindingMapData, SymbolDetail } from '@tatrman/lsp';

export type ModelIndex = TtrmIndex;
export type ModelGraphPayload = TtrmGraph;
export type ObjectDetail = TtrmObjectDetail;
export type SearchHit = TtrmSearchHit;
export type LayoutPayload = TtrmLayoutPayload;

/** Model kinds a backend may serve (mirrors `@tatrman/lsp` RenderableSchemaCode: db|er|md|cnc). */
export type ModelKind = RenderableSchemaCode;

/**
 * Per-backend capability descriptor (Designer Merge, DM-P1 / contracts §1). The shell renders
 * capabilities, never assumes them: a read a backend can't serve degrades visibly (`DM-CAP-*`,
 * see `capability-hints.ts`), never a dead control. Two axes matter — WHICH kinds
 * (`modelKinds`) and, for kinds it does serve, whether the backend returns the rich slot-data
 * graph or only a structural one (contracts §1.1a; that second axis lands with the DS shell's
 * graph consumer in DM-P2).
 */
export interface DataSourceCapabilities {
  /** add/remove-object, applyGraphEdit — further gated by license in the commercial build. */
  readonly edit: boolean;
  /** model kinds this backend can serve at all (`getModelGraph(scope.schema)`). */
  readonly modelKinds: readonly ModelKind[];
  /** `getBindings` available → binding/lineage perspectives can be generated. */
  readonly bindings: boolean;
  /** perspectives (binding/lineage) generable — implied by bindings + cross-face reach. */
  readonly perspectives: boolean;
  /** view-persistence (FO-31) mechanism, or `none` (auto-layout only). */
  readonly layoutPersist: 'in-file' | 'sidecar' | 'none';
  /**
   * The SHAPE of graph a served kind returns (contracts §1.1a — the second capability axis).
   * `'rich'` = the DS `CanvasGraph` slot data the skins render fully (rows/PK/FK, measures/`calc:`,
   * cnc properties); `'structural'` = a row-less dependency graph (the WS `ttrm-adapter`, Veles
   * browse) — the skin's structural marks render but slot bodies are absent (`DM-CAP-002`). Distinct
   * from `modelKinds`: a kind can be *served* (in `modelKinds`) yet only structurally (this axis).
   */
  readonly graphShape: 'rich' | 'structural';
}

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
  readonly capabilities: DataSourceCapabilities;
  /** `.ttrl` sidecar read for a `.ttrg` document (TP-5 T1). Optional — see file header. */
  getLayout?(uri: string): Promise<LayoutPayload>;
  /**
   * Project-wide er↔db bindings (DM-P1 / contracts §1) — feeds the binding + lineage
   * perspectives. Present only when `capabilities.bindings` is true (Worker/full backend);
   * absent on WS/Veles until their servers grow a bindings endpoint.
   */
  getBindings?(): Promise<BindingMapData>;
  /**
   * The inspector/TextDrawer read (DM-P2.S1 / contracts §1) — resolves a qname to its detail
   * (kind, label, source uri + line). Present on the Worker backend (full LSP); absent on WS/Veles
   * (deployed, no source access) → the shell reads `getObject` for structural detail there.
   */
  getSymbolDetail?(qname: string): Promise<SymbolDetail | null>;
}
