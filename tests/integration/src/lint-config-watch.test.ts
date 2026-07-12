// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@tatrman/lsp/server';
import { mkdtempSync, writeFileSync, rmSync, readFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

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

function nextDiagnostics(client: lsp.Connection, uri: string): Promise<lsp.Diagnostic[]> {
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

/** Resolve on the first publish for `uri` whose diagnostics satisfy `pred`. */
function diagnosticsMatching(
  client: lsp.Connection,
  uri: string,
  pred: (diags: lsp.Diagnostic[]) => boolean
): Promise<lsp.Diagnostic[]> {
  return new Promise((resolve) => {
    const off = client.onNotification('textDocument/publishDiagnostics', (params) => {
      const p = params as lsp.PublishDiagnosticsParams;
      if (p.uri === uri && pred(p.diagnostics)) {
        off.dispose();
        resolve(p.diagnostics);
      }
    });
  });
}

describe('live .ttrlint.toml config watch (integration)', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;
  let root: string;

  beforeAll(async () => {
    root = mkdtempSync(join(tmpdir(), 'modeler-ttrlint-'));
    const pair = createPairedConnection();
    client = pair.client;
    server = pair.server;
    createServerConnection(server, {
      async readConfigFile(p: string) {
        return existsSync(p) ? readFileSync(p, 'utf-8') : undefined;
      },
    });
    await client.sendRequest('initialize', { processId: null, rootUri: null, capabilities: {} });
    client.sendNotification('initialized', {});
    await client.sendRequest('modeler/setProjectRoot', { projectRoot: root });
  });

  afterAll(() => {
    client.dispose();
    server.dispose();
    rmSync(root, { recursive: true, force: true });
  });

  it('raising a rule to error in .ttrlint.toml re-publishes with the new severity', async () => {
    const uri = `file://${root}/main.ttrm`;
    const text = `package app
import other.db.dbo.thing
model db schema dbo
def table t { columns: [def column id { type: int }] }
`;

    // 1) Open: recommended → unused-import is a Warning (severity 2).
    const firstPromise = nextDiagnostics(client, uri);
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text },
    });
    const first = await firstPromise;
    const firstUnused = first.find((d) => d.code === 'ttr/unused-import');
    expect(firstUnused?.severity).toBe(lsp.DiagnosticSeverity.Warning);

    // 2) Write a .ttrlint.toml raising unused-import to error, then trigger a reload.
    //    (didOpen fires two initial publishes, so wait for the one carrying the
    //    raised severity rather than just "the next publish".)
    writeFileSync(join(root, '.ttrlint.toml'), `[rules]\nunused-import = "error"\n`);
    const secondPromise = diagnosticsMatching(client, uri, (diags) =>
      diags.some((d) => d.code === 'ttr/unused-import' && d.severity === lsp.DiagnosticSeverity.Error)
    );
    await client.sendRequest('modeler/reloadLintConfig', {});
    const second = await secondPromise;
    const secondUnused = second.find((d) => d.code === 'ttr/unused-import');
    expect(secondUnused?.severity).toBe(lsp.DiagnosticSeverity.Error);
  });
});
