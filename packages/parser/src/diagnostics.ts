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
  MissingPackageDeclaration = 'ttr/missing-package-declaration',
  AmbiguousReference = 'ttr/ambiguous-reference',
  GraphObjectNotFound = 'ttr/graph-object-not-found',
  GraphLayoutStaleNode = 'ttr/graph-layout-stale-node',
  GraphObjectsEmpty = 'ttr/graph-objects-empty',
  GraphNameMismatch = 'ttr/graph-name-mismatch',
  FileOrdering = 'ttr/file-ordering',
  FuzzyWithoutSearchable = 'ttr/fuzzy-without-searchable',
  DuplicateSearchProperty = 'ttr/duplicate-search-property',
  DuplicateMapping = 'ttr/duplicate-mapping',
  // embedded-sql (DESIGN §5/§6): tagged-block tag resolution.
  UnknownLanguageTag = 'ttr/unknown-language-tag',
  LanguageTagMismatch = 'ttr/language-tag-mismatch',
  DeprecatedLanguageProperty = 'ttr/deprecated-language-property',
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