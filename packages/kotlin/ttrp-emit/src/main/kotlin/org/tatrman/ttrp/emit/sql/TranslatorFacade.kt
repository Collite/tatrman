package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.PlanNode
import org.tatrman.proteus.v1.Language
import org.tatrman.proteus.v1.SqlDialect
import org.tatrman.translator.framework.ModelHandle
import org.tatrman.translator.orchestrator.Translator
import org.tatrman.translator.orchestrator.UnparseResult
import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.TtrpEmitException

/**
 * The ONE class in ttrp-emit that touches the translation core (`org.tatrman.translator.*`)
 * and the `plan.v1` wire types. Everything Calcite-shaped lives behind the published
 * `Translator.unparseFromRelNode(PlanNode, Language.SQL, dialect)` — this repo never
 * references a Calcite class (see NoCalciteOutsideFacadeTest). That is a deliberate
 * reconciliation of the Stage-3.1 task list's "drive Calcite directly" rules against the
 * actually-published translator API, which exposes only the whole-plan unparse (overview R9,
 * "whichever granularity the API offers"): the four Calcite exception classes are already
 * caught inside the translator and surfaced as [UnparseResult.Failure] `code`s, which we
 * map to structured [EmitDiagnosticId]s here.
 *
 * A fresh [Translator] is constructed per facade (one facade per island — [SqlIslandEmitter]),
 * honouring the translator's single-use-per-stage contract.
 *
 * @param model the schema surface for this island (base tables + CTE pseudo-tables).
 * @param dialect the target SQL dialect (from [DialectRegistry]).
 */
class TranslatorFacade(
    model: ModelHandle,
    private val dialect: SqlDialect,
) {
    private val translator = Translator(model)

    /**
     * Unparse a single physical relational plan (built by [PlanNodeBuilder]) to dialect SQL.
     * @throws TtrpEmitException with the mapped [EmitDiagnosticId] on any translator failure.
     */
    fun unparse(
        plan: PlanNode,
        island: String? = null,
    ): String =
        when (val r = translator.unparseFromRelNode(plan, Language.SQL, dialect)) {
            is UnparseResult.Success -> r.output
            is UnparseResult.Failure -> throw map(r.code, r.message, island)
        }

    private fun map(
        code: String,
        message: String,
        island: String?,
    ): TtrpEmitException {
        // Translator failure codes (orchestrator/Translator.kt + codec/sql/SqlValidator.kt):
        //   parse_failed            <- SqlParseException
        //   validation_failed       <- ValidationException / SqlValidatorException (folded)
        //   rel_conversion_failed   <- RelConversionException / other RuntimeException
        //   sql_unparse_failed / rel_node_decode_failed / unparse_rejects_pre_physical (orchestrator)
        val id =
            when (code) {
                "parse_failed" -> EmitDiagnosticId.PARSE_FAILED
                "validation_failed" -> EmitDiagnosticId.VALIDATION_FAILED
                "rel_conversion_failed" -> EmitDiagnosticId.REL_CONVERSION_FAILED
                else -> EmitDiagnosticId.REL_CONVERSION_FAILED
            }
        return TtrpEmitException(id, detail = "$code: $message", island = island)
    }
}
