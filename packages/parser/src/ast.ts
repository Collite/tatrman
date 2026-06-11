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

// ----- v2.1: inline mappings -----

export interface MappingPropertyBareId {
  kind: 'bareId';
  id: Reference;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export interface MappingPropertyBlock {
  kind: 'block';
  target?: ObjectValue | Reference;
  columns?: MappingColumnEntry[];
  fk?: Reference;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export type MappingProperty = MappingPropertyBareId | MappingPropertyBlock;

export interface MappingColumnEntry {
  name: string;
  value: MappingColumnValue;
  source: SourceLocation;
  leadingTrivia?: Trivia[];
  trailingTrivia?: Trivia[];
}

export type MappingColumnValue =
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
  definitionSql?: StringValue | TripleStringValue;
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
  mapping?: MappingProperty;
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
  mapping?: MappingProperty;
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
  mapping?: MappingProperty;
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
  sourceText?: StringValue | TripleStringValue;
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
  | DrillMapDef;

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
  schema?: 'db' | 'er' | 'map' | 'query' | 'cnc';
  description?: string;
  tags?: string[];
  objects: string[];
  layout?: GraphLayout;
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