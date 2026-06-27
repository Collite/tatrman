import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';

function createPairedConnection(): { client: lsp.Connection; server: lsp.Connection } {
  const clientTransport = new PassThrough({ objectMode: true });
  const serverTransport = new PassThrough({ objectMode: true });
  const clientReader = new lsp.StreamMessageReader(clientTransport as unknown as NodeJS.ReadableStream);
  const clientWriter = new lsp.StreamMessageWriter(serverTransport as unknown as NodeJS.WritableStream);
  const client = lsp.createConnection(clientReader, clientWriter) as lsp.Connection;
  const serverReader = new lsp.StreamMessageReader(serverTransport as unknown as NodeJS.ReadableStream);
  const serverWriter = new lsp.StreamMessageWriter(clientTransport as unknown as NodeJS.WritableStream);
  const server = lsp.createConnection(serverReader, serverWriter) as lsp.Connection;
  client.listen();
  server.listen();
  return { client, server };
}

function diagnosticsFor(client: lsp.Connection, uri: string): Promise<lsp.Diagnostic[]> {
  return new Promise((resolve) => {
    const off = client.onNotification('textDocument/publishDiagnostics', (params) => {
      const p = params as lsp.PublishDiagnosticsParams;
      if (p.uri === uri) {
        off.dispose();
        resolve(p.diagnostics);
      }
    });
  });
}

describe('LSP lint diagnostics + suppression (integration)', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;

  beforeAll(async () => {
    const pair = createPairedConnection();
    client = pair.client;
    server = pair.server;
    createServerConnection(server);
    await client.sendRequest('initialize', { processId: null, rootUri: null, capabilities: {} });
    client.sendNotification('initialized', {});
  });

  afterAll(() => {
    client.dispose();
    server.dispose();
  });

  it('emits unused-import normally, but suppresses it with ttr-disable-next-line', async () => {
    const baseUri = 'file:///lint-suppress/plain.ttrm';
    const baseText = `package app
import other.db.dbo.thing
model db schema dbo
def table t { columns: [def column id { type: int }] }
`;
    const basePromise = diagnosticsFor(client, baseUri);
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: baseUri, languageId: 'ttr', version: 1, text: baseText },
    });
    const baseDiags = await basePromise;
    expect(baseDiags.map((d) => d.code)).toContain('ttr/unused-import');

    // Same file, with a disable directive on the line above the import.
    const suppressedUri = 'file:///lint-suppress/suppressed.ttrm';
    const suppressedText = `package app
// ttr-disable-next-line unused-import
import other.db.dbo.thing
model db schema dbo
def table t { columns: [def column id { type: int }] }
`;
    const suppressedPromise = diagnosticsFor(client, suppressedUri);
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: suppressedUri, languageId: 'ttr', version: 1, text: suppressedText },
    });
    const suppressedDiags = await suppressedPromise;
    expect(suppressedDiags.map((d) => d.code)).not.toContain('ttr/unused-import');
  });
});
