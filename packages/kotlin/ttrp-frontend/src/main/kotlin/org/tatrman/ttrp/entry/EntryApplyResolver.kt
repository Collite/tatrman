// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttrp.ast.ImportDecl
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.parser.TtrpParser
import org.tatrman.ttrp.resolve.ImportScope
import org.tatrman.ttrp.resolve.ModelIndex

/**
 * EN-P2 apply-program resolution (contracts §1/§3): recognize a `<table>-entry-apply` program by
 * filename (TTR-P identity is the filename — `PRS-002`; the bare-fragment precedent), resolve its
 * target table through `ttr-metadata`, bind the one §5 batch parameter, resolve the verb from
 * [EntryVerbCatalog], and run the surface checks into a typed [EntryApplyUnit].
 *
 * The verb + batch reach this resolver as structured inputs, not parsed from a final verb-call surface
 * — that spelling is deferred (`// EN: surface pins on PLA-2`, contracts §1). The recognition predicate
 * ([isEntryApply]) is ready for `TtrpChecker` to route through once the surface lands (EN-P3+).
 */
object EntryApplyResolver {
    private const val SUFFIX = "-entry-apply"

    /** The typed apply unit — the resolved target + verb + batch and the accumulated diagnostics. */
    data class EntryApplyUnit(
        val fileName: String,
        val targetName: String,
        val target: DbTable?,
        val verb: EntryVerbCatalog.VerbSig?,
        val batch: RowBatch?,
        val diagnostics: List<TtrpDiagnostic>,
    ) {
        /** True when nothing errored — the program typechecks (P2 DoD: valid fixtures per semantics). */
        val ok: Boolean get() = diagnostics.none { it.severity == Severity.ERROR }
    }

    /** True iff [fileName] names an apply program (`<table>-entry-apply.ttrp`). */
    fun isEntryApply(fileName: String): Boolean = targetName(fileName) != null

    /** The `<table>` segment of an apply-program filename, or null when [fileName] is not one. */
    fun targetName(fileName: String): String? {
        val base =
            fileName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .removeSuffix(".ttrp")
        val target = base.removeSuffix(SUFFIX)
        return target.takeIf { it != base && it.isNotEmpty() }
    }

    /**
     * Resolve + typecheck an apply program. [verbId] / [batch] are the (deferred-surface) apply
     * inputs; [physicalDelete] marks a delete expressed as a raw physical delete rather than
     * `delete-rows` (the EN-004 trigger). Parses [source] itself so the unit is self-contained.
     */
    fun resolve(
        fileName: String,
        source: String,
        verbId: String?,
        batch: RowBatch?,
        modelIndex: ModelIndex?,
        physicalDelete: Boolean = false,
    ): EntryApplyUnit {
        val targetName =
            targetName(fileName)
                ?: error("resolve() called on a non-apply file: $fileName")
        val loc = SourceLocation(fileName, 1, 0, 1, 0, 0, 0)
        val diags = mutableListOf<TtrpDiagnostic>()

        val parsed = TtrpParser.parseString(source, fileName)
        val document: TtrpDocument = parsed.document
        diags += parsed.diagnostics

        // ---- target resolution (EN-007) ----
        val imports = importsOf(document)
        val target =
            modelIndex
                ?.findLoadable(targetName, imports)
                ?.filterIsInstance<DbTable>()
                ?.firstOrNull()
        if (target == null) {
            diags +=
                TtrpDiagnostic(
                    TtrpDiagnosticId.EN_007,
                    Severity.ERROR,
                    "apply program `$fileName` targets `$targetName`, which has no md model resolution",
                    loc,
                )
        }

        val verb = verbId?.let { EntryVerbCatalog.byId(it) }

        // ---- batch shape (EN-001) ----
        if (batch != null && target != null) {
            val effectiveDateRequired = verb?.id == "entry.effective-date-change"
            diags += BatchShapeChecker.check(batch, target, effectiveDateRequired, loc)
        }

        // ---- verb vs declaration (EN-002/003/004) ----
        if (verb != null && target != null) {
            diags += VerbDeclarationChecker.check(verb, target, physicalDelete, loc)
        }

        // ---- purity surface (EN-005) ----
        diags += EntryPuritySurfaceCheck.check(document)

        return EntryApplyUnit(fileName, targetName, target, verb, batch, diags)
    }

    private fun importsOf(document: TtrpDocument): List<ImportScope> =
        document.statements
            .filterIsInstance<ImportDecl>()
            .map { ModelIndex.importScope(it.qname.parts, it.qname.text) }
}
