import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';
import { parseString } from '@modeler/parser';

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

const PRODUKT_URI = 'file:///proj/billing/products/produkt.ttr';
const INVOICING_URI = 'file:///proj/billing/invoicing/rel.ttr';
const CONSUMER_URI = 'file:///proj/billing/invoicing/consumer.ttr';
const GRAPH_URI = 'file:///proj/graphs/all.ttrg';

const FILES: Record<string, string> = {
  [PRODUKT_URI]: `package billing.products

schema er namespace entity

def entity produkt {
  description: "Produkt"
}`,
  [INVOICING_URI]: `package billing.invoicing

schema er namespace entity

def entity faktura {
  description: "Faktura"
}

def relation faktura_produkt {
  from: er.entity.faktura
  to: billing.products.er.entity.produkt
}`,
  [CONSUMER_URI]: `package billing.invoicing

import billing.products.er.entity.produkt

schema er namespace entity

def entity objednavka {
  description: "Objednavka"
}

def relation objednavka_produkt {
  from: er.entity.objednavka
  to: produkt
}`,
  [GRAPH_URI]: `graph all {
  schema: er,
  objects: [
    billing.invoicing.er.entity.faktura,
    billing.products.er.entity.produkt
  ]
}`,
};

// --- WorkspaceEdit application (offset-based, edits applied per uri, last-first) ---
function posToOffset(content: string, line: number, character: number): number {
  let off = 0, l = 0;
  while (l < line && off < content.length) { if (content[off] === '\n') l++; off++; }
  return off + character;
}
function applyWorkspaceEdit(contents: Record<string, string>, edit: lsp.WorkspaceEdit): Record<string, string> {
  const out = { ...contents };
  const byUri = new Map<string, Array<{ s: number; e: number; t: string }>>();
  for (const dc of edit.documentChanges ?? []) {
    const tde = dc as lsp.TextDocumentEdit;
    if (!('textDocument' in tde)) continue;
    const uri = tde.textDocument.uri;
    const c = out[uri] ?? '';
    const list = byUri.get(uri) ?? [];
    for (const e of tde.edits) {
      list.push({ s: posToOffset(c, e.range.start.line, e.range.start.character), e: posToOffset(c, e.range.end.line, e.range.end.character), t: e.newText });
    }
    byUri.set(uri, list);
  }
  for (const [uri, list] of byUri) {
    let c = out[uri] ?? '';
    for (const ed of list.sort((x, y) => y.s - x.s)) c = c.slice(0, ed.s) + ed.t + c.slice(ed.e);
    out[uri] = c;
  }
  return out;
}

describe('rename (I1) end-to-end via the LSP', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;

  beforeAll(async () => {
    const p = createPairedConnection();
    client = p.client; server = p.server;
    createServerConnection(server);
    await client.sendRequest('initialize', {
      processId: null,
      rootUri: 'file:///proj',
      workspaceFolders: [{ uri: 'file:///proj', name: 'proj' }],
      capabilities: {},
    });
    client.sendNotification('initialized', {});
    for (const [uri, text] of Object.entries(FILES)) {
      client.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'ttr', version: 1, text } });
    }
    await sleep(300);
  });
  afterAll(() => { client.dispose(); server.dispose(); });

  it('prepareRename on the entity name returns its range + placeholder', async () => {
    const res = await client.sendRequest('textDocument/prepareRename', {
      textDocument: { uri: PRODUKT_URI },
      position: { line: 4, character: 13 }, // inside "produkt"
    }) as { range: lsp.Range; placeholder: string } | null;
    expect(res, 'prepareRename returned null for the entity name').not.toBeNull();
    expect(res!.placeholder).toBe('produkt');
  });

  it('renaming the entity updates def + cross-ref + .ttrg — and does NOT rename the package', async () => {
    const edit = await client.sendRequest('textDocument/rename', {
      textDocument: { uri: PRODUKT_URI },
      position: { line: 4, character: 13 },
      newName: 'produkt_v2',
    }) as lsp.WorkspaceEdit;

    const urisTouched = new Set((edit.documentChanges ?? []).map((d) => (d as lsp.TextDocumentEdit).textDocument.uri));
    expect(urisTouched.has(PRODUKT_URI), 'def site not edited').toBe(true);
    expect(urisTouched.has(INVOICING_URI), 'cross-reference not edited').toBe(true);
    expect(urisTouched.has(GRAPH_URI), '.ttrg objects not edited').toBe(true);

    const after = applyWorkspaceEdit(FILES, edit);

    // The entity is renamed, the package declaration is untouched, and every file parses.
    expect(after[PRODUKT_URI]).toContain('def entity produkt_v2');
    expect(after[PRODUKT_URI]).toContain('package billing.products');
    expect(after[INVOICING_URI]).toContain('billing.products.er.entity.produkt_v2');
    expect(after[GRAPH_URI]).toContain('billing.products.er.entity.produkt_v2');
    // Named import follows the rename, and the bare usage it enables updates too.
    expect(after[CONSUMER_URI]).toContain('import billing.products.er.entity.produkt_v2');
    expect(after[CONSUMER_URI]).toContain('to: produkt_v2');

    for (const [uri, text] of Object.entries(after)) {
      const { errors } = parseString(text, uri);
      expect(errors.filter((e) => e.severity === 'error'), `parse errors in ${uri} after rename: ${JSON.stringify(errors)}`).toEqual([]);
    }
  });

  it('a colliding rename is rejected with an LSP error, not a silent empty edit (I1.5)', async () => {
    await expect(client.sendRequest('textDocument/rename', {
      textDocument: { uri: PRODUKT_URI },
      position: { line: 4, character: 13 },
      newName: 'faktura', // collides with billing.invoicing.er.entity.faktura
    })).rejects.toThrow(/already exists/);
  });

  it('renaming the package (cursor on the package declaration) keeps the `package` keyword and parses', async () => {
    const edit = await client.sendRequest('textDocument/rename', {
      textDocument: { uri: PRODUKT_URI },
      position: { line: 0, character: 10 }, // on "billing.products" in the package decl
      newName: 'billing.products_v2',
    }) as lsp.WorkspaceEdit;

    const after = applyWorkspaceEdit(FILES, edit);
    expect(after[PRODUKT_URI]).toContain('package billing.products_v2');
    const { errors } = parseString(after[PRODUKT_URI], PRODUKT_URI);
    expect(errors.filter((e) => e.severity === 'error'), `package decl malformed after rename: ${after[PRODUKT_URI].split('\n')[0]}`).toEqual([]);
  });
});
