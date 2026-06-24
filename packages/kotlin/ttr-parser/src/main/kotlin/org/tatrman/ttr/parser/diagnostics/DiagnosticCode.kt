package org.tatrman.ttr.parser.diagnostics

/**
 * Canonical diagnostic codes, mirroring `packages/parser/src/diagnostics.ts`
 * one-for-one (every value identical). The full set lives in `ttr-parser` even
 * though some codes are only fired by `ttr-semantics` (Phase 2): the codes are
 * a contract shared by all modeler artifacts, so consumers depend on one
 * canonical enum. See contracts.md §2.8.
 */
enum class DiagnosticCode(
    val id: String,
) {
    ParseError("ttr/parse-error"),
    ParseRecoveryInfo("ttr/parse-recovery-info"),
    UnknownProperty("ttr/unknown-property"),
    UnresolvedReference("ttr/unresolved-reference"),
    DuplicateDefinition("ttr/duplicate-definition"),
    RequiredPropertyMissing("ttr/required-property-missing"),
    InvalidType("ttr/invalid-type"),
    EntityAttributeNotFound("ttr/entity-attribute-not-found"),
    PrimaryKeyColumnNotFound("ttr/primary-key-column-not-found"),
    WrongFileKind("ttr/wrong-file-kind"),
    UnimportedReference("ttr/unimported-reference"),
    UnusedImport("ttr/unused-import"),
    WildcardWithNoMatches("ttr/wildcard-with-no-matches"),
    DuplicateImport("ttr/duplicate-import"),
    CircularPackageDependency("ttr/circular-package-dependency"),
    PackageDeclarationMismatch("ttr/package-declaration-mismatch"),
    MissingPackageDeclaration("ttr/missing-package-declaration"),
    DomainMemberNotFound("ttr/domain-member-not-found"),
    DomainEmpty("ttr/domain-empty"),
    DuplicateDomain("ttr/duplicate-domain"),
    DomainRedundantMember("ttr/domain-redundant-member"),
    AmbiguousReference("ttr/ambiguous-reference"),
    GraphObjectNotFound("ttr/graph-object-not-found"),
    GraphLayoutStaleNode("ttr/graph-layout-stale-node"),
    GraphObjectsEmpty("ttr/graph-objects-empty"),
    GraphNameMismatch("ttr/graph-name-mismatch"),
    FileOrdering("ttr/file-ordering"),
    FuzzyWithoutSearchable("ttr/fuzzy-without-searchable"),
    DuplicateSearchProperty("ttr/duplicate-search-property"),
    DuplicateBinding("ttr/duplicate-binding"),

    // embedded-sql (DESIGN §5/§6): tagged-block tag resolution.
    UnknownLanguageTag("ttr/unknown-language-tag"),
    LanguageTagMismatch("ttr/language-tag-mismatch"),
    DeprecatedLanguageProperty("ttr/deprecated-language-property"),
    ;

    override fun toString(): String = id
}

/** Mirrors `DiagnosticSeverity` in `diagnostics.ts`. */
enum class DiagnosticSeverity { Error, Warning, Information, Hint }
