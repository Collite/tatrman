// SPDX-License-Identifier: Apache-2.0
// DM-P4.S4 — the live ProcessingGraphSource: `ttrp/getGraph` (the committed hero wire fixture) mapped
// to a DS ProcessingGraph via the shape adapter. Proves the merged ProcessingCanvas can render the
// hero from the LIVE backend behind the same interface the fixtures implement.
import { describe, it, expect, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import type { GetGraphResult } from '../types.js';
import { TtrpServerProcessingSource, type TtrpReadRunClient } from '../ttrp-source.js';
import { ttrpToProcessingGraph } from '../to-processing-graph.js';

const here = dirname(fileURLToPath(import.meta.url));
const heroWire = (): GetGraphResult => JSON.parse(readFileSync(join(here, 'fixtures/hero-getGraph.json'), 'utf8'));

function fakeClient(result: GetGraphResult): TtrpReadRunClient {
  return { getGraph: vi.fn().mockResolvedValue(result), run: vi.fn() };
}

describe('ttrpToProcessingGraph — program level', () => {
  it('maps containers to collapsed regions and leaves to program-level nodes', () => {
    const pg = ttrpToProcessingGraph(heroWire(), 'program');
    expect(pg.face).toBe('processing');
    expect(pg.id).toBe('hero.ttrp');
    const byId = Object.fromEntries(pg.nodes.map((n) => [n.id, n]));
    // both containers → kind 'container', collapsed; acc_prep is fragment-derived (sql)
    expect(byId['acc_prep'].kind).toBe('container');
    expect(byId['acc_prep'].collapsed).toBe(true);
    expect(byId['acc_prep'].fragmentDerived).toBe(true);
    expect(byId['crunch'].kind).toBe('container');
    // leaves keep their program-level kinds (store/display; the transfer leaf is a generic op)
    expect(byId['~8'].kind).toBe('display');
    expect(byId['~9'].kind).toBe('store');
    expect(byId['~10'].kind).toBe('store');
  });

  it('maps the program edges with resolved endpoint ports + roles', () => {
    const pg = ttrpToProcessingGraph(heroWire(), 'program');
    expect(pg.edges).toHaveLength(4);
    const e = pg.edges.find((ed) => ed.from === 'acc_prep' && ed.to === 'crunch')!;
    expect(e.role).toBe('data');
    expect(e.fromPort).toBe('acc_prep.out');
    expect(e.toPort).toBe('crunch.accounts');
  });
});

describe('ttrpToProcessingGraph — container drill', () => {
  it('drills into crunch: its own op nodes + edges, not derived', () => {
    const pg = ttrpToProcessingGraph(heroWire(), 'crunch');
    expect(pg.id).toBe('crunch');
    expect(pg.derived).toBe(false);
    expect(pg.nodes.map((n) => n.id).sort()).toEqual(['crunch/b#1', 'crunch/j#1', 'crunch/sales#1', 'crunch/sales#2', 'crunch/sums#1']);
    expect(pg.nodes.every((n) => n.kind === 'op')).toBe(true);
    expect(pg.edges).toHaveLength(4);
  });

  it('a fragment container (acc_prep, sql) drills derived (read-only + banner)', () => {
    const pg = ttrpToProcessingGraph(heroWire(), 'acc_prep');
    expect(pg.derived).toBe(true);
  });

  it('an unknown container yields an empty graph (no throw)', () => {
    const pg = ttrpToProcessingGraph(heroWire(), 'nope');
    expect(pg.nodes).toEqual([]);
    expect(pg.edges).toEqual([]);
  });
});

describe('TtrpServerProcessingSource — over the client', () => {
  it('getProgramGraph pulls ttrp/getGraph and maps it', async () => {
    const client = fakeClient(heroWire());
    const src = new TtrpServerProcessingSource(client, 'file:///hero.ttrp');
    const pg = await src.getProgramGraph('hero.ttrp');
    expect(client.getGraph).toHaveBeenCalledWith('file:///hero.ttrp');
    expect(pg.nodes.some((n) => n.id === 'crunch' && n.kind === 'container')).toBe(true);
  });

  it('getContainerGraph maps the named container drill (accepts a dotted ref)', async () => {
    const src = new TtrpServerProcessingSource(fakeClient(heroWire()));
    const pg = await src.getContainerGraph('hero.crunch');
    expect(pg.id).toBe('crunch');
    expect(pg.nodes).toHaveLength(5);
  });
});
