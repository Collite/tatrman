import type { Trivia } from './cst/trivia.js';

/**
 * Source-file location for a parsed node or diagnostic.
 *
 * Conventions (match ANTLR's TokenStream, not LSP):
 *   - `line` and `endLine` are 1-indexed.
 *   - `column` and `endColumn` are 0-indexed (column of the first character of the token / one past the last).
 *   - `offsetStart` and `offsetEnd` are 0-indexed byte offsets into the source file;
 *     `offsetEnd` is exclusive.
 *
 * LSP consumers must subtract 1 from `line`/`endLine` to produce LSP positions.
 */
export interface SourceLocation {
  file: string;
  line: number;
  column: number;
  endLine: number;
  endColumn: number;
  offsetStart: number;
  offsetEnd: number;
}

// ============================================================================
// Common AST types (used by multiple per-kind types)
// ============================================================================

export type PropertyValue =
  | StringValue
  | TripleStringValue
  | TaggedBlockValue
  | NumberValue
  | BoolValue
  | NullValue
  | IdValue
  | ListValue
  | ObjectValue
  | FunctionCallValue;

export interface StringValue {
  kind: 'string';
  value: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface TripleStringValue {
  kind: 'tripleString';
  value: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

/** Embedded-language dialects backed by a generated SQL grammar (embedded-sql contracts §3). */
export type SqlDialect = 'tsql' | 'postgres' | 'duckdb' | 'mysql' | 'bigquery';

/** Resolved embedded-language kind. Mirrors {@link QueryLanguage}. */
export type LanguageKind = QueryLanguage;

/**
 * A tagged triple-quoted block (`"""<tag>␊…"""`) carrying embedded foreign-language
 * source — SQL today (embedded-sql DESIGN §3, contracts §2.1). Produced only by the
 * `sourceText` / `definitionSql` properties via `embeddedBlock`. The tag is peeled
 * before `value`, so it never reaches the executed SQL.
 */
export interface TaggedBlockValue {
  kind: 'taggedBlock';
  tag: string;                  // raw tag text, e.g. 'sql' | 'ms-sql' | 'postgres'
  language: LanguageKind;       // resolved from the tag via TAG_REGISTRY
  dialect: SqlDialect | null;   // null for a bare `sql` (→ modeler.toml default) or non-SQL
  value: string;                // tag/fence stripped, dedented, one trailing newline removed
  tagSource: SourceLocation;    // span of the tag token text
  valueSource: SourceLocation;  // span of `value` within the file (post-dedent)
  indentWidth: number;          // common indent removed by dedent (for the §8 source map)
  source: SourceLocation;       // whole literal
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface NumberValue {
  kind: 'number';
  value: number;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface BoolValue {
  kind: 'bool';
  value: boolean;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface NullValue {
  kind: 'null';
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface IdValue {
  kind: 'id';
  path: string;
  parts: string[];
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface ListValue {
  kind: 'list';
  items: PropertyValue[];
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface ObjectValue {
  kind: 'object';
  entries: ObjectEntry[];
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface ObjectEntry {
  key: string;
  value: PropertyValue;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface FunctionCallValue {
  kind: 'functionCall';
  name: string;
  args: PropertyValue[];
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface Reference {
  path: string;
  parts: string[];
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface LocalizedString {
  kind: 'localizedString';
  entries: Record<string, string>;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface LocalizedStringList {
  kind: 'localizedStringList';
  entries: Record<string, string[]>;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export type DataType = SimpleDataType | StructuredDataType;

export interface SimpleDataType {
  kind: 'simple';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface StructuredDataType {
  kind: 'structured';
  typeName: string;
  length?: number;
  precision?: number;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export type IndexType = 'primary' | 'secondary' | 'ordered' | 'btree' | 'fulltext';
export type ConstraintType = 'unique' | 'notNull';
export type QueryLanguage = 'SQL' | 'TRANSFORMATION_DSL' | 'DATAFRAME_DSL' | 'REL_NODE';
export type ParameterDirection = 'IN' | 'OUT' | 'INOUT';

export interface SearchBlock {
  kind: 'searchBlock';
  keywords?: LocalizedStringList;
  patterns?: string[];
  descriptions?: LocalizedStringList;
  examples?: string[];
  aliases?: string[];
  searchable?: boolean;
  fuzzy?: boolean;
  duplicateProperties?: string[];
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface ValueLabels {
  kind: 'valueLabels';
  entries: Array<{ key: string; label: LocalizedString; source: SourceLocation }>;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

// ----- v2.1: inline bindings -----

export interface BindingPropertyBareId {
  kind: 'bareId';
  id: Reference;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface BindingPropertyBlock {
  kind: 'block';
  target?: ObjectValue | Reference;
  columns?: BindingColumnEntry[];
  fk?: Reference;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export type BindingProperty = BindingPropertyBareId | BindingPropertyBlock;

export interface BindingColumnEntry {
  name: string;
  value: BindingColumnValue;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export type BindingColumnValue =
  | { kind: 'bareId'; id: Reference; source: SourceLocation }
  | { kind: 'object'; object: ObjectValue; source: SourceLocation };

export interface ParameterDef {
  name: string;
  type?: DataType;
  label?: string;
  direction?: ParameterDirection;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

// ============================================================================
// Schema directive
// ============================================================================

export interface SchemaDirective {
  schemaCode: string;
  namespace?: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

// ============================================================================
// Per-kind definition types
// ============================================================================

export interface ModelDef {
  kind: 'model';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  version?: string;
}

export interface TableDef {
  kind: 'table';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  primaryKey?: string[];
  columns?: ColumnDef[];
  indices?: IndexDef[];
  constraints?: ConstraintDef[];
  search?: SearchBlock;
}

export interface ViewDef {
  kind: 'view';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  columns?: ColumnDef[];
  definitionSql?: StringValue | TripleStringValue | TaggedBlockValue;
  search?: SearchBlock;
}

export interface ColumnDef {
  kind: 'column';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  type?: DataType;
  optional?: boolean;
  isKey?: boolean;
  indexed?: boolean;
  search?: SearchBlock;
}

export interface IndexDef {
  kind: 'index';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  indexType?: IndexType;
  columns?: string[];
}

export interface ConstraintDef {
  kind: 'constraint';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  constraintType?: ConstraintType;
  columns?: string[];
}

export interface FkDef {
  kind: 'fk';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  from?: PropertyValue;
  to?: PropertyValue;
}

export interface ProcedureDef {
  kind: 'procedure';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  parameters?: ParameterDef[];
  resultColumns?: ColumnDef[];
}

export interface EntityDef {
  kind: 'entity';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  labelPlural?: string;
  nameAttribute?: Reference;
  codeAttribute?: Reference;
  aliases?: string[];
  attributes?: AttributeDef[];
  roles?: string[];
  displayLabel?: LocalizedString;
  search?: SearchBlock;
  binding?: BindingProperty;
}

export interface AttributeDef {
  kind: 'attribute';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  type?: DataType;
  isKey?: boolean;
  optional?: boolean;
  valueLabels?: ValueLabels;
  displayLabel?: LocalizedString;
  search?: SearchBlock;
  binding?: BindingProperty;
  // v3.1 MD — the shared attribute body also serves `schema md` dimensions.
  // Both shapes are accepted by the grammar; per-schema validity (md requires
  // `domain:` & forbids `type:`; er the reverse) is enforced in semantics.
  /** MD — `domain:` ref the attribute ranges over (opaque string, resolved in semantics). */
  domainRef?: string;
  /** MD — roll-up aggregation in a hierarchy (e.g. latest-valid address). */
  aggregation?: AggregationSpec;
  /** MD — span-carrying view of `domainRef` (editor-only). */
  crossRefs?: CrossRef[];
}

export interface RelationDef {
  kind: 'relation';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  from?: PropertyValue;
  to?: PropertyValue;
  cardinality?: ObjectValue;
  join?: ListValue;
  search?: SearchBlock;
  binding?: BindingProperty;
}

export interface Er2dbEntityDef {
  kind: 'er2dbEntity';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  entity?: Reference;
  target?: ObjectValue | Reference;
  whereFilter?: ObjectValue;
}

export interface Er2dbAttributeDef {
  kind: 'er2dbAttribute';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  attribute?: Reference;
  target?: ObjectValue | Reference;
}

export interface Er2dbRelationDef {
  kind: 'er2dbRelation';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  relation?: Reference;
  fk?: Reference;
}

export interface QueryDef {
  kind: 'query';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  language?: QueryLanguage;
  parameters?: ParameterDef[];
  sourceText?: StringValue | TripleStringValue | TaggedBlockValue;
  search?: SearchBlock;
}

export interface RoleDef {
  kind: 'role';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  label?: LocalizedString;
  search?: SearchBlock;
}

export interface Er2cncRoleDef {
  kind: 'er2cncRole';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  entity?: Reference;
  role?: Reference;
}

/**
 * v2.2 — `def drill_map <id> { from, to, args, display?, override? }`.
 *
 * `from` / `to` reference existing `def query` patterns (cross-package allowed).
 * `args` maps target-parameter names (declared on the `to` pattern) to column
 * names in `from`'s result projection or string literals — values are kept as
 * `StringValue`s here; ai-platform decides whether each is a column or literal.
 * `display` is a localised chip label; the loader supplies a default when absent.
 * `overrideAuto` suppresses auto-derived drills with the same target.
 *
 * One-to-one for v1: one declaration per (from, to) pair. Multiple drill
 * targets on one source pattern → multiple `def drill_map` blocks.
 */
export interface DrillMapDef {
  kind: 'drillMap';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  from?: Reference;
  to?: Reference;
  args: DrillArgEntry[];
  display?: LocalizedString;
  overrideAuto?: boolean;
}

export interface DrillArgEntry {
  name: string;
  value: StringValue | TripleStringValue;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

// ============================================================================
// v3.1 — MD (multidimensional) logical objects (schema md). Contracts §2.
// Cross-references are opaque strings at the parser layer (resolved in semantics).
// ============================================================================

/**
 * A span-carrying view of one MD cross-reference, parallel to the opaque-string
 * ref fields (the contracts §2 semantic surface). Editor-only convenience — like
 * {@link AreaDef.packageSources} — populated by the walker so semantics can emit
 * positioned `md/unknown-ref` and the LSP can navigate. `role` selects the target
 * namespace during resolution (contracts §5).
 */
export interface CrossRef {
  role: 'domain' | 'map' | 'dimension' | 'measure' | 'hierarchy' | 'grain';
  path: string;
  source: SourceLocation;
}

export interface MdDomainDef {
  kind: 'mdDomain';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  type?: DataType; // reuses the existing DataType
  /** Grammar accepts any id; the `calc`/`bound` value-set is validated in semantics. */
  domainKind?: string;
  restrict?: RestrictClause[];
}

export interface RestrictClause {
  /** Open set: 'range' | 'members' | 'pattern' | 'length' | … (validated in semantics). */
  clause: string;
  value: RangeLiteral | DomainMember[] | PropertyValue;
  source: SourceLocation;
}

export interface RangeLiteral {
  kind: 'rangeLiteral';
  lo: number;
  hi: number;
  source: SourceLocation;
}

export interface DomainMember {
  key: string;
  labels: LocalizedString;
  source: SourceLocation;
}

export interface DimensionDef {
  kind: 'dimension';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  /** Attribute name naming member identity; required-in-semantics. */
  key?: string;
  /** Inline `def attribute` list (shared AttributeDef node). */
  attributes: AttributeDef[];
  /** References to HierarchyDef names. */
  hierarchies?: string[];
  /** Span-carrying view of `hierarchies` (editor-only). */
  crossRefs?: CrossRef[];
}

export interface MdMapDef {
  kind: 'mdMap';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  /** 1..n opaque domain refs. */
  from: string[];
  /** 1..n opaque domain refs (usually one). */
  to: string[];
  /** Normalised from the grammar's `cardinality` object; default N:1 (semantics). */
  cardinality?: '1:1' | 'N:1';
  /** Absent ⇒ table-backed (case-table supplied by md2db_map). */
  calc?: CalcRef;
  /** Span-carrying view of `from`/`to` domain refs (editor-only). */
  crossRefs?: CrossRef[];
}

export interface CalcRef {
  kind: 'calcRef';
  name: string;
  args: CalcArg[];
  source: SourceLocation;
}

export interface CalcArg {
  name: string;
  value: PropertyValue;
  source: SourceLocation;
}

export interface HierarchyDef {
  kind: 'hierarchy';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  dimensionRef?: string; // opaque
  /** Leaf→root order PRESERVED. */
  levels: HierarchyLevel[];
  /** Span-carrying view of `dimensionRef` and per-level `via` map refs (editor-only). */
  crossRefs?: CrossRef[];
}

export interface HierarchyLevel {
  attribute: string;
  /** Optional `via <mapRef>` pinning the connecting map (opaque). */
  via?: string;
  source: SourceLocation;
}

export interface MeasureDef {
  kind: 'measure';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  domainRef?: string; // opaque
  /** Grammar accepts any id; the value-set is validated in semantics. */
  measureClass?: string;
  aggregation?: AggregationSpec;
  validBy?: string; // attribute name
  /** Span-carrying view of `domainRef` (editor-only). */
  crossRefs?: CrossRef[];
}

export interface AggregationSpec {
  /** e.g. 'sum' | 'max' | 'latestValid' | … */
  default?: string;
  /** Per-dimension overrides, e.g. { time: 'latestValid' }. */
  perDimension?: Record<string, string>;
  source: SourceLocation;
}

export interface CubeletDef {
  kind: 'cubelet';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  /** Dotted `Dimension.attribute` opaque refs. */
  grain: string[];
  /** Measure refs or inline defs. */
  measures: (string | MeasureDef)[];
  /** Span-carrying view of grain refs + string measure refs (editor-only). */
  crossRefs?: CrossRef[];
}

// ============================================================================
// v3.1 — MD binding objects (schema binding). Contracts §2 / §4. References are
// opaque strings; shape/journaling discriminated unions are normalised in the
// walker, validated in semantics (Phase 3).
// ============================================================================

export type ShapeSpec =
  | { shape: 'wide' }
  | { shape: 'long'; codeColumn: string; valueColumn: string };

export type AttrColumnBinding =
  | { column: string }
  /** Map-mediated grain column (design §6.1). */
  | { via: string; from: { table: string; column: string } };

export type MeasureColumnBinding = { column: string } | { code: string }; // wide | long

export type JournalingSpec =
  | { mode: 'overwrite' }
  | { mode: 'diff' }
  | { mode: 'invalidate'; validColumn: string };

export interface Md2DbCubeletDef {
  kind: 'md2dbCubelet';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  cubeletRef: string;
  table: string; // target fact-table ref
  shape: ShapeSpec;
  attributes: Record<string, AttrColumnBinding>;
  measures: Record<string, MeasureColumnBinding>;
  journaling?: JournalingSpec;
}

export interface Md2DbDomainDef {
  kind: 'md2dbDomain';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  domainRef: string;
  source_: { table: string; column: string };
}

export interface Md2DbMapDef {
  kind: 'md2dbMap';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  mapRef: string;
  table: string;
  /** from/to domain → case-table column. */
  columns: Record<string, string>;
}

export interface Md2ErCubeletDef {
  kind: 'md2erCubelet';
  name: string;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
  description?: StringValue | TripleStringValue;
  tags?: string[];
  cubeletRef: string;
  entity: string; // target ER entity
  /** attribute → ER attribute (structural only). */
  attributes: Record<string, string>;
}

export type Definition =
  | ModelDef
  | TableDef
  | ViewDef
  | ColumnDef
  | IndexDef
  | ConstraintDef
  | FkDef
  | ProcedureDef
  | EntityDef
  | AttributeDef
  | RelationDef
  | Er2dbEntityDef
  | Er2dbAttributeDef
  | Er2dbRelationDef
  | QueryDef
  | RoleDef
  | Er2cncRoleDef
  | DrillMapDef
  | AreaDef
  | MdDomainDef
  | DimensionDef
  | MdMapDef
  | HierarchyDef
  | MeasureDef
  | CubeletDef
  | Md2DbCubeletDef
  | Md2DbDomainDef
  | Md2DbMapDef
  | Md2ErCubeletDef;

// ============================================================================
// Document / parse result
// ============================================================================

export interface PackageDecl {
  kind: 'packageDecl';
  name: string;
  parts: string[];
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface ImportDecl {
  kind: 'importDecl';
  target: string;
  targetParts: string[];
  wildcard: boolean;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface GraphLayout {
  viewport?: {
    zoom: number;
    panX: number;
    panY: number;
    displayMode: string; // Validated against the DisplayMode union in §11.2 by the semantics layer.
  };
  nodes: Record<string, { x: number; y: number }>;
  edges: Record<string, { bendPoints?: [number, number][] }>;
}

export interface GraphBlock {
  kind: 'graphBlock';
  name: string;
  schema?: 'db' | 'er' | 'binding' | 'query' | 'cnc';
  description?: string;
  tags?: string[];
  objects: string[];
  layout?: GraphLayout;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

/**
 * Subject area (v3.0 — `def area <id> { … }`, replacing the v2.3 `.ttrd` domain
 * block). A normal definition that lives in ordinary model files and registers a
 * resolvable symbol. Drives the resolved-packages `domains` artifact (recursive
 * package closure + entity set).
 */
export interface AreaDef {
  kind: 'area';
  name: string;
  description?: StringValue | TripleStringValue;
  tags?: string[];
  /** Recursive members: each pulls the package and all descendants. May be empty. */
  packages: string[];
  /** Individual entity qnames loaded in addition to whole packages. May be empty. */
  entities: string[];
  /**
   * Per-member source locations, parallel to `packages` / `entities` (editor-only;
   * for go-to-def / find-refs). Contracts carry only the string members; these
   * locations are an additive editor convenience.
   */
  packageSources?: SourceLocation[];
  entitySources?: SourceLocation[];
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface Document {
  packageDecl?: PackageDecl;
  imports: ImportDecl[];
  schemaDirective?: SchemaDirective;
  graph?: GraphBlock;
  definitions: Definition[];
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface ParseError {
  code?: string;
  message: string;
  severity: 'error' | 'warning' | 'info';
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface ParseResult {
  ast?: Document;
  errors: ParseError[];
  sourceFile: string;
}