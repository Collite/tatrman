import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';
import { format } from '@modeler/format';
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
  await new Promise((resolve) => setTimeout(resolve, ms));
}

const TMP = join(tmpdir(), 'modeler-formatting-test');

beforeAll(() => mkdirSync(TMP, { recursive: true }));
afterAll(() => rmSync(TMP, { recursive: true, force: true }));

interface TextEditLike {
  range: { start: { line: number; character: number }; end: { line: number; character: number } };
  newText: string;
}

describe('textDocument/formatting (integration)', () => {
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

  it('returns a full-document edit equal to format(src, uri)', async () => {
    const filePath = join(TMP, 'unformatted.ttr');
    // Deliberately messy spacing/indentation so the formatter produces an edit.
    const src = `schema db namespace dbo
def table users {
columns: [
def column id {    type: int   }
]
}
`;
    writeFileSync(filePath, src);
    const uri = `file://${filePath}`;

    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text: src },
    });
    await sleep(100);

    const edits = (await client.sendRequest('textDocument/formatting', {
      textDocument: { uri },
      options: { tabSize: 4, insertSpaces: true },
    })) as TextEditLike[];

    expect(Array.isArray(edits)).toBe(true);
    expect(edits.length).toBe(1);
    expect(edits[0].newText).toBe(format(src, uri));
  });
});
