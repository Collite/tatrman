// SPDX-License-Identifier: Apache-2.0
export { parseManifest, resolveManifest, resolvePackagesConfig, defaultPackagesConfig, validateManifest, effectiveSchemaId } from './manifest.js';
export type { ProjectManifest, ResolvedManifest, PackagesConfig, PackagesConfigDiagnostic, SchemaBinding, PackageConfig, ManifestDiagnostic } from './manifest.js';
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
export { qnameToKey, buildCanonicalKey, modelForKind, modelHasSchema, classifyReference, resolveReference, MODEL_CODES } from './qname.js';
export type {
  Qname, ModelCode, Vocab, PartialQname, RefSite, SymbolIndex,
  ResolvedReference, ReferenceDiagnostic, ReferenceDiagnosticCode,
} from './qname.js';
export { DocumentSymbolTable } from './symbol-table.js';
export type { SymbolEntry } from './symbol-table.js';
export { validateWorldDocument } from './world-validate.js';
export type { WorldDiagnostic, WorldDiagnosticCode } from './world-validate.js';
export { ProjectSymbolTable } from './project-symbols.js';
export { Resolver } from './resolver.js';
export type { ResolutionResult, LexicalScope, ResolutionStep, ResolutionAttempt } from './resolver.js';
export { collectReferences, collectAllReferences, nestedDefs } from './references.js';
export { ReferenceIndex, enclosingQnameOf } from './reference-index.js';
export type { ReferenceLocation } from './reference-index.js';
export { PackageGraphBuilder, findCyclesOn } from './package-graph.js';

export { AreaTableBuilder, resolveArea, areaPackageClosure } from './area-table.js';
export type { ResolvedArea, AreaEntry } from './area-table.js';
export type { PackageGraph, PackageNode, PackageEdge } from './package-graph.js';
export { packageOfImport } from './references.js';
export { inferPackageFromUri } from './package-inference.js';
export {
  derivedPackage,
  effectivePackage,
  elideRoot,
  classifyPackageMismatch,
  isValidPackageSegment,
  invalidPackageSegments,
} from './derivation.js';
export type { PackageMismatchKind } from './derivation.js';
export { defaultSchemaForKind, defaultNamespaceForSchema, namespaceForKind } from './default-schema.js';
export { resolveMdRef, underlyingDomainOf, mdCrossRefsOf } from './md-resolve.js';
export { shapeSatisfied, validateCalcArgs } from './md-calc.js';
export type { DomainShape, CalcArgProblem } from './md-calc.js';
export { computeLeaves, coLeafClasses, grainReachable, connectingMaps, inferStep } from './md-lattice.js';
export type { MapEdge, StepResult } from './md-lattice.js';
export { buildMdMapGraph, resolveLevelDomains } from './md-graph.js';
export type { MdMapGraph } from './md-graph.js';
export {
  MD_CALC_CATALOG,
  MD_CATALOG_VERSION,
  isKnownCalc,
  getCalcEntry,
  calcNames,
} from './md-catalog-source.js';
export type { CatalogEntry } from './md-catalog-source.js';
export { synthesizeMappings } from './mapping-synthesizer.js';
export { collectBindingReferences } from './mapping-references.js';
export type { BindingReference } from './mapping-references.js';
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

// Grounding Phase 1 (grammar 4.2) — the `semantics { }` block vocabulary,
// validator, and typed result model.
export {
  SEMANTICS_VOCABULARY_VERSION,
  ENTITY_KINDS,
  ATTRIBUTE_ROLES,
  KIND_COMPLETENESS,
  ALL_ROLES,
  ALL_ATTRIBUTE_KEYS,
  ALL_ENTITY_KEYS,
} from './semantics-block/vocabulary.js';
export type { EntityKind, AttributeRole, TypeConstraint, RoleSpec, CompletenessClause } from './semantics-block/vocabulary.js';
export { analyzeSemantics, typeFamilyOf } from './semantics-block/validator.js';
export type { SemanticsDiagnostic, SemanticsAnalysis } from './semantics-block/validator.js';
export { isEntitySemantics, isAttributeSemantics } from './semantics-block/model.js';
export type {
  ResolvedSemantics,
  ResolvedEntitySemantics,
  ResolvedAttributeSemantics,
  SymbolRef,
} from './semantics-block/model.js';
export { editDistance, nearestMatch } from './semantics-block/suggest.js';
