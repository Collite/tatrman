// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/*
 * ED-P1 derivation surface (ED `contracts.md` §1/§2) — the typecheck half of the FO-8 cash-leg unblock
 * EN did not ship: a target column whose value is COMPUTED by a pure `call-fn` from the proposal (the
 * cash leg derived from the security leg), rather than taken from the batch. Like the verb + call-fn
 * surfaces (EN-P2/P5), the spelling is deferred (`// EN: surface pins on PLA-2`), so a derivation
 * reaches the compiler as a structured [DerivationDemand]; this object is the pure typecheck.
 *
 * It reuses the delivered call-fn contract wholesale ([CallFnResolver]: purity EN-005, arity/arg-type/
 * id/version EN-006, deploy-pin P-3) and adds only the derivation-specific checks: the derived column
 * exists on the target md shape, is not ALSO proposed in `values`, and the function's SPI return type
 * matches the md column type. No new diagnostic code — the conditions map onto EN-001 (shape) and
 * EN-006 (call-fn). Lowering + emit are ED-P2; door-side evaluation is ED-P3.
 */

/**
 * A structured derivation (§1): the target [column] is bound to [call] (`call-fn("<id>", args…)`), whose
 * args reference batch/state values. The concrete surface spelling pins on PLA-2; the shape is frozen.
 */
data class DerivationDemand(
    val column: String,
    val call: CallFnDemand,
    val location: SourceLocation,
)

/**
 * `TTRP-EN-001/006` — a program's [DerivationDemand]s against the resolved target and the deploy
 * registry (contracts §2). Pure; no lowering, no emit. [proposedColumns] is the lower-cased set of
 * columns the batch proposes in `values` — a derived column may not also be proposed (a column is
 * either supplied or derived, never both).
 */
object DerivationChecker {
    fun check(
        target: DbTable,
        derivations: List<DerivationDemand>,
        proposedColumns: Set<String>,
        registry: CanonFunctionResolver,
    ): List<TtrpDiagnostic> {
        val out = mutableListOf<TtrpDiagnostic>()
        val columns: Map<String, MdCategory> =
            target.columns.associate {
                it.qname.name
                    .substringAfterLast('.')
                    .lowercase() to categoryOf(it.dataType)
            }

        for (d in derivations) {
            val col = d.column.lowercase()
            val category = columns[col]
            if (category == null) {
                out +=
                    en001(
                        "unknown derived column `${d.column}` — not on the target `${target.qname.name}`",
                        d.location,
                    )
            }
            if (col in proposedColumns) {
                out +=
                    en001(
                        "column `${d.column}` is both proposed and derived — a column is one or the other",
                        d.location,
                    )
            }

            // Reuse the delivered call-fn contract (purity/arity/arg-type/id/version → EN-005/006 + pin).
            val callResult = CallFnResolver.resolve(listOf(d.call), registry)
            out += callResult.diagnostics

            // The function's SPI return type must fit the md column type (EN-006) — only worth checking
            // once the column is known and the call itself resolved (else it is already diagnosed above).
            if (category != null && callResult.ok) {
                val pinned = registry.resolve(d.call.functionId, d.call.versionConstraint) as? Resolution.Pinned
                val ret = pinned?.fn?.sig?.signature?.returns
                if (ret != null && !returnFits(ret, category)) {
                    out +=
                        en006(
                            "derivation of `${d.column}` (md `${category.name.lowercase()}`) is bound to " +
                                "`call-fn(\"${d.call.functionId}\")` returning `$ret`",
                            d.location,
                        )
                }
            }
        }
        return out
    }

    /** A canon-function return type fits a column when their categories agree (OTHER is the coercion escape). */
    private fun returnFits(
        returnType: String,
        column: MdCategory,
    ): Boolean {
        val ret = categoryOf(returnType)
        return ret == column || ret == MdCategory.OTHER || column == MdCategory.OTHER
    }

    private fun en001(
        message: String,
        location: SourceLocation,
    ) = TtrpDiagnostic(TtrpDiagnosticId.EN_001, Severity.ERROR, message, location)

    private fun en006(
        message: String,
        location: SourceLocation,
    ) = TtrpDiagnostic(TtrpDiagnosticId.EN_006, Severity.ERROR, message, location)
}
