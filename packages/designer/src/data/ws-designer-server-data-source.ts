// SPDX-License-Identifier: Apache-2.0
// WS data source: speaks the `ttrm/*` protocol to ttr-designer-server (M3.1).
//
// `capabilities.edit` was `false` through T1–T3 (read-only: no edit affordance
// on the shared `ModelDataSource` interface — T3.2.6). As of T4 the class is
// genuinely edit-capable — `setLayout`/`addObjectToGraph`/`removeObjectFromGraph`/
// `createGraph`/`listGraphs`/`getGraph` below — but those live as extra public
// methods on THIS class, not on `ModelDataSource` (mirroring `getLayout`'s T1
// precedent: `WorkerLspDataSource` has its own separate `LspClient`-based edit
// path via its `lspClient` escape hatch, untouched by this plan). `capabilities.edit`
// is flipped to `true` to reflect that reality — `WsModeApp` no longer imports
// zero edit machinery. On connect it verifies the handshake `protocolVersion === 1`
// (contracts §4); a mismatch is a hard error surfaced to the user.

import type {
  ModelDataSource,
  ModelIndex,
  ModelGraphPayload,
  ObjectDetail,
  SearchHit,
  SearchParams,
  GraphScope,
  Disposable,
  LayoutPayload,
} from './model-data-source.js';
import type {
  TtrmStatus,
  TtrmSearchHit,
  TtrmLayoutCanvas,
  TtrmSetLayoutResult,
  TtrmGraphMetadata,
  TtrmGetGraphResponse,
  TtrmGraphMutationResult,
  TtrmCreateGraphParams,
  TtrmCreateGraphResult,
} from './ttrm-types.js';
import { JsonRpcWsClient, type JsonRpcWsClientOptions } from './json-rpc-ws-client.js';

export const TTRM_PROTOCOL_VERSION = 1;

export class ProtocolVersionMismatchError extends Error {
  constructor(
    readonly expected: number,
    readonly actual: number,
  ) {
    super(`ttr-designer-server protocolVersion ${actual} is not supported (expected ${expected})`);
    this.name = 'ProtocolVersionMismatchError';
  }
}

export class WsDesignerServerDataSource implements ModelDataSource {
  readonly capabilities = { edit: true } as const;
  private readonly client: JsonRpcWsClient;
  private status: TtrmStatus | null = null;

  /** @param origin WS origin, e.g. `ws://127.0.0.1:7270`; `/ttrm` is appended. */
  constructor(origin: string, opts: JsonRpcWsClientOptions = {}) {
    const url = origin.replace(/\/$/, '') + '/ttrm';
    this.client = new JsonRpcWsClient(url, opts);
  }

  /** Connect and verify the handshake. Rejects on protocolVersion mismatch. */
  async connect(): Promise<TtrmStatus> {
    await this.client.connect();
    const status = await this.client.request<TtrmStatus>('ttrm/getStatus');
    if (status.protocolVersion !== TTRM_PROTOCOL_VERSION) {
      this.client.close();
      throw new ProtocolVersionMismatchError(TTRM_PROTOCOL_VERSION, status.protocolVersion);
    }
    this.status = status;
    return status;
  }

  getStatus(): TtrmStatus | null {
    return this.status;
  }

  getModelIndex(): Promise<ModelIndex> {
    return this.client.request<ModelIndex>('ttrm/getModelIndex');
  }

  getModelGraph(scope?: GraphScope): Promise<ModelGraphPayload> {
    const params: Record<string, unknown> = {};
    const scopeObj: Record<string, unknown> = {};
    if (scope?.package) scopeObj.package = scope.package;
    if (scope?.schema) scopeObj.schema = scope.schema;
    if (Object.keys(scopeObj).length > 0) params.scope = scopeObj;
    if (scope?.edgeTypes?.length) params.edgeTypes = scope.edgeTypes;
    return this.client.request<ModelGraphPayload>('ttrm/getModelGraph', params);
  }

  getObject(qname: string): Promise<ObjectDetail> {
    return this.client.request<ObjectDetail>('ttrm/getObject', { qname });
  }

  async search(q: SearchParams): Promise<SearchHit[]> {
    const res = await this.client.request<{ hits: TtrmSearchHit[] }>('ttrm/search', {
      query: q.query,
      ...(q.algorithm ? { algorithm: q.algorithm } : {}),
      ...(q.limit ? { limit: q.limit } : {}),
    });
    return res.hits;
  }

  getLayout(uri: string): Promise<LayoutPayload> {
    return this.client.request<LayoutPayload>('ttrm/getLayout', { uri });
  }

  setLayout(uri: string, canvases: TtrmLayoutCanvas[]): Promise<TtrmSetLayoutResult> {
    return this.client.request<TtrmSetLayoutResult>('ttrm/setLayout', { uri, canvases });
  }

  async listGraphs(): Promise<TtrmGraphMetadata[]> {
    const res = await this.client.request<{ graphs: TtrmGraphMetadata[] }>('ttrm/listGraphs');
    return res.graphs;
  }

  getGraph(uri: string): Promise<TtrmGetGraphResponse> {
    return this.client.request<TtrmGetGraphResponse>('ttrm/getGraph', { uri });
  }

  addObjectToGraph(uri: string, qname: string, autoImport: boolean): Promise<TtrmGraphMutationResult> {
    return this.client.request<TtrmGraphMutationResult>('ttrm/addObjectToGraph', { uri, qname, autoImport });
  }

  removeObjectFromGraph(uri: string, qname: string, pruneUnusedImport: boolean): Promise<TtrmGraphMutationResult> {
    return this.client.request<TtrmGraphMutationResult>('ttrm/removeObjectFromGraph', { uri, qname, pruneUnusedImport });
  }

  createGraph(params: TtrmCreateGraphParams): Promise<TtrmCreateGraphResult> {
    return this.client.request<TtrmCreateGraphResult>('ttrm/createGraph', { ...params });
  }

  onModelChanged(cb: (version: string) => void): Disposable {
    const unsub = this.client.onNotification('ttrm/modelChanged', (params) => {
      const v = (params as { modelVersion?: string } | undefined)?.modelVersion;
      if (typeof v === 'string') cb(v);
    });
    return { dispose: unsub };
  }

  dispose(): void {
    this.client.close();
  }
}
