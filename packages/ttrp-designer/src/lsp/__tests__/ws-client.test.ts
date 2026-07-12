// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { LspClient } from '../ws-client.js';
import { FakeWebSocket } from './fake-websocket.js';

/**
 * The typed TTR-P LSP WS client (T5.3.3): request/response id correlation, one JSON-RPC
 * message per text frame (no Content-Length on the wire), and didOpen sent as a
 * notification (no id).
 */
describe('LspClient over WS', () => {
  function connected(): { client: LspClient; ws: FakeWebSocket } {
    let ws!: FakeWebSocket;
    const client = new LspClient('ws://127.0.0.1:9257/lsp', {
      wsFactory: (u) => {
        ws = new FakeWebSocket(u);
        return ws;
      },
    });
    return { client, ws: ws! };
  }

  it('correlates a getGraph request to its response', async () => {
    const { client } = connected();
    await client.connect();
    // Grab the ws after connect (factory ran during connect).
    const ws = (client as unknown as { rpc: { ws: FakeWebSocket } }).rpc.ws;
    client.openDocument('file:///hero.ttrp', 'uses world "x"');

    const p = client.getGraph();
    const req = JSON.parse(ws.sent[ws.sent.length - 1]!);
    expect(req.method).toBe('ttrp/getGraph');
    expect(req.id).toBeTypeOf('number');
    ws.receive({ jsonrpc: '2.0', id: req.id, result: { graph: { program: 'hero.ttrp', containers: [], leaves: [], edges: [] } } });
    const result = await p;
    expect(result.graph.program).toBe('hero.ttrp');
  });

  it('sends didOpen as a notification (no id) — one message per frame', async () => {
    const { client } = connected();
    await client.connect();
    const ws = (client as unknown as { rpc: { ws: FakeWebSocket } }).rpc.ws;
    client.openDocument('file:///hero.ttrp', 'text');
    const frames = ws.sent.map((s) => JSON.parse(s));
    const didOpen = frames.find((f) => f.method === 'textDocument/didOpen');
    expect(didOpen).toBeDefined();
    expect(didOpen.id).toBeUndefined();
    expect(didOpen.params.textDocument.uri).toBe('file:///hero.ttrp');
    // Every frame is a single JSON object (no header framing).
    for (const s of ws.sent) expect(() => JSON.parse(s)).not.toThrow();
  });

  it('routes publishDiagnostics notifications to the handler', async () => {
    const { client } = connected();
    await client.connect();
    const ws = (client as unknown as { rpc: { ws: FakeWebSocket } }).rpc.ws;
    let received: { uri: string; count: number } | null = null;
    client.onDiagnostics((uri, ds) => {
      received = { uri, count: ds.length };
    });
    ws.receive({
      jsonrpc: '2.0',
      method: 'textDocument/publishDiagnostics',
      params: { uri: 'file:///hero.ttrp', diagnostics: [{ message: 'x', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 1 } } }] },
    });
    expect(received).toEqual({ uri: 'file:///hero.ttrp', count: 1 });
  });
});
