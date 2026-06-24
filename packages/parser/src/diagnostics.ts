export enum DiagnosticCode {
  ParseError = 'ttr/parse-error',
  ParseRecoveryInfo = 'ttr/parse-recovery-info',
  UnknownProperty = 'ttr/unknown-property',
  UnresolvedReference = 'ttr/unresolved-reference',
  DuplicateDefinition = 'ttr/duplicate-definition',
  RequiredPropertyMissing = 'ttr/required-property-missing',
  InvalidType = 'ttr/invalid-type',
  EntityAttributeNotFound = 'ttr/entity-attribute-not-found',
  PrimaryKeyColumnNotFound = 'ttr/primary-key-column-not-found',
  WrongFileKind = 'ttr/wrong-file-kind',
  UnimportedReference = 'ttr/unimported-reference',
  UnusedImport = 'ttr/unused-import',
  WildcardWithNoMatches = 'ttr/wildcard-with-no-matches',
  DuplicateImport = 'ttr/duplicate-import',
  CircularPackageDependency = 'ttr/circular-package-dependency',
  PackageDeclarationMismatch = 'ttr/package-declaration-mismatch',
  PackagePrefixDivergence = 'ttr/package-prefix-divergence',
  MissingPackageDeclaration = 'ttr/missing-package-declaration',
  // Packages & Domains PD3 — `.ttrd` domain resolution.
  DomainMemberNotFound = 'ttr/domain-member-not-found',
  DomainEmpty = 'ttr/domain-empty',
  DuplicateDomain = 'ttr/duplicate-domain',
  DomainRedundantMember = 'ttr/domain-redundant-member',
  AmbiguousReference = 'ttr/ambiguous-reference',
  GraphObjectNotFound = 'ttr/graph-object-not-found',
  GraphLayoutStaleNode = 'ttr/graph-layout-stale-node',
  GraphObjectsEmpty = 'ttr/graph-objects-empty',
  GraphNameMismatch = 'ttr/graph-name-mismatch',
  FileOrdering = 'ttr/file-ordering',
  FuzzyWithoutSearchable = 'ttr/fuzzy-without-searchable',
  DuplicateSearchProperty = 'ttr/duplicate-search-property',
  DuplicateBinding = 'ttr/duplicate-binding',
  // embedded-sql (DESIGN §5/§6): tagged-block tag resolution.
  UnknownLanguageTag = 'ttr/unknown-language-tag',
  LanguageTagMismatch = 'ttr/language-tag-mismatch',
  DeprecatedLanguageProperty = 'ttr/deprecated-language-property',
  // embedded-sql (DESIGN §12.8 / contracts §5.1): SQL reference resolution
  // against the TTR `db` symbol table. Best-effort (warnings); see §3.4/§3.5.
  SqlUnknownTable = 'sql-unknown-table',
  SqlUnknownColumn = 'sql-unknown-column',
  SqlAmbiguousColumn = 'sql-ambiguous-column',
  SqlColumnNotOnAlias = 'sql-column-not-on-alias',
  SqlUndeclaredParam = 'sql-undeclared-param',
  SqlUnusedParam = 'sql-unused-param',
}

export enum DiagnosticSeverity {
  Error = 'error',
  Warning = 'warning',
  Information = 'information',
  Hint = 'hint',
}

export interface DiagnosticEntry {
  code: DiagnosticCode;
  message: string;
  severity: DiagnosticSeverity;
}