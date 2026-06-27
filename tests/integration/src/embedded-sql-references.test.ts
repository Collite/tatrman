import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';
import { parseSql, extract } from '@modeler/sql';
import { resolveManifest } from '@modeler/semantics';
import type { SqlDialect } from '@modeler/parser';

/**
 * embedded-sql 4.3 — find-references (reverse direction). "Find references" on a
 * `db` table/column returns its usages inside SQL blocks across files, unioned
 * with the existing TTR references. Folding (§6.2) attributes a tsql `users` and
 * a postgres `Users` to the same symbol. Desktop-only harness.
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
[sql.defaults.postgres]
database = "WH"
schema   = "dbo"
`;

const DB_TTR = `model db schema dbo
def table users {
  columns: [ def column email { type: varchar }, def column id { type: int } ]
}
`;
// tsql block, lower-case `users`.
const Q1 = 'model query schema q1\n\ndef query q1 {\n  sourceText: """sql\nSELECT email FROM users\n"""\n}\n';
// postgres block, capitalised `Users` — folds to the same symbol.
const Q2 = 'model query schema q2\n\ndef query q2 {\n  sourceText: """postgres\nSELECT email FROM Users\n"""\n}\n';

type Loc = { uri: string; range: { start: { line: number; character: number } } };

describe('embedded-SQL find-references (4.3)', () => {
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
      ['file:///proj/db.ttrm', DB_TTR],
      ['file:///proj/q1.ttrm', Q1],
      ['file:///proj/q2.ttrm', Q2],
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

  const references = (uri: string, line: number, character: number, includeDeclaration = true) =>
    client.sendRequest('textDocument/references', {
      textDocument: { uri },
      position: { line, character },
      context: { includeDeclaration },
    }) as Promise<Loc[]>;

  it('find-refs on the db table includes SQL usages across files + both dialects', async () => {
    // `users` in `def table users` (line 2 → 0-based 1, char 10).
    const refs = await references('file:///proj/db.ttrm', 1, 10);
    const uris = refs.map((r) => r.uri);
    expect(uris).toContain('file:///proj/db.ttrm'); // declaration
    expect(uris).toContain('file:///proj/q1.ttrm'); // tsql `users`
    expect(uris).toContain('file:///proj/q2.ttrm'); // postgres `Users` (folded)
  });

  it('excludes the declaration when includeDeclaration is false', async () => {
    const refs = await references('file:///proj/db.ttrm', 1, 10, false);
    expect(refs.some((r) => r.uri === 'file:///proj/db.ttrm')).toBe(false);
    expect(refs.filter((r) => /q[12]\.ttrm$/.test(r.uri)).length).toBe(2);
  });

  it('find-refs on a db column returns its SQL usages across files', async () => {
    // `email` column def in db.ttrm (line 3 → 0-based 2). Find the char of `email`.
    const emailChar = DB_TTR.split('\n')[2].indexOf('email');
    const refs = await references('file:///proj/db.ttrm', 2, emailChar);
    const sqlUsages = refs.filter((r) => /q[12]\.ttrm$/.test(r.uri));
    expect(sqlUsages.length).toBe(2);
  });

  it('find-refs invoked from inside a SQL usage returns all usages', async () => {
    // `users` inside q1's SQL (file line 4, char of `users`).
    const usersChar = Q1.split('\n')[4].indexOf('users');
    const refs = await references('file:///proj/q1.ttrm', 4, usersChar);
    expect(refs.map((r) => r.uri)).toContain('file:///proj/q2.ttrm');
  });
});
