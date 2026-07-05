/**
 * TS side of the conformance harness. Produces the normalised JSON dump
 * (contracts.md §5) that must be byte-identical to the Kotlin
 * `ConformanceDump.dump` output. See `packages/kotlin/ttr-parser/.../ConformanceDump.kt`
 * for the canonical spec; the two MUST be kept in lock-step (AST-NAMING.md).
 *
 * Normalisation: `kind` = TTR keyword, property names = TTR surface names, no
 * SourceLocation, object keys sorted alphabetically (recursively), present-only
 * properties (false booleans / empty lists & objects omitted), whole numbers as
 * integers.
 */
import type {
  ParseResult,
  Definition,
  PropertyValue,
  ObjectEntry,
  DataType,
  SearchBlock,
  LocalizedString,
  LocalizedStringList,
  ValueLabels,
  ParameterDef,
  BindingProperty,
  BindingColumnEntry,
  Reference,
  ObjectValue,
  EngineDef,
  ExecutorDef,
  StorageDef,
  WorldSchemaDef,
} from '@modeler/parser';

type Json = string | number | boolean | null | Json[] | { [k: string]: Json };

const KIND_KEYWORD: Record<string, string> = {
  model: 'model',
  table: 'table',
  view: 'view',
  column: 'column',
  index: 'index',
  constraint: 'constraint',
  fk: 'fk',
  procedure: 'procedure',
  entity: 'entity',
  attribute: 'attribute',
  relation: 'relation',
  er2dbEntity: 'er2db_entity',
  er2dbAttribute: 'er2db_attribute',
  er2dbRelation: 'er2db_relation',
  query: 'query',
  role: 'role',
  er2cncRole: 'er2cnc_role',
  drillMap: 'drill_map',
  world: 'world',
};

export function dump(result: ParseResult): string {
  return print(dumpTree(result));
}

export function dumpTree(result: ParseResult): Json {
  const ast = result.ast;
  const sd = ast?.modelDirective;
  return {
    schemaDirective: sd ? { code: sd.modelCode, namespace: sd.schema ?? null } : null,
    package: ast?.packageDecl?.name ?? null,
    imports: (ast?.imports ?? []).map((i) => ({ target: i.target, wildcard: i.wildcard })),
    definitions: (ast?.definitions ?? []).map(defTree),
  };
}

function defTree(d: Definition): Json {
  return {
    kind: KIND_KEYWORD[d.kind] ?? d.kind,
    name: d.name,
    description: descOf((d as { description?: PropertyValue }).description),
    tags: (d as { tags?: string[] }).tags ?? [],
    properties: propsOf(d),
  };
}

function descOf(desc: PropertyValue | undefined): Json {
  if (!desc) return null;
  if (desc.kind === 'string' || desc.kind === 'tripleString') return desc.value;
  return null;
}

/**
 * Serialises a `sourceText` / `definitionSql` value (embedded-sql §6.1). A tagged
 * block becomes a `{ kind, tag, language, dialect, value }` object so the
 * tag/dialect resolution is conformance-checked; a plain string or triple-string
 * serialises to its bare value (matching the Kotlin dumper).
 */
function embeddedDump(v: PropertyValue): Json {
  if (v.kind === 'taggedBlock') {
    return { kind: 'taggedBlock', tag: v.tag, language: v.language, dialect: v.dialect, value: v.value };
  }
  if (v.kind === 'string' || v.kind === 'tripleString') return v.value;
  return null;
}

function propsOf(d: Definition): { [k: string]: Json } {
  const p: { [k: string]: Json } = {};
  const set = (k: string, v: Json | undefined) => {
    if (v !== undefined) p[k] = v;
  };
  switch (d.kind) {
    case 'project':
      if (d.version != null) p.version = d.version;
      break;
    case 'table':
      if (d.primaryKey?.length) p.primaryKey = d.primaryKey;
      if (d.columns?.length) p.columns = d.columns.map(defTree);
      if (d.indices?.length) p.indices = d.indices.map(defTree);
      if (d.constraints?.length) p.constraints = d.constraints.map(defTree);
      set('search', search(d.search));
      break;
    case 'view':
      if (d.columns?.length) p.columns = d.columns.map(defTree);
      if (d.definitionSql) p.definitionSql = embeddedDump(d.definitionSql);
      set('search', search(d.search));
      break;
    case 'column':
      if (d.type) p.type = dataType(d.type);
      if (d.optional) p.optional = true;
      if (d.isKey) p.isKey = true;
      if (d.indexed) p.indexed = true;
      set('search', search(d.search));
      break;
    case 'index':
      if (d.indexType) p.indexType = d.indexType;
      if (d.columns?.length) p.columns = d.columns;
      break;
    case 'constraint':
      if (d.constraintType) p.constraintType = d.constraintType;
      if (d.columns?.length) p.columns = d.columns;
      break;
    case 'fk':
      if (d.from) p.from = pv(d.from);
      if (d.to) p.to = pv(d.to);
      break;
    case 'procedure':
      if (d.parameters?.length) p.parameters = d.parameters.map(param);
      if (d.resultColumns?.length) p.resultColumns = d.resultColumns.map(defTree);
      break;
    case 'entity':
      if (d.labelPlural != null) p.labelPlural = d.labelPlural;
      if (d.nameAttribute) p.nameAttribute = d.nameAttribute.path;
      if (d.codeAttribute) p.codeAttribute = d.codeAttribute.path;
      if (d.aliases?.length) p.aliases = d.aliases;
      if (d.attributes?.length) p.attributes = d.attributes.map(defTree);
      if (d.roles?.length) p.roles = d.roles;
      set('displayLabel', loc(d.displayLabel));
      set('search', search(d.search));
      set('binding', d.binding ? binding(d.binding) : undefined);
      break;
    case 'attribute':
      if (d.type) p.type = dataType(d.type);
      if (d.isKey) p.isKey = true;
      if (d.optional) p.optional = true;
      set('displayLabel', loc(d.displayLabel));
      set('valueLabels', valueLabels(d.valueLabels));
      set('search', search(d.search));
      set('binding', d.binding ? binding(d.binding) : undefined);
      break;
    case 'relation':
      if (d.from) p.from = pv(d.from);
      if (d.to) p.to = pv(d.to);
      if (d.cardinality) p.cardinality = pv(d.cardinality);
      if (d.join && d.join.items.length) p.join = d.join.items.map(pv);
      set('search', search(d.search));
      set('binding', d.binding ? binding(d.binding) : undefined);
      break;
    case 'er2dbEntity':
      if (d.entity) p.entity = d.entity.path;
      if (d.target) p.target = targetVal(d.target);
      if (d.whereFilter) p.whereFilter = pv(d.whereFilter);
      break;
    case 'er2dbAttribute':
      if (d.attribute) p.attribute = d.attribute.path;
      if (d.target) p.target = targetVal(d.target);
      break;
    case 'er2dbRelation':
      if (d.relation) p.relation = d.relation.path;
      if (d.fk) p.fk = d.fk.path;
      break;
    case 'query':
      if (d.language) p.language = d.language;
      if (d.parameters?.length) p.parameters = d.parameters.map(param);
      if (d.sourceText) p.sourceText = embeddedDump(d.sourceText);
      set('search', search(d.search));
      break;
    case 'role':
      set('label', loc(d.label));
      set('search', search(d.search));
      break;
    case 'er2cncRole':
      if (d.entity) p.entity = d.entity.path;
      if (d.role) p.role = d.role.path;
      break;
    case 'drillMap': {
      if (d.from) p.from = d.from.path;
      if (d.to) p.to = d.to.path;
      if (d.args.length) {
        const args: { [k: string]: Json } = {};
        for (const a of d.args) args[a.name] = a.value.value;
        p.args = args;
      }
      set('display', loc(d.display));
      if (d.overrideAuto) p.override = true;
      break;
    }
    case 'area':
      if (d.packages.length) p.packages = d.packages;
      if (d.entities.length) p.entities = d.entities;
      break;
    case 'world':
      if (d.extends) p.extends = d.extends;
      if (d.engines.length) p.engines = d.engines.map(enginePartTree);
      if (d.executors.length) p.executors = d.executors.map(enginePartTree);
      if (d.storages.length) p.storages = d.storages.map(storageTree);
      break;
  }
  return p;
}

// ----- world member serialisers (present-only flat objects; TTR-surface shape) -----

function manifestDump(m: Record<string, PropertyValue>): Json {
  const o: { [k: string]: Json } = {};
  for (const k of Object.keys(m)) o[k] = pv(m[k]);
  return o;
}

function enginePartTree(e: EngineDef | ExecutorDef): Json {
  const m: { [k: string]: Json } = { kind: e.kind, name: e.name };
  const desc = descOf(e.description);
  if (desc !== null) m.description = desc;
  if (e.tags?.length) m.tags = e.tags;
  if (e.type) m.type = e.type;
  if (e.version) m.version = e.version;
  if (e.extends) m.extends = e.extends;
  if (Object.keys(e.manifest).length) m.manifest = manifestDump(e.manifest);
  return m;
}

function storageTree(s: StorageDef): Json {
  const m: { [k: string]: Json } = { kind: s.kind, name: s.name };
  const desc = descOf(s.description);
  if (desc !== null) m.description = desc;
  if (s.tags?.length) m.tags = s.tags;
  if (s.type) m.type = s.type;
  if (s.via) m.via = s.via;
  if (s.hosts.length) m.hosts = s.hosts;
  if (s.staging) m.staging = true;
  if (s.extends) m.extends = s.extends;
  if (s.schemas.length) m.schemas = s.schemas.map(worldSchemaTree);
  if (Object.keys(s.manifest).length) m.manifest = manifestDump(s.manifest);
  return m;
}

function worldSchemaTree(w: WorldSchemaDef): Json {
  const fields: { [k: string]: Json } = {};
  for (const f of w.fields) fields[f.name] = f.type;
  return { kind: 'schema', name: w.name, fields };
}

function pv(v: PropertyValue): Json {
  switch (v.kind) {
    case 'string':
      return { kind: 'string', value: v.value };
    case 'tripleString':
      return { kind: 'tripleString', value: v.value };
    case 'taggedBlock':
      return embeddedDump(v);
    case 'number':
      return { kind: 'number', value: v.value };
    case 'bool':
      return { kind: 'bool', value: v.value };
    case 'null':
      return { kind: 'null' };
    case 'id':
      return { kind: 'id', parts: v.parts, path: v.path };
    case 'list':
      return { kind: 'list', items: v.items.map(pv) };
    case 'object':
      return { kind: 'object', entries: objEntries(v.entries) };
    case 'functionCall':
      return { kind: 'functionCall', name: v.name, args: v.args.map(pv) };
  }
}

function objEntries(entries: ObjectEntry[]): { [k: string]: Json } {
  const o: { [k: string]: Json } = {};
  for (const e of entries) o[e.key] = pv(e.value);
  return o;
}

function dataType(dt: DataType): Json {
  if (dt.kind === 'simple') return { name: dt.name };
  const o: { [k: string]: Json } = { name: dt.typeName };
  if (dt.length != null) o.length = dt.length;
  if (dt.precision != null) o.precision = dt.precision;
  return o;
}

function search(s: SearchBlock | undefined): Json | undefined {
  if (!s) return undefined;
  const m: { [k: string]: Json } = {};
  if (s.searchable) m.searchable = true;
  if (s.fuzzy) m.fuzzy = true;
  const kw = locList(s.keywords);
  if (kw) m.keywords = kw;
  if (s.patterns?.length) m.patterns = s.patterns;
  const desc = locList(s.descriptions);
  if (desc) m.descriptions = desc;
  if (s.examples?.length) m.examples = s.examples;
  if (s.aliases?.length) m.aliases = s.aliases;
  return Object.keys(m).length ? m : undefined;
}

function loc(v: LocalizedString | undefined): Json | undefined {
  if (!v || !v.entries || Object.keys(v.entries).length === 0) return undefined;
  return { ...v.entries };
}

function locList(v: LocalizedStringList | undefined): Json | undefined {
  if (!v || !v.entries || Object.keys(v.entries).length === 0) return undefined;
  return { ...v.entries };
}

function valueLabels(v: ValueLabels | undefined): Json | undefined {
  if (!v || v.entries.length === 0) return undefined;
  const o: { [k: string]: Json } = {};
  for (const e of v.entries) o[e.key] = loc(e.label) ?? {};
  return o;
}

function param(p: ParameterDef): Json {
  const m: { [k: string]: Json } = { name: p.name };
  if (p.type) m.type = dataType(p.type);
  if (p.label != null) m.label = p.label;
  if (p.direction != null) m.direction = p.direction;
  return m;
}

function binding(m: BindingProperty): Json {
  if (m.kind === 'bareId') return { kind: 'bareId', id: m.id.path };
  const o: { [k: string]: Json } = { kind: 'block' };
  if (m.target) o.target = targetVal(m.target);
  if (m.columns?.length) o.columns = m.columns.map(bindingColumn);
  if (m.fk) o.fk = m.fk.path;
  return o;
}

function bindingColumn(e: BindingColumnEntry): Json {
  const v = e.value;
  const value: Json =
    v.kind === 'bareId' ? { kind: 'bareId', id: v.id.path } : { kind: 'object', object: pv(v.object) };
  return { name: e.name, value };
}

function targetVal(t: ObjectValue | Reference): Json {
  if ('kind' in t && t.kind === 'object') return pv(t);
  return (t as Reference).path;
}

// ----- canonical printer: matches Kotlin's printCanonical / JSON.stringify(_, null, 4) -----

function print(value: Json): string {
  return JSON.stringify(sortDeep(value), null, 4);
}

function sortDeep(value: Json): Json {
  if (Array.isArray(value)) return value.map(sortDeep);
  if (value !== null && typeof value === 'object') {
    const out: { [k: string]: Json } = {};
    for (const k of Object.keys(value).sort()) out[k] = sortDeep(value[k]);
    return out;
  }
  return value;
}
