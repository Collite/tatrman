// GetGraphResponse → ModelGraph converter. Pure data shaping (no rendering engine); moved
// out of the (deleted) cy/ module so the render loop carries no cytoscape lineage.

import type { GetGraphResponse, ModelGraph, RenderableSchemaCode } from '@tatrman/lsp';

const RENDERABLE = new Set<GetGraphResponse['schema']>(['db', 'er', 'md', 'cnc']);

export function getGraphResponseToModelGraph(response: GetGraphResponse): ModelGraph {
  // db/er/md/cnc render directly; binding/query have no modeling canvas → fall back to er.
  const schema: RenderableSchemaCode = RENDERABLE.has(response.schema) ? (response.schema as RenderableSchemaCode) : 'er';
  const nodes = response.nodes.map((n) => ({ ...n, schemaCode: schema }));
  return { schemaCode: schema, nodes, edges: response.edges };
}
