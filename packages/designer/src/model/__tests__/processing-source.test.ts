// SPDX-License-Identifier: Apache-2.0
// DM-P4.S1 (ported from modeler DS-P5.S1.T1) — the fixture ProcessingGraphSource serves the hero
// orchestration graph and the container drill-ins (contracts §5). Shape only (the pure
// ProcessingGraph→CanvasGraph map is canvas-core's processing-map test).
import { describe, it, expect } from 'vitest';
import { fixtureProcessingSource } from '../processing-source.js';

const src = fixtureProcessingSource();

describe('fixtureProcessingSource — orchestration (getProgramGraph)', () => {
  it('serves monthly_sales with the two containers + store/display leaves', async () => {
    const pg = await src.getProgramGraph('monthly_sales');
    expect(pg.face).toBe('processing');
    expect(pg.id).toBe('monthly_sales');
    const kinds = Object.fromEntries(pg.nodes.map((n) => [n.id, n.kind]));
    expect(kinds).toMatchObject({ extract: 'container', crunch: 'container', store: 'store', display: 'display' });
  });

  it('carries engine/dialect on containers and the fragment-derived marking on extract', async () => {
    const pg = await src.getProgramGraph('monthly_sales');
    const extract = pg.nodes.find((n) => n.id === 'extract')!;
    expect(extract.engine).toBe('sql @ mssql');
    expect(extract.fragmentDerived).toBe(true);
    expect(pg.nodes.find((n) => n.id === 'crunch')!.engine).toBe('polars');
  });

  it('has the synthesized transfer edge and the store→display control edge', async () => {
    const pg = await src.getProgramGraph('monthly_sales');
    const roles = pg.edges.map((e) => e.role);
    expect(roles).toContain('transfer');
    expect(roles).toContain('control');
    const control = pg.edges.find((e) => e.role === 'control')!;
    expect(control.from).toBe('store');
    expect(control.to).toBe('display');
  });

  it("keeps the crunch region's unconnected rejects stub (D-2)", async () => {
    const pg = await src.getProgramGraph('monthly_sales');
    const crunch = pg.nodes.find((n) => n.id === 'crunch')!;
    const rejects = crunch.ports!.find((p) => p.role === 'rejects')!;
    expect(rejects).toBeDefined();
    expect(rejects.connected).toBe(false);
  });
});

describe('fixtureProcessingSource — drill-in (getContainerGraph)', () => {
  it('drills into crunch: a polars op-graph (join → filter → aggregate) with the filter rejects stub', async () => {
    const pg = await src.getContainerGraph('crunch');
    expect(pg.face).toBe('processing');
    expect(pg.derived).toBeFalsy();
    const ids = pg.nodes.map((n) => n.id);
    expect(ids).toEqual(expect.arrayContaining(['join', 'filter', 'aggregate']));
    const filter = pg.nodes.find((n) => n.id === 'filter')!;
    expect(filter.ports!.some((p) => p.role === 'rejects' && !p.connected)).toBe(true);
  });

  it('drills into extract: the sql-fragment-derived canvas is flagged derived (read-only + banner)', async () => {
    const pg = await src.getContainerGraph('extract');
    expect(pg.derived).toBe(true);
    expect(pg.nodes.some((n) => (n.bodyText ?? '').includes('select'))).toBe(true);
  });

  it('returns an empty graph for an unknown container (no throw)', async () => {
    const pg = await src.getContainerGraph('nope');
    expect(pg.nodes).toEqual([]);
    expect(pg.edges).toEqual([]);
  });
});
