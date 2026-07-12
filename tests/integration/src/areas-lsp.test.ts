// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@tatrman/lsp/server';
import { findProjectRoot, loadProject } from '@tatrman/semantics/node-only';
import { mkdtempSync, writeFileSync, mkdirSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { pathToFileURL } from 'url';

// PD3 — smoke test: a `.ttrd` opened in the LSP resolves its members. go-to-def
// on a `packages:` member jumps to the package's files; on an `entities:` member
// to the entity def. getProjectInfo lists the domain with its closure size.

function createPairedConnection(): { client: lsp.Connection; server: lsp.Connection } {
  const ct = new PassThrough({ objectMode: true });
  const st = new PassThrough({ objectMode: true });
  const client = lsp.createConnection(
    new lsp.StreamMessageReader(ct as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(st as unknown as NodeJS.WritableStream)
  ) as lsp.Connection;
  const server = lsp.createConnection(
    new lsp.StreamMessageReader(st as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(ct as unknown as NodeJS.WritableStream)
  ) as lsp.Connection;
  client.listen();
  server.listen();
  return { client, server };
}
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const entityFile = (pkg: string, e: string) =>
  `package ${pkg}\nmodel er schema entity\ndef entity ${e} { attributes: [def attribute id { type: int }] }\n`;

describe('v3.0 — def area subject areas in the LSP', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;
  let root: string;
  let ttrdUri: string;
  let aUri: string;
  let abUri: string;

  beforeAll(async () => {
    root = mkdtempSync(join(tmpdir(), 'modeler-pd3-'));
    writeFileSync(join(root, 'modeler.toml'), '[project]\nname = "pd3"\n', 'utf-8');
    mkdirSync(join(root, 'a'));
    mkdirSync(join(root, 'a', 'b'));
    writeFileSync(join(root, 'a', 'er.ttrm'), entityFile('a', 'artikl'), 'utf-8');
    writeFileSync(join(root, 'a', 'b', 'er.ttrm'), entityFile('a.b', 'sub'), 'utf-8');
    const ttrd = 'def area core {\n  packages: [a],\n  entities: [a.b.er.entity.sub]\n}\n';
    writeFileSync(join(root, 'core.ttrm'), ttrd, 'utf-8');

    aUri = pathToFileURL(join(root, 'a', 'er.ttrm')).href;
    abUri = pathToFileURL(join(root, 'a', 'b', 'er.ttrm')).href;
    ttrdUri = pathToFileURL(join(root, 'core.ttrm')).href;

    const pair = createPairedConnection();
    client = pair.client;
    server = pair.server;
    createServerConnection(server, {
      async loadManifest(rootUri: string) {
        const p = rootUri.startsWith('file://') ? new URL(rootUri).pathname : rootUri;
        return (await loadProject(await findProjectRoot(p, p))).manifest;
      },
      async scanProjectFiles(r: string) {
        const { readFile } = await import('fs/promises');
        const project = await loadProject(r);
        return Promise.all(
          project.ttrFiles.map(async (f) => ({ uri: pathToFileURL(f).href, text: await readFile(f, 'utf-8') }))
        );
      },
    });

    await client.sendRequest('initialize', { processId: null, rootUri: pathToFileURL(root).href, capabilities: {} });
    client.sendNotification('initialized', {});
    await sleep(50);
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: ttrdUri, languageId: 'ttr', version: 1, text: 'def area core {\n  packages: [a],\n  entities: [a.b.er.entity.sub]\n}\n' },
    });
    await sleep(80);
  });

  afterAll(async () => {
    await sleep(50);
    client.dispose();
    server.dispose();
  });

  it('go-to-def on a packages: member jumps to the package files (recursive closure a + a.b)', async () => {
    // Cursor on `a` in `  packages: [a],` (line index 1, char 13).
    const res = (await client.sendRequest('textDocument/definition', {
      textDocument: { uri: ttrdUri },
      position: { line: 1, character: 13 },
    })) as lsp.Location | lsp.Location[] | null;

    const locs = Array.isArray(res) ? res : res ? [res] : [];
    const uris = locs.map((l) => l.uri);
    expect(uris).toContain(aUri);
    expect(uris).toContain(abUri); // recursive: a.b is included
  });

  it('go-to-def on an entities: member jumps to the entity def', async () => {
    // Cursor inside `a.b.er.entity.sub` on line index 2.
    const res = (await client.sendRequest('textDocument/definition', {
      textDocument: { uri: ttrdUri },
      position: { line: 2, character: 16 },
    })) as lsp.Location | lsp.Location[] | null;

    const loc = Array.isArray(res) ? res[0] : res;
    expect(loc?.uri).toBe(abUri);
  });

  it('getProjectInfo lists the domain with its recursive closure size', async () => {
    const info = (await client.sendRequest('modeler/getProjectInfo', {
      textDocument: { uri: ttrdUri },
    })) as { areas: Array<{ name: string; packageMemberCount: number; resolvedPackageCount: number }> };

    const core = info.areas.find((d) => d.name === 'core');
    expect(core).toBeDefined();
    expect(core!.packageMemberCount).toBe(1);
    expect(core!.resolvedPackageCount).toBe(2); // a + a.b
  });
});
