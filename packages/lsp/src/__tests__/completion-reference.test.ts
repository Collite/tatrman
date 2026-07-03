import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '../server.js';
import { parseString } from '@modeler/parser';
import { buildImportTextEdit } from '@modeler/edit';

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

describe('completion-reference', () => {
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

  it('returns CompletionItemKind.Reference for all items', async () => {
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

    expect(result.items.length).toBeGreaterThan(0);
    const item = result.items[0] as { kind: number };
    expect(item.kind).toBe(18);
  });

  it('sortText bucket ordering: same-package before unimported', async () => {
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

    expect(result.items.length).toBeGreaterThan(0);
    const items = result.items as Array<{ sortText?: string; detail?: string }>;
    expect(items[0].sortText).toBeDefined();
    const artiklItem = items.find(i => i.detail?.includes('artikl'));
    expect(artiklItem).toBeDefined();
    expect(artiklItem!.sortText!.startsWith('0_')).toBe(true);
  });

  it('returns empty list outside a reference position', async () => {
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
});

describe('import-edits', () => {
  it('buildImportTextEdit inserts import on its own line after package', () => {
    const content = `package billing.invoicing

model er schema entity

def entity artikl {}`;

    const doc = parseString(content, 'file:///test.ttrm').ast!;
    const result = buildImportTextEdit(content, doc, 'billing.products');

    expect(result).not.toBeNull();
    expect(result!.edit.newText).toBe('import billing.products\n');

    const edit = result!.edit;
    expect(edit.range.start.line).toBe(edit.range.end.line);
    expect(edit.range.start.character).toBe(0);
    expect(edit.newText).toContain('import billing.products');
  });

  it('buildImportTextEdit inserts in alphabetical order', () => {
    const content = `package billing.invoicing

import billing.products.*

model er schema entity`;

    const doc = parseString(content, 'file:///test.ttrm').ast!;
    const result = buildImportTextEdit(content, doc, 'billing.invoicing');

    expect(result).not.toBeNull();
    expect(result!.edit.newText).toBe('import billing.invoicing\n');
  });

  it('buildImportTextEdit returns null when import already exists', () => {
    const content = `package billing.invoicing

import billing.products

model er schema entity`;

    const doc = parseString(content, 'file:///test.ttrm').ast!;
    const result = buildImportTextEdit(content, doc, 'billing.products');

    expect(result).toBeNull();
  });

  it('apply edit to content produces valid parseable file', () => {
    const content = `package billing.invoicing

model er schema entity

def entity artikl {}`;

    const doc = parseString(content, 'file:///test.ttrm').ast!;
    const result = buildImportTextEdit(content, doc, 'billing.products');

    const lines = content.split('\n');
    const edit = result!.edit;
    lines.splice(edit.range.start.line, edit.range.end.line - edit.range.start.line, edit.newText);
    const modified = lines.join('\n');

    const reparsed = parseString(modified, 'file:///test.ttrm');
    expect(reparsed.errors.length).toBe(0);
    expect(modified).toContain('import billing.products\n');
  });

  it('inserts with blank line after package when no imports present', () => {
    const content = `package billing.invoicing
model er schema entity
def entity artikl {}`;

    const doc = parseString(content, 'file:///test.ttrm').ast!;
    const result = buildImportTextEdit(content, doc, 'billing.products');

    expect(result).not.toBeNull();
    const edit = result!.edit;
    expect(edit.newText).toBe('import billing.products\n');
    expect(edit.range.start.line).toBeGreaterThan(0);
  });
});

describe('completion-config integration', () => {
  let clientConnection: lsp.Connection;
  let serverConnection: lsp.Connection;

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

  afterEach(() => {
    clientConnection?.dispose();
    serverConnection?.dispose();
  });

  it('autoImport: false suppresses additionalTextEdits on reference completion', async () => {
    const { client, server } = createPairedConnection();
    clientConnection = client;
    serverConnection = server;

    let configResponse: unknown[] = [false];
    clientConnection.onRequest('workspace/configuration', (_params: { items: Array<{ section: string }> }) => {
      return configResponse;
    });

    createServerConnection(serverConnection, { completionAutoImport: undefined });

    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: 'file:///proj',
      capabilities: { workspace: { configuration: true } },
    });
    clientConnection.sendNotification('initialized', {});

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///proj/pkg_a/test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package pkg_a\n\ndef entity artikl {}`,
      },
    });
    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///proj/pkg_b/consumer.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package pkg_b\n\nmodel er schema entity\n\ndef relation uses_artikl {\n  from: pkg_a.er.entity.artikl\n}`,
      },
    });

    await new Promise((r) => setTimeout(r, 100));

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///proj/pkg_b/consumer.ttrm' },
      position: { line: 4, character: 12 },
      context: { triggerKind: 1 },
    }) as { isIncomplete: boolean; items: unknown[] };

    expect(result.items.length).toBeGreaterThan(0);
    const artiklItem = (result.items as Array<{ label: string; additionalTextEdits?: unknown[] }>).find(
      (i) => i.label === 'artikl'
    );
    expect(artiklItem).toBeDefined();
    expect(artiklItem!.additionalTextEdits, 'autoImport: false should suppress additionalTextEdits').toBeUndefined();

    configResponse = [true];

    clientConnection.sendNotification('workspace/didChangeConfiguration', { settings: { 'modeler.completion': { autoImport: true } } });
    await new Promise((r) => setTimeout(r, 100));

    const result2 = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///proj/pkg_b/consumer.ttrm' },
      position: { line: 4, character: 12 },
      context: { triggerKind: 1 },
    }) as { isIncomplete: boolean; items: unknown[] };

    const artiklItem2 = (result2.items as Array<{ label: string; additionalTextEdits?: unknown[] }>).find(
      (i) => i.label === 'artikl'
    );
    expect(artiklItem2).toBeDefined();
    expect(artiklItem2!.additionalTextEdits, 'autoImport: true should include additionalTextEdits').toBeDefined();
    expect(artiklItem2!.additionalTextEdits!.length).toBeGreaterThan(0);
  });
});