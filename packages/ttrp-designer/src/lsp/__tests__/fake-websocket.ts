import type { WsLike } from '../json-rpc-ws-client.js';

/** A controllable WebSocket stand-in (jsdom has none). Tests drive frames by hand. */
export class FakeWebSocket implements WsLike {
  onopen: ((ev: unknown) => void) | null = null;
  onclose: ((ev: unknown) => void) | null = null;
  onerror: ((ev: unknown) => void) | null = null;
  onmessage: ((ev: { data: unknown }) => void) | null = null;

  readonly sent: string[] = [];
  closed = false;

  constructor(readonly url: string) {
    // Fire open after the client has attached its handlers (same tick, next microtask).
    queueMicrotask(() => this.onopen?.({}));
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.closed = true;
    this.onclose?.({});
  }

  /** Deliver a raw frame (object → JSON) to the client. */
  receive(frame: unknown): void {
    this.onmessage?.({ data: typeof frame === 'string' ? frame : JSON.stringify(frame) });
  }

  /** The last outbound request as a parsed object. */
  lastSent(): { id: number; method: string; params: unknown } {
    return JSON.parse(this.sent[this.sent.length - 1]!);
  }
}
