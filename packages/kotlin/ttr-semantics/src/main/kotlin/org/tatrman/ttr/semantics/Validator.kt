package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.diagnostics.DiagnosticCode
import org.tatrman.ttr.parser.diagnostics.DiagnosticSeverity
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.ImportStatement
import org.tatrman.ttr.parser.model.SourceLocation

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
 * `packages/semantics/src/validator.ts`. Implemented in stage 2.5.
 */
class Validator(
    @Suppress("unused") private val symbols: SymbolTable,
    @Suppress("unused") private val resolver: Resolver,
    @Suppress("unused") private val manifest: ResolvedManifest,
) {
    fun validateDocument(doc: SemanticDocument): List<ValidationDiagnostic> =
        TODO("Validator.validateDocument — stage 2.5")

    fun validateReferences(doc: SemanticDocument): List<ValidationDiagnostic> =
        TODO("Validator.validateReferences — stage 2.5")

    fun validateProject(): List<ValidationDiagnostic> = TODO("Validator.validateProject — stage 2.5")

    fun validateImports(doc: SemanticDocument): List<ValidationDiagnostic> =
        TODO("Validator.validateImports — stage 2.5")
}
