// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@tatrman/lsp/server';
import { parseSql, extract } from '@tatrman/sql';
import { resolveManifest } from '@tatrman/semantics';
import type { SqlDialect } from '@tatrman/parser';

/**
 * embedded-sql 4.1/4.2 — SQL IDE features end-to-end through the LSP. Hover over a
 * SQL column/table resolves to the TTR `db` symbol (type/description/column
 * count); go-to-definition jumps to the `db` def; ambiguous bare columns return
 * all candidates. Desktop-only — the harness wires `analyzeSqlBlock` like stdio.
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

const DB_TTR = `model db schema dbo
def table users {
  columns: [
    def column email { type: varchar, description: "User email address" },
    def column id { type: int, isKey: true }
  ]
}
def table orders {
  columns: [ def column id { type: int }, def column user_id { type: int } ]
}
`;

type Loc = { uri: string; range: { start: { line: number; character: number } } };

describe('embedded-SQL hover + go-to-definition (4.1/4.2)', () => {
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
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: 'file:///proj/db.ttrm', languageId: 'ttr', version: 1, text: DB_TTR },
    });
    await sleep(120);
  });

  afterAll(() => {
    client.dispose();
    server.dispose();
  });

  // Body sits on file line 4 (0-based): `SELECT u.email FROM users u`.
  const QUERY =
    'model query schema query\n\ndef query q {\n  sourceText: """sql\nSELECT u.email FROM users u\n"""\n}\n';

  async function open(uri: string, text: string) {
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text },
    });
    await sleep(100);
  }
  const hover = (uri: string, line: number, character: number) =>
    client.sendRequest('textDocument/hover', { textDocument: { uri }, position: { line, character } }) as Promise<{
      contents: { value: string };
    } | null>;
  const define = (uri: string, line: number, character: number) =>
    client.sendRequest('textDocument/definition', { textDocument: { uri }, position: { line, character } }) as Promise<
      Loc | Loc[] | null
    >;

  it('hover over a SQL column shows its TTR type + description', async () => {
    await open('file:///proj/h.ttrm', QUERY);
    const h = await hover('file:///proj/h.ttrm', 4, 9); // `email`
    expect(h?.contents.value).toContain('db.dbo.table.users.email');
    expect(h?.contents.value).toContain('varchar');
    expect(h?.contents.value).toContain('User email address');
  });

  it('hover over a SQL table shows its column count', async () => {
    const h = await hover('file:///proj/h.ttrm', 4, 20); // `users`
    expect(h?.contents.value).toContain('db.dbo.table.users');
    expect(h?.contents.value).toContain('Columns:');
  });

  it('hover over a SQL keyword returns no hover', async () => {
    expect(await hover('file:///proj/h.ttrm', 4, 0)).toBeNull(); // `SELECT`
  });

  it('go-to-definition on a table jumps to the db table def', async () => {
    const d = await define('file:///proj/h.ttrm', 4, 20); // `users`
    expect(Array.isArray(d)).toBe(false);
    expect((d as Loc).uri).toMatch(/db\.ttrm$/);
    expect((d as Loc).range.start.line).toBe(1); // `def table users` on line 2 (0-based 1)
  });

  it('go-to-definition on a qualified column jumps to the column def', async () => {
    const d = await define('file:///proj/h.ttrm', 4, 9); // `email`
    expect(Array.isArray(d)).toBe(false);
    expect((d as Loc).uri).toMatch(/db\.ttrm$/);
    expect((d as Loc).range.start.line).toBe(3); // `def column email` line
  });

  it('go-to-definition on an ambiguous bare column returns all candidates', async () => {
    const q =
      'model query schema query\n\ndef query q {\n  sourceText: """sql\nSELECT id FROM users JOIN orders ON users.id = orders.id\n"""\n}\n';
    await open('file:///proj/amb.ttrm', q);
    const d = await define('file:///proj/amb.ttrm', 4, 7); // bare `id`
    expect(Array.isArray(d)).toBe(true);
    expect((d as Loc[]).length).toBe(2);
    expect((d as Loc[]).every((l) => /db\.ttrm$/.test(l.uri))).toBe(true);
  });

  it('go-to-definition on an unresolved table returns nothing', async () => {
    const q =
      'model query schema query\n\ndef query q {\n  sourceText: """sql\nSELECT * FROM nonesuch\n"""\n}\n';
    await open('file:///proj/none.ttrm', q);
    expect(await define('file:///proj/none.ttrm', 4, 14)).toBeNull(); // `nonesuch`
  });
});
