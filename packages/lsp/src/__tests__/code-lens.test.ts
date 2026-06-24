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

describe('code lens (I4)', () => {
  it('emits "N references" per def and "N files in package" on the package decl', async () => {
    const { client, server } = pair();
    createServerConnection(server);
    await client.sendRequest('initialize', { processId: null, rootUri: 'file:///proj', workspaceFolders: [{ uri: 'file:///proj', name: 'proj' }], capabilities: {} });
    client.sendNotification('initialized', {});
    const files = {
      'file:///proj/billing/products/produkt.ttrm': 'package billing.products\n\nschema er namespace entity\n\ndef entity produkt {}',
      'file:///proj/billing/products/podprodukt.ttrm': 'package billing.products\n\nschema er namespace entity\n\ndef entity podprodukt {}\n\ndef relation r {\n  from: er.entity.podprodukt\n  to: er.entity.produkt\n}',
    };
    for (const [uri, text] of Object.entries(files)) {
      client.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'ttr', version: 1, text } });
    }
    await sleep(300);

    const lenses = await client.sendRequest('textDocument/codeLens', {
      textDocument: { uri: 'file:///proj/billing/products/produkt.ttrm' },
    }) as lsp.CodeLens[];

    const titles = lenses.map((l) => l.command?.title);
    // produkt is referenced once (by relation r in podprodukt.ttrm).
    expect(titles).toContain('1 reference');
    // package billing.products spans 2 files.
    expect(titles).toContain('2 files in package');

    const refLens = lenses.find((l) => l.command?.title === '1 reference');
    expect(refLens!.command!.command).toBe('modeler.showReferences');
    expect(refLens!.command!.arguments).toEqual(['billing.products.er.entity.produkt']);

    const pkgLens = lenses.find((l) => l.command?.title === '2 files in package');
    expect(pkgLens!.command!.command).toBe('modeler.listPackageFiles');
    expect(pkgLens!.command!.arguments).toEqual(['billing.products']);

    client.dispose(); server.dispose();
  });
});
