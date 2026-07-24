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

/** JSON-RPC "method not found" — the server lacks the requested method (A1-CAP-* degrade). */
export const METHOD_NOT_FOUND = -32601;

/** A validate diagnostic in the contracts §2 shape (range: 0-based, half-open). [MIRROR: lsp-client types] */
export interface TtrpValidateDiagnostic {
  severity: 'error' | 'warning' | 'info';
  code: string;
  message: string;
  range: { start: { line: number; col: number }; end: { line: number; col: number } };
  step?: string;
  suggestedAlternative?: string | null;
}

/**
 * `ttrp/validate` result (contracts §2). `supported:false` is the A1-CAP-002 degrade — the connected
 * server lacks the method; the caller shows the degraded chip and offers "run unvalidated" (arch §3).
 */
export type TtrpValidateResult =
  | { supported: true; ok: boolean; diagnostics: TtrpValidateDiagnostic[] }
  | { supported: false };

/** The raw `ttrp/validate` wire shape (P0 probe): flat line/col, `source` param, no `ok`/`range`/`step`. */
interface RawValidateDiagnostic {
  code: string;
  severity: string;
  message: string;
  suggestedAlternative?: string | null;
  line: number;
  column: number;
  endLine: number;
  endColumn: number;
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

  /**
   * Validate DRAFT text (contracts §2; Server-open, read-only — parse + frontend checks, NO run).
   * Maps the server's flat wire shape → the §2 range/ok shape. A server lacking the method
   * (JSON-RPC method-not-found) resolves to `{ supported: false }` — the A1-CAP-002 degrade,
   * never a throw (arch §3). `uri` gives cross-file context.
   */
  async validate(text: string, uri = this.uri): Promise<TtrpValidateResult> {
    try {
      const res = await this.rpc.request<{ diagnostics: RawValidateDiagnostic[] }>('ttrp/validate', { source: text, uri });
      const diagnostics: TtrpValidateDiagnostic[] = (res.diagnostics ?? []).map((d) => ({
        severity: (d.severity === 'error' || d.severity === 'warning' || d.severity === 'info' ? d.severity : 'error'),
        code: d.code,
        message: d.message,
        range: { start: { line: d.line, col: d.column }, end: { line: d.endLine, col: d.endColumn } },
        ...(d.suggestedAlternative != null ? { suggestedAlternative: d.suggestedAlternative } : {}),
      }));
      const ok = diagnostics.every((d) => d.severity !== 'error');
      return { supported: true, ok, diagnostics };
    } catch (err) {
      // Duck-type on the JSON-RPC code (an LspRpcError carries `.code`) — robust to realm identity.
      if ((err as { code?: number } | null)?.code === METHOD_NOT_FOUND) return { supported: false };
      throw err;
    }
  }

  onDiagnostics(handler: (uri: string, diagnostics: PublishedDiagnostic[]) => void): () => void {
    return this.rpc.onNotification('textDocument/publishDiagnostics', (params) => {
      const p = params as { uri: string; diagnostics: PublishedDiagnostic[] };
      handler(p.uri, p.diagnostics ?? []);
    });
  }
}
