// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import {
  generateLineage,
  type LineageInput, type LineageModel, type LineageGraph, type LineageObject,
} from '../index.js';

// Hero-shaped composed lineage model. Data flows source→consumer along from→to:
//   db.OrderLine.NetAmount ──binds──▶ er.OrderLine.net_amount ──derives──▶ md.Sales.net_amount
//   md.Sales.net_amount ──derives──▶ md.Sales.margin_pct        (calc dependent, downstream)
//   monthly_sales(program) ──reads──▶ db.OrderLine.NetAmount     (who makes the number, 1 hop)
const OBJ = {
  dbCol: 'orders_hero.db.dbo.table.OrderLine.NetAmount',
  erAttr: 'orders_hero.er.entity.OrderLine.net_amount',
  mdMeasure: 'orders_hero.md.cubelet.Sales.net_amount',
  mdCalc: 'orders_hero.md.cubelet.Sales.margin_pct',
  program: 'orders_hero.program.monthly_sales',
};

const objects: LineageObject[] = [
  { qname: OBJ.dbCol, kind: 'column', label: 'NetAmount', face: 'db' },
  { qname: OBJ.erAttr, kind: 'attribute', label: 'net_amount', face: 'er' },
  { qname: OBJ.mdMeasure, kind: 'measure', label: 'net_amount', face: 'md' },
  { qname: OBJ.mdCalc, kind: 'calc', label: 'margin_pct', face: 'md' },
  { qname: OBJ.program, kind: 'program', label: 'monthly_sales', face: 'program' },
];
const model: LineageModel = {
  objects,
  links: [
    { from: OBJ.dbCol, to: OBJ.erAttr, relation: 'binds' },
    { from: OBJ.erAttr, to: OBJ.mdMeasure, relation: 'derives' },
    { from: OBJ.mdMeasure, to: OBJ.mdCalc, relation: 'derives' },
    { from: OBJ.program, to: OBJ.dbCol, relation: 'reads' },
  ],
};

const runs = [{ forObject: OBJ.program, runs: [{ qname: 'run:2026-07-01', label: 'run 2026-07-01' }] }];

const q = (over: Partial<LineageInput['query']>): LineageInput['query'] => ({
  root: { qname: OBJ.mdMeasure, kind: 'measure' }, scope: 'neighborhood', direction: 'upstream', ...over,
});
const qnamesIn = (g: LineageGraph): Set<string> => new Set(g.layers.flatMap((l) => l.nodes.map((n) => n.ref.qname)));

describe('lineage generator — scopes (C-3)', () => {
  it('α column: exactly the upstream model bind-chain md ← er ← db (no program, no calc)', () => {
    const g = generateLineage({ query: q({ scope: 'column' }), model });
    expect(qnamesIn(g)).toEqual(new Set([OBJ.mdMeasure, OBJ.erAttr, OBJ.dbCol]));
    // ordered db → er → md
    expect(g.layers.map((l) => l.face)).toEqual(['db', 'er', 'md']);
  });

  it('β neighborhood (default): α + the writing program, one hop', () => {
    const g = generateLineage({ query: q({ scope: 'neighborhood' }), model });
    expect(qnamesIn(g)).toEqual(new Set([OBJ.mdMeasure, OBJ.erAttr, OBJ.dbCol, OBJ.program]));
    expect(g.layers.map((l) => l.face)).toEqual(['db', 'er', 'md', 'program']);
  });

  it('γ fullPath: β + calc dependents (margin_pct) + run instances', () => {
    const g = generateLineage({ query: q({ scope: 'fullPath' }), model, runs });
    const names = qnamesIn(g);
    expect(names.has(OBJ.mdCalc)).toBe(true); // calc dependent
    expect(names.has('run:2026-07-01')).toBe(true); // run instance
    expect(g.layers.map((l) => l.face)).toEqual(['db', 'er', 'md', 'program', 'runs']);
    expect(g.degraded).toBeUndefined();
  });

  it('any-root rule: rooting from the db column pulls its provenance (program), NOT the downstream chain', () => {
    const g = generateLineage({ query: q({ root: { qname: OBJ.dbCol, kind: 'column' }, scope: 'neighborhood' }), model });
    // from the db column: α (upstream model sources) is empty; β adds the one-hop NON-model
    // neighbor — the writing program. The downstream model-derives chain (er/md/calc) is γ, NOT β,
    // so it must be absent here (the earlier comment claiming β pulls it was wrong).
    expect(qnamesIn(g)).toEqual(new Set([OBJ.dbCol, OBJ.program]));
    expect(qnamesIn(g).has(OBJ.erAttr)).toBe(false);
    expect(qnamesIn(g).has(OBJ.mdMeasure)).toBe(false);
  });

  it('rooting from an er attribute works too (any column/attribute/measure)', () => {
    const g = generateLineage({ query: q({ root: { qname: OBJ.erAttr, kind: 'attribute' }, scope: 'column' }), model });
    expect(qnamesIn(g)).toEqual(new Set([OBJ.erAttr, OBJ.dbCol]));
  });
});

describe('lineage generator — degradation (C-3 / DS-PERSP-001)', () => {
  it('a γ request with NO runs source degrades to β + a labeled degradation, data present', () => {
    const g = generateLineage({ query: q({ scope: 'fullPath' }), model }); // no runs
    expect(g.degraded).toEqual({ requested: 'fullPath', served: 'neighborhood', reason: 'runs-need-platform-backend' });
    // served content is β (α + program), no run nodes
    expect(qnamesIn(g).has('run:2026-07-01')).toBe(false);
    expect(qnamesIn(g).has(OBJ.program)).toBe(true);
  });
});

describe('lineage generator — impact = transpose (C-4)', () => {
  // A MEANINGFUL directional test (not the tautology "downstream(F) == upstream(F^T)", which is
  // true by construction): over the SAME model, upstream and downstream from a mid-chain root must
  // reach DIFFERENT, correctly-oriented node sets, and the α edges must be exact reverses.
  it('α from the mid-chain er attribute: upstream reaches the db source, downstream the md consumer', () => {
    const root = { qname: OBJ.erAttr, kind: 'attribute' as const };
    const up = generateLineage({ query: q({ root, scope: 'column', direction: 'upstream' }), model });
    const down = generateLineage({ query: q({ root, scope: 'column', direction: 'downstream' }), model });
    // upstream (sources): er ← db. downstream (consumers): er → md → margin_pct.
    expect(qnamesIn(up)).toEqual(new Set([OBJ.erAttr, OBJ.dbCol]));
    expect(qnamesIn(down)).toEqual(new Set([OBJ.erAttr, OBJ.mdMeasure, OBJ.mdCalc]));
    // they are genuinely different orientations, not the same graph
    expect(qnamesIn(up)).not.toEqual(qnamesIn(down));
  });

  it('at γ (equal node sets) every downstream edge is exactly the reverse of an upstream edge', () => {
    // γ reaches the full connected component either way (undirected closure) → SAME node set, so the
    // ONLY difference is edge orientation: down.edges must be up.edges with from/to swapped. This is
    // the real transpose assertion (the node sets are held equal, so it can't pass vacuously).
    const root = { qname: OBJ.erAttr, kind: 'attribute' as const };
    const up = generateLineage({ query: q({ root, scope: 'fullPath', direction: 'upstream' }), model, runs });
    const down = generateLineage({ query: q({ root, scope: 'fullPath', direction: 'downstream' }), model, runs });
    expect(qnamesIn(up)).toEqual(qnamesIn(down)); // same nodes
    // compare only MODEL edges — run edges are downstream-leaf attachments added post-traversal
    // (not part of the transposed link set), so they carry the same orientation both ways.
    const modelEdges = (es: typeof up.edges) => es.filter((e) => e.relation !== 'runs');
    const norm = (es: typeof up.edges) => es.map((e) => `${e.from}|${e.to}|${e.relation}`).sort();
    const upReversed = modelEdges(up.edges).map((e) => ({ from: e.to, to: e.from, relation: e.relation }));
    expect(norm(modelEdges(down.edges))).toEqual(norm(upReversed));
    expect(modelEdges(down.edges).length).toBeGreaterThan(0); // and there ARE edges to reverse
  });

  it('downstream from the db column reaches the calc dependent (impact direction)', () => {
    const g = generateLineage({ query: q({ root: { qname: OBJ.dbCol, kind: 'column' }, scope: 'fullPath', direction: 'downstream' }), model, runs });
    expect(qnamesIn(g).has(OBJ.mdCalc)).toBe(true);
  });
});
