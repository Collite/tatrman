import { describe, it, expect } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';
import path from 'path';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const miniDir = path.resolve(__dirname, '../../../samples/v1.1-mini');

function pair() {
  const a = new PassThrough({ objectMode: true });
  const b = new PassThrough({ objectMode: true });
  const client = lsp.createConnection(new lsp.StreamMessageReader(a as never), new lsp.StreamMessageWriter(b as never)) as lsp.Connection;
  const server = lsp.createConnection(new lsp.StreamMessageReader(b as never), new lsp.StreamMessageWriter(a as never)) as lsp.Connection;
  client.listen(); server.listen();
  return { client, server };
}
const sleep = (ms: number) => new Promise<void>(r => setTimeout(r, ms));
// Mirror what the Designer opens into the TTR server: every .ttr/.ttrg
// (incl. graphs/*.ttrg), but NOT modeler.toml — non-TTR files are filtered out
// client-side so they are never parsed as TTR (see isModelFile in App.tsx).
function walk(dir: string): string[] {
  const out: string[] = [];
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    if (e.name === '.modeler' || e.name.startsWith('.')) continue;
    const f = join(dir, e.name);
    if (e.isDirectory()) out.push(...walk(f));
    else if (e.name.endsWith('.ttr') || e.name.endsWith('.ttrg')) out.push(f);
  }
  return out;
}

// Mimics the Designer browser flow: rootUri null, modeler/setProjectRoot, then
// virtual file:///v1.1-mini/<rel> URIs. Guards the package-inference root bug.
describe('browser-style (rootUri:null) load of v1.1-mini', () => {
  async function loadErrorCodes(opts: { setRoot: boolean }): Promise<Record<string, number>> {
    const { client, server } = pair();
    createServerConnection(server as never);
    await client.sendRequest('initialize', { processId: null, rootUri: null, capabilities: {} });
    client.sendNotification('initialized', {});
    await sleep(100);

    const errorCodes: Record<string, number> = {};
    client.onNotification('textDocument/publishDiagnostics', (p: lsp.PublishDiagnosticsParams) => {
      for (const d of p.diagnostics) {
        if (d.severity === lsp.DiagnosticSeverity.Error) {
          const code = String(d.code ?? 'unknown');
          errorCodes[code] = (errorCodes[code] ?? 0) + 1;
        }
      }
    });

    if (opts.setRoot) {
      await client.sendRequest('modeler/setProjectRoot', { projectRoot: 'file:///v1.1-mini' });
    }
    for (const f of walk(miniDir)) {
      const rel = path.relative(miniDir, f).split(path.sep).join('/');
      client.sendNotification('textDocument/didOpen', {
        textDocument: { uri: `file:///v1.1-mini/${rel}`, languageId: 'ttr', version: 1, text: readFileSync(f, 'utf-8') },
      });
    }
    await sleep(800);
    client.dispose(); server.dispose();
    return errorCodes;
  }

  it('without setProjectRoot, package/dir checks are skipped — no spurious mismatch errors (PD1)', async () => {
    // Before PD1, an unknown project root caused packages to be mis-inferred
    // from the absolute path, producing false package-declaration-mismatch
    // errors (this test guarded that bug). PD1 makes derivation root-aware: with
    // no known root the directory/declaration comparison is skipped entirely, so
    // the transient pre-setProjectRoot window no longer emits spurious errors.
    const codes = await loadErrorCodes({ setRoot: false });
    expect(codes['ttr/package-declaration-mismatch'] ?? 0).toBe(0);
    expect(codes['ttr/package-prefix-divergence'] ?? 0).toBe(0);
  });

  it('with modeler/setProjectRoot, resolves with zero error diagnostics', async () => {
    const codes = await loadErrorCodes({ setRoot: true });
    expect(codes, `unexpected errors: ${JSON.stringify(codes)}`).toEqual({});
  });

  it('all_er getGraph returns relation edges, and getSymbolDetail resolves package-qualified node qnames', async () => {
    const { client, server } = pair();
    createServerConnection(server as never);
    await client.sendRequest('initialize', { processId: null, rootUri: null, capabilities: {} });
    client.sendNotification('initialized', {});
    await client.sendRequest('modeler/setProjectRoot', { projectRoot: 'file:///v1.1-mini' });
    for (const f of walk(miniDir)) {
      const rel = path.relative(miniDir, f).split(path.sep).join('/');
      client.sendNotification('textDocument/didOpen', {
        textDocument: { uri: `file:///v1.1-mini/${rel}`, languageId: 'ttr', version: 1, text: readFileSync(f, 'utf-8') },
      });
    }
    await sleep(800);

    // Issue 1: edges between package-qualified entities must resolve (was 0).
    const g = await client.sendRequest('modeler/getGraph', { uri: 'file:///v1.1-mini/graphs/all_er.ttrg' }) as { nodes: { qname: string }[]; edges: { fromNode: string; toNode: string }[] };
    expect(g.edges.length).toBeGreaterThan(0);
    // Every edge endpoint must reference a node id present in the graph.
    const nodeIds = new Set(g.nodes.map((n) => n.qname));
    for (const e of g.edges) {
      expect(nodeIds.has(e.fromNode), `dangling fromNode ${e.fromNode}`).toBe(true);
      expect(nodeIds.has(e.toNode), `dangling toNode ${e.toNode}`).toBe(true);
    }

    // Issue 3: getSymbolDetail keyed by the package-qualified node qname resolves,
    // and reports that same canonical qname back (so the UI can look it up).
    const q = g.nodes[0].qname;
    const detail = await client.sendRequest('modeler/getSymbolDetail', { qname: q }) as { qname: string; kind: string } | null;
    expect(detail, `getSymbolDetail(${q}) returned null`).not.toBeNull();
    expect(detail!.qname).toBe(q);

    client.dispose(); server.dispose();
  });
});
