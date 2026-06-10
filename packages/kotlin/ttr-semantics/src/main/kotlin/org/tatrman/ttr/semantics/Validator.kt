package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.diagnostics.DiagnosticCode
import org.tatrman.ttr.parser.diagnostics.DiagnosticSeverity
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.ColumnDef
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.ImportStatement
import org.tatrman.ttr.parser.model.QueryDef
import org.tatrman.ttr.parser.model.RelationDef
import org.tatrman.ttr.parser.model.RoleDef
import org.tatrman.ttr.parser.model.SearchHintsValue
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TableDef
import org.tatrman.ttr.parser.model.ViewDef

/** Lint policy from `modeler.toml` (TS `ResolvedManifest.lint`). */
data class ManifestLint(
    val strict: Boolean = false,
    val requireDescriptions: Boolean = false,
)

/** Resolved manifest config the validator consults (subset of TS `ResolvedManifest`). */
data class ResolvedManifest(
    val projectRoot: String = "",
    val lint: ManifestLint = ManifestLint(),
)

/** One validation diagnostic (TS `ValidationDiagnostic`). */
data class ValidationDiagnostic(
    val code: DiagnosticCode,
    val severity: DiagnosticSeverity,
    val message: String,
    val source: SourceLocation,
)

/**
 * The pieces of a parsed document the validators need: the TS `Document`
 * counterpart for the Kotlin `ParseResult`.
 */
data class SemanticDocument(
    val uri: String,
    val definitions: List<Definition>,
    val schemaCode: String,
    val namespace: String,
    val packageName: String,
    val imports: List<ImportStatement>,
)

/**
 * Per-kind and cross-reference validators. Ported from
 * `packages/semantics/src/validator.ts`.
 *
 * Scope note (see `docs/grammar-master/`): validators that need data the Kotlin
 * `ParseResult` does not carry (graph block, package-decl source, raw search
 * sub-property duplicates) are not ported — that is, file-ordering, .ttrg-graph,
 * package-declaration, and duplicate-search-property checks live only on the TS
 * side. The conformance harness runs the identical portable subset on both.
 */
class Validator(
    private val symbols: SymbolTable,
    private val resolver: Resolver,
    private val manifest: ResolvedManifest,
) {
    fun validateDocument(doc: SemanticDocument): List<ValidationDiagnostic> {
        val diagnostics = mutableListOf<ValidationDiagnostic>()

        for (def in doc.definitions) {
            when (def) {
                is EntityDef -> {
                    if (def.attributes.isEmpty()) {
                        diagnostics +=
                            error(
                                DiagnosticCode.RequiredPropertyMissing,
                                "Entity must have at least one attribute",
                                def.source,
                            )
                    }
                    def.nameAttribute?.let { ref ->
                        val last = ref.path.substringAfterLast('.')
                        if (def.attributes.none { it.name == last }) {
                            diagnostics +=
                                error(
                                    DiagnosticCode.EntityAttributeNotFound,
                                    "nameAttribute '${ref.path}' not found on entity '${def.name}'",
                                    def.source,
                                )
                        }
                    }
                    def.codeAttribute?.let { ref ->
                        val last = ref.path.substringAfterLast('.')
                        if (def.attributes.none { it.name == last }) {
                            diagnostics +=
                                error(
                                    DiagnosticCode.EntityAttributeNotFound,
                                    "codeAttribute '${ref.path}' not found on entity '${def.name}'",
                                    def.source,
                                )
                        }
                    }
                }

                is TableDef -> {
                    if (def.columns.isEmpty()) {
                        diagnostics +=
                            error(
                                DiagnosticCode.RequiredPropertyMissing,
                                "Table must have at least one column",
                                def.source,
                            )
                    }
                    for (pkCol in def.primaryKey) {
                        if (def.columns.none { it.name == pkCol }) {
                            diagnostics +=
                                error(
                                    DiagnosticCode.PrimaryKeyColumnNotFound,
                                    "Primary key column '$pkCol' not found on table '${def.name}'",
                                    def.source,
                                )
                        }
                    }
                }

                is ColumnDef ->
                    if (def.type == null) {
                        diagnostics +=
                            error(DiagnosticCode.RequiredPropertyMissing, "Column must have a type", def.source)
                    }

                is AttributeDef ->
                    if (def.type == null) {
                        diagnostics +=
                            error(DiagnosticCode.RequiredPropertyMissing, "Attribute must have a type", def.source)
                    }

                else -> {}
            }

            if (manifest.lint.requireDescriptions && def.description.isNullOrEmpty()) {
                diagnostics +=
                    diag(
                        DiagnosticCode.RequiredPropertyMissing,
                        DiagnosticSeverity.Warning,
                        "Definition should have a description",
                        def.source,
                    )
            }

            for ((sb, src) in searchBlocksOf(def)) {
                if (sb.fuzzy && !sb.searchable) {
                    diagnostics +=
                        diag(
                            DiagnosticCode.FuzzyWithoutSearchable,
                            DiagnosticSeverity.Warning,
                            "fuzzy search is enabled but the element is not marked searchable; set searchable: true",
                            src,
                        )
                }
            }
        }

        return diagnostics
    }

    fun validateReferences(doc: SemanticDocument): List<ValidationDiagnostic> {
        val diagnostics = mutableListOf<ValidationDiagnostic>()

        for (collected in collectAllReferences(doc.definitions)) {
            val enclosingQname = enclosingQnameOf(collected.ownerDef, doc.schemaCode, doc.namespace, doc.packageName)
            val res =
                resolver.resolveReference(
                    Resolver.Ref(collected.path, collected.parts),
                    ResolutionContext(
                        schemaCode = doc.schemaCode,
                        namespace = doc.namespace,
                        enclosingQname = enclosingQname,
                        imports = doc.imports,
                        packageName = doc.packageName,
                    ),
                )

            when (res) {
                is ResolutionResult.Unresolved ->
                    if (res.reason == ResolutionResult.Reason.Ambiguous) {
                        diagnostics +=
                            error(
                                DiagnosticCode.AmbiguousReference,
                                "Ambiguous reference: '${collected.path}' matches ${res.candidates.size} definitions via wildcard imports",
                                res.candidates.firstOrNull()?.source ?: collected.source,
                            )
                    } else {
                        diagnostics +=
                            diag(
                                DiagnosticCode.UnresolvedReference,
                                if (manifest.lint.strict) DiagnosticSeverity.Error else DiagnosticSeverity.Warning,
                                "Unresolved reference: '${collected.path}' (tried ${res.tried.joinToString(
                                    ", ",
                                ) { it.candidate }})",
                                collected.source,
                            )
                    }

                is ResolutionResult.Resolved ->
                    if (res.viaStep == ResolutionStep.FullyQualified && doc.packageName.isNotEmpty()) {
                        val resolvedPackage = res.symbol.packageName
                        if (resolvedPackage.isNotEmpty() && resolvedPackage != doc.packageName) {
                            val importedPkgs = doc.imports.map { packageOfImport(it) }.toSet()
                            if (resolvedPackage !in importedPkgs) {
                                diagnostics +=
                                    diag(
                                        DiagnosticCode.UnimportedReference,
                                        DiagnosticSeverity.Information,
                                        "Reference to '${res.symbol.qname}' resolves via package search; consider adding an import",
                                        collected.source,
                                    )
                            }
                        }
                    }
            }
        }

        return diagnostics
    }

    fun validateProject(): List<ValidationDiagnostic> {
        val diagnostics = mutableListOf<ValidationDiagnostic>()

        for (dup in symbols.duplicates()) {
            val hasInline = dup.entries.any { it.mappingSource == MappingSource.Inline }
            if (hasInline && isEr2db(dup.entries.first().kind)) continue
            for (entry in dup.entries) {
                val others =
                    dup.entries
                        .filter { !(it.documentUri == entry.documentUri && it.source.line == entry.source.line) }
                        .joinToString(", ") { "${it.documentUri}:${it.source.line}" }
                diagnostics +=
                    error(
                        DiagnosticCode.DuplicateDefinition,
                        "Duplicate definition of '${dup.qname}' (also at $others)",
                        entry.source,
                    )
            }
        }

        diagnostics += validateDuplicateMappings()
        return diagnostics
    }

    fun validateImports(doc: SemanticDocument): List<ValidationDiagnostic> {
        val diagnostics = mutableListOf<ValidationDiagnostic>()
        val seenTargets = mutableSetOf<String>()

        for (imp in doc.imports) {
            if (imp.wildcard && symbols.getByPackage(imp.target).isEmpty()) {
                diagnostics +=
                    diag(
                        DiagnosticCode.WildcardWithNoMatches,
                        DiagnosticSeverity.Warning,
                        "Wildcard import '${imp.target}.*' has no matching definitions",
                        imp.source,
                    )
            }
            if (!seenTargets.add(imp.target)) {
                diagnostics +=
                    diag(
                        DiagnosticCode.DuplicateImport,
                        DiagnosticSeverity.Warning,
                        "Duplicate import of '${imp.target}'",
                        imp.source,
                    )
            }
        }

        val usedTargets = mutableSetOf<String>()
        for (collected in collectAllReferences(doc.definitions)) {
            val res =
                resolver.resolveReference(
                    Resolver.Ref(collected.path, collected.parts),
                    ResolutionContext(
                        schemaCode = doc.schemaCode,
                        namespace = doc.namespace,
                        imports = doc.imports,
                        packageName = doc.packageName,
                    ),
                )
            if (res is ResolutionResult.Resolved &&
                (res.viaStep == ResolutionStep.NamedImport || res.viaStep == ResolutionStep.WildcardImport)
            ) {
                usedTargets += res.symbol.qname.substringBeforeLast('.')
            }
        }

        for (imp in doc.imports) {
            if (!imp.wildcard) {
                val pkg = packageOfImport(imp)
                if (pkg.isNotEmpty() && pkg !in usedTargets) {
                    diagnostics +=
                        diag(
                            DiagnosticCode.UnusedImport,
                            DiagnosticSeverity.Warning,
                            "Import '${imp.target}' is not referenced",
                            imp.source,
                        )
                }
            }
        }

        return diagnostics
    }

    private fun validateDuplicateMappings(): List<ValidationDiagnostic> {
        val diagnostics = mutableListOf<ValidationDiagnostic>()
        for (qname in symbols.allQnames()) {
            val entries = symbols.getAll(qname)
            if (entries.size < 2) continue
            if (!isEr2db(entries.first().kind)) continue
            val sources = entries.map { it.mappingSource ?: MappingSource.Explicit }.toSet()
            if (MappingSource.Inline !in sources) continue
            for (e in entries) {
                val others =
                    entries
                        .filter { !(it.documentUri == e.documentUri && it.source.line == e.source.line) }
                        .joinToString(", ") { "${it.documentUri}:${it.source.line}" }
                diagnostics +=
                    error(
                        DiagnosticCode.DuplicateMapping,
                        "Duplicate mapping for \"$qname\" — declared in ${entries.size} places: $others",
                        e.source,
                    )
            }
        }
        return diagnostics
    }

    private fun isEr2db(kind: String): Boolean =
        kind == "er2dbEntity" || kind == "er2dbAttribute" || kind == "er2dbRelation"

    /** Search blocks reachable from a top-level def, paired with the best source span. */
    private fun searchBlocksOf(def: Definition): List<Pair<SearchHintsValue, SourceLocation>> {
        val out = mutableListOf<Pair<SearchHintsValue, SourceLocation>>()
        // Mirrors `searchBlocksOf` in validator.ts: every def carrying a top-level
        // `search` field (all but column/attribute) contributes it, plus the child
        // search blocks on table/view columns and entity attributes. Procedures have
        // no `search` field and their result-columns are NOT walked (TS parity).
        when (def) {
            is EntityDef -> {
                out += def.search to def.source
                def.attributes.forEach { out += it.search to it.source }
            }
            is TableDef -> {
                out += def.search to def.source
                def.columns.forEach { out += it.search to it.source }
            }
            is ViewDef -> {
                out += def.search to def.source
                def.columns.forEach { out += it.search to it.source }
            }
            is RelationDef -> out += def.search to def.source
            is QueryDef -> out += def.search to def.source
            is RoleDef -> out += def.search to def.source
            else -> {}
        }
        return out
    }

    private fun error(
        code: DiagnosticCode,
        message: String,
        source: SourceLocation,
    ) = ValidationDiagnostic(code, DiagnosticSeverity.Error, message, source)

    private fun diag(
        code: DiagnosticCode,
        severity: DiagnosticSeverity,
        message: String,
        source: SourceLocation,
    ) = ValidationDiagnostic(code, severity, message, source)
}
