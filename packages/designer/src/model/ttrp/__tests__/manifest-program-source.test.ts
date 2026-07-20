// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { ManifestProgramSource, ManifestNotFoundError } from '../manifest-program-source.js';
import { programGraphToProcessing } from '../program-graph-to-processing.js';
import { manifestToProgramGraph, ManifestShapeError, type BundleManifestV2 } from '../manifest-program-graph.js';

const heroText = (): string =>
  readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'fixtures', 'manifest-v2-hero.json'), 'utf8');

function fetchStub(body: string, status = 200): typeof fetch {
  return vi.fn(async (url: string | URL, init?: RequestInit) => {
    expect(String(url)).toContain('/v1/manifests/');
    return {
      ok: status >= 200 && status < 300,
      status,
      json: async () => JSON.parse(body),
      // expose headers the caller passed for assertion
      _headers: (init?.headers ?? {}) as Record<string, string>,
    } as unknown as Response;
  }) as unknown as typeof fetch;
}

describe('ManifestProgramSource (S9.T2 — static manifest → ProcessingCanvas)', () => {
  it('fetches the manifest and maps it to a ProcessingGraph (islands→nodes, transfers→transfer edges)', async () => {
    const src = new ManifestProgramSource('https://veles.example', { fetchImpl: fetchStub(heroText()), token: 't' });
    const g = await src.getProgramGraph('erp');

    expect(g.face).toBe('processing');
    expect(g.derived).toBe(true); // read-only view
    expect(g.nodes.map((n) => n.id).sort()).toEqual(['acc_prep', 'notify_failure', 'summarize']);
    const acc = g.nodes.find((n) => n.id === 'acc_prep')!;
    expect(acc.kind).toBe('op');
    expect(acc.engine).toBe('erp_pg/bash');
    const transfer = g.edges.find((e) => e.role === 'transfer');
    expect(transfer).toMatchObject({ from: 'acc_prep', to: 'summarize' });
    const errorEdge = g.edges.find((e) => e.role === 'control');
    expect(errorEdge).toMatchObject({ from: 'acc_prep', to: 'notify_failure' });
  });

  it('sends the bearer token to Veles', async () => {
    const f = fetchStub(heroText());
    const src = new ManifestProgramSource('https://veles.example/', { fetchImpl: f, token: 'dev-tok' });
    await src.getProgramGraph('erp');
    const init = (f as unknown as { mock: { calls: [string, RequestInit][] } }).mock.calls[0][1];
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer dev-tok');
  });

  it('surfaces a 404 as ManifestNotFoundError (a not-found state, not a crash)', async () => {
    const src = new ManifestProgramSource('https://veles.example', { fetchImpl: fetchStub('{}', 404) });
    await expect(src.getProgramGraph('missing')).rejects.toBeInstanceOf(ManifestNotFoundError);
  });

  it('a v1 (wrong-schemaVersion) manifest fails as a typed ManifestShapeError, not a silent TypeError', async () => {
    const v1 = JSON.stringify({ schemaVersion: 1, islands: [] });
    const src = new ManifestProgramSource('https://veles.example', { fetchImpl: fetchStub(v1) });
    await expect(src.getProgramGraph('erp')).rejects.toBeInstanceOf(ManifestShapeError);
  });

  it('a body with no islands array fails as ManifestShapeError (not "reading map of undefined")', async () => {
    const src = new ManifestProgramSource('https://veles.example', { fetchImpl: fetchStub('{"schemaVersion":2}') });
    await expect(src.getProgramGraph('erp')).rejects.toBeInstanceOf(ManifestShapeError);
  });

  it('a dangling transfer ref (typo island name) fails as ManifestShapeError, not an ELK crash', async () => {
    const bad = JSON.stringify({
      schemaVersion: 2,
      islands: [{ name: 'a', engine: 'e', executor: 'x' }],
      transfers: [{ from: 'a', to: 'MISSPELLED', via: 's' }],
    });
    const src = new ManifestProgramSource('https://veles.example', { fetchImpl: fetchStub(bad) });
    await expect(src.getProgramGraph('erp')).rejects.toBeInstanceOf(ManifestShapeError);
  });

  it('a non-JSON body surfaces as a diagnosable ManifestShapeError (not a raw SyntaxError)', async () => {
    const badFetch = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => {
        throw new SyntaxError('Unexpected token < in JSON');
      },
    })) as unknown as typeof fetch;
    const src = new ManifestProgramSource('https://veles.example', { fetchImpl: badFetch });
    await expect(src.getProgramGraph('erp')).rejects.toBeInstanceOf(ManifestShapeError);
  });

  it('the adapter matches manifestToProgramGraph → programGraphToProcessing directly', () => {
    const manifest = JSON.parse(heroText()) as BundleManifestV2;
    const direct = programGraphToProcessing(manifestToProgramGraph(manifest), 'erp');
    expect(direct.nodes).toHaveLength(3);
    // every edge references declared node ids + carries explicit anchor ports
    for (const e of direct.edges) {
      expect(direct.nodes.some((n) => n.id === e.from)).toBe(true);
      expect(direct.nodes.some((n) => n.id === e.to)).toBe(true);
      expect(e.fromPort).toBeTruthy();
      expect(e.toPort).toBeTruthy();
    }
  });
});
