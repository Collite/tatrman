import {
  DocumentSymbol,
  SymbolKind,
  Range,
} from 'vscode-languageserver';
import type { Document, Definition, GraphBlock } from '@modeler/parser';

export function buildDocumentSymbols(doc: Document): DocumentSymbol[] {
  if (doc.graph) {
    return buildGraphSymbols(doc.graph);
  }
  return buildTtrSymbols(doc);
}

function buildGraphSymbols(graph: GraphBlock): DocumentSymbol[] {
  const children: DocumentSymbol[] = graph.objects.map((qname) => ({
    name: qname,
    kind: SymbolKind.Class,
    range: graph.source ? toRange(graph.source) : { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
    selectionRange: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
    detail: 'graph object',
    children: [],
  }));

  return [{
    name: graph.name || 'Unnamed Graph',
    kind: SymbolKind.File,
    range: toRange(graph.source),
    selectionRange: { start: { line: graph.source.line - 1, character: graph.source.column }, end: { line: graph.source.line - 1, character: graph.source.column + (graph.name?.length ?? 0) } },
    detail: graph.schema ? `schema ${graph.schema}` : undefined,
    children,
  }];
}

function buildTtrSymbols(doc: Document): DocumentSymbol[] {
  const children: DocumentSymbol[] = [];

  if (doc.schemaDirective?.schemaCode) {
    const schemaKind = doc.schemaDirective.schemaCode;
    children.push({
      name: `${schemaKind}${doc.schemaDirective.namespace ? `.${doc.schemaDirective.namespace}` : ''}`,
      kind: SymbolKind.Namespace,
      range: doc.schemaDirective.source ? toRange(doc.schemaDirective.source) : { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
      selectionRange: doc.schemaDirective.source
        ? { start: { line: doc.schemaDirective.source.line - 1, character: doc.schemaDirective.source.column }, end: { line: doc.schemaDirective.source.line - 1, character: doc.schemaDirective.source.endColumn } }
        : { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
      detail: 'schema',
      children: doc.definitions.map((def) => buildDefSymbol(def)),
    });
  } else {
    children.push(...doc.definitions.map((def) => buildDefSymbol(def)));
  }

  if (doc.packageDecl) {
    return [{
      name: doc.packageDecl.name,
      kind: SymbolKind.Package,
      range: toRange(doc.packageDecl.source),
      selectionRange: { start: { line: doc.packageDecl.source.line - 1, character: doc.packageDecl.source.column }, end: { line: doc.packageDecl.source.line - 1, character: doc.packageDecl.source.endColumn } },
      detail: 'package',
      children,
    }];
  }

  return children;
}

function buildDefSymbol(def: Definition): DocumentSymbol {
  const kind = symbolKindOfDef(def.kind);
  const children: DocumentSymbol[] = [];

  const nestedKinds = nestedDefKinds(def);
  for (const [array, subKind] of nestedKinds) {
    const arr = (def as unknown as Record<string, unknown>)[array] as Definition[] | undefined;
    if (Array.isArray(arr)) {
      for (const child of arr) {
        children.push(buildNestedDefSymbol(child, subKind));
      }
    }
  }

  return {
    name: def.name,
    kind,
    range: toRange(def.source),
    selectionRange: { start: { line: def.source.line - 1, character: def.source.column }, end: { line: def.source.line - 1, character: def.source.endColumn } },
    detail: `def ${def.kind}`,
    children,
  };
}

function buildNestedDefSymbol(def: Definition, subKind: SymbolKind): DocumentSymbol {
  return {
    name: def.name,
    kind: subKind,
    range: toRange(def.source),
    selectionRange: { start: { line: def.source.line - 1, character: def.source.column }, end: { line: def.source.line - 1, character: def.source.endColumn } },
    detail: `def ${def.kind}`,
    children: [],
  };
}

function symbolKindOfDef(kind: string): SymbolKind {
  switch (kind) {
    case 'entity':
    case 'table':
    case 'view':
    case 'model':
      return SymbolKind.Class;
    case 'attribute':
    case 'column':
    case 'index':
    case 'constraint':
    case 'fk':
    case 'resultColumn':
      return SymbolKind.Field;
    case 'procedure':
    case 'query':
      return SymbolKind.Method;
    case 'relation':
      return SymbolKind.Interface;
    case 'role':
    case 'er2cncRole':
      return SymbolKind.Constant;
    default:
      return SymbolKind.Class;
  }
}

function nestedDefKinds(def: Definition): Array<[string, SymbolKind]> {
  switch (def.kind) {
    case 'entity':
      return [['attributes', SymbolKind.Field]];
    case 'table':
      return [['columns', SymbolKind.Field], ['indices', SymbolKind.Field], ['constraints', SymbolKind.Field]];
    case 'view':
      return [['columns', SymbolKind.Field]];
    case 'procedure':
      return [['resultColumns', SymbolKind.Field]];
    default:
      return [];
  }
}

function toRange(source: { line: number; column: number; endLine: number; endColumn: number }): Range {
  return {
    start: { line: source.line - 1, character: source.column },
    end: { line: source.endLine - 1, character: source.endColumn },
  };
}