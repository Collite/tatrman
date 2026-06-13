import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';
import { parseSql, extract } from '@modeler/sql';
import { resolveManifest } from '@modeler/semantics';
import type { SqlDialect } from '@modeler/parser';

/**
 * embedded-sql 4.5 — rename across the boundary. Renaming a `db` table/column
 * rewrites the TTR def AND every in-SQL usage across files. Multi-part names keep
 * their qualifier (`dbo.Orders` → `dbo.Invoices`), and a column rename touches
 * only the matching column. prepareRename is offered only on a resolved db ref.
 */
function createPairedConnection(): { client: lsp.Connection; server: lsp.Connection } {
  const a = new PassThrough({ objectMode: true });
  const b = new PassThrough({ objectMode: true });
  const client = lsp.createConnection(
    new lsp.StreamMessageReader(a as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(b as unknown as NodeJS.WritableStream),
  ) as lsp.Connection;
  const server = lsp.createConnection(
    new lsp.StreamMessageReader(b as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(a as unknown as NodeJS.WritableStream),
  ) as lsp.Connection;
  client.listen();
  server.listen();
  return { client, server };
}

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

const MODELER_TOML = `[sql]
default-dialect = "tsql"
[[sql.namespace-map]]
namespace = "dbo"
database  = "WH"
schema    = "dbo"
[sql.defaults.tsql]
database = "WH"
schema   = "dbo"
`;
const DB_TTR = `schema db namespace dbo
def table Orders {
  columns: [ def column id { type: int }, def column total { type: int } ]
}
`;
const Q1 = 'schema query namespace q1\n\ndef query q1 {\n  sourceText: """sql\nSELECT id FROM Orders\n"""\n}\n';
// multi-part name + alias: `dbo.Orders o`.
const Q2 = 'schema query namespace q2\n\ndef query q2 {\n  sourceText: """sql\nSELECT total FROM dbo.Orders o\n"""\n}\n';

interface Edit {
  range: { start: { line: number; character: number }; end: { line: number; character: number } };
  newText: string;
}
interface DocChange {
  textDocument: { uri: string };
  edits: Edit[];
}
type Flat = { uri: string } & Edit;
function flatten(res: { documentChanges?: DocChange[] } | null): Flat[] {
  const out: Flat[] = [];
  for (const dc of res?.documentChanges ?? []) for (const e of dc.edits) out.push({ uri: dc.textDocument.uri, ...e });
  return out;
}

describe('embedded-SQL rename across the boundary (4.5)', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;

  beforeAll(async () => {
    const pair = createPairedConnection();
    client = pair.client;
    server = pair.server;
    createServerConnection(server, {
      analyzeSqlBlock(value: string, dialect: SqlDialect) {
        const { tree } = parseSql(value, dialect);
        return tree ? extract(tree, dialect) : undefined;
      },
      async loadManifest() {
        return resolveManifest(undefined, '/proj');
      },
      async readConfigFile(path: string) {
        return path.endsWith('modeler.toml') ? MODELER_TOML : undefined;
      },
    });
    await client.sendRequest('initialize', {
      processId: null,
      rootUri: 'file:///proj',
      capabilities: {},
    });
    client.sendNotification('initialized', {});
    for (const [uri, text] of [
      ['file:///proj/db.ttr', DB_TTR],
      ['file:///proj/q1.ttr', Q1],
      ['file:///proj/q2.ttr', Q2],
    ] as const) {
      client.sendNotification('textDocument/didOpen', {
        textDocument: { uri, languageId: 'ttr', version: 1, text },
      });
      await sleep(80);
    }
    await sleep(80);
  });

  afterAll(() => {
    client.dispose();
    server.dispose();
  });

  const rename = (uri: string, line: number, character: number, newName: string) =>
    client.sendRequest('textDocument/rename', {
      textDocument: { uri },
      position: { line, character },
      newName,
    }) as Promise<{ documentChanges?: DocChange[] } | null>;
  const prepare = (uri: string, line: number, character: number) =>
    client.sendRequest('textDocument/prepareRename', {
      textDocument: { uri },
      position: { line, character },
    }) as Promise<{ placeholder: string } | null>;

  it('renames a db table + its in-SQL usages, preserving the schema qualifier', async () => {
    const edits = flatten(await rename('file:///proj/db.ttr', 1, 'def table '.length, 'Invoices'));
    // db def itself.
    expect(edits.some((e) => /db\.ttr$/.test(e.uri) && e.newText === 'Invoices')).toBe(true);
    // q1: bare `Orders` → `Invoices`.
    const e1 = edits.find((e) => /q1\.ttr$/.test(e.uri));
    expect(e1).toBeDefined();
    expect(e1!.newText).toBe('Invoices');
    expect(e1!.range.start.character).toBe(Q1.split('\n')[4].indexOf('Orders'));
    // q2: only the `Orders` segment of `dbo.Orders` (qualifier kept).
    const e2 = edits.find((e) => /q2\.ttr$/.test(e.uri));
    expect(e2).toBeDefined();
    expect(e2!.newText).toBe('Invoices');
    expect(e2!.range.start.character).toBe(Q2.split('\n')[4].indexOf('Orders'));
    expect(e2!.range.end.character).toBe(Q2.split('\n')[4].indexOf('Orders') + 'Orders'.length);
  });

  it('renames a db column only where that column is used', async () => {
    const totalCol = DB_TTR.split('\n')[2].indexOf('total', DB_TTR.split('\n')[2].indexOf('def column id'));
    const edits = flatten(await rename('file:///proj/db.ttr', 2, totalCol, 'amount'));
    // q2 uses `total`; q1 does not.
    expect(edits.some((e) => /q2\.ttr$/.test(e.uri) && e.newText === 'amount')).toBe(true);
    expect(edits.some((e) => /q1\.ttr$/.test(e.uri))).toBe(false);
  });

  it('rename invoked from inside a SQL usage rewrites all usages', async () => {
    const orders = Q1.split('\n')[4].indexOf('Orders');
    const edits = flatten(await rename('file:///proj/q1.ttr', 4, orders, 'Invoices'));
    const uris = edits.map((e) => e.uri);
    expect(uris.some((u) => /db\.ttr$/.test(u))).toBe(true);
    expect(uris.some((u) => /q2\.ttr$/.test(u))).toBe(true);
  });

  it('prepareRename is offered on a SQL table ref but not on a keyword', async () => {
    const orders = Q1.split('\n')[4].indexOf('Orders');
    const prep = await prepare('file:///proj/q1.ttr', 4, orders);
    expect(prep?.placeholder).toBe('Orders');
    expect(await prepare('file:///proj/q1.ttr', 4, 0)).toBeNull(); // `SELECT`
  });
});
