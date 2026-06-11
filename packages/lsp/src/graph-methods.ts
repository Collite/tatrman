import { parseString, type Document, type Definition } from '@modeler/parser';
import { computeGraphEdges, buildNodeForDef, type ModelGraphNode, type ModelGraphEdge } from './model-graph.js';
import { findCyclesOn, type PackageGraph } from '@modeler/semantics';

export interface GraphMetadata {
  uri: string;
  name: string;
  schema: 'db' | 'er' | 'map' | 'query' | 'cnc';
  description?: string;
  tags: string[];
  objectCount: number;
  missingObjectCount: number;
}

export interface GetGraphResponse {
  schema: 'db' | 'er' | 'map' | 'query' | 'cnc';
  nodes: ModelGraphNode[];
  edges: ModelGraphEdge[];
  layout: GraphLayoutOutput;
  missingObjects: string[];
  imports: string[];
}

export interface GraphLayoutOutput {
  viewport?: { zoom: number; panX: number; panY: number; displayMode: string };
  nodes: Record<string, { x: number; y: number }>;
  edges: Record<string, { bendPoints?: [number, number][] }>;
}

export interface PackageGraphResponse {
  packages: { name: string; documentUris: string[] }[];
  dependencies: { from: string; to: string; citedBy: string[] }[];
  cycles: string[][];
}

function getAllTtrgUris(documents: Map<string, string>): string[] {
  return [...documents.keys()].filter((uri) => uri.endsWith('.ttrg'));
}

function parseAllDocs(documents: Map<string, string>): Map<string, Document> {
  const docs = new Map<string, Document>();
  for (const [uri, content] of documents) {
    const result = parseString(content, uri);
    if (result.ast) docs.set(uri, result.ast);
  }
  return docs;
}

function buildQnameToDef(asts: Document[]): Map<string, { def: Definition; schemaCode: string; namespace: string; packageName: string }> {
  const map = new Map<string, { def: Definition; schemaCode: string; namespace: string; packageName: string }>();
  for (const ast of asts) {
    // TODO(pkg-schema-defaults): presentation-layer schema default; out of scope
    // for the schema-by-kind correctness fix. Should later use defaultSchemaForKind.
    const schemaCode = ast.schemaDirective?.schemaCode ?? 'er';
    const namespace = ast.schemaDirective?.namespace ?? '';
    const packageName = ast.packageDecl?.name ?? '';
    for (const def of ast.definitions) {
      addDefAndChildren(map, def, schemaCode, namespace, packageName);
    }
  }
  return map;
}

function addDefAndChildren(
  map: Map<string, { def: Definition; schemaCode: string; namespace: string; packageName: string }>,
  def: Definition,
  schemaCode: string,
  namespace: string,
  packageName: string
): void {
  const segments: string[] = [];
  if (packageName) segments.push(packageName);
  segments.push(schemaCode);
  segments.push(namespace || def.kind);
  segments.push(def.name);
  const qname = segments.join('.');
  map.set(qname, { def, schemaCode, namespace, packageName });

  const children: Definition[] = [];
  if (def.kind === 'entity' && def.attributes) children.push(...def.attributes);
  if (def.kind === 'table') {
    if (def.columns) children.push(...def.columns);
    if (def.indices) children.push(...def.indices);
    if (def.constraints) children.push(...def.constraints);
  }
  if (def.kind === 'view' && def.columns) children.push(...def.columns);
  if (def.kind === 'procedure' && def.resultColumns) children.push(...def.resultColumns);

  for (const child of children) {
    const childSegments = [...segments, child.name];
    const childQname = childSegments.join('.');
    map.set(childQname, { def: child, schemaCode, namespace, packageName });
  }
}

export function getPackageGraphFromCache(pkgGraph: PackageGraph): PackageGraphResponse {
  return {
    packages: pkgGraph.nodes.map((n) => ({ name: n.name, documentUris: n.documentUris })),
    dependencies: pkgGraph.edges.map((e) => ({ from: e.from, to: e.to, citedBy: e.citedBy })),
    cycles: findCyclesOn(pkgGraph),
  };
}

export function listGraphs(
  documents: Map<string, string>,
  qnameToDef?: Map<string, { def: Definition; schemaCode: string; namespace: string }>,
): GraphMetadata[] {
  const uris = getAllTtrgUris(documents);
  const results: GraphMetadata[] = [];

  for (const uri of uris) {
    const content = documents.get(uri);
    if (content === undefined) continue;
    const result = parseString(content, uri);
    const graph = result.ast?.graph;
    if (!graph) continue;

    let missingObjectCount = 0;
    if (qnameToDef) {
      for (const objQname of graph.objects) {
        if (!qnameToDef.has(objQname)) missingObjectCount++;
      }
    }

    results.push({
      uri,
      name: graph.name,
      schema: graph.schema ?? 'er',
      description: graph.description,
      tags: graph.tags ?? [],
      objectCount: graph.objects.length,
      missingObjectCount,
    });
  }

  return results;
}

export function getGraph(
  uri: string,
  documents: Map<string, string>,
  preferredLang = 'en',
): GetGraphResponse | null {
  const content = documents.get(uri);
  if (content === undefined) return null;
  const result = parseString(content, uri);
  const graph = result.ast?.graph;
  if (!result.ast || !graph) return null;

  const schema = graph.schema ?? 'er';
  const objectSet = new Set(graph.objects);

  const allDocs = parseAllDocs(documents);
  const qnameToDef = buildQnameToDef([...allDocs.values()]);

  const missingObjects = graph.objects.filter((qname) => !qnameToDef.has(qname));

  const nodes: ModelGraphNode[] = [];

  for (const objQname of objectSet) {
    const entry = qnameToDef.get(objQname);
    if (!entry) continue;
    const { def, schemaCode, namespace } = entry;
    const node = buildNodeForDef(def, schemaCode, namespace, preferredLang);
    if (node) {
      node.qname = objQname;
      nodes.push(node);
    }
  }

  const edges = computeGraphEdges(graph, [...allDocs.values()], qnameToDef);

  const layout: GraphLayoutOutput = {
    viewport: graph.layout?.viewport ? {
      zoom: graph.layout.viewport.zoom,
      panX: graph.layout.viewport.panX,
      panY: graph.layout.viewport.panY,
      displayMode: graph.layout.viewport.displayMode,
    } : undefined,
    nodes: graph.layout?.nodes ?? {},
    edges: graph.layout?.edges ?? {},
  };

  const imports = result.ast.imports.map((imp) => imp.target);

  return { schema, nodes, edges, layout, missingObjects, imports };
}