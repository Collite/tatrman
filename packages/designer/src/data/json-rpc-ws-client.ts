// SPDX-License-Identifier: Apache-2.0
// Browser WebSocket JSON-RPC 2.0 client for the `ttrm/*` protocol.
//
// - monotonically increasing numeric request id + pending-request map (id correlation,
//   robust to out-of-order responses);
// - per-request timeout (default 10 s) → rejected promise;
// - notifications (method present, no id) → handler registry;
// - error objects {code,message,data} → typed TtrmRpcError.
//
// One message per text frame; NO batch (matches ttr-designer-server, M3.1). No
// reconnect heuristics in v1 (P2): a `close` surfaces via the onClose callback.

export class TtrmRpcError extends Error {
  constructor(
    readonly code: number,
    message: string,
    readonly data?: unknown,
  ) {
    super(message);
    this.name = 'TtrmRpcError';
  }
}

interface JsonRpcResponse {
  jsonrpc: '2.0';
  id?: number | string | null;
  method?: string;
  params?: unknown;
  result?: unknown;
  error?: { code: number; message: string; data?: unknown };
}

/** Minimal WebSocket surface used here — lets tests inject a FakeWebSocket. */
export interface WsLike {
  send(data: string): void;
  close(): void;
  onopen: ((ev: unknown) => void) | null;
  onclose: ((ev: unknown) => void) | null;
  onerror: ((ev: unknown) => void) | null;
  onmessage: ((ev: { data: unknown }) => void) | null;
}

/** Handshake info a factory may honor (e.g. a Node `ws` factory sets headers). */
export interface WsConnectInfo {
  headers?: Record<string, string>;
}

export type WsFactory = (url: string, connect?: WsConnectInfo) => WsLike;

export interface JsonRpcWsClientOptions {
  /** Injectable for tests; defaults to the browser `WebSocket`. */
  wsFactory?: WsFactory;
  /** Per-request timeout in ms (default 10_000). */
  requestTimeoutMs?: number;
  onClose?: () => void;
  /**
   * Bearer token sent as `Authorization: Bearer <token>` on the handshake (VelesTtrmDataSource, VS-2).
   * NOTE: a browser `WebSocket` cannot set request headers, so the default browser factory cannot
   * apply this — a Node/dev factory (or the test seam) does. The browser auth story is the deferred
   * IdP flow. // PL-P1: IdP flow post-v1 ⚑
   */
  bearerToken?: string;
}

interface Pending {
  resolve: (v: unknown) => void;
  reject: (e: unknown) => void;
  timer: ReturnType<typeof setTimeout>;
}

export class JsonRpcWsClient {
  private ws: WsLike | null = null;
  private nextId = 1;
  private readonly pending = new Map<number, Pending>();
  private readonly notificationHandlers = new Map<string, Array<(params: unknown) => void>>();
  private readonly timeoutMs: number;
  private readonly wsFactory: WsFactory;
  private readonly onClose?: () => void;
  private readonly bearerToken?: string;

  constructor(
    private readonly url: string,
    opts: JsonRpcWsClientOptions = {},
  ) {
    this.timeoutMs = opts.requestTimeoutMs ?? 10_000;
    this.onClose = opts.onClose;
    this.bearerToken = opts.bearerToken;
    this.wsFactory =
      opts.wsFactory ??
      // Browser default: cannot set the Authorization header on a WS handshake. // PL-P1: IdP flow post-v1 ⚑
      ((u: string) => new WebSocket(u) as unknown as WsLike);
  }

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const connect: WsConnectInfo | undefined = this.bearerToken
        ? { headers: { Authorization: `Bearer ${this.bearerToken}` } }
        : undefined;
      const ws = this.wsFactory(this.url, connect);
      this.ws = ws;
      let settled = false;
      ws.onopen = () => {
        settled = true;
        resolve();
      };
      ws.onerror = (ev) => {
        if (settled) return;
        settled = true;
        reject(new Error(`WebSocket error connecting to ${this.url}: ${String(ev)}`));
      };
      ws.onclose = () => {
        // Only the CURRENTLY-active socket's close should drop `this.ws` and fail pending requests — a
        // stale socket's late close (after a reconnect) must not reject requests that belong to the new
        // socket, nor null out a newer `this.ws`.
        const wasActive = this.ws === ws;
        if (wasActive) this.ws = null;
        // A socket that closes WITHOUT ever firing `error`/`open` (a clean-close handshake failure) would
        // otherwise leave connect() pending forever — reject it here.
        if (!settled) {
          settled = true;
          reject(new Error(`WebSocket to ${this.url} closed before opening`));
        }
        if (wasActive) this.failAllPending(new Error('WebSocket closed'));
        this.onClose?.();
      };
      ws.onmessage = (ev) => this.handleFrame(ev.data);
    });
  }

  private handleFrame(raw: unknown): void {
    let msg: JsonRpcResponse;
    try {
      msg = JSON.parse(String(raw)) as JsonRpcResponse;
    } catch {
      return; // ignore unparseable frames
    }
    // Notification: method present, no id.
    if (msg.method && (msg.id === undefined || msg.id === null)) {
      const handlers = this.notificationHandlers.get(msg.method) ?? [];
      for (const h of handlers) h(msg.params);
      return;
    }
    if (typeof msg.id !== 'number') return;
    const pending = this.pending.get(msg.id);
    if (!pending) return;
    this.pending.delete(msg.id);
    clearTimeout(pending.timer);
    if (msg.error) {
      pending.reject(new TtrmRpcError(msg.error.code, msg.error.message, msg.error.data));
    } else {
      pending.resolve(msg.result);
    }
  }

  request<T>(method: string, params?: Record<string, unknown>): Promise<T> {
    const ws = this.ws;
    if (!ws) return Promise.reject(new Error('client not connected'));
    const id = this.nextId++;
    const frame = JSON.stringify({ jsonrpc: '2.0', id, method, params: params ?? {} });
    return new Promise<T>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`request ${method} (id ${id}) timed out after ${this.timeoutMs}ms`));
      }, this.timeoutMs);
      this.pending.set(id, { resolve: resolve as (v: unknown) => void, reject, timer });
      try {
        ws.send(frame);
      } catch (err) {
        // Socket died between the `!ws` guard and send() — clean up this entry+timer
        // (failAllPending already handled any others) and reject now.
        this.pending.delete(id);
        clearTimeout(timer);
        reject(err instanceof Error ? err : new Error(String(err)));
      }
    });
  }

  onNotification(method: string, handler: (params: unknown) => void): () => void {
    const list = this.notificationHandlers.get(method) ?? [];
    list.push(handler);
    this.notificationHandlers.set(method, list);
    return () => {
      const cur = this.notificationHandlers.get(method);
      if (!cur) return;
      this.notificationHandlers.set(
        method,
        cur.filter((h) => h !== handler),
      );
    };
  }

  private failAllPending(err: Error): void {
    for (const [, p] of this.pending) {
      clearTimeout(p.timer);
      p.reject(err);
    }
    this.pending.clear();
  }

  close(): void {
    this.ws?.close();
    this.ws = null;
  }
}
