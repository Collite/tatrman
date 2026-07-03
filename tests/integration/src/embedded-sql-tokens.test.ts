import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';

/**
 * embedded-sql 2.4 — the LSP merges embedded-SQL semantic tokens into the
 * document response, positioned via the §8 source map. Legend indices (see the
 * server's initialize): keyword=7, string=4, number=5, comment=6, variable=8,
 * operator=13, parameter=14. Bare identifiers (table/column) are NOT classified
 * until Phase 3 (`class`/`property`), so Phase 2 highlights keywords, literals,
 * comments, operators, variables, and `{param}` placeholders.
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

interface Tok {
  line: number;
  char: number;
  len: number;
  type: number;
  mod: number;
}
function decode(data: number[]): Tok[] {
  const out: Tok[] = [];
  let line = 0;
  let char = 0;
  for (let i = 0; i < data.length; i += 5) {
    const dl = data[i]!;
    const dc = data[i + 1]!;
    line += dl;
    char = dl === 0 ? char + dc : dc;
    out.push({ line, char, len: data[i + 2]!, type: data[i + 3]!, mod: data[i + 4]! });
  }
  return out;
}

const KEYWORD = 7;
const COMMENT = 6;
const NUMBER = 5;
const OPERATOR = 13;
const PARAMETER = 14;

describe('embedded-SQL semantic tokens (2.4)', () => {
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

  async function tokensFor(uri: string, text: string): Promise<Tok[]> {
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text },
    });
    await sleep(80);
    const res = (await client.sendRequest('textDocument/semanticTokens/full', {
      textDocument: { uri },
    })) as { data: number[] };
    return decode(res.data);
  }

  it('maps a SQL keyword to its file position (§8 source map)', async () => {
    // body indented 2 → file line 4 (0-based), SELECT at col 2.
    const text =
      'model query schema query\n\ndef query q {\n  sourceText: """sql\n  SELECT 1 FROM t -- c\n  """\n}\n';
    const toks = await tokensFor('file:///sql-kw.ttrm', text);
    expect(toks.some((t) => t.type === KEYWORD && t.line === 4 && t.char === 2 && t.len === 6)).toBe(true);
    // `1` → number, `FROM` → keyword, `-- c` → comment, all on file line 4.
    expect(toks.some((t) => t.type === NUMBER && t.line === 4)).toBe(true);
    expect(toks.filter((t) => t.type === KEYWORD && t.line === 4).length).toBeGreaterThanOrEqual(2);
    expect(toks.some((t) => t.type === COMMENT && t.line === 4)).toBe(true);
  });

  it('overlays a {param} placeholder as a parameter token over the whole span', async () => {
    const text =
      'model query schema query\n\ndef query q {\n  sourceText: """sql\nSELECT * FROM t WHERE id = {nazev}\n"""\n}\n';
    const toks = await tokensFor('file:///sql-param.ttrm', text);
    // value line 1 → file line 4 (0-based), indentWidth 0. `{nazev}` starts at
    // col 27 (S=0 … `= ` … `{`=27) and is 7 chars wide.
    const param = toks.find((t) => t.type === PARAMETER);
    expect(param).toBeDefined();
    expect(param!.line).toBe(4);
    expect(param!.char).toBe(27);
    expect(param!.len).toBe(7);
    // No operator/keyword token should overlap inside the placeholder span.
    expect(toks.some((t) => t.line === 4 && t.char > 27 && t.char < 34 && t.type !== PARAMETER)).toBe(false);
  });

  it('still highlights keywords in malformed SQL (lexer-first; no throw)', async () => {
    const text =
      'model query schema query\n\ndef query q {\n  sourceText: """sql\nSELECT FROM WHERE\n"""\n}\n';
    const toks = await tokensFor('file:///sql-broken.ttrm', text);
    // Three keyword tokens on the body line despite being un-parseable.
    expect(toks.filter((t) => t.type === KEYWORD && t.line === 4).length).toBe(3);
  });

  it('emits no SQL tokens for an untagged triple-string or a non-SQL block', async () => {
    const text =
      'model query schema query\n\ndef query q {\n  description: """\nSELECT not sql\n"""\n  sourceText: """transform\nrelation.filter(x)\n"""\n}\n';
    const toks = await tokensFor('file:///sql-none.ttrm', text);
    expect(toks.some((t) => t.type === KEYWORD)).toBe(false);
    expect(toks.some((t) => t.type === PARAMETER)).toBe(false);
    expect(toks.some((t) => t.type === OPERATOR)).toBe(false);
    expect(toks.some((t) => t.type === COMMENT)).toBe(false);
  });
});
