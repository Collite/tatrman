// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@tatrman/lsp/server';
import { mkdtempSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { pathToFileURL } from 'url';

// RG-P4.S1.T6 — the lexicon LSP surface. Hover on a lexicon entry shows the
// resolved target; go-to-definition from `for:` jumps to the target def; the
// desugar diagnostics (missing target / wrong-model / duplicate form) publish.
// All of this "falls out" of the reference resolver + lint pipeline — we assert
// the wiring, we do not rebuild it.

function createPairedConnection(): { client: lsp.Connection; server: lsp.Connection } {
  const ct = new PassThrough({ objectMode: true });
  const st = new PassThrough({ objectMode: true });
  const client = lsp.createConnection(
    new lsp.StreamMessageReader(ct as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(st as unknown as NodeJS.WritableStream),
  ) as lsp.Connection;
  const server = lsp.createConnection(
    new lsp.StreamMessageReader(st as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(ct as unknown as NodeJS.WritableStream),
  ) as lsp.Connection;
  client.listen();
  server.listen();
  return { client, server };
}
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

const MD = ['model md', 'def measure net { domain: md.Money, aggregation: sum }', ''].join('\n');
const LEX = [
  'model lexicon',
  'def term trzba { for: md.measure.net, forms: ["tržba", "obrat"] }',
  'def term ghost { for: md.measure.nonexistent, forms: ["x"] }',
  '',
].join('\n');

describe('RG-P4.S1.T6 — lexicon LSP features', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;
  let mdUri: string;
  let lexUri: string;
  const diagnostics = new Map<string, lsp.Diagnostic[]>();
  const lexLineOf = (n: number) => LEX.split('\n')[n];

  beforeAll(async () => {
    const root = mkdtempSync(join(tmpdir(), 'modeler-lex-'));
    writeFileSync(join(root, 'modeler.toml'), '[project]\nname = "lex"\n', 'utf-8');
    writeFileSync(join(root, 'md.ttrm'), MD, 'utf-8');
    writeFileSync(join(root, 'lex.ttrm'), LEX, 'utf-8');
    mdUri = pathToFileURL(join(root, 'md.ttrm')).href;
    lexUri = pathToFileURL(join(root, 'lex.ttrm')).href;

    const pair = createPairedConnection();
    client = pair.client;
    server = pair.server;
    client.onNotification('textDocument/publishDiagnostics', (p: lsp.PublishDiagnosticsParams) => {
      diagnostics.set(p.uri, p.diagnostics);
    });
    createServerConnection(server, {
      async scanProjectFiles() {
        return [
          { uri: mdUri, text: MD },
          { uri: lexUri, text: LEX },
        ];
      },
    });

    await client.sendRequest('initialize', { processId: null, rootUri: pathToFileURL(root).href, capabilities: {} });
    client.sendNotification('initialized', {});
    await sleep(50);
    client.sendNotification('textDocument/didOpen', { textDocument: { uri: lexUri, languageId: 'ttr', version: 1, text: LEX } });
    await sleep(150);
  });

  afterAll(async () => {
    await sleep(30);
    client.dispose();
    server.dispose();
  });

  it('hover on a `for:` target resolves to the measure def', async () => {
    const ch = lexLineOf(1).indexOf('md.measure.net') + 2;
    const res = (await client.sendRequest('textDocument/hover', {
      textDocument: { uri: lexUri },
      position: { line: 1, character: ch },
    })) as lsp.Hover | null;
    const value = res && typeof res.contents === 'object' && 'value' in res.contents ? res.contents.value : '';
    expect(value).toContain('md.measure.net');
  });

  it('go-to-definition from `for:` jumps to the measure def in md.ttrm (line 1)', async () => {
    const ch = lexLineOf(1).indexOf('md.measure.net') + 2;
    const res = (await client.sendRequest('textDocument/definition', {
      textDocument: { uri: lexUri },
      position: { line: 1, character: ch },
    })) as lsp.Location | lsp.Location[] | null;
    const loc = Array.isArray(res) ? res[0] : res;
    expect(loc?.uri).toBe(mdUri);
    expect(loc?.range.start.line).toBe(1);
  });

  it('publishes an unresolved-reference for the dangling `for: md.measure.nonexistent`', () => {
    const d = diagnostics.get(lexUri) ?? [];
    expect(d.some((x) => x.code === 'ttr/unresolved-reference')).toBe(true);
  });
});
