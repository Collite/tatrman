import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';
import { mkdtempSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { pathToFileURL } from 'url';

// Phase 3 (3C5) — end-to-end binding round-trip: a logical model + a `schema
// binding` file flow through the LSP; the binding diagnostics publish per file.

function createPairedConnection() {
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

const LOGICAL = `schema md
def domain AccountKind { type: string, kind: bound }
def domain CCCode { type: string }
def domain CustCode { type: string }
def domain Money { type: decimal }
def dimension Customer { key: code, attributes: [def attribute code { domain: md.CustCode }] }
def map cc_to_cust { from: md.CCCode, to: md.CustCode, cardinality: { from: "N", to: "1" } }
def measure net { domain: md.Money, aggregation: sum }
def cubelet sales { grain: [Customer.code], measures: [net] }
`;

// A clean binding set: bound-domain source, table-map columns, wide cubelet.
const BINDING_OK = `schema binding
def md2db_domain ak { domain: md.AccountKind, source: { table: db.dbo.A, column: K } }
def md2db_map cm { map: md.cc_to_cust, target: db.dbo.M, columns: { CCCode: C1, CustCode: C2 } }
def md2db_cubelet sw { cubelet: md.sales, target: db.dbo.S, shape: wide, attributes: { Customer.code: CC }, measures: { net: N } }
`;

// Seeded: md2db_domain on a non-bound domain → md/source-on-unbound-domain.
const BINDING_BAD = BINDING_OK.replace('domain: md.AccountKind', 'domain: md.CCCode');

describe('Phase 3 — MD binding round-trip', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;
  let bindingUri: string;
  const diagnostics = new Map<string, lsp.Diagnostic[]>();

  beforeAll(async () => {
    const root = mkdtempSync(join(tmpdir(), 'modeler-mdb-'));
    writeFileSync(join(root, 'modeler.toml'), '[project]\nname = "mdb"\n', 'utf-8');
    writeFileSync(join(root, 'model.ttrm'), LOGICAL, 'utf-8');
    writeFileSync(join(root, 'binding.ttrm'), BINDING_BAD, 'utf-8');
    const modelUri = pathToFileURL(join(root, 'model.ttrm')).href;
    bindingUri = pathToFileURL(join(root, 'binding.ttrm')).href;

    const pair = createPairedConnection();
    client = pair.client;
    server = pair.server;
    client.onNotification('textDocument/publishDiagnostics', (p: lsp.PublishDiagnosticsParams) => {
      diagnostics.set(p.uri, p.diagnostics);
    });
    createServerConnection(server, {
      async scanProjectFiles() {
        return [
          { uri: modelUri, text: LOGICAL },
          { uri: bindingUri, text: BINDING_BAD },
        ];
      },
    });

    await client.sendRequest('initialize', { processId: null, rootUri: pathToFileURL(root).href, capabilities: {} });
    client.sendNotification('initialized', {});
    await sleep(50);
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: modelUri, languageId: 'ttr', version: 1, text: LOGICAL },
    });
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: bindingUri, languageId: 'ttr', version: 1, text: BINDING_BAD },
    });
    await sleep(150);
  });

  afterAll(async () => {
    await sleep(30);
    client.dispose();
    server.dispose();
  });

  it('publishes md/source-on-unbound-domain on the binding file for the seeded error', () => {
    const d = diagnostics.get(bindingUri) ?? [];
    expect(d.some((x) => x.code === 'md/source-on-unbound-domain')).toBe(true);
  });
});
