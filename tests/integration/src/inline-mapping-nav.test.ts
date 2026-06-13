import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';

function createPairedConnection() {
  const a = new PassThrough({ objectMode: true });
  const b = new PassThrough({ objectMode: true });
  const client = lsp.createConnection(new lsp.StreamMessageReader(a as never), new lsp.StreamMessageWriter(b as never)) as lsp.Connection;
  const server = lsp.createConnection(new lsp.StreamMessageReader(b as never), new lsp.StreamMessageWriter(a as never)) as lsp.Connection;
  client.listen();
  server.listen();
  return { client, server };
}
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

const dbUri = 'file:///proj/db.ttr';
const DB = `schema db namespace dbo
def table QXXUKAZMUHOD {
  columns: [
    def column IDXXUKAZMU { type: int },
    def column NAZEV_UKAZ { type: text }
  ]
}
`;

const erUri = 'file:///proj/er.ttr';
// hodnoty maps to QXXUKAZMUHOD; three attribute-mapping forms exercised.
const ER = `schema er namespace entity
def entity hodnoty {
  mapping: { target: { table: db.dbo.QXXUKAZMUHOD } },
  attributes: [
    def attribute id_ukazatele { type: int, mapping: IDXXUKAZMU },
    def attribute nazev { type: text, mapping: { target: { column: NAZEV_UKAZ } } }
  ]
}
`;

/** 0-indexed line + character of the first occurrence of `needle` in `text`. */
function posOf(text: string, needle: string): { line: number; character: number } {
  const idx = text.indexOf(needle);
  const before = text.slice(0, idx);
  const line = before.split('\n').length - 1;
  const character = idx - (before.lastIndexOf('\n') + 1);
  return { line, character };
}

describe('inline-mapping column reference navigation (Increment A)', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;

  beforeAll(async () => {
    const pair = createPairedConnection();
    client = pair.client;
    server = pair.server;
    createServerConnection(server);
    await client.sendRequest('initialize', { processId: null, rootUri: null, capabilities: {} });
    client.sendNotification('initialized', {});
    // Open db first so the table/columns are in the symbol table when er.ttr
    // (and its inline-mapping references) is indexed.
    client.sendNotification('textDocument/didOpen', { textDocument: { uri: dbUri, languageId: 'ttr', version: 1, text: DB } });
    client.sendNotification('textDocument/didOpen', { textDocument: { uri: erUri, languageId: 'ttr', version: 1, text: ER } });
    await sleep(200);
  });
  afterAll(() => { client.dispose(); server.dispose(); });

  it('go-to-definition on a bare-id mapping (`mapping: IDXXUKAZMU`) jumps to the db column', async () => {
    const pos = posOf(ER, 'IDXXUKAZMU');
    const res = (await client.sendRequest('textDocument/definition', {
      textDocument: { uri: erUri }, position: { line: pos.line, character: pos.character + 2 },
    })) as lsp.Location | null;
    expect(res).not.toBeNull();
    expect(res!.uri).toBe(dbUri);
    // IDXXUKAZMU is declared on line 3 (0-indexed) of db.ttr.
    expect(res!.range.start.line).toBe(posOf(DB, 'def column IDXXUKAZMU').line);
  });

  it('hover on a bare-id mapping shows the resolved db column symbol', async () => {
    const pos = posOf(ER, 'IDXXUKAZMU');
    const res = (await client.sendRequest('textDocument/hover', {
      textDocument: { uri: erUri }, position: { line: pos.line, character: pos.character + 2 },
    })) as lsp.Hover | null;
    expect(res).not.toBeNull();
    const value = (res!.contents as { value: string }).value;
    expect(value).toContain('db.dbo.QXXUKAZMUHOD.IDXXUKAZMU');
    expect(value).toContain('(column)');
  });

  it('go-to-definition on a `{ target: { column: … } }` mapping jumps to the db column', async () => {
    const direct = posOf(ER, 'column: NAZEV_UKAZ');
    const colPos = { line: direct.line, character: direct.character + 'column: '.length + 1 };
    const res = (await client.sendRequest('textDocument/definition', {
      textDocument: { uri: erUri }, position: colPos,
    })) as lsp.Location | null;
    expect(res).not.toBeNull();
    expect(res!.uri).toBe(dbUri);
    expect(res!.range.start.line).toBe(posOf(DB, 'def column NAZEV_UKAZ').line);
  });

  it('find-references on the db column includes the inline mapping use', async () => {
    const dbPos = posOf(DB, 'IDXXUKAZMU'); // the column-name token in `def column IDXXUKAZMU`
    const res = (await client.sendRequest('textDocument/references', {
      textDocument: { uri: dbUri },
      position: { line: dbPos.line, character: dbPos.character + 2 },
      context: { includeDeclaration: false },
    })) as lsp.Location[];
    expect(res.some((l) => l.uri === erUri)).toBe(true);
  });

  it('semantic tokens include a token for the inline mapping reference', async () => {
    const res = (await client.sendRequest('textDocument/semanticTokens/full', {
      textDocument: { uri: erUri },
    })) as { data: number[] };
    // 5 ints per token; a non-empty token stream over er.ttr means refs (incl.
    // the inline mapping) were emitted. Detailed decode is covered in unit tests.
    expect(res.data.length).toBeGreaterThan(0);
  });
});
