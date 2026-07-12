// SPDX-License-Identifier: Apache-2.0
// Thin adapter: the ttrm dependency-graph DTO (contracts §4) → Cytoscape elements.
//
// The WS server's `getModelGraph` is the *dependency-graph* shape
// ({qname,kind,label,schema,pkg} / {from,to,type}) — NOT the richer renderable
// ModelGraph (rows, fk/relation, cardinalities) that `cy/adapter.ts` consumes.
// This is the recorded contracts delta (contracts.md §v1.3): WS mode renders
// row-less node boxes and enriches on-demand via `ttrm/getObject`. Whether §4 grows
// renderable fields is deferred to the C1-f arc — do not force this through
// `cy/adapter.ts`'s ModelGraph shape.

import type { CyElement } from './adapter.js';
import type { TtrmGraph } from '../data/ttrm-types.js';

const NODE_HEIGHT_PX = 40;

export function ttrmGraphToCyElements(graph: TtrmGraph): CyElement[] {
  const elements: CyElement[] = [];
  const known = new Set(graph.nodes.map((n) => n.qname));

  for (const node of graph.nodes) {
    elements.push({
      group: 'nodes',
      data: {
        id: node.qname,
        qname: node.qname,
        kind: node.kind,
        label: node.label,
        // Row-less box (no per-column detail in the dependency-graph DTO).
        labelHtml: `<div class="cy-node-label"><div class="cy-node-title">${escapeHtml(node.label)}</div></div>`,
        schema: node.schema,
        pkg: node.pkg,
        h: NODE_HEIGHT_PX,
      },
    });
  }

  for (const edge of graph.edges) {
    // Skip dangling edges whose endpoints aren't in scope.
    if (!known.has(edge.from) || !known.has(edge.to)) continue;
    elements.push({
      group: 'edges',
      data: {
        id: `${edge.from}→${edge.to}:${edge.type}`,
        qname: `${edge.from}→${edge.to}`,
        kind: edge.type.toLowerCase(),
        edgeType: edge.type,
        source: edge.from,
        target: edge.to,
        fromCardinality: null,
        toCardinality: null,
      },
    });
  }

  return elements;
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
