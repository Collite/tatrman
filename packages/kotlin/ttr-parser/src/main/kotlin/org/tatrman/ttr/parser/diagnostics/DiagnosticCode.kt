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
    AreaMemberNotFound("ttr/area-member-not-found"),
    AreaEmpty("ttr/area-empty"),
    DuplicateArea("ttr/duplicate-area"),
    AreaRedundantMember("ttr/area-redundant-member"),
    AmbiguousReference("ttr/ambiguous-reference"),
    GraphObjectNotFound("ttr/graph-object-not-found"),
    GraphLayoutStaleNode("ttr/graph-layout-stale-node"),
    GraphObjectsEmpty("ttr/graph-objects-empty"),
    GraphNameMismatch("ttr/graph-name-mismatch"),
    FileOrdering("ttr/file-ordering"),
    FuzzyWithoutSearchable("ttr/fuzzy-without-searchable"),
    DuplicateSearchProperty("ttr/duplicate-search-property"),
    DuplicateBinding("ttr/duplicate-binding"),

    // Grounding Phase 1 (grammar 4.2): a `semantics { … }` entry whose value is a
    // nested object/list rather than a scalar. Keeps ttr-semantics' input flat.
    SemanticsNonScalarValue("ttr/semantics-non-scalar"),

    // Grounding Phase 1 — `semantics { … }` vocabulary/shape validation. These
    // stable TTR-SEM-2xx codes are the cross-repo contract mirrored by ai-platform's
    // closed proto enums (feature-grounding-contracts.md §4); the vocabulary
    // (ttr-semantics `SEMANTICS_VOCABULARY_VERSION`) and the enums version together.
    SemUnknownKey("TTR-SEM-200"),
    SemUnknownRole("TTR-SEM-201"),
    SemUnknownKind("TTR-SEM-202"),
    SemDuplicateKey("TTR-SEM-203"),
    SemMisplacedKeyword("TTR-SEM-204"),
    SemTypeConstraint("TTR-SEM-205"),
    SemCompleteness("TTR-SEM-206"),
    SemMultipleEventDate("TTR-SEM-207"),
    SemBadPeriodRef("TTR-SEM-208"),
    SemBadCurrencyRef("TTR-SEM-209"),
    SemGeoPair("TTR-SEM-210"),
    SemValidPair("TTR-SEM-211"),

    // embedded-sql (DESIGN §5/§6): tagged-block tag resolution.
    UnknownLanguageTag("ttr/unknown-language-tag"),
    LanguageTagMismatch("ttr/language-tag-mismatch"),
    DeprecatedLanguageProperty("ttr/deprecated-language-property"),
    ;

    override fun toString(): String = id
}

/** Mirrors `DiagnosticSeverity` in `diagnostics.ts`. */
enum class DiagnosticSeverity { Error, Warning, Information, Hint }
