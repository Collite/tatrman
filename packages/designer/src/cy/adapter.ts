// SPDX-License-Identifier: Apache-2.0
import type { GetGraphResponse, ModelGraph, ModelGraphNode, ModelGraphRow, DisplayMode, RenderableSchemaCode } from '@tatrman/lsp';

export interface CyElement {
  group: 'nodes' | 'edges';
  data: Record<string, unknown>;
}

function escape(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function renderRowHtml(row: ModelGraphRow, displayMode: DisplayMode): string {
  const markers: string[] = [];
  if (row.isNameAttribute) markers.push('\u2605 ');
  if (row.isCodeAttribute) markers.push('# ');
  const name = markers.join('') + `<span class="cy-row-name">${escape(row.name)}</span>`;
  if (displayMode === 'just-names') return name;
  const type = row.type ? `<span class="cy-row-type">${escape(row.type)}</span>` : '';
  if (displayMode === 'with-types') return `${name} ${type}`;
  const constraints: string[] = [];
  if (row.isKey) constraints.push(escape('PK'));
  if (!row.optional) constraints.push(escape('NN'));
  const badge = constraints.length
    ? `<span class="cy-row-badge">${constraints.join('')}</span>`
    : '';
  return `${name} ${type} ${badge}`.trim();
}

function nodeLabelHtml(node: ModelGraphNode, displayMode: DisplayMode): string {
  const title = `<div class="cy-node-title">${escape(node.label)}</div>`;
  const rows = node.rows
    .map((r) => `<div class="cy-row">${renderRowHtml(r, displayMode)}</div>`)
    .join('');
  return `<div class="cy-node-label">${title}${rows ? `<div class="cy-rows">${rows}</div>` : ''}</div>`;
}

// Node body must be tall enough to host the HTML label. The HTML overlay is
// roughly: 28 px title + 18 px per row + 12 px padding.
const ROW_HEIGHT_PX = 18;
const TITLE_HEIGHT_PX = 28;
const VERTICAL_PADDING_PX = 12;

function nodeHeight(node: ModelGraphNode): number {
  return TITLE_HEIGHT_PX + node.rows.length * ROW_HEIGHT_PX + VERTICAL_PADDING_PX;
}

export function modelGraphToCyElements(
  graph: ModelGraph,
  displayMode: DisplayMode
): CyElement[] {
  const elements: CyElement[] = [];

  for (const node of graph.nodes) {
    elements.push({
      group: 'nodes',
      data: {
        // Cytoscape uses `id` to match `source`/`target` on edges — must equal the qname
        // that ModelGraphEdge.fromNode / toNode reference.
        id: node.qname,
        qname: node.qname,
        kind: node.kind,
        label: node.label,
        labelHtml: nodeLabelHtml(node, displayMode),
        h: nodeHeight(node),
      },
    });
  }

  for (const edge of graph.edges) {
    elements.push({
      group: 'edges',
      data: {
        id: edge.id,
        qname: edge.qname,
        kind: edge.kind,
        source: edge.fromNode,
        target: edge.toNode,
        fromCardinality: edge.fromCardinality,
        toCardinality: edge.toCardinality,
      },
    });
  }

  return elements;
}

export function getGraphResponseToModelGraph(response: GetGraphResponse): ModelGraph {
  const schema: RenderableSchemaCode = (response.schema === 'db' || response.schema === 'er')
    ? response.schema
    : 'er';
  const nodes = response.nodes.map((n) => ({ ...n, schemaCode: schema }));
  return { schemaCode: schema, nodes, edges: response.edges };
}