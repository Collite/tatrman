import { describe, it, expect } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '../server.js';

// End-to-end guard for the completion-config wiring (review-055 F1 / review-056 F3):
// the server must read modeler.completion.autoImport from the client via
// workspace/configuration and honour it. The config-completion.test.ts unit
// tests only cover the cache helper in isolation; this drives a real completion.

function pair(): { client: lsp.Connection; server: lsp.Connection } {
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

const PRODUKT = `package billing.products\n\nschema er namespace entity\n\ndef entity produkt {\n  description: "Produkt"\n}`;
const INVOICING = `package billing.invoicing\n\nschema er namespace entity\n\ndef relation artikl_produkt {\n  from: billing.products.er.entity.produkt\n}`;

/** Boots a server, has the client answer workspace/configuration for
 *  modeler.completion.autoImport with `autoImportSetting`, opens a cross-package
 *  reference scenario, and returns how many completion items carry an
 *  auto-import edit (plus how many config requests the server issued). */
async function completeWithAutoImport(autoImportSetting: boolean): Promise<{ configRequests: number; withImports: number }> {
  const { client, server } = pair();
  createServerConnection(server);

  let configRequests = 0;
  client.onRequest('workspace/configuration', (p: { items: { section?: string }[] }) => {
    configRequests++;
    return p.items.map((i) => (i.section === 'modeler.completion.autoImport' ? autoImportSetting : undefined));
  });

  await client.sendRequest('initialize', {
    processId: null,
    rootUri: null,
    capabilities: { workspace: { configuration: true } },
  });
  client.sendNotification('initialized', {});
  await sleep(100); // let onInitialized → loadCompletionConfig resolve

  for (const [uri, text] of [['file:///tmp/produkt.ttrm', PRODUKT], ['file:///tmp/invoicing.ttrm', INVOICING]] as const) {
    client.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'ttr', version: 1, text } });
  }
  await sleep(100);

  const result = await client.sendRequest('textDocument/completion', {
    textDocument: { uri: 'file:///tmp/invoicing.ttrm' },
    position: { line: 4, character: 10 },
    context: { triggerKind: 2, triggerCharacter: '.' },
  }) as { items: Array<{ additionalTextEdits?: unknown[] }> };

  const withImports = result.items.filter((i) => i.additionalTextEdits && i.additionalTextEdits.length > 0).length;
  client.dispose();
  server.dispose();
  return { configRequests, withImports };
}

describe('completion honours modeler.completion.autoImport end-to-end', () => {
  it('autoImport=true: server reads the config and emits an auto-import edit', async () => {
    const { configRequests, withImports } = await completeWithAutoImport(true);
    expect(configRequests, 'server must query workspace/configuration').toBeGreaterThan(0);
    expect(withImports, 'expected at least one item with an auto-import edit').toBeGreaterThan(0);
  });

  it('autoImport=false: the auto-import edit is suppressed', async () => {
    const { configRequests, withImports } = await completeWithAutoImport(false);
    expect(configRequests, 'server must query workspace/configuration').toBeGreaterThan(0);
    expect(withImports, 'autoImport:false must suppress additionalTextEdits').toBe(0);
  });
});
