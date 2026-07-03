import { describe, it, expect } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '../server.js';

function pair() {
  const a = new PassThrough({ objectMode: true });
  const b = new PassThrough({ objectMode: true });
  const client = lsp.createConnection(new lsp.StreamMessageReader(a as never), new lsp.StreamMessageWriter(b as never)) as lsp.Connection;
  const server = lsp.createConnection(new lsp.StreamMessageReader(b as never), new lsp.StreamMessageWriter(a as never)) as lsp.Connection;
  client.listen(); server.listen();
  return { client, server };
}
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

// Token type legend indices (see server.ts initialize):
const PACKAGE_NAME = 9, IMPORTED = 10, LOCAL = 11, UNIMPORTED = 12;

function tokenTypes(data: number[]): Set<number> {
  const types = new Set<number>();
  for (let i = 0; i < data.length; i += 5) types.add(data[i + 3]);
  return types;
}

describe('semantic tokens v1.1 (I4)', () => {
  it('tags packageName, localSymbol, importedSymbol, and unimportedReference', async () => {
    const { client, server } = pair();
    createServerConnection(server);
    await client.sendRequest('initialize', { processId: null, rootUri: 'file:///proj', workspaceFolders: [{ uri: 'file:///proj', name: 'proj' }], capabilities: {} });
    client.sendNotification('initialized', {});

    const files: Record<string, string> = {
      'file:///proj/a/x.ttrm': 'package a\n\nmodel er schema entity\n\ndef entity ax {}',
      'file:///proj/b/y.ttrm': 'package b\n\nmodel er schema entity\n\ndef entity by {}',
      'file:///proj/main/m.ttrm': [
        'package main',
        '',
        'import a.er.entity.ax',
        '',
        'model er schema entity',
        '',
        'def entity localE {}',
        '',
        'def relation r {',
        '  from: er.entity.localE',          // same package → localSymbol
        '  to: a.er.entity.ax',              // imported → importedSymbol
        '}',
        '',
        'def relation r2 {',
        '  from: er.entity.localE',
        '  to: b.er.entity.by',              // not imported → unimportedReference
        '}',
      ].join('\n'),
    };
    for (const [uri, text] of Object.entries(files)) {
      client.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'ttr', version: 1, text } });
    }
    await sleep(400);

    const tokens = await client.sendRequest('textDocument/semanticTokens/full', {
      textDocument: { uri: 'file:///proj/main/m.ttrm' },
    }) as lsp.SemanticTokens;

    const types = tokenTypes(tokens.data);
    expect(types.has(PACKAGE_NAME), 'package decl not tagged packageName').toBe(true);
    expect(types.has(LOCAL), 'same-package ref not tagged localSymbol').toBe(true);
    expect(types.has(IMPORTED), 'imported ref not tagged importedSymbol').toBe(true);
    expect(types.has(UNIMPORTED), 'unimported ref not tagged unimportedReference').toBe(true);

    client.dispose(); server.dispose();
  });
});
