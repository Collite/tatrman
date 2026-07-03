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

/** Apply a single TextDocumentEdit's edits to `text` (descending by offset). */
function applyTextDocumentEdit(text: string, edit: lsp.TextDocumentEdit): string {
  const lineStarts = [0];
  for (let i = 0; i < text.length; i++) if (text[i] === '\n') lineStarts.push(i + 1);
  const off = (p: lsp.Position) => lineStarts[p.line] + p.character;
  const sorted = [...edit.edits].sort((a, b) => off(b.range.start) - off(a.range.start));
  let out = text;
  for (const e of sorted) out = out.slice(0, off(e.range.start)) + e.newText + out.slice(off(e.range.end));
  return out;
}

describe('LSP autofix code actions (integration)', () => {
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

  it('offers a quick-fix for unused-import; applying it removes the import', async () => {
    const uri = 'file:///autofix/main.ttrm';
    const text = `package app
import other.db.dbo.thing
model db schema dbo
def table t { columns: [def column id { type: int }] }
`;
    const diagsP = diagnosticsFor(client, uri);
    client.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'ttr', version: 1, text } });
    const diags = await diagsP;
    const unused = diags.find((d) => d.code === 'ttr/unused-import');
    expect(unused).toBeDefined();

    const actions = (await client.sendRequest('textDocument/codeAction', {
      textDocument: { uri },
      range: unused!.range,
      context: { diagnostics: [unused] },
    })) as lsp.CodeAction[];

    const quickfix = actions.find((a) => a.kind === lsp.CodeActionKind.QuickFix);
    expect(quickfix, JSON.stringify(actions)).toBeDefined();
    const tde = quickfix!.edit!.documentChanges![0] as lsp.TextDocumentEdit;
    const out = applyTextDocumentEdit(text, tde);
    expect(out).not.toContain('import other.db.dbo.thing');
    expect(out).toContain('def table t');
  });

  it('offers a suggestion (refactor) — not a quick-fix — for package-declaration-mismatch', async () => {
    const uri = 'file:///autofix/wrong/sub/m.ttrm';
    await client.sendRequest('modeler/setProjectRoot', { projectRoot: '/autofix/wrong' });
    // `renamed` is a leaf-only override of the directory package `sub`, so it
    // stays a plain declaration-mismatch (a prefix divergence is a separate rule).
    const text = `package renamed
model db schema dbo
def table t { columns: [def column id { type: int }] }
`;
    const diagsP = diagnosticsFor(client, uri);
    client.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'ttr', version: 1, text } });
    const diags = await diagsP;
    const mismatch = diags.find((d) => d.code === 'ttr/package-declaration-mismatch');
    expect(mismatch).toBeDefined();

    const actions = (await client.sendRequest('textDocument/codeAction', {
      textDocument: { uri },
      range: mismatch!.range,
      context: { diagnostics: [mismatch] },
    })) as lsp.CodeAction[];

    const forMismatch = actions.filter((a) => (a.diagnostics ?? []).some((d) => d.code === 'ttr/package-declaration-mismatch'));
    expect(forMismatch.length).toBeGreaterThan(0);
    expect(forMismatch.every((a) => a.kind === lsp.CodeActionKind.RefactorRewrite)).toBe(true);
  });
});
