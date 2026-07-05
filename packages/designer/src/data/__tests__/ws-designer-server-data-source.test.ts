import { describe, it, expect, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { FakeWebSocket } from './fake-websocket.js';
import {
  WsDesignerServerDataSource,
  ProtocolVersionMismatchError,
} from '../ws-designer-server-data-source.js';
import { JsonRpcWsClient, TtrmRpcError } from '../json-rpc-ws-client.js';

const FIXTURES = join(dirname(fileURLToPath(import.meta.url)), 'fixtures', 'ttrm');
const fixture = (name: string) => JSON.parse(readFileSync(join(FIXTURES, name), 'utf8'));

/** Wire a data source to a fresh FakeWebSocket and expose the socket to the test. */
function wired(): { source: WsDesignerServerDataSource; socket: () => FakeWebSocket } {
  let sock: FakeWebSocket | null = null;
  const source = new WsDesignerServerDataSource('ws://127.0.0.1:7270', {
    wsFactory: (url) => {
      sock = new FakeWebSocket(url);
      return sock;
    },
  });
  return { source, socket: () => sock! };
}

/** Wait until at least `n` frames have been sent (the post-open getStatus is gated on a microtask). */
async function untilSent(s: FakeWebSocket, n: number): Promise<void> {
  for (let i = 0; i < 100 && s.sent.length < n; i++) await Promise.resolve();
}

/** Connect + answer the handshake; returns the live socket. */
async function establish(
  source: WsDesignerServerDataSource,
  socket: () => FakeWebSocket,
): Promise<FakeWebSocket> {
  const cp = source.connect();
  const s = socket();
  await untilSent(s, 1);
  s.receive({ ...fixture('get-status.json'), id: s.lastSent().id });
  await cp;
  return s;
}

describe('WsDesignerServerDataSource', () => {
  it('appends /ttrm to the origin and verifies protocolVersion on connect', async () => {
    const { source, socket } = wired();
    const connectP = source.connect();
    const s = socket();
    await untilSent(s, 1);
    expect(s.url).toBe('ws://127.0.0.1:7270/ttrm');
    expect(s.lastSent().method).toBe('ttrm/getStatus');
    s.receive({ ...fixture('get-status.json'), id: s.lastSent().id });
    const status = await connectP;
    expect(status.protocolVersion).toBe(1);
    expect(source.capabilities.edit).toBe(false);
  });

  it('rejects a protocolVersion mismatch', async () => {
    const { source, socket } = wired();
    const connectP = source.connect();
    const s = socket();
    await untilSent(s, 1);
    s.receive({ jsonrpc: '2.0', id: s.lastSent().id, result: { protocolVersion: 2, modelVersion: null, loadedAt: null, repoRoot: '/x', issues: [] } });
    await expect(connectP).rejects.toBeInstanceOf(ProtocolVersionMismatchError);
    expect(s.closed).toBe(true);
  });

  it('maps getModelIndex / getModelGraph / getObject / search to ttrm/* results', async () => {
    const { source, socket } = wired();
    const s = await establish(source, socket);

    const indexP = source.getModelIndex();
    expect(s.lastSent().method).toBe('ttrm/getModelIndex');
    s.receive({ ...fixture('get-model-index.json'), id: s.lastSent().id });
    const index = await indexP;
    expect(index.packages).toEqual(['acme.erp']);
    expect(index.schemas).toEqual(['db', 'er']);

    const graphP = source.getModelGraph();
    s.receive({ ...fixture('get-model-graph.json'), id: s.lastSent().id });
    const graph = await graphP;
    expect(graph.nodes).toHaveLength(2);
    expect(graph.edges[0]).toMatchObject({ from: 'acme.erp.db.orders', to: 'acme.erp.db.customers', type: 'REFERENCES' });

    const objP = source.getObject('acme.erp.db.customers');
    expect(s.lastSent().params).toMatchObject({ qname: 'acme.erp.db.customers' });
    s.receive({ ...fixture('get-object.json'), id: s.lastSent().id });
    expect((await objP).object.qname).toBe('acme.erp.db.customers');

    const hitsP = source.search({ query: 'cust' });
    expect(s.lastSent().method).toBe('ttrm/search');
    s.receive({ ...fixture('search.json'), id: s.lastSent().id });
    const hits = await hitsP;
    expect(hits[0]).toMatchObject({ qname: 'acme.erp.db.customers', matchedField: 'name' });
  });

  it('correlates responses arriving out of order', async () => {
    const { source, socket } = wired();
    const s = await establish(source, socket);

    const indexP = source.getModelIndex();
    const idIndex = s.lastSent().id;
    const objP = source.getObject('acme.erp.db.customers');
    const idObj = s.lastSent().id;
    expect(idObj).not.toBe(idIndex);

    // Respond to the SECOND request first.
    s.receive({ ...fixture('get-object.json'), id: idObj });
    s.receive({ ...fixture('get-model-index.json'), id: idIndex });

    expect((await objP).object.qname).toBe('acme.erp.db.customers');
    expect((await indexP).modelVersion).toBe('m-3f9a');
  });

  it('surfaces JSON-RPC errors as TtrmRpcError with data.kind', async () => {
    const { source, socket } = wired();
    const s = await establish(source, socket);

    const p = source.getObject('acme.erp.db.no_such');
    s.receive({ ...fixture('error-not-found.json'), id: s.lastSent().id });
    await expect(p).rejects.toBeInstanceOf(TtrmRpcError);
    await p.catch((e: TtrmRpcError) => {
      expect(e.code).toBe(-32001);
      expect((e.data as { kind: string }).kind).toBe('not-found');
    });
  });

  it('fires onModelChanged subscribers with the new version', async () => {
    const { source, socket } = wired();
    const s = await establish(source, socket);

    const cb = vi.fn();
    source.onModelChanged(cb);
    s.receive(fixture('model-changed.json'));
    expect(cb).toHaveBeenCalledWith('m-4b01');
  });

  it('rejects a pending request with a timeout', async () => {
    vi.useFakeTimers();
    try {
      const client = new JsonRpcWsClient('ws://127.0.0.1:7270/ttrm', {
        wsFactory: (url) => new FakeWebSocket(url),
        requestTimeoutMs: 1000,
      });
      await client.connect();
      const p = client.request('ttrm/getStatus');
      const assertion = expect(p).rejects.toThrow(/timed out/);
      await vi.advanceTimersByTimeAsync(1001);
      await assertion;
    } finally {
      vi.useRealTimers();
    }
  });
});
