// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, vi } from 'vitest';
import { VelesDataSource, VelesReadError } from '../veles-data-source.js';

/** A fetch stub: records requested URLs and answers from a path→response map. */
function fakeFetch(routes: Record<string, { status?: number; body: unknown }>) {
  const calls: string[] = [];
  const impl = vi.fn(async (input: string | URL | Request) => {
    const url = String(input);
    calls.push(url);
    // Match by pathname+search against the registered keys (suffix match so a base
    // prefix like /veles or http://host doesn't have to be repeated in the map).
    const key = Object.keys(routes).find((k) => url.endsWith(k));
    if (!key) return new Response('null', { status: 404 });
    const r = routes[key];
    return new Response(JSON.stringify(r.body), {
      status: r.status ?? 200,
      headers: { 'content-type': 'application/json' },
    });
  });
  return { impl: impl as unknown as typeof fetch, calls };
}

const INDEX = { packages: ['er'], schemas: ['er', 'db'], areas: [], counts: { objects: 3, schemas: 2, areas: 0 }, modelVersion: 'v1' };

describe('VelesDataSource', () => {
  it('is read-only', () => {
    expect(new VelesDataSource('/veles').capabilities.edit).toBe(false);
    // DM-P1 capability descriptor: the thinnest backend — db/er only, no bindings, auto-layout.
    expect(new VelesDataSource('/veles').capabilities).toEqual({
      edit: false,
      modelKinds: ['db', 'er'],
      bindings: false,
      perspectives: false,
      layoutPersist: 'none',
    });
  });

  it('getModelIndex GETs /model/index and returns it', async () => {
    const { impl, calls } = fakeFetch({ '/model/index': { body: INDEX } });
    const src = new VelesDataSource('/veles', { fetchImpl: impl });
    expect(await src.getModelIndex()).toEqual(INDEX);
    expect(calls[0]).toBe('/veles/model/index');
  });

  it('joins a full origin base', async () => {
    const { impl, calls } = fakeFetch({ '/model/index': { body: INDEX } });
    const src = new VelesDataSource('http://localhost:7260', { fetchImpl: impl });
    await src.getModelIndex();
    expect(calls[0]).toBe('http://localhost:7260/model/index');
  });

  it('getModelGraph passes schema/package as query params', async () => {
    const graph = { nodes: [], edges: [] };
    const { impl, calls } = fakeFetch({ '/model/graph?schema=er&package=er.core': { body: graph } });
    const src = new VelesDataSource('', { fetchImpl: impl });
    expect(await src.getModelGraph({ schema: 'er', package: 'er.core' })).toEqual(graph);
    expect(calls[0]).toBe('/model/graph?schema=er&package=er.core');
  });

  it('getObject url-encodes the qname', async () => {
    const detail = { object: { qname: 'er.entity.artikl', kind: 'entity', label: 'Artikl', schema: 'er', pkg: 'er' }, sourceLocation: 'x.ttr:1', references: [] };
    const { impl, calls } = fakeFetch({ '/model/object?qname=er.entity.artikl': { body: detail } });
    const src = new VelesDataSource('/veles', { fetchImpl: impl });
    expect(await src.getObject('er.entity.artikl')).toEqual(detail);
    expect(calls[0]).toContain('/model/object?qname=er.entity.artikl');
  });

  it('search returns hits and skips the fetch for a blank query', async () => {
    const hits: unknown[] = [{ qname: 'er.entity.artikl', score: 0.9, matchedField: 'name' }];
    const { impl, calls } = fakeFetch({ '/model/search?query=art&limit=5': { body: { hits } } });
    const src = new VelesDataSource('/veles', { fetchImpl: impl });
    expect(await src.search({ query: 'art', limit: 5 })).toEqual(hits);
    expect(await src.search({ query: '   ' })).toEqual([]);
    expect(calls).toHaveLength(1); // only the non-blank query hit the wire
  });

  it('throws VelesReadError with the server error detail on a non-ok response', async () => {
    const { impl } = fakeFetch({ '/model/object?qname=missing': { status: 404, body: { error: 'not found' } } });
    const src = new VelesDataSource('/veles', { fetchImpl: impl });
    await expect(src.getObject('missing')).rejects.toBeInstanceOf(VelesReadError);
    await expect(src.getObject('missing')).rejects.toThrow(/404.*not found/);
  });

  it('onModelChanged polls /status, seeds the baseline silently, and fires on a version change', async () => {
    let version = 'v1';
    const impl = vi.fn(async () => new Response(JSON.stringify({ model_version: version }), { status: 200 })) as unknown as typeof fetch;
    let tick: () => void = () => {};
    const src = new VelesDataSource('/veles', {
      fetchImpl: impl,
      setIntervalImpl: (cb) => {
        tick = cb;
        return 1 as unknown as ReturnType<typeof setInterval>;
      },
      clearIntervalImpl: vi.fn(),
    });
    const cb = vi.fn();
    const sub = src.onModelChanged(cb);

    tick(); // first poll seeds baseline (v1) — no callback
    await vi.waitFor(() => expect(impl).toHaveBeenCalledTimes(1));
    expect(cb).not.toHaveBeenCalled();

    version = 'v2';
    tick();
    await vi.waitFor(() => expect(cb).toHaveBeenCalledWith('v2'));

    sub.dispose();
  });
});
