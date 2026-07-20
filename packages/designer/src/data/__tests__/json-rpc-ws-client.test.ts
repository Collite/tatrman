// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { JsonRpcWsClient, type WsLike } from '../json-rpc-ws-client.js';

/** A WsLike the test drives by hand — unlike FakeWebSocket it does NOT auto-open. */
class ManualWs implements WsLike {
  onopen: ((ev: unknown) => void) | null = null;
  onclose: ((ev: unknown) => void) | null = null;
  onerror: ((ev: unknown) => void) | null = null;
  onmessage: ((ev: { data: unknown }) => void) | null = null;
  readonly sent: string[] = [];
  send(data: string): void {
    this.sent.push(data);
  }
  close(): void {
    this.onclose?.({});
  }
  open(): void {
    this.onopen?.({});
  }
}

describe('JsonRpcWsClient connect() settling (R3)', () => {
  it('rejects connect() when the socket closes WITHOUT ever opening (clean-close handshake failure)', async () => {
    const ws = new ManualWs();
    const client = new JsonRpcWsClient('wss://veles.example/v1/ttrm', { wsFactory: () => ws });
    const p = client.connect();
    // No error event, just a close — connect() must reject, not hang forever.
    ws.close();
    await expect(p).rejects.toThrow(/closed before opening/);
  });

  it('a stale socket close does not reject requests belonging to a newer socket', async () => {
    const sockets: ManualWs[] = [];
    const client = new JsonRpcWsClient('wss://veles.example/v1/ttrm', {
      wsFactory: () => {
        const w = new ManualWs();
        sockets.push(w);
        return w;
      },
    });

    const c1 = client.connect();
    sockets[0]!.open();
    await c1;

    // Reconnect: a second socket becomes the active one.
    const c2 = client.connect();
    sockets[1]!.open();
    await c2;

    // A request on the NEW (active) socket…
    const req = client.request('ttrm/getGraph', { uri: 'x' });
    // …must survive the STALE first socket's late close.
    sockets[0]!.close();

    // Answer on the active socket → the request resolves (was not nuked by the stale close).
    const sent = JSON.parse(sockets[1]!.sent[sockets[1]!.sent.length - 1]!) as { id: number };
    sockets[1]!.onmessage?.({ data: JSON.stringify({ jsonrpc: '2.0', id: sent.id, result: { ok: true } }) });
    await expect(req).resolves.toEqual({ ok: true });
  });
});
