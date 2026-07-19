// SPDX-License-Identifier: Apache-2.0
// WorkerLspDataSource — wraps the existing browser-worker LSP path as a
// ModelDataSource (contracts §6). capabilities.edit === true. The App keeps using
// the raw `LspClient` for graph-list / layout / edit operations via the exposed
// `lspClient` escape hatch (§6 is a READ contract; edit ops stay on LspClient,
// gated by capabilities.edit — recorded contracts delta, contracts.md §v1.3).
//
// This whole path is the offline fallback as of TP-5 T4 (`WsDesignerServerDataSource`
// now has real `ttrm/*` edit methods too) — decided 2026-07-15 (T5.2) to keep both,
// not retire this one. Works with no `ttr-designer-server` process running.
//
// The mappings here are thin — no new semantics. Where the worker world is
// graph-centric (no repo-wide index/version), the mapping is best-effort and
// documented inline; the worker App does not route its read paths through these
// (it calls LspClient directly), so this class exists for the abstraction + the
// escape hatch, unit-pinned to issue exactly the expected modeler/* requests.

import type { LspClient } from '../lsp-client.js';
import type { ModelGraph, RenderableSchemaCode, BindingMapData, SymbolDetail } from '@tatrman/lsp';
import type {
  ModelDataSource,
  DataSourceCapabilities,
  ModelIndex,
  ModelGraphPayload,
  ObjectDetail,
  SearchHit,
  SearchParams,
  GraphScope,
  Disposable,
} from './model-data-source.js';
import type { TtrmNode, TtrmEdge } from './ttrm-types.js';

export interface WorkerContext {
  projectRoot: string;
  /** The currently open graph document uri (set as graphs are opened). */
  graphUri?: string;
}

/** Map the renderable ModelGraph (rows/fk/relation) down to the ttrm dependency-graph DTO. */
export function modelGraphToTtrm(mg: ModelGraph): ModelGraphPayload {
  const nodes: TtrmNode[] = mg.nodes.map((n) => ({
    qname: n.qname,
    kind: n.kind,
    label: n.label,
    schema: n.schemaCode,
    pkg: '',
  }));
  const edges: TtrmEdge[] = mg.edges.map((e) => ({
    from: e.fromNode,
    to: e.toNode,
    type: e.kind === 'fk' ? 'REFERENCES' : 'MAPS_TO',
  }));
  return { nodes, edges };
}

export class WorkerLspDataSource implements ModelDataSource {
  // Full-featured: the Worker path carries the S1 lsp graft (md/cnc + getBindings). edit:true is
  // the local-file offline capability — further gated by license in the commercial build.
  readonly capabilities: DataSourceCapabilities = {
    edit: true,
    modelKinds: ['db', 'er', 'md', 'cnc'],
    bindings: true,
    perspectives: true,
    layoutPersist: 'in-file',
    graphShape: 'rich', // the Worker path carries the DS lsp graft → full CanvasGraph slot data (§1.1a)
  } as const;

  constructor(
    readonly lspClient: LspClient,
    private readonly ctx: WorkerContext,
  ) {}

  setGraphUri(uri: string): void {
    this.ctx.graphUri = uri;
  }

  async getModelIndex(): Promise<ModelIndex> {
    const [graphList, pkgGraph] = await Promise.all([
      this.lspClient.listGraphs(this.ctx.projectRoot),
      this.lspClient.getPackageGraph(),
    ]);
    const packages = pkgGraph.packages.map((p) => p.name);
    const schemas = [...new Set(graphList.graphs.map((g) => g.schema))];
    return {
      packages,
      schemas,
      areas: [],
      counts: { objects: 0, schemas: schemas.length, areas: 0 },
      modelVersion: '',
    };
  }

  async getModelGraph(scope?: GraphScope): Promise<ModelGraphPayload> {
    const uri = this.ctx.graphUri;
    if (!uri) throw new Error('WorkerLspDataSource.getModelGraph: no current graph uri');
    const mg = await this.lspClient.getModelGraph(uri, (scope?.schema ?? 'db') as RenderableSchemaCode);
    return modelGraphToTtrm(mg);
  }

  getBindings(): Promise<BindingMapData> {
    return this.lspClient.getBindings();
  }

  getSymbolDetail(qname: string): Promise<SymbolDetail | null> {
    return this.lspClient.getSymbolDetail(qname);
  }

  async getObject(qname: string): Promise<ObjectDetail> {
    const detail = await this.lspClient.getSymbolDetail(qname);
    return {
      object: {
        qname,
        kind: detail?.kind ?? '',
        label: detail?.label ?? detail?.name ?? qname,
        schema: '',
        pkg: '',
      },
      sourceLocation: detail ? { file: detail.sourceUri, line: detail.sourceLine, column: 0 } : '',
      references: [],
    };
  }

  async search(q: SearchParams): Promise<SearchHit[]> {
    // Same client-side filtering AddObjectPicker does today (pinned, not improved).
    const symbols = await this.lspClient.listSymbols(q.limit ? { limit: q.limit } : {});
    const needle = q.query.toLowerCase();
    return symbols
      .filter((s) => s.qname.toLowerCase().includes(needle) || s.name.toLowerCase().includes(needle))
      .map((s) => ({ qname: s.qname, score: 1, matchedField: 'name' }));
  }

  onModelChanged(_cb: (version: string) => void): Disposable {
    // The worker path has no file watching (documents are pushed via didOpen).
    return { dispose() {} };
  }
}
