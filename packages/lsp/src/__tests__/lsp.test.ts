import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '../server.js';

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

async function sleep(ms: number): Promise<void> {
  await new Promise(resolve => setTimeout(resolve, ms));
}

describe('lsp', () => {
  let clientConnection: lsp.Connection;
  let serverConnection: lsp.Connection;

  beforeEach(async () => {
    const { client, server } = createPairedConnection();
    clientConnection = client;
    serverConnection = server;
    createServerConnection(serverConnection);
  });

  afterEach(() => {
    clientConnection.dispose();
    serverConnection.dispose();
  });

  it('after initialize, server returns textDocumentSync openClose capability', async () => {
    const result = await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: null,
      capabilities: {},
    }) as { capabilities: { textDocumentSync: { openClose: boolean; change: number } } };

    expect(result.capabilities.textDocumentSync.openClose).toBe(true);
    expect(result.capabilities.textDocumentSync.change).toBe(1);
  });

  it('textDocument/didOpen with malformed content publishes a diagnostic with code and source', async () => {
    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: null,
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    const diagnosticsPromise = new Promise<{ diagnostics: unknown[] }>((resolve) => {
      clientConnection.onNotification('textDocument/publishDiagnostics', (params) => {
        resolve(params as { diagnostics: unknown[] });
      });
    });

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: 'def entity {',
      },
    });

    const diagnostics = await diagnosticsPromise;
    expect(diagnostics.diagnostics.length).toBeGreaterThan(0);
    const diag = diagnostics.diagnostics[0] as { code?: string; source?: string };
    expect(diag.code).toBe('ttr/parse-error');
    expect(diag.source).toBe('modeler');
  });

  it('modeler/getModelGraph returns expected stub nodes after didOpen', async () => {
    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: null,
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: 'def entity foo {}',
      },
    });

    await sleep(50);

    const result = await clientConnection.sendRequest('modeler/getModelGraph', {
      textDocument: { uri: 'file:///test.ttrm' },
      schema: 'er',
    }) as { schemaCode: string; nodes: Array<{ qname: string; kind: string; label: string; rows: unknown[] }>; edges: unknown[] };

    expect(result.schemaCode).toBe('er');
    expect(result.nodes).toHaveLength(1);
    expect(result.nodes[0].qname).toBe('er.entity.foo');
    expect(result.nodes[0].kind).toBe('entity');
    expect(result.nodes[0].label).toBe('foo');
    expect(result.edges).toEqual([]);
  });

  it('textDocument/didOpen with .ttrl file produces no diagnostics', async () => {
    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: null,
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    let diagnosticsReceived = false;
    clientConnection.onNotification('textDocument/publishDiagnostics', () => {
      diagnosticsReceived = true;
    });

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///test.ttrl',
        languageId: 'ttrl',
        version: 1,
        text: '{ "version": 1, "nodes": {} }',
      },
    });

    await sleep(50);
    expect(diagnosticsReceived).toBe(false);
  });
});