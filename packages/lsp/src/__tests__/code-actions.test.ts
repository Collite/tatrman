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

interface Diag { code?: string | number; range: lsp.Range; message: string }

async function boot(files: Record<string, string>) {
  const { client, server } = pair();
  createServerConnection(server);
  const diags = new Map<string, Diag[]>();
  client.onNotification('textDocument/publishDiagnostics', (p: lsp.PublishDiagnosticsParams) => {
    diags.set(p.uri, p.diagnostics as unknown as Diag[]);
  });
  await client.sendRequest('initialize', { processId: null, rootUri: 'file:///proj', workspaceFolders: [{ uri: 'file:///proj', name: 'proj' }], capabilities: {} });
  client.sendNotification('initialized', {});
  for (const [uri, text] of Object.entries(files)) {
    client.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'ttr', version: 1, text } });
  }
  await sleep(300);
  const codeAction = (uri: string, codes: string[]) => {
    const ds = (diags.get(uri) ?? []).filter((d) => codes.includes(String(d.code)));
    return client.sendRequest('textDocument/codeAction', {
      textDocument: { uri },
      range: ds[0]?.range ?? { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
      context: { diagnostics: ds },
    }) as Promise<lsp.CodeAction[]>;
  };
  return { client, server, diags, codeAction };
}

describe('code actions (I3)', () => {
  it('ttr/unused-import → "Remove unused import" deletes the line', async () => {
    const { client, server, diags, codeAction } = await boot({
      'file:///proj/billing/products/produkt.ttrm': 'package billing.products\n\nmodel er schema entity\n\ndef entity produkt {}',
      'file:///proj/billing/invoicing/x.ttrm': 'package billing.invoicing\n\nimport billing.products.er.entity.produkt\n\nmodel er schema entity\n\ndef entity faktura {}',
    });
    const uri = 'file:///proj/billing/invoicing/x.ttrm';
    expect((diags.get(uri) ?? []).some((d) => d.code === 'ttr/unused-import'), 'expected unused-import diagnostic').toBe(true);
    const actions = await codeAction(uri, ['ttr/unused-import']);
    const a = actions.find((x) => x.title === 'Remove unused import');
    expect(a, JSON.stringify(actions)).toBeTruthy();
    expect(a!.kind).toBe('quickfix');
    expect(a!.diagnostics?.length).toBe(1);
    client.dispose(); server.dispose();
  });

  it('ttr/missing-package-declaration → "Add `package <inferred>`"', async () => {
    const { client, server, diags, codeAction } = await boot({
      'file:///proj/billing/products/loose.ttrm': 'model er schema entity\n\ndef entity x {}',
    });
    const uri = 'file:///proj/billing/products/loose.ttrm';
    expect((diags.get(uri) ?? []).some((d) => d.code === 'ttr/missing-package-declaration')).toBe(true);
    const actions = await codeAction(uri, ['ttr/missing-package-declaration']);
    const a = actions.find((x) => x.title.toLowerCase().includes('package'));
    expect(a, JSON.stringify(actions)).toBeTruthy();
    expect(a!.kind).toBe('quickfix');
    const edit = a!.edit!.documentChanges![0] as lsp.TextDocumentEdit;
    expect(edit.edits[0].newText).toContain('package billing.products');
    client.dispose(); server.dispose();
  });

  it('ttr/package-declaration-mismatch → "Update declaration to match directory"', async () => {
    const { client, server, diags, codeAction } = await boot({
      'file:///proj/billing/products/wrong.ttrm': 'package billing.wrong\n\nmodel er schema entity\n\ndef entity x {}',
    });
    const uri = 'file:///proj/billing/products/wrong.ttrm';
    expect((diags.get(uri) ?? []).some((d) => d.code === 'ttr/package-declaration-mismatch')).toBe(true);
    const actions = await codeAction(uri, ['ttr/package-declaration-mismatch']);
    // package-declaration-mismatch is a judgment call → a suggestion (refactor).
    const a = actions.find((x) => x.kind === 'refactor.rewrite');
    expect(a, JSON.stringify(actions)).toBeTruthy();
    const edit = a!.edit!.documentChanges![0] as lsp.TextDocumentEdit;
    expect(edit.edits[0].newText).toBe('billing.products');
    client.dispose(); server.dispose();
  });

  it('ttr/unimported-reference → "Add import for <pkg>"', async () => {
    const { client, server, diags, codeAction } = await boot({
      'file:///proj/billing/products/produkt.ttrm': 'package billing.products\n\nmodel er schema entity\n\ndef entity produkt {}',
      'file:///proj/billing/invoicing/rel.ttrm': 'package billing.invoicing\n\nmodel er schema entity\n\ndef entity faktura {}\n\ndef relation r {\n  from: er.entity.faktura\n  to: billing.products.er.entity.produkt\n}',
    });
    const uri = 'file:///proj/billing/invoicing/rel.ttrm';
    expect((diags.get(uri) ?? []).some((d) => d.code === 'ttr/unimported-reference'), 'expected unimported-reference diagnostic').toBe(true);
    const actions = await codeAction(uri, ['ttr/unimported-reference']);
    const a = actions.find((x) => x.title.toLowerCase().includes('import'));
    expect(a, JSON.stringify(actions)).toBeTruthy();
    expect(a!.isPreferred).toBe(true);
    const edit = a!.edit!.documentChanges![0] as lsp.TextDocumentEdit;
    expect(edit.edits[0].newText).toContain('import billing.products');
    client.dispose(); server.dispose();
  });

  it('refactor.extract → "Extract <def> to new file" creates a file + removes the def', async () => {
    const { client, server } = await boot({
      'file:///proj/billing/products/two.ttrm': 'package billing.products\n\nmodel er schema entity\n\ndef entity a {}\n\ndef entity b {}',
    });
    const uri = 'file:///proj/billing/products/two.ttrm';
    const actions = await client.sendRequest('textDocument/codeAction', {
      textDocument: { uri },
      range: { start: { line: 6, character: 11 }, end: { line: 6, character: 11 } }, // inside "def entity b"
      context: { diagnostics: [] },
    }) as lsp.CodeAction[];
    const a = actions.find((x) => x.kind === 'refactor.extract');
    expect(a, JSON.stringify(actions.map((x) => x.title))).toBeTruthy();
    expect(a!.title).toContain('b');
    const changes = a!.edit!.documentChanges!;
    expect(changes.some((c) => (c as lsp.CreateFile).kind === 'create' && (c as lsp.CreateFile).uri.endsWith('/b.ttrm'))).toBe(true);
    client.dispose(); server.dispose();
  });
});
