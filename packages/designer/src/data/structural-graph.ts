// SPDX-License-Identifier: Apache-2.0
// Structural GetGraphResponse mapper (Designer Merge, DM-P2.S3 / contracts §1.1a — the graph-SHAPE
// axis). The Worker backend returns the rich DS `GetGraphResponse` natively (rows/PK-FK, measures,
// cnc props — the slot data the skins render). WS + Veles serve only the row-less dependency graph
// (`TtrmNode`/`TtrmEdge`); this maps that structural shape up to the `GetGraphResponse` the shell
// consumes, with empty `rows` — the skin's structural marks render, slot bodies are absent. Honest
// partial render (`DM-CAP-002` via `capabilities.graphShape === 'structural'`), never a failure.

import type { GetGraphResponse, ModelGraphNode, ModelGraphEdge, RenderableSchemaCode } from '@tatrman/lsp';
import type { TtrmNode, TtrmEdge } from './ttrm-types.js';

const NODE_KINDS = new Set<ModelGraphNode['kind']>(['table', 'view', 'entity', 'cubelet', 'dimension']);
const RENDERABLE = new Set<RenderableSchemaCode>(['db', 'er', 'md', 'cnc']);

/** best-effort structural kind: keep a known DS node kind, else default by schema (structural-only). */
function coerceKind(raw: string, schema: RenderableSchemaCode): ModelGraphNode['kind'] {
  if (NODE_KINDS.has(raw as ModelGraphNode['kind'])) return raw as ModelGraphNode['kind'];
  switch (schema) {
    case 'db': return 'table';
    case 'md': return 'cubelet';
    default: return 'entity';
  }
}

/** row-less dependency edge → structural ModelGraphEdge (no cardinalities/source spans on this axis). */
function mapEdge(e: TtrmEdge, i: number): ModelGraphEdge {
  return {
    id: `${e.from}->${e.to}#${i}`,
    qname: `${e.from}->${e.to}`,
    kind: e.type === 'REFERENCES' ? 'fk' : 'relation',
    fromNode: e.from,
    toNode: e.to,
    fromCardinality: null,
    toCardinality: null,
    sourceUri: '',
    sourceLocation: { line: 0, column: 0 },
  };
}

/**
 * Map a structural (row-less) graph — WS `ttrm/getGraph`, Veles browse — to the `GetGraphResponse`
 * the shell consumes. `rows` is empty (no slot data on this backend); `layout`/`imports` are empty
 * (layout is read separately via the ViewStateStore; imports are a Worker-only concept).
 */
export function ttrmToGetGraphResponse(
  schema: string,
  nodes: TtrmNode[],
  edges: TtrmEdge[],
  missingObjects: string[] = [],
): GetGraphResponse {
  const s: RenderableSchemaCode = RENDERABLE.has(schema as RenderableSchemaCode) ? (schema as RenderableSchemaCode) : 'er';
  return {
    schema: s,
    nodes: nodes.map((n): ModelGraphNode => ({
      qname: n.qname,
      kind: coerceKind(n.kind, s),
      name: n.label,
      schemaCode: s,
      label: n.label,
      sourceUri: '',
      sourceLocation: { line: 0, column: 0 },
      rows: [], // structural-only — no slot data on this backend (§1.1a / DM-CAP-002)
    })),
    edges: edges.map(mapEdge),
    layout: { nodes: {}, edges: {} },
    missingObjects,
    imports: [],
  };
}
