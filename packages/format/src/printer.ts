import type {
  Document, Definition, SourceLocation, ParameterDef, GraphBlock,
} from '@modeler/parser';
import { Doc, text, verbatim, concat, line, hardline, indent, group, join } from './ir.js';
import { render } from './render.js';

export interface FormatConfig {
  separator: 'newline' | 'comma' | 'preserve';
  alignKeys: boolean;
  indentSpaces: number;
  width: number;
}

export const DEFAULT_FORMAT_CONFIG: FormatConfig = {
  separator: 'preserve',
  alignKeys: false,
  indentSpaces: 4,
  width: 100,
};

interface Ctx {
  source: string;
  config: FormatConfig;
}

function slice(src: string, loc: SourceLocation): string {
  return src.slice(loc.offsetStart, loc.offsetEnd);
}

/** Verbatim source text of any node that carries a `source` span. */
function vsrc(ctx: Ctx, node: { source: SourceLocation }): Doc {
  return verbatim(slice(ctx.source, node.source));
}

function strListDoc(arr: string[], quoted: boolean): Doc {
  const items = arr.map((s) => (quoted ? JSON.stringify(s) : s));
  return text(`[${items.join(', ')}]`);
}

interface Prop { key: string; value: Doc }

// The AST stores camelCase kinds, but the grammar's `def` keyword is snake_case
// for the mapping kinds.
const KIND_KEYWORD: Record<string, string> = {
  er2dbEntity: 'er2db_entity',
  er2dbAttribute: 'er2db_attribute',
  er2dbRelation: 'er2db_relation',
  er2cncRole: 'er2cnc_role',
};
function kindKeyword(kind: string): string {
  return KIND_KEYWORD[kind] ?? kind;
}

// Collect a def's present properties as ordered [key, valueDoc] pairs. Order is
// fixed (canonical) so formatting is deterministic / idempotent. Values that
// carry a `source` span are sliced verbatim; source-less primitives are
// reconstructed in their grammar form.
function propsOf(def: Definition, ctx: Ctx): Prop[] {
  const p: Prop[] = [];
  const add = (key: string, value: Doc | undefined) => { if (value) p.push({ key, value }); };
  const v = (n: { source: SourceLocation } | undefined) => (n ? vsrc(ctx, n) : undefined);
  const b = (x: boolean | undefined) => (x === undefined ? undefined : text(x ? 'true' : 'false'));
  const qstr = (x: string | undefined) => (x === undefined ? undefined : text(JSON.stringify(x)));
  const defList = (arr: Definition[] | undefined) => (arr && arr.length ? defListDoc(arr, ctx) : undefined);

  switch (def.kind) {
    case 'model':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true)); add('version', qstr(def.version));
      break;
    case 'table':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('primaryKey', def.primaryKey && strListDoc(def.primaryKey, true));
      add('columns', defList(def.columns)); add('indices', defList(def.indices)); add('constraints', defList(def.constraints));
      add('search', v(def.search));
      break;
    case 'view':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('columns', defList(def.columns)); add('definitionSql', v(def.definitionSql)); add('search', v(def.search));
      break;
    case 'column':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('type', v(def.type)); add('optional', b(def.optional)); add('isKey', b(def.isKey)); add('indexed', b(def.indexed));
      add('search', v(def.search));
      break;
    case 'index':
      // Grammar keyword for index/constraint type is `type` (DATA_TYPE token).
      add('description', v(def.description)); add('type', def.indexType && text(def.indexType));
      add('columns', def.columns && strListDoc(def.columns, true));
      break;
    case 'constraint':
      add('description', v(def.description)); add('type', def.constraintType && text(def.constraintType));
      add('columns', def.columns && strListDoc(def.columns, true));
      break;
    case 'fk':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('from', v(def.from)); add('to', v(def.to));
      break;
    case 'procedure':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('parameters', def.parameters && def.parameters.length ? paramListDoc(def.parameters, ctx) : undefined);
      add('resultColumns', defList(def.resultColumns));
      break;
    case 'entity':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('labelPlural', qstr(def.labelPlural)); add('nameAttribute', v(def.nameAttribute)); add('codeAttribute', v(def.codeAttribute));
      add('aliases', def.aliases && strListDoc(def.aliases, true)); add('attributes', defList(def.attributes));
      add('roles', def.roles && strListDoc(def.roles, false)); add('displayLabel', v(def.displayLabel)); add('search', v(def.search));
      break;
    case 'attribute':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('type', v(def.type)); add('isKey', b(def.isKey)); add('optional', b(def.optional));
      add('valueLabels', v(def.valueLabels)); add('displayLabel', v(def.displayLabel)); add('search', v(def.search));
      break;
    case 'relation':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('from', v(def.from)); add('to', v(def.to));
      add('cardinality', v(def.cardinality)); add('join', v(def.join)); add('search', v(def.search));
      break;
    case 'er2dbEntity':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('entity', v(def.entity)); add('target', v(def.target)); add('whereFilter', v(def.whereFilter));
      break;
    case 'er2dbAttribute':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('attribute', v(def.attribute)); add('target', v(def.target));
      break;
    case 'er2dbRelation':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('relation', v(def.relation)); add('fk', v(def.fk));
      break;
    case 'query':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('language', def.language && text(def.language));
      add('parameters', def.parameters && def.parameters.length ? paramListDoc(def.parameters, ctx) : undefined);
      add('sourceText', v(def.sourceText)); add('search', v(def.search));
      break;
    case 'role':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('label', v(def.label)); add('search', v(def.search));
      break;
    case 'er2cncRole':
      add('description', v(def.description)); add('tags', def.tags && strListDoc(def.tags, true));
      add('entity', v(def.entity)); add('role', v(def.role));
      break;
  }
  return p;
}

function paramListDoc(params: ParameterDef[], ctx: Ctx): Doc {
  // Parameters carry a source span each; slice verbatim, one per line.
  const items = params.map((pm) => vsrc(ctx, pm));
  return concat([
    text('['),
    indent(concat([hardline, join(items, concat([text(','), hardline]))])),
    hardline,
    text(']'),
  ]);
}

function defListDoc(defs: Definition[], ctx: Ctx): Doc {
  const items = defs.map((d) => formatDef(d, ctx, false));
  return concat([
    text('['),
    indent(concat([hardline, join(items, concat([text(','), hardline]))])),
    hardline,
    text(']'),
  ]);
}

function propsDoc(props: Prop[], ctx: Ctx, broken: boolean): Doc {
  const align = ctx.config.alignKeys && broken;
  const maxKey = align ? Math.max(...props.map((pr) => pr.key.length)) : 0;
  const entry = (pr: Prop): Doc =>
    concat([text(align ? `${pr.key}:`.padEnd(maxKey + 2) : `${pr.key}: `), pr.value]);
  const sep = concat([text(','), broken ? hardline : line]);
  return join(props.map(entry), sep);
}

/** A def's properties on one line: `k: v, k2: v2`. */
function inlinePropsDoc(props: Prop[]): Doc {
  return join(props.map((pr) => concat([text(`${pr.key}: `), pr.value])), text(', '));
}

function formatDef(def: Definition, ctx: Ctx, topLevel: boolean): Doc {
  const props = propsOf(def, ctx);
  const header = `def ${kindKeyword(def.kind)} ${def.name} {`;
  if (props.length === 0) return text(`def ${kindKeyword(def.kind)} ${def.name} {}`);

  if (!topLevel) {
    // Nested defs are width-based: the group renders flat if it fits, else breaks.
    const body = concat([indent(concat([line, propsDoc(props, ctx, false)])), line, text('}')]);
    return group(concat([text(header), body]));
  }

  // Top-level layout is governed by the `separator` setting:
  //   'newline'  → always one property per line
  //   'comma'    → always a single line (forced inline, regardless of width)
  //   'preserve' → break iff the original def spanned multiple lines
  const broken = ctx.config.separator === 'newline'
    ? true
    : ctx.config.separator === 'comma'
      ? false
      : def.source.line !== def.source.endLine;

  if (!broken) {
    return concat([text(header), text(' '), inlinePropsDoc(props), text(' }')]);
  }
  const body = concat([indent(concat([hardline, propsDoc(props, ctx, true)])), hardline, text('}')]);
  return concat([text(header), body]);
}

function formatGraph(graph: GraphBlock): Doc {
  const props: Prop[] = [];
  if (graph.schema) props.push({ key: 'schema', value: text(graph.schema) });
  if (graph.description !== undefined) props.push({ key: 'description', value: text(JSON.stringify(graph.description)) });
  if (graph.tags && graph.tags.length) props.push({ key: 'tags', value: strListDoc(graph.tags, true) });
  // objects always break one-per-line.
  const objects = concat([
    text('['),
    indent(concat([hardline, join(graph.objects.map((o) => text(o)), concat([text(','), hardline]))])),
    hardline,
    text(']'),
  ]);
  props.push({ key: 'objects', value: graph.objects.length ? objects : text('[]') });
  if (graph.layout) props.push({ key: 'layout', value: formatLayout(graph.layout) });

  const sep = concat([text(','), hardline]);
  const entry = (pr: Prop) => concat([text(`${pr.key}: `), pr.value]);
  return concat([
    text(`graph ${graph.name} {`),
    indent(concat([hardline, join(props.map(entry), sep)])),
    hardline,
    text('}'),
  ]);
}

function formatLayout(layout: NonNullable<GraphBlock['layout']>): Doc {
  const nodeKeys = Object.keys(layout.nodes);
  const nodeEntries = nodeKeys.map((q) => text(`${q}: { x: ${layout.nodes[q].x}, y: ${layout.nodes[q].y} }`));
  const nodesDoc = nodeKeys.length
    ? concat([text('{'), indent(concat([hardline, join(nodeEntries, concat([text(','), hardline]))])), hardline, text('}')])
    : text('{}');
  const items: Doc[] = [];
  if (layout.viewport) {
    const vp = layout.viewport;
    items.push(text(`viewport: { zoom: ${vp.zoom}, panX: ${vp.panX}, panY: ${vp.panY}, displayMode: ${JSON.stringify(vp.displayMode)} }`));
  }
  items.push(concat([text('nodes: '), nodesDoc]));
  return concat([text('{'), indent(concat([hardline, join(items, concat([text(','), hardline]))])), hardline, text('}')]);
}

export function formatDocument(ast: Document, source: string, config: FormatConfig = DEFAULT_FORMAT_CONFIG): string {
  const ctx: Ctx = { source, config };
  const blocks: string[] = [];

  if (ast.packageDecl) blocks.push(`package ${ast.packageDecl.name}`);
  if (ast.imports.length) {
    blocks.push(ast.imports.map((imp) => `import ${imp.target}${imp.wildcard ? '.*' : ''}`).join('\n'));
  }
  if (ast.schemaDirective) {
    const sd = ast.schemaDirective;
    blocks.push(`schema ${sd.schemaCode}${sd.namespace ? ` namespace ${sd.namespace}` : ''}`);
  }
  if (ast.graph) {
    blocks.push(render(formatGraph(ast.graph), { width: config.width, indentSpaces: config.indentSpaces }));
  }
  for (const def of ast.definitions) {
    blocks.push(render(formatDef(def, ctx, true), { width: config.width, indentSpaces: config.indentSpaces }));
  }

  return blocks.join('\n\n') + '\n';
}
