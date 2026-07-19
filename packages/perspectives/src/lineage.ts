// SPDX-License-Identifier: Apache-2.0
import type {
  PerspectiveGenerator, PerspectiveResult,
  LineageInput, LineageGraph, LineageLink, LineageNode, LineageLayer, LineageObject,
  LineageScope,
} from './types.js';
import { LINEAGE_FACE_ORDER } from './types.js';
import { NotImplementedYet } from './binding.js';

const MODEL_RELATIONS = new Set(['binds', 'derives']); // the α bind-chain

/** Transpose a link set (source↔consumer). Impact (downstream) IS upstream over the transpose
 *  — one code path, a flag (C-4). */
function transpose(links: LineageLink[]): LineageLink[] {
  return links.map((l) => ({ from: l.to, to: l.from, relation: l.relation }));
}

/**
 * Lineage perspective (C-3 + C-4). Pure cross-face composition over a `LineageModel`:
 *  · α `column`       — the directional model bind-chain (binds/derives) from the root;
 *  · β `neighborhood` — α + every 1-hop neighbor via any relation (pulls in the writing program);
 *  · γ `fullPath`     — the full connected component + run instances (calc dependents, runs).
 * Direction transposes the graph (impact = downstream = upstream over the transpose). A γ request
 * with no runs source degrades to β + a DS-PERSP-001 hint (data present, honestly labeled).
 */
export function generateLineage(input: LineageInput): LineageGraph {
  const { query, model } = input;
  const { root, scope, direction } = query;

  // impact (downstream) = the SAME traversal over the transposed graph.
  const links = direction === 'downstream' ? transpose(model.links) : model.links;

  // upstream neighbors of n over `links`: the sources feeding n (l.to === n ⇒ l.from).
  const upstream = (n: string, relOk: (r: string) => boolean): string[] =>
    links.filter((l) => l.to === n && relOk(l.relation)).map((l) => l.from);
  // undirected 1-hop neighbors of n, optionally filtered by relation.
  const around = (n: string, relOk: (r: string) => boolean = () => true): string[] => [
    ...links.filter((l) => l.to === n && relOk(l.relation)).map((l) => l.from),
    ...links.filter((l) => l.from === n && relOk(l.relation)).map((l) => l.to),
  ];

  const objByQname = new Map(model.objects.map((o) => [o.qname, o]));
  if (!objByQname.has(root.qname)) return { layers: [], edges: [] };

  // α — directional model bind-chain.
  const alpha = new Set<string>([root.qname]);
  {
    const stack = [root.qname];
    while (stack.length) {
      const n = stack.pop()!;
      for (const src of upstream(n, (r) => MODEL_RELATIONS.has(r))) {
        if (!alpha.has(src)) { alpha.add(src); stack.push(src); }
      }
    }
  }

  // degradation: γ needs a runs source.
  let effective: LineageScope = scope;
  let degraded: LineageGraph['degraded'];
  if (scope === 'fullPath' && (!input.runs || input.runs.length === 0)) {
    effective = 'neighborhood';
    degraded = { requested: 'fullPath', served: 'neighborhood', reason: 'runs-need-platform-backend' };
  }

  const selected = new Set(alpha);
  if (effective === 'neighborhood') {
    // β adds the provenance neighbors (who makes/consumes this number, one hop) — NOT the
    // downstream model-derives dependents (those are γ). Provenance = non-model relations.
    for (const n of [...alpha]) for (const nb of around(n, (r) => !MODEL_RELATIONS.has(r))) selected.add(nb);
  } else if (effective === 'fullPath') {
    // full connected component via any relation.
    const stack = [...alpha];
    while (stack.length) {
      const n = stack.pop()!;
      for (const nb of around(n)) if (!selected.has(nb)) { selected.add(nb); stack.push(nb); }
    }
  }

  // real (model) edges among selected objects, oriented as traversed.
  const edges = links.filter((l) => selected.has(l.from) && selected.has(l.to));

  // run instances (γ only, with a runs source): downstream leaf nodes + `runs` edges.
  const runNodes: LineageObject[] = [];
  if (effective === 'fullPath' && input.runs) {
    for (const { forObject, runs } of input.runs) {
      if (!selected.has(forObject)) continue;
      for (const r of runs) {
        runNodes.push({ qname: r.qname, kind: 'run', label: r.label, face: 'runs' });
        edges.push({ from: forObject, to: r.qname, relation: 'runs' });
      }
    }
  }

  // group selected objects (+ run nodes) into ordered, non-empty layers.
  const allNodes: LineageObject[] = [...[...selected].map((q) => objByQname.get(q)!).filter(Boolean), ...runNodes];
  const layers: LineageLayer[] = LINEAGE_FACE_ORDER.map((face) => ({
    face,
    nodes: allNodes.filter((o) => o.face === face).map((o): LineageNode => ({ kind: o.kind, ref: { qname: o.qname, kind: o.kind, label: o.label }, label: o.label, face })),
  })).filter((l) => l.nodes.length > 0);

  return degraded ? { layers, edges, degraded } : { layers, edges };
}

/**
 * Lineage perspective generator (C-3 + C-4). Wraps {@link generateLineage} in the pinned
 * `PerspectiveResult` (custom → the purpose-built `lineage-layers` view; C-1 γ).
 */
export const lineageGenerator: PerspectiveGenerator<LineageInput, PerspectiveResult> = {
  id: 'lineage',
  generate(input: LineageInput): PerspectiveResult {
    // guard the shape the interface test still exercises with a stub input
    if (!input || !input.model || !Array.isArray(input.model.objects)) {
      throw new NotImplementedYet('lineageGenerator.generate (needs a composed LineageModel)', 'DS-P4.S2');
    }
    return { kind: 'custom', view: 'lineage-layers', data: generateLineage(input) };
  },
};
