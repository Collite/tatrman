// SPDX-License-Identifier: Apache-2.0
// DM-P4.S4: the LIVE TTR-P client over the `/lsp` WebSocket (:9257), lifted from the standalone
// @tatrman/ttrp-designer (which retires in S5). **FO-21 (Finding B):** this OPEN lift carries ONLY the
// read + run methods — `applyGraphEdit` (edit) and its `ttrp/applyGraphEdit` marker + the `GraphEdit`
// β-vocab are LEFT BEHIND (the bundle-check forbids `applyGraphEdit`; processing edit routes through
// the DS GraphOp doors in @tatrman/designer-authoring instead). Renamed `LspClient` → `TtrpLspClient`
// to avoid clashing with the modeling worker LSP client.
import { JsonRpcWsClient, LspRpcError, type JsonRpcWsClientOptions } from './json-rpc-ws-client.js';
import type { CanvasLayout, GetGraphResult, GetLayoutResult, GetWorldResult } from './types.js';

export interface RunResult {
  runId: string;
  exitCode: number;
  out: string[];
}

/** The LSP `ContentModified` code — a stale document version. */
export const CONTENT_MODIFIED = -32801;

/** Published diagnostic (LSP `textDocument/publishDiagnostics` shape, trimmed). */
export interface PublishedDiagnostic {
  range: { start: { line: number; character: number }; end: { line: number; character: number } };
  severity?: number;
  code?: string;
  message: string;
}

/**
 * Typed TTR-P read+run client over the `/lsp` WebSocket. Mirrors the custom `ttrp/*` READ methods
 * (getGraph/getWorld/getLayout/setLayout) + `ttrp/run`. `setLayout` is view-state (FO-31 read-half);
 * `run` mutates no model doc (read-half). NO `applyGraphEdit` (edit — FO-21).
 */
export class TtrpLspClient {
  private readonly rpc: JsonRpcWsClient;
  private version = 0;
  private uri = '';

  constructor(
    url = import.meta.env?.VITE_TTRP_LSP_URL ?? 'ws://127.0.0.1:9257/lsp',
    opts: JsonRpcWsClientOptions = {},
  ) {
    this.rpc = new JsonRpcWsClient(url, opts);
  }

  connect(): Promise<void> {
    return this.rpc.connect();
  }

  close(): void {
    this.rpc.close();
  }

  async initialize(): Promise<void> {
    await this.rpc.request('initialize', { processId: null, capabilities: {} });
    this.rpc.notify('initialized', {});
  }

  /** Open a document (didOpen). Tracks the version so getGraph/run stay in sync. */
  openDocument(uri: string, text: string, languageId = 'ttrp'): void {
    this.uri = uri;
    this.version = 1;
    this.rpc.notify('textDocument/didOpen', {
      textDocument: { uri, languageId, version: this.version, text },
    });
  }

  currentVersion(): number {
    return this.version;
  }

  getGraph(uri = this.uri, version = this.version): Promise<GetGraphResult> {
    return this.rpc.request<GetGraphResult>('ttrp/getGraph', { uri, version });
  }

  getWorld(uri = this.uri): Promise<GetWorldResult> {
    return this.rpc.request<GetWorldResult>('ttrp/getWorld', { uri });
  }

  getLayout(uri = this.uri): Promise<GetLayoutResult> {
    return this.rpc.request<GetLayoutResult>('ttrp/getLayout', { uri });
  }

  setLayout(canvases: CanvasLayout[], uri = this.uri, layoutVersion = 1): Promise<{ ok: boolean }> {
    return this.rpc.request<{ ok: boolean }>('ttrp/setLayout', {
      uri,
      layout: { version: layoutVersion, canvases },
    });
  }

  /** True when an error is a stale-version rejection (client should re-pull + replay). */
  static isStale(err: unknown): boolean {
    return err instanceof LspRpcError && err.code === CONTENT_MODIFIED;
  }

  run(uri = this.uri, version = this.version): Promise<RunResult> {
    return this.rpc.request<RunResult>('ttrp/run', { uri, version });
  }

  onDiagnostics(handler: (uri: string, diagnostics: PublishedDiagnostic[]) => void): () => void {
    return this.rpc.onNotification('textDocument/publishDiagnostics', (params) => {
      const p = params as { uri: string; diagnostics: PublishedDiagnostic[] };
      handler(p.uri, p.diagnostics ?? []);
    });
  }
}
