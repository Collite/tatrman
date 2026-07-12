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
  await new Promise((resolve) => setTimeout(resolve, ms));
}

describe('completion-schema-def-kind', () => {
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

  describe('model code completion', () => {
    it('returns the v4.0 model codes after the `model` keyword (no retired query)', async () => {
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
          text: `model <CURSOR>`,
        },
      });

      await sleep(50);

      const result = await clientConnection.sendRequest('textDocument/completion', {
        textDocument: { uri: 'file:///test.ttrm' },
        position: { line: 0, character: 6 },
        context: { triggerKind: 1 },
      }) as { isIncomplete: boolean; items: unknown[] };

      const labels = (result.items as Array<{ label: string }>).map((i) => i.label);
      // v4.0 model codes (D14: no `query`; D15: `cnc` present; `md` included).
      expect(labels.sort()).toEqual(['binding', 'cnc', 'db', 'er', 'md']);
      expect(labels).not.toContain('query');
    });

    it('does not return schema codes in other contexts', async () => {
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
          text: `def entity foo {
  schema
}`,
        },
      });

      await sleep(50);

      const result = await clientConnection.sendRequest('textDocument/completion', {
        textDocument: { uri: 'file:///test.ttrm' },
        position: { line: 1, character: 2 },
        context: { triggerKind: 1 },
      }) as { isIncomplete: boolean; items: unknown[] };

      const labels = (result.items as Array<{ label: string }>).map((i) => i.label);
      expect(labels).not.toContain('db');
      expect(labels).not.toContain('er');
    });
  });

  describe('def kind completion', () => {
    it('returns all def kinds after def keyword with no schema', async () => {
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
          text: `def <CURSOR>`,
        },
      });

      await sleep(50);

      const result = await clientConnection.sendRequest('textDocument/completion', {
        textDocument: { uri: 'file:///test.ttrm' },
        position: { line: 0, character: 4 },
        context: { triggerKind: 1 },
      }) as { isIncomplete: boolean; items: unknown[] };

      expect(result.items.length).toBeGreaterThan(0);
      const labels = (result.items as Array<{ label: string }>).map((i) => i.label);
      expect(labels).toContain('entity');
      expect(labels).toContain('table');
      expect(labels).toContain('column');
      expect(labels).toContain('attribute');
      expect(labels).toContain('relation');
    });

    it('returns er-specific def kinds inside model er file', async () => {
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
          text: `model er schema entity

def <CURSOR>`,
        },
      });

      await sleep(50);

      const result = await clientConnection.sendRequest('textDocument/completion', {
        textDocument: { uri: 'file:///test.ttrm' },
        position: { line: 2, character: 4 },
        context: { triggerKind: 1 },
      }) as { isIncomplete: boolean; items: unknown[] };

      const labels = (result.items as Array<{ label: string }>).map((i) => i.label);
      expect(labels).toContain('entity');
      expect(labels).toContain('attribute');
      expect(labels).toContain('relation');
      expect(labels).toContain('er2cncRole');
      expect(labels).not.toContain('table');
      expect(labels).not.toContain('column');
    });

    it('returns db-specific def kinds inside model db file', async () => {
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
          text: `model db schema dbo

def <CURSOR>`,
        },
      });

      await sleep(50);

      const result = await clientConnection.sendRequest('textDocument/completion', {
        textDocument: { uri: 'file:///test.ttrm' },
        position: { line: 2, character: 4 },
        context: { triggerKind: 1 },
      }) as { isIncomplete: boolean; items: unknown[] };

      const labels = (result.items as Array<{ label: string }>).map((i) => i.label);
      expect(labels).toContain('table');
      expect(labels).toContain('view');
      expect(labels).toContain('column');
      expect(labels).toContain('index');
      expect(labels).toContain('constraint');
      expect(labels).toContain('fk');
      expect(labels).toContain('procedure');
      expect(labels).not.toContain('entity');
    });
  });
});