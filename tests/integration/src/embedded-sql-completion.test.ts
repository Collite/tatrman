import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';
import { parseSql, extract } from '@modeler/sql';
import { resolveManifest } from '@modeler/semantics';
import type { SqlDialect } from '@modeler/parser';

/**
 * embedded-sql 4.4 — SQL completion. After FROM/JOIN, offer `db` tables inserted
 * as dialect-correct qualified names (reverse namespace map); after
 * SELECT/WHERE/`alias.`, offer in-scope columns. Desktop-only harness.
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

// dbo ⇄ (WH, dbo) for tsql; public ⇄ (core, public) for postgres.
const MODELER_TOML = `[sql]
default-dialect = "tsql"
[[sql.namespace-map]]
namespace = "dbo"
database  = "WH"
schema    = "dbo"
[[sql.namespace-map]]
namespace = "public"
database  = "core"
schema    = "public"
[sql.defaults.tsql]
database = "WH"
schema   = "dbo"
[sql.defaults.postgres]
database = "core"
schema   = "public"
`;

// Two db files: a tsql-style `dbo` model and a postgres-style `public` model.
const DB_DBO = `model db schema dbo
def table Orders {
  columns: [ def column id { type: int }, def column total { type: money, description: "Order total" } ]
}
`;
const DB_PUBLIC = `model db schema public
def table customers {
  columns: [ def column id { type: int }, def column email { type: text } ]
}
`;

type Item = { label: string; insertText?: string; detail?: string };

describe('embedded-SQL completion (4.4)', () => {
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
    await client.sendRequest('initialize', { processId: null, rootUri: 'file:///proj', capabilities: {} });
    client.sendNotification('initialized', {});
    for (const [uri, text] of [
      ['file:///proj/dbo.ttrm', DB_DBO],
      ['file:///proj/public.ttrm', DB_PUBLIC],
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

  async function open(uri: string, text: string) {
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text },
    });
    await sleep(100);
  }
  const complete = async (uri: string, line: number, character: number): Promise<Item[]> => {
    const res = (await client.sendRequest('textDocument/completion', {
      textDocument: { uri },
      position: { line, character },
    })) as { items: Item[] } | Item[] | null;
    if (!res) return [];
    return Array.isArray(res) ? res : res.items;
  };

  it('after FROM offers a tsql table as a dbo-qualified name', async () => {
    // `SELECT id FROM ` — cursor at end of body line (file line 4), char 15.
    const q = 'model query schema query\n\ndef query q {\n  sourceText: """sql\nSELECT id FROM \n"""\n}\n';
    await open('file:///proj/c1.ttrm', q);
    const items = await complete('file:///proj/c1.ttrm', 4, 'SELECT id FROM '.length);
    const orders = items.find((i) => i.label === 'Orders');
    expect(orders).toBeDefined();
    expect(orders!.insertText).toBe('dbo.Orders');
  });

  it('after FROM in a postgres block offers a public-qualified name', async () => {
    const q = 'model query schema query\n\ndef query q {\n  sourceText: """postgres\nSELECT id FROM \n"""\n}\n';
    await open('file:///proj/c2.ttrm', q);
    const items = await complete('file:///proj/c2.ttrm', 4, 'SELECT id FROM '.length);
    const cust = items.find((i) => i.label === 'customers');
    expect(cust).toBeDefined();
    expect(cust!.insertText).toBe('public.customers');
  });

  it('after an alias dot offers that table’s columns only', async () => {
    const q = 'model query schema query\n\ndef query q {\n  sourceText: """sql\nSELECT o. FROM Orders o\n"""\n}\n';
    await open('file:///proj/c3.ttrm', q);
    const items = await complete('file:///proj/c3.ttrm', 4, 'SELECT o.'.length);
    const labels = items.map((i) => i.label).sort();
    expect(labels).toEqual(['id', 'total']);
    const total = items.find((i) => i.label === 'total');
    expect(total!.detail).toContain('money');
  });

  it('after SELECT offers in-scope columns', async () => {
    const q = 'model query schema query\n\ndef query q {\n  sourceText: """sql\nSELECT  FROM Orders\n"""\n}\n';
    await open('file:///proj/c4.ttrm', q);
    const items = await complete('file:///proj/c4.ttrm', 4, 'SELECT '.length);
    expect(items.map((i) => i.label).sort()).toEqual(['id', 'total']);
  });
});
