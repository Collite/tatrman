// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import type { MdMapDef } from '@tatrman/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { buildMdMapGraph } from '../md-graph.js';

// review-071 T-P2 — a map whose `from`/`to` does not resolve to a domain must DROP its edge (Kotlin's
// GrainLattice.of is canonical and drops it); keeping a raw-ref edge diverged leaves/reachability.
const MODEL = `model md
def domain A { type: int }
def domain B { type: int }
def map good     { from: md.A, to: md.B,    cardinality: { from: "N", to: "1" } }
def map dangling { from: md.A, to: md.Nope, cardinality: { from: "N", to: "1" } }
`;

function mapsAndSymbols(): { symbols: ProjectSymbolTable; maps: MdMapDef[] } {
  const ast = parseString(MODEL, 'file:///m.ttrm').ast!;
  const symbols = new ProjectSymbolTable();
  symbols.upsertDocument('file:///m.ttrm', ast, 'md', '');
  const maps = ast.definitions.filter((d): d is MdMapDef => d.kind === 'mdMap');
  return { symbols, maps };
}

describe('buildMdMapGraph — dangling-ref edges (review-071 T-P2)', () => {
  it('drops an edge whose ref does not resolve to a domain, matching Kotlin GrainLattice.of', () => {
    const { symbols, maps } = mapsAndSymbols();
    const graph = buildMdMapGraph(symbols, maps);
    // only the `good` A→B edge survives; the `dangling` A→Nope edge is dropped (not kept as a raw ref).
    expect(graph.edges.map((e) => `${e.from}->${e.to}`)).toEqual(['md.domain.A->md.domain.B']);
    expect(graph.nodes).not.toContain('md.Nope');
    expect(graph.nodes).not.toContain('md.domain.Nope');
  });
});
