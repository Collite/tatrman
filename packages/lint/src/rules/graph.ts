import { DiagnosticCode } from '@modeler/parser';
import type { SourceLocation } from '@modeler/parser';
import type { Rule } from '../rule.js';

// Ported from Validator.validateTtrgGraph (graph rules, .ttrg only) and
// Validator.validateFileOrdering (file-ordering). The graph rules only apply to
// `.ttrg` documents that carry a `graph { … }` block, matching the old LSP gate.

function fileNameOf(uri: string): string {
  return uri.replace(/^.*[/\\]/, '').replace(/\.ttrg$/, '');
}

const graphMissingSchema: Rule = {
  id: 'graph-missing-schema',
  code: DiagnosticCode.RequiredPropertyMissing,
  category: 'graph',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'A graph block must declare a schema.',
  check(ctx) {
    if (ctx.scope !== 'document' || !ctx.uri.endsWith('.ttrg')) return;
    const graph = ctx.ast.graph;
    if (graph && !graph.schema) {
      ctx.report({ source: graph.source, message: "graph requires a 'schema' property (e.g. schema: er)" });
    }
  },
};

const graphObjectsEmpty: Rule = {
  id: 'graph-objects-empty',
  code: DiagnosticCode.GraphObjectsEmpty,
  category: 'graph',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A graph block has an empty objects list.',
  check(ctx) {
    if (ctx.scope !== 'document' || !ctx.uri.endsWith('.ttrg')) return;
    const graph = ctx.ast.graph;
    if (graph && graph.objects && graph.objects.length === 0) {
      ctx.report({ source: graph.source, message: 'Graph objects list is empty; the graph will render nothing' });
    }
  },
};

const graphNameMismatch: Rule = {
  id: 'graph-name-mismatch',
  code: DiagnosticCode.GraphNameMismatch,
  category: 'graph',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'The graph name should match its file name.',
  check(ctx) {
    if (ctx.scope !== 'document' || !ctx.uri.endsWith('.ttrg')) return;
    const graph = ctx.ast.graph;
    if (graph && graph.name && graph.name !== fileNameOf(ctx.uri)) {
      ctx.report({
        source: graph.source,
        message: `Graph name '${graph.name}' does not match file name '${fileNameOf(ctx.uri)}'`,
      });
    }
  },
};

const graphObjectNotFound: Rule = {
  id: 'graph-object-not-found',
  code: DiagnosticCode.GraphObjectNotFound,
  category: 'graph',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A graph object references a qname not present in the project.',
  check(ctx) {
    if (ctx.scope !== 'document' || !ctx.uri.endsWith('.ttrg')) return;
    const graph = ctx.ast.graph;
    if (!graph || !graph.objects) return;
    for (const qname of graph.objects) {
      if (!ctx.symbols.get(qname)) {
        ctx.report({ source: graph.source, message: `Graph object '${qname}' not found in project` });
      }
    }
  },
};

const graphLayoutStaleNode: Rule = {
  id: 'graph-layout-stale-node',
  code: DiagnosticCode.GraphLayoutStaleNode,
  category: 'graph',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'The layout references a node that is not in the objects list.',
  check(ctx) {
    if (ctx.scope !== 'document' || !ctx.uri.endsWith('.ttrg')) return;
    const graph = ctx.ast.graph;
    if (!graph || !graph.layout?.nodes) return;
    const objectQnames = new Set(graph.objects ?? []);
    for (const qname of Object.keys(graph.layout.nodes)) {
      if (!objectQnames.has(qname)) {
        ctx.report({ source: graph.source, message: `Layout references '${qname}' which is not in objects list` });
      }
    }
  },
};

const fileOrdering: Rule = {
  id: 'file-ordering',
  code: DiagnosticCode.FileOrdering,
  category: 'style',
  scope: 'document',
  defaultSeverity: 'warning',
  // Reports out-of-order top-level elements. Its autofix is owned by the
  // formatter (`ttr fmt` canonicalises order — design §7); no edit here.
  docs: 'Top-level elements should appear in canonical order (package → imports → schema/graph → defs).',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    const ast = ctx.ast;
    const schemaLine = ast.schemaDirective?.source?.line ?? Infinity;
    const graphLine = ast.graph?.source?.line ?? Infinity;
    const pkgLine = ast.packageDecl?.source?.line ?? Infinity;
    const firstImportLine = ast.imports?.[0]?.source?.line ?? Infinity;
    const firstDefLine = ast.definitions?.[0]?.source?.line ?? Infinity;
    const push = (message: string, source: SourceLocation): void => ctx.report({ source, message });

    if (pkgLine !== Infinity && firstImportLine !== Infinity && pkgLine > firstImportLine) {
      push('package declaration must appear before import declarations', ast.imports[0].source);
    }
    if (firstImportLine !== Infinity && schemaLine !== Infinity && firstImportLine > schemaLine) {
      push('import declarations must appear before schema directive', ast.schemaDirective!.source);
    }
    if (firstImportLine !== Infinity && graphLine !== Infinity && firstImportLine > graphLine) {
      push('import declarations must appear before graph block', ast.graph!.source);
    }
    if (schemaLine !== Infinity && firstDefLine !== Infinity && schemaLine > firstDefLine) {
      push('schema directive must appear before definitions', ast.definitions[0].source);
    }
    if (graphLine !== Infinity && firstDefLine !== Infinity && graphLine > firstDefLine) {
      push('graph block must appear before definitions', ast.definitions[0].source);
    }
  },
};

export const GRAPH_RULES: Rule[] = [
  graphMissingSchema,
  graphObjectsEmpty,
  graphNameMismatch,
  graphObjectNotFound,
  graphLayoutStaleNode,
  fileOrdering,
];
