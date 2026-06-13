import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';
import { mkdtempSync, writeFileSync, rmSync, readFileSync, existsSync, unlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { loadProject } from '@modeler/semantics/node-only';

function createPairedConnection(): { client: lsp.Connection; server: lsp.Connection } {
  const clientTransport = new PassThrough({ objectMode: true });
  const serverTransport = new PassThrough({ objectMode: true });
  const clientReader = new lsp.StreamMessageReader(clientTransport as unknown as NodeJS.ReadableStream);
  const clientWriter = new lsp.StreamMessageWriter(serverTransport as unknown as NodeJS.WritableStream);
  const client = lsp.createConnection(clientReader, clientWriter) as lsp.Connection;
  const serverReader = new lsp.StreamMessageReader(serverTransport as unknown as NodeJS.ReadableStream);
  const serverWriter = new lsp.StreamMessageWriter(clientTransport as unknown as NodeJS.WritableStream);
  const server = lsp.createConnection(serverReader, serverWriter) as lsp.Connection;
  client.listen();
  server.listen();
  return { client, server };
}

/** Resolve on the first publish for `uri` whose diagnostics satisfy `pred`. */
function diagnosticsMatching(
  client: lsp.Connection,
  uri: string,
  pred: (diags: lsp.Diagnostic[]) => boolean
): Promise<lsp.Diagnostic[]> {
  return new Promise((resolve) => {
    const off = client.onNotification('textDocument/publishDiagnostics', (params) => {
      const p = params as lsp.PublishDiagnosticsParams;
      if (p.uri === uri && pred(p.diagnostics)) {
        off.dispose();
        resolve(p.diagnostics);
      }
    });
  });
}

const PRODUKT = `package billing.products
schema er namespace entity
def entity produkt {
  attributes: [def attribute id_produktu { type: int, isKey: true }]
}
`;

const PODPRODUKT = `package billing.products
schema er namespace entity
def entity podprodukt {
  attributes: [def attribute id_podproduktu { type: int, isKey: true }]
}
`;

// References two entities that live in *other* files of the same package. These
// resolve only if the project is loaded as a whole — opening this file alone is
// not enough unless the server seeds the symbol table from disk.
const RELATIONS = `package billing.products
schema er namespace entity
def relation podprodukt_produkt { from: er.entity.podprodukt, to: er.entity.produkt }
`;

function unresolved(diags: lsp.Diagnostic[]): lsp.Diagnostic[] {
  return diags.filter((d) => d.code === 'ttr/unresolved-reference');
}

describe('whole-project symbol table — resolution across unopened files', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;
  let root: string;
  let relUri: string;
  let produktPath: string;

  beforeAll(async () => {
    root = mkdtempSync(join(tmpdir(), 'modeler-project-wide-'));
    produktPath = join(root, 'produkt.ttr');
    writeFileSync(produktPath, PRODUKT);
    writeFileSync(join(root, 'podprodukt.ttr'), PODPRODUKT);
    const relPath = join(root, 'relations.ttr');
    writeFileSync(relPath, RELATIONS);
    relUri = pathToFileURL(relPath).href;

    const pair = createPairedConnection();
    client = pair.client;
    server = pair.server;
    createServerConnection(server, {
      async readConfigFile(p: string) {
        return existsSync(p) ? readFileSync(p, 'utf-8') : undefined;
      },
      async scanProjectFiles(r: string) {
        const project = await loadProject(r);
        return project.ttrFiles.map((f) => ({ uri: pathToFileURL(f).href, text: readFileSync(f, 'utf-8') }));
      },
      async readProjectFile(uri: string) {
        if (!uri.startsWith('file://')) return undefined;
        const p = fileURLToPath(uri);
        return existsSync(p) ? readFileSync(p, 'utf-8') : undefined;
      },
    });

    await client.sendRequest('initialize', {
      processId: null,
      rootUri: pathToFileURL(root).href,
      capabilities: {},
    });
    client.sendNotification('initialized', {});
  });

  afterAll(() => {
    client.dispose();
    server.dispose();
    rmSync(root, { recursive: true, force: true });
  });

  it('resolves references into sibling files that were never opened', async () => {
    // Open ONLY the relation file. `er.entity.produkt` / `er.entity.podprodukt`
    // live in files we never open — they must resolve from the disk-seeded table.
    const diagsPromise = diagnosticsMatching(client, relUri, () => true);
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: relUri, languageId: 'ttr', version: 1, text: RELATIONS },
    });
    const diags = await diagsPromise;
    expect(
      unresolved(diags),
      `unexpected unresolved refs: ${JSON.stringify(unresolved(diags))}`,
    ).toHaveLength(0);
  });

  it('re-resolves when a referenced file is deleted on disk (watched-file sync)', async () => {
    // Delete produkt.ttr on disk and notify the server as the client watcher
    // would. `er.entity.produkt` should now go unresolved in the open relation
    // file, while `er.entity.podprodukt` still resolves.
    // Match on the *quoted* referenced symbol, not a substring: the unresolved
    // message embeds scoped "tried" candidates (e.g. `...podprodukt_produkt...`,
    // the relation's own name) that would trip a naive `includes` check.
    const quotedRef = (d: lsp.Diagnostic): string | undefined =>
      /Unresolved reference: '([^']+)'/.exec(d.message)?.[1];

    unlinkSync(produktPath);
    const diagsPromise = diagnosticsMatching(client, relUri, (diags) =>
      unresolved(diags).some((d) => quotedRef(d) === 'er.entity.produkt'),
    );
    client.sendNotification('workspace/didChangeWatchedFiles', {
      changes: [{ uri: pathToFileURL(produktPath).href, type: lsp.FileChangeType.Deleted }],
    });
    const diags = await diagsPromise;
    const refs = unresolved(diags).map(quotedRef);
    expect(refs).toContain('er.entity.produkt'); // the deleted entity
    expect(refs).not.toContain('er.entity.podprodukt'); // sibling file still loaded
  });
});
