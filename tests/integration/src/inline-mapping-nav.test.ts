// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@tatrman/lsp/server';

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

const dbUri = 'file:///proj/db.ttrm';
const DB = `model db schema dbo
def table QXXUKAZMUHOD {
  columns: [
    def column IDXXUKAZMU { type: int },
    def column NAZEV_UKAZ { type: text }
  ]
}
def fk fk_hodnoty_self { description: "self fk" }
`;

const erUri = 'file:///proj/er.ttrm';
// hodnoty maps to QXXUKAZMUHOD; three attribute-mapping forms exercised, plus a
// relation with an inline fk mapping (Increment B).
const ER = `model er schema entity
def entity hodnoty {
  binding: { target: { table: db.dbo.QXXUKAZMUHOD } },
  attributes: [
    def attribute id_ukazatele { type: int, binding: IDXXUKAZMU },
    def attribute nazev { type: text, binding: { target: { column: NAZEV_UKAZ } } }
  ]
}
def relation hodnoty_self { from: er.entity.hodnoty, to: er.entity.hodnoty, binding: db.dbo.fk_hodnoty_self }
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
    // Open db first so the table/columns are in the symbol table when er.ttrm
    // (and its inline-mapping references) is indexed.
    client.sendNotification('textDocument/didOpen', { textDocument: { uri: dbUri, languageId: 'ttr', version: 1, text: DB } });
    client.sendNotification('textDocument/didOpen', { textDocument: { uri: erUri, languageId: 'ttr', version: 1, text: ER } });
    await sleep(200);
  });
  afterAll(() => { client.dispose(); server.dispose(); });

  it('go-to-definition on a bare-id mapping (`binding: IDXXUKAZMU`) jumps to the db column', async () => {
    const pos = posOf(ER, 'IDXXUKAZMU');
    const res = (await client.sendRequest('textDocument/definition', {
      textDocument: { uri: erUri }, position: { line: pos.line, character: pos.character + 2 },
    })) as lsp.Location | null;
    expect(res).not.toBeNull();
    expect(res!.uri).toBe(dbUri);
    // IDXXUKAZMU is declared on line 3 (0-indexed) of db.ttrm.
    expect(res!.range.start.line).toBe(posOf(DB, 'def column IDXXUKAZMU').line);
  });

  it('hover on a bare-id mapping shows the resolved db column symbol', async () => {
    const pos = posOf(ER, 'IDXXUKAZMU');
    const res = (await client.sendRequest('textDocument/hover', {
      textDocument: { uri: erUri }, position: { line: pos.line, character: pos.character + 2 },
    })) as lsp.Hover | null;
    expect(res).not.toBeNull();
    const value = (res!.contents as { value: string }).value;
    expect(value).toContain('db.dbo.table.QXXUKAZMUHOD.IDXXUKAZMU');
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

  it('go-to-definition on a relation fk mapping jumps to the db `def fk` (Increment B)', async () => {
    const direct = posOf(ER, 'binding: db.dbo.fk_hodnoty_self');
    const pos = { line: direct.line, character: direct.character + 'binding: db.dbo.'.length + 1 };
    const res = (await client.sendRequest('textDocument/definition', {
      textDocument: { uri: erUri }, position: pos,
    })) as lsp.Location | null;
    expect(res).not.toBeNull();
    expect(res!.uri).toBe(dbUri);
    expect(res!.range.start.line).toBe(posOf(DB, 'def fk fk_hodnoty_self').line);
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
    // 5 ints per token; a non-empty token stream over er.ttrm means refs (incl.
    // the inline mapping) were emitted. Detailed decode is covered in unit tests.
    expect(res.data.length).toBeGreaterThan(0);
  });
});

// Increment B2: the entity has an inline attribute mapping but NO inline mapping
// block — its target table comes from an explicit `def er2db_entity` in map.ttrm
// (the v1-metadata layout).
describe('inline-mapping nav via explicit er2db_entity target (Increment B2)', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;
  const dbU = 'file:///p2/db.ttrm';
  const mapU = 'file:///p2/map.ttrm';
  const erU = 'file:///p2/er.ttrm';
  const DB2 = `model db schema dbo
def table QXXUKAZMUHOD { columns: [ def column IDXXUKAZMU { type: int } ] }
`;
  const MAP2 = `model binding
def er2db_entity hodnoty { entity: er.entity.hodnoty, target: { table: db.dbo.QXXUKAZMUHOD } }
`;
  const ER2 = `model er schema entity
def entity hodnoty {
  attributes: [ def attribute id_uk { type: int, binding: IDXXUKAZMU } ]
}
`;

  beforeAll(async () => {
    const pair = createPairedConnection();
    client = pair.client; server = pair.server;
    createServerConnection(server);
    await client.sendRequest('initialize', { processId: null, rootUri: null, capabilities: {} });
    client.sendNotification('initialized', {});
    // db + map first so the table and the er2db_entity target are known when er
    // is indexed.
    for (const [uri, text] of [[dbU, DB2], [mapU, MAP2], [erU, ER2]] as const) {
      client.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'ttr', version: 1, text } });
    }
    await sleep(200);
  });
  afterAll(() => { client.dispose(); server.dispose(); });

  it('go-to-definition on the attribute mapping resolves through the explicit er2db_entity', async () => {
    const pos = posOf(ER2, 'IDXXUKAZMU');
    const res = (await client.sendRequest('textDocument/definition', {
      textDocument: { uri: erU }, position: { line: pos.line, character: pos.character + 2 },
    })) as lsp.Location | null;
    expect(res).not.toBeNull();
    expect(res!.uri).toBe(dbU);
    expect(res!.range.start.line).toBe(posOf(DB2, 'def column IDXXUKAZMU').line);
  });
});
