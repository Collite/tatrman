// SPDX-License-Identifier: Apache-2.0
// WS data source: speaks the `ttrm/*` protocol to ttr-designer-server (M3.1).
//
// Read-only: capabilities.edit === false → the Designer hides every edit
// affordance (T3.2.6). On connect it verifies the handshake `protocolVersion === 1`
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
} from './model-data-source.js';
import type { TtrmStatus, TtrmSearchHit } from './ttrm-types.js';
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
  readonly capabilities = { edit: false } as const;
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
