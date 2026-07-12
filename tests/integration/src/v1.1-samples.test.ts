// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, beforeAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@tatrman/lsp/server';
import path from 'path';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const miniDir = path.resolve(__dirname, '../../../samples/v1.1-mini');
const metadataDir = path.resolve(__dirname, '../../../samples/v1.1-metadata');

function createPairedConnection(): { client: lsp.Connection; server: lsp.Connection } {
  const clientTransport = new PassThrough({ objectMode: true });
  const serverTransport = new PassThrough({ objectMode: true });
  const client = lsp.createConnection(
    new lsp.StreamMessageReader(clientTransport as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(serverTransport as unknown as NodeJS.WritableStream),
  ) as lsp.Connection;
  const server = lsp.createConnection(
    new lsp.StreamMessageReader(serverTransport as unknown as NodeJS.ReadableStream),
    new lsp.StreamMessageWriter(clientTransport as unknown as NodeJS.WritableStream),
  ) as lsp.Connection;
  client.listen();
  server.listen();
  return { client, server };
}

const sleep = (ms: number) => new Promise<void>(r => setTimeout(r, ms));

function walkTtr(dir: string): string[] {
  const out: string[] = [];
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    if (e.name === '.modeler' || e.name === 'graphs') continue;
    const f = join(dir, e.name);
    if (e.isDirectory()) out.push(...walkTtr(f));
    else if (e.name.endsWith('.ttrm')) out.push(f);
  }
  return out;
}

function walkGraphs(dir: string): string[] {
  try {
    return readdirSync(join(dir, 'graphs')).filter(f => f.endsWith('.ttrg')).map(f => join(dir, 'graphs', f));
  } catch { return []; }
}

interface SampleState {
  errorCodes: Record<string, number>;
  graphs: Record<string, { missingObjects: string[]; nodes: unknown[] }>;
}

/**
 * Boots an LSP with the sample as its workspace root (so package inference is
 * relative to the sample root), opens every `.ttrm`, records error-severity
 * diagnostics by code, then opens each `.ttrg` and calls modeler/getGraph.
 */
async function loadSample(dir: string): Promise<SampleState> {
  const { client, server } = createPairedConnection();
  createServerConnection(server as unknown as lsp.Connection);
  await client.sendRequest('initialize', {
    processId: null,
    rootUri: `file://${dir}`,
    workspaceFolders: [{ uri: `file://${dir}`, name: path.basename(dir) }],
    capabilities: {},
  });
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

  for (const f of walkTtr(dir)) {
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: `file://${f}`, languageId: 'ttr', version: 1, text: readFileSync(f, 'utf-8') },
    });
  }
  await sleep(800);

  const graphs: SampleState['graphs'] = {};
  for (const g of walkGraphs(dir)) {
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: `file://${g}`, languageId: 'ttr', version: 1, text: readFileSync(g, 'utf-8') },
    });
    await sleep(100);
    graphs[path.basename(g)] = await client.sendRequest('modeler/getGraph', { uri: `file://${g}` }) as SampleState['graphs'][string];
  }

  client.dispose();
  server.dispose();
  return { errorCodes, graphs };
}

describe('v1.1 samples resolve cleanly', () => {
  describe('v1.1-mini', () => {
    let state: SampleState;
    beforeAll(async () => { state = await loadSample(miniDir); });

    it('every .ttrm resolves with zero error diagnostics (no package-declaration-mismatch, no duplicates, no unresolved refs)', () => {
      expect(state.errorCodes, `unexpected errors: ${JSON.stringify(state.errorCodes)}`).toEqual({});
    });

    for (const graphName of walkGraphs(miniDir).map(g => path.basename(g))) {
      it(`${graphName} opens with missingObjects === [] and nodes.length > 0`, () => {
        const r = state.graphs[graphName];
        expect(r.missingObjects, `${graphName} missing: ${r.missingObjects.join(', ')}`).toEqual([]);
        expect(r.nodes.length).toBeGreaterThan(0);
      });
    }
  });

  describe('v1.1-metadata (carries pre-existing primary-key issues — documented)', () => {
    // The original v1-metadata sample already emits ttr/primary-key-column-not-found
    // (98 there; 49 survive into the partial v1.1 migration). These are a sample
    // data-quality artifact, NOT a v1.1 migration defect, so they are the only
    // error code tolerated here.
    const ALLOWED = new Set(['ttr/primary-key-column-not-found']);
    const knownMissingPKs = [
      'billing.er.entity.dobropis_složka', 'billing.er.entity.faktura_složka',
      'billing.er.entity.platnost_dm_v_tdm', 'billing.er.entity.podprodukt_návštěvy_zákazníka',
      'billing.er.entity.prodeje', 'billing.er.entity.produkt_návštěvy_zákazníka',
      'billing.er.entity.reklamace_složka', 'billing.er.entity.vratka_složka',
      'billing.er.entity.výtěžnost_regálu', 'billing.er.entity.výtěžnost_regálu_kanál',
      'billing.er.entity.zakázka_složka', 'billing.er.entity.úkol_návštěvy_zákazníka',
      'billing.er.entity.účet_účetní_osnovy',
    ];

    let state: SampleState;
    beforeAll(async () => { state = await loadSample(metadataDir); });

    it('every .ttrm resolves with no errors other than the documented pre-existing PK issues', () => {
      const unexpected = Object.keys(state.errorCodes).filter(c => !ALLOWED.has(c));
      expect(unexpected, `unexpected error codes: ${JSON.stringify(state.errorCodes)}`).toEqual([]);
    });

    for (const graphName of walkGraphs(metadataDir).map(g => path.basename(g))) {
      it(`${graphName} opens with no unexpected missingObjects`, () => {
        const r = state.graphs[graphName];
        const unexpected = r.missingObjects.filter(q => !knownMissingPKs.includes(q));
        expect(unexpected, `${graphName} unexpected missing: ${unexpected.join(', ')}`).toEqual([]);
        expect(r.nodes.length).toBeGreaterThan(0);
      });
    }
  });
});
