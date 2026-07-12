// SPDX-License-Identifier: Apache-2.0
import Ajv2020Module from 'ajv/dist/2020.js';
import type { Definition, Document, EntityDef, ObjectValue, Reference, SimpleDataType, StructuredDataType, RoleDef, GraphBlock } from '@tatrman/parser';
import type { ProjectSymbolTable, Resolver, ReferenceIndex, ResolvedManifest } from '@tatrman/semantics';
import { buildCanonicalKey } from '@tatrman/semantics';

export type RenderableSchemaCode = 'db' | 'er';
export type DisplayMode = 'just-names' | 'with-types' | 'with-constraints';
export type SchemaCode = 'db' | 'er' | 'binding' | 'query' | 'cnc';

export type Cardinality = 'one' | 'zero-or-one' | 'many' | 'one-or-many';

export interface ModelGraphNode {
  qname: string;
  kind: 'table' | 'view' | 'entity';
  name: string;
  schemaCode: RenderableSchemaCode;
  label: string;
  sourceUri: string;
  sourceLocation: { line: number; column: number };
  rows: ModelGraphRow[];
}

export interface ModelGraphRow {
  name: string;
  qname: string;
  kind: 'column' | 'attribute';
  type: string | null;
  isKey: boolean;
  optional: boolean;
  isNameAttribute: boolean;
  isCodeAttribute: boolean;
}

export interface ModelGraphEdge {
  id: string;
  qname: string;
  kind: 'fk' | 'relation';
  fromNode: string;
  toNode: string;
  fromCardinality: Cardinality | null;
  toCardinality: Cardinality | null;
  sourceUri: string;
  sourceLocation: { line: number; column: number };
}

export interface ModelGraph {
  schemaCode: RenderableSchemaCode;
  nodes: ModelGraphNode[];
  edges: ModelGraphEdge[];
}

export type DataType = DataTypeSimple | DataTypeStructured | undefined;
export interface DataTypeSimple { kind: 'simple'; name: string }
export interface DataTypeStructured { kind: 'structured'; typeName: string; length?: number; precision?: number }

export function renderDataType(t: DataType | SimpleDataType | StructuredDataType | undefined): string | null {
  if (!t) return null;
  if (t.kind === 'simple') return t.name;
  if (t.kind === 'structured') {
    const parts: string[] = [];
    if (typeof t.length === 'number') parts.push(String(t.length));
    if (typeof t.precision === 'number') parts.push(String(t.precision));
    return parts.length === 0 ? t.typeName : `${t.typeName}(${parts.join(',')})`;
  }
  return null;
}

export function parseCardinality(s: string): Cardinality | null {
  switch (s) {
    case '1': return 'one';
    case '0..1': return 'zero-or-one';
    case '0..*':
    case 'n':
    case '*': return 'many';
    case '1..n':
    case '1..*': return 'one-or-many';
    default: return null;
  }
}

export function extractCardinality(obj: ObjectValue | undefined): { from: Cardinality | null; to: Cardinality | null } {
  if (!obj) return { from: null, to: null };
  const lookup = (key: string): Cardinality | null => {
    const entry = obj.entries.find((e) => e.key === key);
    if (!entry || entry.value.kind !== 'string') return null;
    return parseCardinality(entry.value.value);
  };
  return { from: lookup('from'), to: lookup('to') };
}

function buildEdgeForDef(
  def: Definition,
  schemaCode: string,
  namespace: string,
  knownQnames: Set<string>,
  packageName = '',
): ModelGraphEdge | null {
  const defQname = buildQname(schemaCode, namespace, def.kind, [def.name], packageName);
  if (def.kind === 'fk') {
    const fromQname = extractFkRef(def.from, schemaCode, namespace, knownQnames, packageName);
    const toQname = extractFkRef(def.to, schemaCode, namespace, knownQnames, packageName);
    if (fromQname && toQname) {
      return {
        id: defQname,
        qname: defQname,
        kind: 'fk',
        fromNode: fromQname,
        toNode: toQname,
        fromCardinality: null,
        toCardinality: null,
        sourceUri: def.source.file,
        sourceLocation: { line: def.source.line, column: def.source.column },
      };
    }
  } else if (def.kind === 'relation') {
    const fromRef = def.from?.kind === 'id' ? { path: def.from.path, parts: def.from.parts } : null;
    const toRef = def.to?.kind === 'id' ? { path: def.to.path, parts: def.to.parts } : null;
    const fromQname = fromRef ? resolveRef(fromRef, schemaCode, namespace, knownQnames, packageName) : null;
    const toQname = toRef ? resolveRef(toRef, schemaCode, namespace, knownQnames, packageName) : null;
    if (fromQname && toQname) {
      const card = extractCardinality(def.cardinality);
      return {
        id: defQname,
        qname: defQname,
        kind: 'relation',
        fromNode: fromQname,
        toNode: toQname,
        fromCardinality: card.from,
        toCardinality: card.to,
        sourceUri: def.source.file,
        sourceLocation: { line: def.source.line, column: def.source.column },
      };
    }
  }
  return null;
}

// Layout sidecar types
export interface ViewportState {
  zoom: number;
  panX: number;
  panY: number;
  displayMode: DisplayMode;
}

export interface LayoutFile {
  version: 1;
  viewport?: ViewportState;
  nodes: Record<string, { x: number; y: number }>;
  edges: Record<string, { bendPoints: Array<[number, number]> }>;
}

export function emptyLayout(): LayoutFile {
  return {
    version: 1,
    nodes: {},
    edges: {},
  };
}

const layoutSchema = {
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  $id: 'https://tatrman.org/schemas/layout/1.json',
  title: 'Tatrman Modeler layout sidecar',
  type: 'object',
  required: ['version', 'nodes', 'edges'],
  additionalProperties: false,
  properties: {
    version: { const: 1 },
    viewport: { $ref: '#/$defs/viewport' },
    nodes: {
      type: 'object',
      patternProperties: {
        '^.+$': {
          type: 'object',
          required: ['x', 'y'],
          additionalProperties: false,
          properties: { x: { type: 'number' }, y: { type: 'number' } },
        },
      },
      additionalProperties: false,
    },
    edges: {
      type: 'object',
      patternProperties: {
        '^.+$': {
          type: 'object',
          required: ['bendPoints'],
          additionalProperties: false,
          properties: {
            bendPoints: {
              type: 'array',
              items: { type: 'array', items: { type: 'number' }, minItems: 2, maxItems: 2 },
            },
          },
        },
      },
      additionalProperties: false,
    },
  },
  $defs: {
    viewport: {
      type: 'object',
      required: ['zoom', 'panX', 'panY', 'displayMode'],
      additionalProperties: false,
      properties: {
        zoom: { type: 'number', exclusiveMinimum: 0 },
        panX: { type: 'number' },
        panY: { type: 'number' },
        displayMode: { enum: ['just-names', 'with-types', 'with-constraints'] },
      },
    },
  },
};

// ajv/dist/2020.js has a default export but TypeScript can't infer the constructor
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const AjvClass = (Ajv2020Module as any).default ?? Ajv2020Module;
const ajv = new AjvClass({ strict: false });
const validateLayoutFn = ajv.compile(layoutSchema);

export function validateLayout(unknown: unknown): LayoutFile | null {
  if (validateLayoutFn(unknown)) return unknown as LayoutFile;
  return null;
}

// Symbol detail types
export type PerKindData =
  | { kind: 'table'; columns: ModelGraphRow[]; primaryKey: string[] }
  | { kind: 'view'; columns: ModelGraphRow[] }
  | { kind: 'entity'; attributes: ModelGraphRow[]; nameAttributeQname: string | null; codeAttributeQname: string | null; roleQnames: string[] }
  | { kind: 'fk'; fromQname: string; toQname: string }
  | { kind: 'relation'; fromQname: string; toQname: string; fromCardinality: Cardinality | null; toCardinality: Cardinality | null }
  | { kind: 'role'; labelByLanguage: Record<string, string> }
  | { kind: 'query' }
  | { kind: 'er2dbEntity'; entityQname: string; targetDescription: string }
  | { kind: 'er2dbAttribute'; attributeQname: string; targetDescription: string }
  | { kind: 'er2dbRelation'; relationQname: string; fkQname: string }
  | { kind: 'er2cncRole'; entityQname: string; roleQname: string }
  | { kind: 'other' };

export interface SymbolDetail {
  qname: string;
  kind: Definition['kind'];
  name: string;
  label: string;
  description: string | null;
  tags: string[];
  sourceUri: string;
  sourceLine: number;
  perKindData: PerKindData;
  referencedBy: Array<{ qname: string; sourceUri: string; sourceLine: number }>;
}

// v4.0 uniform key. The model/schema/kind come from the owning def's `kind`
// (a column/attribute row passes its parent table/entity kind); `namespace` is
// the file `schema` id (db only). `schemaCode` (the file model directive) is
// retained in the signature for call-site symmetry but is not part of the key.
function buildQname(_schemaCode: string, namespace: string, kind: string, parts: string[], packageName = ''): string {
  return buildCanonicalKey({ packageName, schemaId: namespace, kind, parts });
}

/**
 * Render an er2db `target:` as a short display string for the graph.
 * Recognises the three target-object keys — `table`, `view`, `query` — and the
 * bare-reference form (`target: db.dbo.X`, treated as a table). `target` is an
 * `ObjectValue | Reference`, so we read its `entries`, not flat properties.
 */
export function er2dbTargetDescription(target: ObjectValue | Reference | undefined): string {
  if (!target) return '';
  if ('kind' in target && target.kind === 'object') {
    for (const key of ['table', 'view', 'query'] as const) {
      const entry = target.entries.find((e) => e.key === key);
      if (entry && entry.value.kind === 'id') return `${key}:${entry.value.path}`;
    }
    return '';
  }
  // Bare reference form: `target: db.dbo.X` → behaves as a table target.
  return 'path' in target ? `table:${target.path}` : '';
}

function getDisplayLabel(def: Definition, preferredLang: string): string {
  if (def.kind === 'entity') {
    const entity = def as EntityDef;
    if (entity.displayLabel && entity.displayLabel.kind === 'localizedString') {
      const entry = entity.displayLabel.entries[preferredLang];
      if (entry) return entry;
    }
  }
  if (def.kind === 'table' && def.description) {
    if (def.description.kind === 'string' || def.description.kind === 'tripleString') {
      return def.description.value;
    }
  }
  return def.name;
}

function getDescription(def: Definition): string | null {
  if ('description' in def && def.description) {
    if (def.description.kind === 'string' || def.description.kind === 'tripleString') {
      return def.description.value;
    }
  }
  return null;
}

function buildSymbolDetailForDef(
  def: Definition,
  schemaCode: string,
  namespace: string,
  preferredLang: string,
  documentUri: string,
  refIndex: ReferenceIndex,
  packageName = ''
): SymbolDetail {
  const qname = buildQname(schemaCode, namespace, def.kind, [def.name], packageName);
  const tags: string[] = ('tags' in def ? (def as { tags?: string[] }).tags : []) ?? [];

  let perKindData: PerKindData = { kind: 'other' };
  if (def.kind === 'table' && def.columns) {
    const columns = (def.columns ?? []).map(col => ({
      name: col.name,
      qname: buildQname(schemaCode, namespace, def.kind, [def.name, col.name], packageName),
      kind: 'column' as const,
      type: renderDataType(col.type),
      isKey: !!(col.isKey || (def.primaryKey ?? []).includes(col.name)),
      optional: !!col.optional,
      isNameAttribute: false,
      isCodeAttribute: false,
    }));
    perKindData = { kind: 'table', columns, primaryKey: def.primaryKey ?? [] };
  } else if (def.kind === 'view' && def.columns) {
    const columns = (def.columns ?? []).map(col => ({
      name: col.name,
      qname: buildQname(schemaCode, namespace, def.kind, [def.name, col.name], packageName),
      kind: 'column' as const,
      type: renderDataType(col.type),
      isKey: false,
      optional: !!col.optional,
      isNameAttribute: false,
      isCodeAttribute: false,
    }));
    perKindData = { kind: 'view', columns };
  } else if (def.kind === 'entity') {
    const nameAttrPath = def.nameAttribute?.path;
    const codeAttrPath = def.codeAttribute?.path;
    const attributes = (def.attributes ?? []).map(attr => ({
      name: attr.name,
      qname: buildQname(schemaCode, namespace, def.kind, [def.name, attr.name], packageName),
      kind: 'attribute' as const,
      type: renderDataType(attr.type),
      isKey: !!attr.isKey,
      optional: !!attr.optional,
      isNameAttribute: attr.name === nameAttrPath,
      isCodeAttribute: attr.name === codeAttrPath,
    }));
    perKindData = {
      kind: 'entity',
      attributes,
      nameAttributeQname: def.nameAttribute ? buildQname(schemaCode, namespace, def.kind, [def.name, def.nameAttribute.path], packageName) : null,
      codeAttributeQname: def.codeAttribute ? buildQname(schemaCode, namespace, def.kind, [def.name, def.codeAttribute.path], packageName) : null,
      roleQnames: def.roles ?? [],
    };
  } else if (def.kind === 'fk') {
    const fromRef = def.from?.kind === 'id' ? { path: def.from.path, parts: def.from.parts } : null;
    const toRef = def.to?.kind === 'id' ? { path: def.to.path, parts: def.to.parts } : null;
    const fromQname = fromRef ? [schemaCode, namespace, fromRef.path].filter(s => s !== '').join('.') : null;
    const toQname = toRef ? [schemaCode, namespace, toRef.path].filter(s => s !== '').join('.') : null;
    perKindData = { kind: 'fk', fromQname: fromQname ?? '', toQname: toQname ?? '' };
  } else if (def.kind === 'relation') {
    const fromRef = def.from?.kind === 'id' ? { path: def.from.path, parts: def.from.parts } : null;
    const toRef = def.to?.kind === 'id' ? { path: def.to.path, parts: def.to.parts } : null;
    const fromQname = fromRef ? [schemaCode, namespace, fromRef.path].filter(s => s !== '').join('.') : null;
    const toQname = toRef ? [schemaCode, namespace, toRef.path].filter(s => s !== '').join('.') : null;
    const card = extractCardinality(def.cardinality);
    perKindData = { kind: 'relation', fromQname: fromQname ?? '', toQname: toQname ?? '', fromCardinality: card.from, toCardinality: card.to };
  } else if (def.kind === 'role') {
    const labelByLanguage: Record<string, string> = {};
    const role = def as RoleDef;
    if (role.label && role.label.kind === 'localizedString') {
      Object.assign(labelByLanguage, role.label.entries);
    }
    perKindData = { kind: 'role', labelByLanguage };
  } else if (def.kind === 'query') {
    perKindData = { kind: 'query' };
  } else if (def.kind === 'er2dbEntity') {
    const e2 = def as { entity?: { path: string }; target?: ObjectValue | Reference };
    const entityRef = e2.entity?.path ?? '';
    const targetDesc = er2dbTargetDescription(e2.target);
    perKindData = { kind: 'er2dbEntity', entityQname: entityRef, targetDescription: targetDesc };
  } else if (def.kind === 'er2dbAttribute') {
    const e2a = def as { attribute?: { path: string }; target?: ObjectValue | Reference };
    const attrRef = e2a.attribute?.path ?? '';
    const targetDesc = er2dbTargetDescription(e2a.target);
    perKindData = { kind: 'er2dbAttribute', attributeQname: attrRef, targetDescription: targetDesc };
  } else if (def.kind === 'er2dbRelation') {
    const e2r = def as { relation?: { path: string }; fk?: { path: string } };
    perKindData = {
      kind: 'er2dbRelation',
      relationQname: e2r.relation?.path ?? '',
      fkQname: e2r.fk?.path ?? '',
    };
  } else if (def.kind === 'er2cncRole') {
    const e2c = def as { entity?: { path: string }; role?: { path: string } };
    perKindData = {
      kind: 'er2cncRole',
      entityQname: e2c.entity?.path ?? '',
      roleQname: e2c.role?.path ?? '',
    };
  }

  const seen = new Set<string>();
  const referencedBy = refIndex.findByQname(qname).map(loc => ({
    qname: loc.referrerQname ?? loc.targetQname,
    sourceUri: loc.documentUri,
    sourceLine: loc.source.line,
  })).filter(loc => {
    if (seen.has(loc.qname)) return false;
    seen.add(loc.qname);
    return true;
  });

  return {
    qname,
    kind: def.kind,
    name: def.name,
    label: getDisplayLabel(def, preferredLang),
    description: getDescription(def),
    tags,
    sourceUri: documentUri,
    sourceLine: def.source.line,
    perKindData,
    referencedBy,
  };
}

export function buildSymbolDetail(
  qname: string,
  symbols: ProjectSymbolTable,
  resolver: Resolver,
  refIndex: ReferenceIndex,
  manifest: ResolvedManifest,
  getDocument: (uri: string) => string | null,
  parseDocument: (content: string, uri: string) => { ast?: Document | null }
): SymbolDetail | null {
  const symbol = symbols.get(qname);
  if (!symbol) return null;

  // The v4.0 key is `[pkg.]<model>.<schema?>.<kind>.<name>`. Recover the def by
  // its name + kind (recorded on the symbol entry), and the db schema handle (db
  // only) from the segment right after the `db` model segment. The package and
  // schema are threaded back into the key builders so the detail's qnames match
  // the canonical symbol-table keys everywhere else.
  const localQname = symbol.packageName && qname.startsWith(`${symbol.packageName}.`)
    ? qname.slice(symbol.packageName.length + 1)
    : qname;
  const localSegs = localQname.split('.');
  const schemaCode = localSegs[0] ?? 'db';
  const schemaId = schemaCode === 'db' ? (localSegs[1] ?? '') : '';

  const realDef = findDefByQname(symbol.documentUri, symbol.name, symbol.kind, getDocument, parseDocument);
  if (!realDef) return null;

  const detail = buildSymbolDetailForDef(
    realDef,
    schemaCode,
    schemaId,
    manifest.preferredLanguage,
    symbol.documentUri,
    refIndex,
    symbol.packageName
  );
  // The builder already produces the canonical package-qualified key; keep the
  // asked-for qname verbatim so callers key the detail by the same id.
  if (detail) detail.qname = qname;
  return detail;
}

// v1 limitation: only top-level defs (table / view / entity / role / relation) are
// looked up by their name + kind. Nested members (columns/attributes) are not
// resolved here; the Designer inspector only opens on top-level nodes in v1.
function findDefByQname(
  uri: string,
  name: string,
  // Accepts nested world-member kinds too (v4.1); they never match a top-level
  // def, so lookups for them return null.
  kind: Definition['kind'] | 'engine' | 'executor' | 'storage' | 'worldSchema',
  getDocument: (uri: string) => string | null,
  parseDocument: (content: string, uri: string) => { ast?: Document | null }
): Definition | null {
  const content = getDocument(uri);
  if (!content) return null;
  const result = parseDocument(content, uri);
  if (!result.ast) return null;
  for (const def of result.ast.definitions) {
    if (def.name === name && def.kind === kind) return def;
  }
  return null;
}

function resolveRef(ref: { path: string; parts: string[] }, schemaCode: string, namespace: string, knownQnames: Set<string>, packageName = ''): string | null {
  const parts = ref.parts;
  // 1. Ref as written: already fully/cross-package qualified (v1.1) or v1
  //    non-package (`er.entity.x`, `db.dbo.TABLE.COLUMN`). Drop trailing parts
  //    until a known qname matches, so an FK column ref collapses to its table
  //    node (FKs target columns but the edge is node-to-node).
  for (let i = parts.length; i >= 1; i--) {
    const candidate = parts.slice(0, i).join('.');
    if (knownQnames.has(candidate)) return candidate;
  }
  // 2. Package-relative ref (e.g. `er.entity.artikl` written inside package
  //    `billing.invoicing`): prefix the current package, again dropping trailing
  //    parts. This is the common case in packaged projects, where the .ttrg
  //    object set is package-qualified but same-package refs are not.
  if (packageName) {
    for (let i = parts.length; i >= 1; i--) {
      const candidate = `${packageName}.${parts.slice(0, i).join('.')}`;
      if (knownQnames.has(candidate)) return candidate;
    }
  }
  // 3. Bare ref relative to the current schema/namespace: `TABLE` or `TABLE.COLUMN`.
  for (let i = 1; i <= parts.length; i++) {
    const base = [schemaCode, namespace, ...parts.slice(0, i)].filter(s => s !== '').join('.');
    if (knownQnames.has(base)) return base;
    if (packageName && knownQnames.has(`${packageName}.${base}`)) return `${packageName}.${base}`;
  }
  // 4. v4.0: the node key carries a kind segment the written ref omits
  //    (`db.dbo.PRODUCTS` → `db.dbo.table.PRODUCTS`). Collapse to the node whose
  //    trailing name matches — trying the deepest name part first, so an fk
  //    column ref (`…PRODUCTS.PRODUCT_ID`) falls back to its table node.
  for (let j = parts.length - 1; j >= 0; j--) {
    const name = parts[j];
    for (const q of knownQnames) {
      if (q === name || q.endsWith(`.${name}`)) return q;
    }
  }
  return null;
}

export function buildModelGraph(ast: Document, schema: RenderableSchemaCode, preferredLang = 'en'): ModelGraph {
  return buildProjectModelGraph([ast], schema, preferredLang);
}

// computeGraphEdges: objects in .ttrg are expected to be fully-qualified qnames
// (contract §7.1, decision D2). Endpoints resolve via resolveRef against the
// objects set -- NOT the six-step Resolver. Bare/wildcard-imported objects in
// a .ttrg will not resolve here.
export function computeGraphEdges(
  graph: GraphBlock,
  asts: Document[],
  _qnameToDef?: Map<string, { def: Definition; schemaCode: string; namespace: string; packageName: string }>
): ModelGraphEdge[] {
  const objectSet = new Set(graph.objects ?? []);
  if (objectSet.size === 0) return [];

  const edges: ModelGraphEdge[] = [];

  for (const ast of asts) {
    const schemaCode = ast.modelDirective?.modelCode ?? 'er';
    const namespace = ast.modelDirective?.schema ?? '';
    const packageName = ast.packageDecl?.name ?? '';

    for (const def of ast.definitions) {
      const defQname = buildQname(schemaCode, namespace, def.kind, [def.name], packageName);
      if (!objectSet.has(defQname)) continue;
      const edge = buildEdgeForDef(def, schemaCode, namespace, objectSet, packageName);
      if (edge) edges.push(edge);
    }
  }

  return edges;
}

export function buildNodeForDef(
  def: Definition,
  schemaCode: string,
  namespace: string,
  preferredLang: string,
  packageName = '',
): ModelGraphNode | null {
  if (def.kind === 'table') {
    const rows: ModelGraphRow[] = (def.columns ?? []).map(col => ({
      name: col.name,
      qname: buildQname(schemaCode, namespace, def.kind, [def.name, col.name], packageName),
      kind: 'column' as const,
      type: renderDataType(col.type),
      isKey: !!(col.isKey || (def.primaryKey ?? []).includes(col.name)),
      optional: !!col.optional,
      isNameAttribute: false,
      isCodeAttribute: false,
    }));
    return {
      qname: buildQname(schemaCode, namespace, def.kind, [def.name], packageName),
      kind: 'table',
      name: def.name,
      schemaCode: schemaCode as RenderableSchemaCode,
      label: def.name,
      sourceUri: def.source.file,
      sourceLocation: { line: def.source.line, column: def.source.column },
      rows,
    };
  } else if (def.kind === 'view') {
    const rows: ModelGraphRow[] = (def.columns ?? []).map(col => ({
      name: col.name,
      qname: buildQname(schemaCode, namespace, def.kind, [def.name, col.name], packageName),
      kind: 'column' as const,
      type: renderDataType(col.type),
      isKey: false,
      optional: !!col.optional,
      isNameAttribute: false,
      isCodeAttribute: false,
    }));
    return {
      qname: buildQname(schemaCode, namespace, def.kind, [def.name], packageName),
      kind: 'view',
      name: def.name,
      schemaCode: schemaCode as RenderableSchemaCode,
      label: def.name,
      sourceUri: def.source.file,
      sourceLocation: { line: def.source.line, column: def.source.column },
      rows,
    };
  } else if (def.kind === 'entity') {
    const entity = def as EntityDef;
    const nameAttrPath = entity.nameAttribute?.path;
    const codeAttrPath = entity.codeAttribute?.path;
    const rows: ModelGraphRow[] = (entity.attributes ?? []).map(attr => ({
      name: attr.name,
      qname: buildQname(schemaCode, namespace, def.kind, [def.name, attr.name], packageName),
      kind: 'attribute' as const,
      type: renderDataType(attr.type),
      isKey: !!attr.isKey,
      optional: !!attr.optional,
      isNameAttribute: attr.name === nameAttrPath,
      isCodeAttribute: attr.name === codeAttrPath,
    }));
    return {
      qname: buildQname(schemaCode, namespace, def.kind, [def.name], packageName),
      kind: 'entity',
      name: def.name,
      schemaCode: schemaCode as RenderableSchemaCode,
      label: getDisplayLabel(def, preferredLang),
      sourceUri: def.source.file,
      sourceLocation: { line: def.source.line, column: def.source.column },
      rows,
    };
  }
  return null;
}

export function buildProjectModelGraph(asts: Document[], schema: RenderableSchemaCode, preferredLang = 'en'): ModelGraph {
  const nodes: ModelGraphNode[] = [];
  const edges: ModelGraphEdge[] = [];
  const knownNodes = new Map<string, { def: Definition; qname: string; schemaCode: string; namespace: string; packageName: string }>();

  for (const ast of asts) {
    if (ast.modelDirective?.modelCode && ast.modelDirective.modelCode !== schema) continue;

    const schemaCode = ast.modelDirective?.modelCode ?? schema;
    const namespace = ast.modelDirective?.schema ?? '';
    const packageName = ast.packageDecl?.name ?? '';

    for (const def of ast.definitions) {
      if (def.kind === 'table' || def.kind === 'view' || def.kind === 'entity') {
        const qname = buildQname(schemaCode, namespace, def.kind, [def.name], packageName);
        knownNodes.set(qname, { def, qname, schemaCode, namespace, packageName });
      }
    }
  }

  const knownQnames = new Set(knownNodes.keys());

  for (const [, { def, schemaCode, namespace, packageName }] of knownNodes) {
    const node = buildNodeForDef(def, schemaCode, namespace, preferredLang, packageName);
    if (node) nodes.push(node);
  }

  for (const ast of asts) {
    if (ast.modelDirective?.modelCode && ast.modelDirective.modelCode !== schema) continue;

    const schemaCode = ast.modelDirective?.modelCode ?? schema;
    const namespace = ast.modelDirective?.schema ?? '';
    const packageName = ast.packageDecl?.name ?? '';

    for (const def of ast.definitions) {
      if (def.kind === 'fk' || def.kind === 'relation') {
        const edge = buildEdgeForDef(def, schemaCode, namespace, knownQnames, packageName);
        if (edge) edges.push(edge);
      }
    }
  }

  return { schemaCode: schema as RenderableSchemaCode, nodes, edges };
}

// FK edges are table-to-table; we pick the first column to derive the source
// table — multi-column FKs collapse to one edge.
function extractFkRef(pv: import('@tatrman/parser').PropertyValue | undefined, schemaCode: string, namespace: string, knownQnames: Set<string>, packageName = ''): string | null {
  if (!pv) return null;
  if (pv.kind === 'id') {
    return resolveRef({ path: pv.path, parts: pv.parts }, schemaCode, namespace, knownQnames, packageName);
  }
  if (pv.kind === 'list' && pv.items[0]?.kind === 'id') {
    return resolveRef({ path: pv.items[0].path, parts: pv.items[0].parts }, schemaCode, namespace, knownQnames, packageName);
  }
  return null;
}