// SPDX-License-Identifier: Apache-2.0
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

describe('completion', () => {
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

  it('textDocument/completion returns items inside a from: value', async () => {
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
        text: `package billing.invoicing

def entity artikl {
  description: "Artikl"
}

def relation artikl_produkt {
  from: artikl
  to: billing.products.er.entity.produkt
}`,
      },
    });

    await sleep(50);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///test.ttrm' },
      position: { line: 7, character: 8 },
      context: { triggerKind: 2, triggerCharacter: '.' },
    }) as { isIncomplete: boolean; items: unknown[] };

    expect(result.isIncomplete).toBe(false);
    expect(result.items.length).toBeGreaterThan(0);
    const item = result.items[0] as {
      label: string;
      kind: number;
      detail: string;
      documentation?: { kind: string; value: string };
      additionalTextEdits?: unknown[];
    };
    expect(item.label).toBeDefined();
    expect(item.kind).toBe(18);
    expect(item.detail).toBeDefined();
    expect(item.detail).toContain('(');
  });

  it('textDocument/completion returns empty when cursor is not in a reference position', async () => {
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
        text: `def entity artikl {
  description: "Artikl"
}`,
      },
    });

    await sleep(50);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///test.ttrm' },
      position: { line: 0, character: 5 },
      context: { triggerKind: 2, triggerCharacter: '.' },
    }) as { isIncomplete: boolean; items: unknown[] };

    expect(result.items.length).toBe(0);
  });

  it('textDocument/completion result shape and bucket labels', async () => {
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
        text: `package billing.invoicing

def entity artikl {
  description: "Artikl"
}

def relation artikl_produkt {
  from: billing.products.er.entity.produkt
}`,
      },
    });

    await sleep(50);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///test.ttrm' },
      position: { line: 7, character: 8 },
      context: { triggerKind: 2, triggerCharacter: '.' },
    }) as { isIncomplete: boolean; items: unknown[] };

    expect(result.items.length).toBeGreaterThan(0);
    const item = result.items[0] as {
      label: string;
      kind: number;
      detail: string;
      documentation?: { kind: string; value: string };
    };
    expect(item.label).toBeDefined();
    expect(item.kind).toBe(18);
    expect(item.detail).toContain('(');
  });
});