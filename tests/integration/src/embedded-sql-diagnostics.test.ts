import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@tatrman/lsp/server';
import { parseSql, extract } from '@tatrman/sql';
import { resolveManifest } from '@tatrman/semantics';
import type { SqlDialect } from '@tatrman/parser';

/**
 * embedded-sql 3.4 — SQL reference resolution diagnostics, end-to-end through the
 * LSP. A `.ttrm` query whose embedded SQL references a missing column on a modelled
 * `db` table reports `sql-unknown-column` at the column's file position (mapped
 * via the §8 source map). Desktop-only: the harness wires `analyzeSqlBlock`
 * (`parseSql` + `extract`), the same way the stdio host does.
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
def table Orders {
  columns: [ def column id { type: int }, def column total { type: int } ]
}
`;

interface Diag {
  message: string;
  code?: string;
  range: { start: { line: number; character: number }; end: { line: number; character: number } };
}

describe('embedded-SQL reference diagnostics (3.4)', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;
  const byUri = new Map<string, Diag[]>();

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
    client.onNotification('textDocument/publishDiagnostics', (p: { uri: string; diagnostics: Diag[] }) => {
      byUri.set(p.uri, p.diagnostics);
    });
    await client.sendRequest('initialize', {
      processId: null,
      rootUri: 'file:///proj',
      capabilities: {},
    });
    client.sendNotification('initialized', {});
    // Register the db schema so its tables/columns are in the symbol table.
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: 'file:///proj/db.ttrm', languageId: 'ttr', version: 1, text: DB_TTR },
    });
    await sleep(120);
  });

  afterAll(() => {
    client.dispose();
    server.dispose();
  });

  async function diagsFor(uri: string, text: string): Promise<Diag[]> {
    byUri.delete(uri);
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text },
    });
    // Poll until the server publishes diagnostics for this uri (it always does,
    // even when the set is empty), then a short settle for any follow-up publish.
    // Robust under parallel test load, where a fixed sleep races the LSP and
    // intermittently reads before diagnostics arrive.
    const deadline = Date.now() + 3000;
    while (!byUri.has(uri) && Date.now() < deadline) await sleep(20);
    await sleep(50);
    return byUri.get(uri) ?? [];
  }

  it('reports sql-unknown-column at the column position in the file', async () => {
    // Body line 1 (file line 4, 0-based) at indent 0: `SELECT bad FROM Orders`.
    const text =
      'model query schema query\n\ndef query q {\n  sourceText: """sql\nSELECT bad FROM Orders\n"""\n}\n';
    const diags = await diagsFor('file:///proj/q.ttrm', text);
    const sql = diags.filter((d) => d.code === 'sql-unknown-column');
    expect(sql).toHaveLength(1);
    expect(sql[0].range.start.line).toBe(4);
    expect(sql[0].range.start.character).toBe(7); // `bad` after `SELECT `
    expect(sql[0].message).toContain('bad');
  });

  it('reports no SQL diagnostics when every reference resolves', async () => {
    const text =
      'model query schema query\n\ndef query q2 {\n  sourceText: """sql\nSELECT id, total FROM Orders\n"""\n}\n';
    const diags = await diagsFor('file:///proj/q2.ttrm', text);
    expect(diags.filter((d) => d.code?.startsWith('sql-'))).toHaveLength(0);
  });

  it('reports sql-undeclared-param at an undeclared {param} placeholder (§3.5)', async () => {
    // `{nazev}` is used in the SQL but the query declares no parameters.
    const text =
      'model query schema query\n\ndef query q3 {\n  sourceText: """sql\nSELECT id FROM Orders WHERE total = {nazev}\n"""\n}\n';
    const diags = await diagsFor('file:///proj/q3.ttrm', text);
    const undeclared = diags.filter((d) => d.code === 'sql-undeclared-param');
    expect(undeclared).toHaveLength(1);
    expect(undeclared[0].range.start.line).toBe(4);
    expect(undeclared[0].range.start.character).toBe(36); // start of `{nazev}`
    expect(undeclared[0].message).toContain('nazev');
  });
});
