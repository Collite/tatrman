// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.materialize

import org.tatrman.ttr.parser.model.AttrColumnBinding
import org.tatrman.ttr.parser.model.CubeletDef
import org.tatrman.ttr.parser.model.CubeletMeasure
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.JournalingSpec
import org.tatrman.ttr.parser.model.MeasureColumnBinding
import org.tatrman.ttr.parser.model.Md2dbCubeletDef
import org.tatrman.ttr.parser.model.Reference
import org.tatrman.ttr.parser.model.ShapeSpec
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttrp.ast.MdWithClause

/** The physical shape of a materialized cubelet's backing table (contracts §2 · R27 `with { shape }`). */
enum class MatShape { WIDE, LONG }

/** The journaling mode of a materialized cubelet (contracts §12 · R27 `with { journal }`; default overwrite). */
enum class MatJournal { OVERWRITE, INVALIDATE, DIFF }

/**
 * The inferred definition of a **materialized** cubelet (`C := e with { … }`, R27). Materialize on a
 * fresh target derives the whole logical + physical model from the RHS shape and the `with` clause,
 * then emits it as a generated `.ttrm` (MDS7; [MaterializeEmitter]). This spec is the compile-side
 * intermediate the checker records and the emitter renders — it makes the inference (R27's "logical
 * definition inferred") an explicit, testable value rather than a side effect buried in emission.
 *
 * Conventions (deterministic, so re-runs and diffs behave — MDS7):
 *  - **grain** = the RHS free dims (`Dimension.attribute`, in the RHS's canonical order); **measure** =
 *    the RHS measure (v1: single, D12);
 *  - each grain attribute binds to a column `dimension_attribute` (lowercased, `.`→`_`) —
 *    collision-free across dimensions and readable;
 *  - **wide** binds the measure to a column named after it; **long** uses a `measure_code`/`value`
 *    column pair with the measure's upper-cased name as its code;
 *  - the backing table defaults to `db.dbo.md_<name>` (overridable via `with { table: … }`), in the
 *    default `db` schema;
 *  - **invalidate** journaling uses an `is_current` valid column (the role-based declaration is S5C-B.4).
 */
data class MaterializeSpec(
    val name: String,
    /** RHS free dims as `Dimension.attribute` refs, in canonical order — the new cubelet's grain. */
    val grain: List<String>,
    /** The RHS measure name (v1: a single measure, D12). */
    val measure: String,
    val shape: MatShape,
    /** The backing fact table ref (`db.dbo.md_<name>` by default, or the `with { table }` override). */
    val table: String,
    val journal: MatJournal,
) {
    /**
     * The generated logical + physical definitions (R27): a `def cubelet` (grain + measure ref) and its
     * `def md2db_cubelet` binding. Rendered to `.ttrm` text by [MaterializeEmitter]; the [schemaCode] is
     * the model schema the cubelet lives in (its binding's `cubelet:` ref is `<schemaCode>.<name>`).
     */
    fun toDefinitions(schemaCode: String = DEFAULT_SCHEMA): List<Definition> {
        val cubelet =
            CubeletDef(
                name = name,
                source = SourceLocation.UNKNOWN,
                grain = grain.map { Reference(it) },
                measures = listOf(CubeletMeasure.Ref(Reference(measure))),
            )
        // LinkedHashMap keeps grain order; the renderer sorts map-valued props by key regardless (B.1).
        val attributes: Map<String, AttrColumnBinding> =
            grain.associateWithTo(LinkedHashMap()) { AttrColumnBinding.Column(columnFor(it)) }
        val measures: Map<String, MeasureColumnBinding> =
            mapOf(
                measure to
                    when (shape) {
                        MatShape.WIDE -> MeasureColumnBinding.Column(measure)
                        MatShape.LONG -> MeasureColumnBinding.Code(measure.uppercase())
                    },
            )
        val shapeSpec =
            when (shape) {
                MatShape.WIDE -> ShapeSpec.Wide
                MatShape.LONG -> ShapeSpec.Long(codeColumn = LONG_CODE_COLUMN, valueColumn = LONG_VALUE_COLUMN)
            }
        val journalingSpec =
            when (journal) {
                MatJournal.OVERWRITE -> JournalingSpec.Overwrite
                MatJournal.DIFF -> JournalingSpec.Diff
                MatJournal.INVALIDATE -> JournalingSpec.Invalidate(validColumn = INVALIDATE_VALID_COLUMN)
            }
        val binding =
            Md2dbCubeletDef(
                name = "${name}$BINDING_SUFFIX",
                source = SourceLocation.UNKNOWN,
                cubeletRef = Reference("$schemaCode.$name"),
                table = Reference(table),
                shape = shapeSpec,
                attributes = attributes,
                measures = measures,
                journaling = journalingSpec,
            )
        return listOf(cubelet, binding)
    }

    /** The physical column a grain attribute binds to: `Customer.name` → `customer_name` (collision-free). */
    private fun columnFor(attribute: String): String = attribute.lowercase().replace('.', '_')

    companion object {
        const val DEFAULT_SCHEMA = "md"
        const val BINDING_SUFFIX = "_binding"
        const val LONG_CODE_COLUMN = "measure_code"
        const val LONG_VALUE_COLUMN = "value"
        const val INVALIDATE_VALID_COLUMN = "is_current"

        /**
         * Build a spec from the statement's inferred [grain]/[measure] and its `with` clause, applying the
         * R27 defaults. The `with` keys are already validated by the checker (shape required and one of
         * `wide`/`long`); an unrecognised `journal:` value falls back to the `overwrite` default.
         */
        fun from(
            name: String,
            grain: List<String>,
            measure: String,
            withClause: MdWithClause?,
        ): MaterializeSpec {
            val keys = withClause?.entries?.associate { it.key to it.value } ?: emptyMap()
            val shape = if (keys["shape"] == "long") MatShape.LONG else MatShape.WIDE
            val journal =
                when (keys["journal"]) {
                    "invalidate" -> MatJournal.INVALIDATE
                    "diff" -> MatJournal.DIFF
                    else -> MatJournal.OVERWRITE
                }
            val table = keys["table"] ?: "db.dbo.md_$name"
            return MaterializeSpec(name, grain, measure, shape, table, journal)
        }
    }
}
