// SPDX-License-Identifier: Apache-2.0
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
    // FO-21 (FO-P0.S2.T4): back to `false` — this is the Studio Viewer's WS data
    // source. The mutation RPCs (addObjectToGraph/removeObjectFromGraph/createGraph)
    // moved to the platform authoring extension + `ttr-designer-edit-server`;
    // setLayout stays (view-persistence, read-half).
    expect(source.capabilities.edit).toBe(false);
    // DM-P1 capability descriptor: serves db/er/cnc (structural), no bindings/perspectives, .ttrl sidecar.
    expect(source.capabilities).toEqual({
      edit: false,
      modelKinds: ['db', 'er', 'cnc'],
      bindings: false,
      perspectives: false,
      layoutPersist: 'sidecar',
      graphShape: 'structural',
    });
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

  it('maps getLayout to the ttrm/getLayout result (T1, TP-5)', async () => {
    const { source, socket } = wired();
    const s = await establish(source, socket);

    const layoutP = source.getLayout!('file:///proj/graphs/all_er.ttrg');
    expect(s.lastSent().method).toBe('ttrm/getLayout');
    expect(s.lastSent().params).toMatchObject({ uri: 'file:///proj/graphs/all_er.ttrg' });
    s.receive({ ...fixture('get-layout.json'), id: s.lastSent().id });
    const layout = await layoutP;
    expect(layout.exists).toBe(true);
    expect(layout.canvases[0]).toMatchObject({ key: 'all_er', mode: 'manual' });
    expect(layout.canvases[0].nodes[0]).toMatchObject({ qname: 'acme.erp.db.customers', x: 320, y: 180 });
    expect(layout.orphaned).toEqual([]);
  });

  it('maps a sidecar-absent getLayout response to exists=false, no error', async () => {
    const { source, socket } = wired();
    const s = await establish(source, socket);

    const layoutP = source.getLayout!('file:///proj/graphs/no_sidecar.ttrg');
    s.receive({ ...fixture('get-layout-absent.json'), id: s.lastSent().id });
    const layout = await layoutP;
    expect(layout.exists).toBe(false);
    expect(layout.canvases).toEqual([]);
  });

  it('maps setLayout to the ttrm/setLayout ack (T4)', async () => {
    const { source, socket } = wired();
    const s = await establish(source, socket);

    const canvases = [{ key: 'all_er', skin: null, mode: 'manual' as const, nodes: [{ qname: 'a.b.c', x: 1, y: 2 }], collapsed: [] }];
    const setP = source.setLayout('file:///proj/graphs/all_er.ttrg', canvases);
    expect(s.lastSent().method).toBe('ttrm/setLayout');
    expect(s.lastSent().params).toMatchObject({ uri: 'file:///proj/graphs/all_er.ttrg', canvases });
    s.receive({ jsonrpc: '2.0', id: s.lastSent().id, result: { ok: true } });
    expect((await setP).ok).toBe(true);
  });

  it('maps listGraphs to the ttrm/listGraphs result (T4)', async () => {
    const { source, socket } = wired();
    const s = await establish(source, socket);

    const listP = source.listGraphs();
    expect(s.lastSent().method).toBe('ttrm/listGraphs');
    s.receive({ ...fixture('list-graphs.json'), id: s.lastSent().id });
    const graphs = await listP;
    expect(graphs).toHaveLength(1);
    expect(graphs[0]).toMatchObject({ name: 'all_er', schema: 'er', objectCount: 3 });
  });

  it('maps getGraph to the ttrm/getGraph result (T4)', async () => {
    const { source, socket } = wired();
    const s = await establish(source, socket);

    const graphP = source.getGraph('file:///proj/graphs/all_er.ttrg');
    expect(s.lastSent().method).toBe('ttrm/getGraph');
    expect(s.lastSent().params).toMatchObject({ uri: 'file:///proj/graphs/all_er.ttrg' });
    s.receive({ ...fixture('get-graph.json'), id: s.lastSent().id });
    const graph = await graphP;
    // getGraph now returns the mapped GetGraphResponse (DM-P2.S3, §1.1a structural) — node count
    // + missingObjects survive the map; rows are empty (structural-only).
    expect(graph).not.toBeNull();
    expect(graph!.nodes).toHaveLength(1);
    expect(graph!.nodes[0].rows).toEqual([]);
    expect(graph!.missingObjects).toEqual(['acme.erp.db.ghost']);
  });

  // FO-21 (FO-P0.S2.T4): the addObjectToGraph / removeObjectFromGraph / createGraph
  // mapping tests moved out with the methods — they re-enter as the platform
  // authoring extension's WS edit-client tests (against `ttr-designer-edit-server`).

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
