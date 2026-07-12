// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@tatrman/lsp/server';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { writeFileSync, mkdirSync, rmSync } from 'node:fs';

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

const INTEGRATION_TMP = join(tmpdir(), 'modeler-completion-test');

beforeAll(() => {
  mkdirSync(INTEGRATION_TMP, { recursive: true });
});

afterAll(() => {
  rmSync(INTEGRATION_TMP, { recursive: true, force: true });
});

describe('completion (integration)', () => {
  let clientConnection: lsp.Connection;
  let serverConnection: lsp.Connection;

  beforeAll(async () => {
    const { client, server } = createPairedConnection();
    clientConnection = client;
    serverConnection = server;
    createServerConnection(serverConnection);
  });

  afterAll(() => {
    clientConnection.dispose();
    serverConnection.dispose();
  });

  it('textDocument/completion returns non-empty list inside a from: value', async () => {
    const filePath = join(INTEGRATION_TMP, 'artikl.ttrm');
    writeFileSync(filePath, `package billing.invoicing

def entity artikl {
  description: "Artikl"
}

def relation artikl_produkt {
  from: artikl
  to: billing.products.er.entity.produkt
}`);

    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: null,
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: `file://${filePath}`,
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

    await sleep(100);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: `file://${filePath}` },
      position: { line: 7, character: 8 },
      context: { triggerKind: 2, triggerCharacter: '.' },
    }) as { isIncomplete: boolean; items: unknown[] };

    expect(result.items.length).toBeGreaterThan(0);
    const first = result.items[0] as { label: string; kind: number; detail: string };
    expect(first.label).toBeTruthy();
    expect(first.kind).toBe(18);
    expect(first.detail).toContain('(');
  });

  it('textDocument/completion returns empty when outside a reference position', async () => {
    const filePath = join(INTEGRATION_TMP, 'artikl2.ttrm');
    writeFileSync(filePath, `def entity artikl {
  description: "Artikl"
}`);

    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: null,
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: `file://${filePath}`,
        languageId: 'ttr',
        version: 1,
        text: `def entity artikl {
  description: "Artikl"
}`,
      },
    });

    await sleep(100);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: `file://${filePath}` },
      position: { line: 0, character: 5 },
      context: { triggerKind: 2, triggerCharacter: '.' },
    }) as { isIncomplete: boolean; items: unknown[] };

    expect(result.items.length).toBe(0);
  });

  it('auto-import: unimported candidate has additionalTextEdits that produce a valid file', async () => {
    const productsPath = join(INTEGRATION_TMP, 'produkt.ttrm');
    writeFileSync(productsPath, `package billing.products

model er schema entity

def entity produkt {
  description: "Produkt"
}`);

    const invoicingPath = join(INTEGRATION_TMP, 'invoicing.ttrm');
    writeFileSync(invoicingPath, `package billing.invoicing

model er schema entity

def relation artikl_produkt {
  from: billing.products.er.entity.produkt
}`);

    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: null,
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: `file://${productsPath}`,
        languageId: 'ttr',
        version: 1,
        text: `package billing.products

model er schema entity

def entity produkt {
  description: "Produkt"
}`,
      },
    });

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: `file://${invoicingPath}`,
        languageId: 'ttr',
        version: 1,
        text: `package billing.invoicing

model er schema entity

def relation artikl_produkt {
  from: billing.products.er.entity.produkt
}`,
      },
    });

    await sleep(100);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: `file://${invoicingPath}` },
      position: { line: 4, character: 10 },
      context: { triggerKind: 2, triggerCharacter: '.' },
    }) as { isIncomplete: boolean; items: unknown[] };

    const unimportedItems = (result.items as Array<{
      additionalTextEdits?: Array<{ newText: string; range: { start: { line: number; character: number }; end: { line: number; character: number } } }>;
      detail?: string;
    }>).filter(item => item.additionalTextEdits && item.additionalTextEdits.length > 0);

    expect(unimportedItems.length).toBeGreaterThan(0);

    const edit = unimportedItems[0].additionalTextEdits![0];
    expect(edit.newText).toContain('import billing.products');
    expect(edit.newText).toContain('\n');
    expect(edit.range.start.line).toBeGreaterThan(0);
    expect(edit.range.start.character).toBe(0);
  });
});