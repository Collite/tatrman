// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { parseString } from '@tatrman/parser';
import type { CubeletDef, MdMapDef, MeasureDef } from '@tatrman/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { buildMdMapGraph, computeLeaves, coLeafClasses, grainReachable } from '../index.js';
import type { MapEdge } from '../index.js';
import { defaultAgg, defaultMeasure } from '../md-defaults.js';

/**
 * review-071 T-P2 / S1-B5 — the TS↔Kotlin Layer-A parity harness. Both languages parse the SAME
 * `md-parity.ttrm`, build the grain lattice + defaults, and render one canonical line-based summary;
 * both must equal `md-parity.golden.txt`. A line format (not JSON) is used so byte-equality is trivial
 * across the two serializers. Kotlin is canonical; this side matches it. The Kotlin twin is
 * `ttr-semantics` `MdParitySpec`.
 */
const dir = fileURLToPath(new URL('./fixtures/', import.meta.url));
const model = readFileSync(`${dir}md-parity.ttrm`, 'utf8');
const golden = readFileSync(`${dir}md-parity.golden.txt`, 'utf8');

// The reachability pairs the summary probes (shared with the Kotlin twin).
const REACH_PAIRS: [string, string][] = [
  ['Code', 'Name'],
  ['Day', 'Month'],
  ['Day', 'Region'],
  ['Day', 'Year'],
  ['Name', 'Region'],
];

function render(): string {
  const ast = parseString(model, 'file:///md-parity.ttrm').ast!;
  const symbols = new ProjectSymbolTable();
  symbols.upsertDocument('file:///md-parity.ttrm', ast, 'md', '');
  const defs = ast.definitions;
  const maps = defs.filter((d): d is MdMapDef => d.kind === 'mdMap');
  const measures = defs.filter((d): d is MeasureDef => d.kind === 'measure');
  const cubelets = defs.filter((d): d is CubeletDef => d.kind === 'cubelet');

  // Normalise the graph to SIMPLE domain names (Kotlin keys by simple name; TS by qname) so both
  // operate on the same graph. `nodes` = edge endpoints (isolated domains — e.g. a measure's Money —
  // are the documented-benign node-set divergence and are excluded from the leaf/co-leaf comparison).
  const simple = (n: string): string => n.split('.').pop() as string;
  const graph = buildMdMapGraph(symbols, maps);
  const edges: MapEdge[] = graph.edges.map((e) => ({ from: simple(e.from), to: simple(e.to), oneToOne: e.oneToOne }));
  const nodes = [...new Set(graph.nodes.map(simple))];

  const leaves = [...computeLeaves(nodes, edges)].sort();
  const edgeLines = edges.map((e) => `${e.from} -> ${e.to} (${e.oneToOne ? '1:1' : 'N:1'})`).sort();
  const classes = coLeafClasses(nodes, edges)
    .map((c) => [...c].sort().join(','))
    .sort();
  const reach = grainReachable(edges);
  const reachLines = REACH_PAIRS.map(([a, b]) => `${a} -> ${b} : ${reach(a, b)}`).sort();
  const cdm = cubelets.map((c) => `${c.name} = ${defaultMeasure(c)}`).sort();
  const mda = measures.map((m) => `${m.name} = ${defaultAgg(m)}`).sort();

  return [
    '== leaves ==',
    ...leaves,
    '== edges ==',
    ...edgeLines,
    '== coLeafClasses ==',
    ...classes,
    '== reachable ==',
    ...reachLines,
    '== defaults.cubeletDefaultMeasure ==',
    ...cdm,
    '== defaults.measureDefaultAgg ==',
    ...mda,
  ].join('\n') + '\n';
}

describe('MD Layer-A TS↔Kotlin parity (T-P2 / S1-B5)', () => {
  it('the TS lattice + defaults summary matches the canonical golden', () => {
    expect(render()).toBe(golden);
  });
});
