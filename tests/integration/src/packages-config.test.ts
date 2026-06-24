import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';
import { DiagnosticCode } from '@modeler/parser';
import { findProjectRoot, loadProject } from '@modeler/semantics/node-only';
import { mkdtempSync, writeFileSync, mkdirSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { pathToFileURL } from 'url';

// PD1 — boot the LSP harness over a fixture project whose modeler.toml sets
// [packages] root = "cz.dfpartner" and layout = "strict", and assert (a)
// getProjectInfo reports canonical, root-prefixed package names, and (b) a
// declaration that mismatches its directory surfaces an Error under "strict".

function createPairedConnection(): { client: lsp.Connection; server: lsp.Connection } {
  const clientTransport = new PassThrough({ objectMode: true });
  const serverTransport = new PassThrough({ objectMode: true });
  const client = lsp.createConnection(
    new lsp.StreamMessageReader(clientTransport as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(serverTransport as unknown as NodeJS.WritableStream)
  ) as lsp.Connection;
  const server = lsp.createConnection(
    new lsp.StreamMessageReader(serverTransport as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(clientTransport as unknown as NodeJS.WritableStream)
  ) as lsp.Connection;
  client.listen();
  server.listen();
  return { client, server };
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

describe('PD1 — [packages] config end to end', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;
  let projectRoot: string;
  let skladUri: string;
  let obchodUri: string;

  beforeAll(async () => {
    projectRoot = mkdtempSync(join(tmpdir(), 'modeler-pd1-'));
    writeFileSync(
      join(projectRoot, 'modeler.toml'),
      '[packages]\nroot = "cz.dfpartner"\nlayout = "strict"\n',
      'utf-8'
    );
    // Undeclared nested file → effective package is root-prefixed.
    mkdirSync(join(projectRoot, 'sklad'));
    writeFileSync(
      join(projectRoot, 'sklad', 'er.ttrm'),
      'schema er namespace entity\ndef entity polozka { attributes: [def attribute id { type: int }] }\n',
      'utf-8'
    );
    // Declared package mismatches its directory (leaf-only) → Error under strict.
    mkdirSync(join(projectRoot, 'obchod'));
    writeFileSync(
      join(projectRoot, 'obchod', 'er.ttrm'),
      'package renamed\nschema er namespace entity\ndef entity faktura { attributes: [def attribute id { type: int }] }\n',
      'utf-8'
    );
    skladUri = pathToFileURL(join(projectRoot, 'sklad', 'er.ttrm')).href;
    obchodUri = pathToFileURL(join(projectRoot, 'obchod', 'er.ttrm')).href;

    const pair = createPairedConnection();
    client = pair.client;
    server = pair.server;
    createServerConnection(server, {
      async loadManifest(rootUri: string) {
        const docPath = rootUri.startsWith('file://') ? new URL(rootUri).pathname : rootUri;
        const root = await findProjectRoot(docPath, docPath);
        return (await loadProject(root)).manifest;
      },
      async scanProjectFiles(root: string) {
        const { readFile } = await import('fs/promises');
        const project = await loadProject(root);
        const out: Array<{ uri: string; text: string }> = [];
        for (const file of project.ttrFiles) {
          out.push({ uri: pathToFileURL(file).href, text: await readFile(file, 'utf-8') });
        }
        return out;
      },
      async readConfigFile() {
        return undefined;
      },
    });

    await client.sendRequest('initialize', {
      processId: null,
      rootUri: pathToFileURL(projectRoot).href,
      capabilities: {},
    });
    client.sendNotification('initialized', {});
    await sleep(50);
  });

  afterAll(async () => {
    // Let any in-flight publishDiagnostics settle before tearing the pipe down,
    // otherwise the async publish races the dispose ("Connection is disposed").
    await sleep(50);
    client.dispose();
    server.dispose();
  });

  it('getProjectInfo reports canonical, root-prefixed package names', async () => {
    const info = (await client.sendRequest('modeler/getProjectInfo', {
      textDocument: { uri: skladUri },
    })) as { packages: Array<{ name: string }> };

    const names = info.packages.map((p) => p.name);
    // Undeclared nested file derives its package with the configured root prefix.
    expect(names).toContain('cz.dfpartner.sklad');
  });

  it('a declaration mismatching its directory surfaces an Error under strict', async () => {
    const diags = await new Promise<lsp.PublishDiagnosticsParams>((resolve) => {
      const off = client.onNotification('textDocument/publishDiagnostics', (params) => {
        const p = params as lsp.PublishDiagnosticsParams;
        if (p.uri === obchodUri) {
          off.dispose();
          resolve(p);
        }
      });
      client.sendNotification('textDocument/didOpen', {
        textDocument: {
          uri: obchodUri,
          languageId: 'ttr',
          version: 1,
          text: 'package renamed\nschema er namespace entity\ndef entity faktura { attributes: [def attribute id { type: int }] }\n',
        },
      });
    });

    const mismatch = diags.diagnostics.find(
      (d) => d.code === DiagnosticCode.PackageDeclarationMismatch
    );
    expect(mismatch).toBeDefined();
    expect(mismatch!.severity).toBe(lsp.DiagnosticSeverity.Error);
  });
});
