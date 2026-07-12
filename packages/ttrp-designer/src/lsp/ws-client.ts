// SPDX-License-Identifier: Apache-2.0
import { JsonRpcWsClient, LspRpcError, type JsonRpcWsClientOptions } from './json-rpc-ws-client.js';
import type {
  CanvasLayout,
  GetGraphResult,
  GetLayoutResult,
  GetWorldResult,
} from '../graph/types.js';
import type { GraphEdit } from '../edits/graph-edits.js';

/** LSP `WorkspaceEdit` (opaque here — the host applies it, then we re-pull). */
export type WorkspaceEdit = Record<string, unknown>;

export interface RunResult {
  runId: string;
  exitCode: number;
  out: string[];
}

/** The LSP `ContentModified` code — a stale document version (contracts §4 / TTRP-EDIT-001). */
export const CONTENT_MODIFIED = -32801;

/** Published diagnostic (LSP `textDocument/publishDiagnostics` shape, trimmed). */
export interface PublishedDiagnostic {
  range: { start: { line: number; character: number }; end: { line: number; character: number } };
  severity?: number;
  code?: string;
  message: string;
}

/**
 * Typed TTR-P LSP client over the `/lsp` WebSocket (Stage 5.1 wire: one JSON-RPC
 * message per text frame — the transport-agnostic {@link JsonRpcWsClient} enforces it).
 * Mirrors the custom `ttrp/*` methods (contracts §4). `applyGraphEdit`/`run` land in 5.4.
 */
export class LspClient {
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

  /** β edits → WorkspaceEdit (Stage 5.4). Throws {@link LspRpcError} with CONTENT_MODIFIED on a stale version. */
  applyGraphEdit(edits: GraphEdit[], uri = this.uri, version = this.version): Promise<WorkspaceEdit> {
    return this.rpc.request<WorkspaceEdit>('ttrp/applyGraphEdit', { uri, version, edits });
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
