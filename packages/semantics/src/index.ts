export { parseManifest, resolveManifest } from './manifest.js';
export type { ProjectManifest, ResolvedManifest } from './manifest.js';
export {
  loadSqlConfig,
  parseSqlConfig,
  emptySqlConfig,
  namespaceFor,
  sqlNameFor,
  defaultsFor,
} from './sql-config.js';
export type {
  SqlConfig,
  SqlNamespaceMapping,
  SqlDefaults,
  SqlConfigDiagnostic,
} from './sql-config.js';
export { loadProjectFromOpenDocuments } from './project.js';
export type { Project } from './project.js';
export { qnameToString, parseQname, buildQname } from './qname.js';
export type { Qname } from './qname.js';
export { DocumentSymbolTable } from './symbol-table.js';
export type { SymbolEntry } from './symbol-table.js';
export { ProjectSymbolTable } from './project-symbols.js';
export { Resolver } from './resolver.js';
export type { ResolutionResult, LexicalScope, ResolutionStep, ResolutionAttempt } from './resolver.js';
export { collectReferences, collectAllReferences, nestedDefs } from './references.js';
export { ReferenceIndex, enclosingQnameOf } from './reference-index.js';
export type { ReferenceLocation } from './reference-index.js';
export { PackageGraphBuilder, findCyclesOn } from './package-graph.js';
export type { PackageGraph, PackageNode, PackageEdge } from './package-graph.js';
export { packageOfImport } from './references.js';
export { inferPackageFromUri } from './package-inference.js';
export { defaultSchemaForKind } from './default-schema.js';
export { synthesizeMappings } from './mapping-synthesizer.js';
export { collectMappingReferences } from './mapping-references.js';
export type { MappingReference } from './mapping-references.js';
export { foldEq, foldIdent } from './sql/fold.js';
export {
  resolveSqlReferences,
  resolveSqlRefAt,
  buildSqlDbIndex,
  sqlScopeTables,
  resolveSqlTableName,
} from './sql/resolve.js';
export type {
  SqlDiagnostic,
  SqlResolveContext,
  SqlRefHit,
  DbTableInfo,
  SqlDbIndex,
  SqlScopeTable,
} from './sql/resolve.js';
export { checkSqlParameters } from './sql/param-check.js';
export type { SqlParamUsage, SqlParamCheckResult } from './sql/param-check.js';
export { SqlReferenceIndex } from './sql/reference-index.js';
export type { SqlRefLocation, SqlRefEntry, SqlRefRange } from './sql/reference-index.js';
