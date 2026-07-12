// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@tatrman/lsp/server';
import { mkdtempSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { pathToFileURL } from 'url';

// Grounding Phase 1 (grammar 4.2) — the `semantics { }` validator surfaced through
// the standard LSP diagnostics pipeline (didOpen → publishDiagnostics).

function createPairedConnection(): { client: lsp.Connection; server: lsp.Connection } {
  const ct = new PassThrough({ objectMode: true });
  const st = new PassThrough({ objectMode: true });
  const client = lsp.createConnection(
    new lsp.StreamMessageReader(ct as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(st as unknown as NodeJS.WritableStream)
  ) as lsp.Connection;
  const server = lsp.createConnection(
    new lsp.StreamMessageReader(st as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(ct as unknown as NodeJS.WritableStream)
  ) as lsp.Connection;
  client.listen();
  server.listen();
  return { client, server };
}
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

const MODEL = [
  'model er',
  'def entity Transaction {',
  '  attributes: [',
  '    def attribute amount { type: decimal, semantics { role: amout } }',
  '  ]',
  '}',
  '',
].join('\n');

describe('Grounding Phase 1 — semantics block LSP diagnostics', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;
  let uri: string;
  const diagnostics = new Map<string, lsp.Diagnostic[]>();

  beforeAll(async () => {
    const root = mkdtempSync(join(tmpdir(), 'modeler-sem-'));
    writeFileSync(join(root, 'modeler.toml'), '[project]\nname = "sem"\n', 'utf-8');
    writeFileSync(join(root, 'm.ttrm'), MODEL, 'utf-8');
    uri = pathToFileURL(join(root, 'm.ttrm')).href;

    const pair = createPairedConnection();
    client = pair.client;
    server = pair.server;
    client.onNotification('textDocument/publishDiagnostics', (p: lsp.PublishDiagnosticsParams) => {
      diagnostics.set(p.uri, p.diagnostics);
    });
    createServerConnection(server, {
      async scanProjectFiles() {
        return [{ uri, text: MODEL }];
      },
    });

    await client.sendRequest('initialize', { processId: null, rootUri: pathToFileURL(root).href, capabilities: {} });
    client.sendNotification('initialized', {});
    await sleep(50);
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text: MODEL },
    });
    await sleep(150);
  });

  afterAll(async () => {
    await sleep(30);
    client.dispose();
    server.dispose();
  });

  it('publishes TTR-SEM-201 for an unknown role, with a nearest-match suggestion', () => {
    const d = diagnostics.get(uri) ?? [];
    const hit = d.find((x) => x.code === 'TTR-SEM-201');
    expect(hit).toBeDefined();
    expect(hit?.message).toContain('amount'); // did-you-mean nearest match for `amout`
    expect(hit?.source).toBe('modeler');
  });
});
