// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

import org.tatrman.ttr.parser.model.AttrColumnBinding
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.JournalingSpec
import org.tatrman.ttr.parser.model.Md2dbCubeletDef
import org.tatrman.ttr.parser.model.Md2dbDomainDef
import org.tatrman.ttr.parser.model.Md2dbMapDef
import org.tatrman.ttr.parser.model.MeasureColumnBinding
import org.tatrman.ttr.parser.model.ShapeSpec

/**
 * The MD → physical binding graph (contracts §4) — the structural layer the read/write lowering (S4)
 * consumes, distinct from the logical [MdModel] (MDS2). Built from the `md2db_*` parser defs;
 * validation stays TS-side. Keyed by resolved simple names (the leading `md.` namespace stripped, as
 * [MdModel] does), so `bindings.cubelets["sales"]` pairs with `model.cubelets["sales"]`.
 *
 * `md2er_cubelet` (structural er binding) is intentionally excluded — it feeds the er read path, not
 * the db lowering. Multi-source cubelets (several `md2db_cubelet` defs for one cubelet, contracts
 * §4.1/§6.6) collapse to one [CubeletBinding]: the **first** def is canonical (attributes/measures/
 * shape/journaling — the defs agree on grain), and every def's fact table is collected into
 * [CubeletBinding.sources] for the read lowering to UNION.
 */
data class MdBindings(
    val cubelets: Map<String, CubeletBinding>,
    val domains: Map<String, DomainBinding>,
    val maps: Map<String, MapBinding>,
) {
    companion object {
        fun from(defs: List<Definition>): MdBindings = MdBindingsBuilder.build(defs)
    }
}

/** A cubelet → fact-table binding: physical [table], [shape], per-attribute/measure columns, journaling. */
data class CubeletBinding(
    /** The logical cubelet's simple name. */
    val cubelet: String,
    /** The primary backing fact table (full physical ref, e.g. `db.dbo.f_sales`); the first of [sources]. */
    val table: String,
    val shape: BindingShape,
    /** `Dimension.attribute` → its column binding (grain + drillable columns). */
    val attributes: Map<String, AttrBinding>,
    /** Measure name → its column binding. */
    val measures: Map<String, MeasureBinding>,
    val journaling: Journaling,
    /**
     * All fact tables bound to this cubelet (contracts §4.1 multi-source): one entry for the single-
     * source case, several for a cubelet with multiple `md2db_cubelet` defs — the read lowering UNIONs
     * them. They share column bindings (they agree on grain + measures), differing only in table. Empty
     * is treated as `[table]` by consumers.
     */
    val sources: List<String> = emptyList(),
)

/** Wide (one column per measure) or long (a measure-code column + a value column). */
sealed interface BindingShape {
    data object Wide : BindingShape

    data class Long(
        val codeColumn: String,
        val valueColumn: String,
    ) : BindingShape
}

/** How an attribute reaches a column: directly, or via a map-backing table (a join hop, design §6.1). */
sealed interface AttrBinding {
    data class Column(
        val column: String,
    ) : AttrBinding

    data class Hop(
        val via: String,
        val fromTable: String,
        val fromColumn: String,
    ) : AttrBinding
}

/** A measure's value: a wide column, or a `code` selected from the long value column. */
sealed interface MeasureBinding {
    data class Column(
        val column: String,
    ) : MeasureBinding

    data class Code(
        val code: String,
    ) : MeasureBinding
}

/** Journaling mode of a cubelet binding (contracts §12). */
sealed interface Journaling {
    data object Overwrite : Journaling

    data object Diff : Journaling

    data class Invalidate(
        val validColumn: String,
    ) : Journaling
}

/** A bound domain's member source (a `SELECT DISTINCT column FROM table`, §7). */
data class DomainBinding(
    val domain: String,
    val table: String,
    val column: String,
)

/** A table-backed map's case table + its from/to-domain → column mapping (hop joins). */
data class MapBinding(
    val map: String,
    val table: String,
    val columns: Map<String, String>,
)

/** Builds [MdBindings] from the `md2db_*` parser defs. Resolves refs to simple names as [MdModel] does. */
internal object MdBindingsBuilder {
    fun build(defs: List<Definition>): MdBindings {
        val cubelets =
            defs
                .filterIsInstance<Md2dbCubeletDef>()
                .mapNotNull { d -> (d.cubeletRef?.path?.substringAfterLast('.') ?: return@mapNotNull null) to d }
                .groupBy({ it.first }, { it.second })
                .mapValues { (cubelet, cubeletDefs) ->
                    // Multi-source (§4.1): the first def is canonical (defs agree on grain/measures);
                    // every def's fact table is collected into `sources` for the read UNION.
                    val primary = cubeletDefs.first()
                    CubeletBinding(
                        cubelet = cubelet,
                        table = primary.table?.path ?: "",
                        shape = shapeOf(primary.shape),
                        attributes = primary.attributes.mapValues { attrBinding(it.value) },
                        measures = primary.measures.mapValues { measureBinding(it.value) },
                        journaling = journalingOf(primary.journaling),
                        sources = cubeletDefs.mapNotNull { it.table?.path }.distinct(),
                    )
                }
        val domains =
            defs
                .filterIsInstance<Md2dbDomainDef>()
                .mapNotNull { d ->
                    val name = d.domainRef?.path?.substringAfterLast('.') ?: return@mapNotNull null
                    val cs = d.columnSource ?: return@mapNotNull null
                    name to DomainBinding(domain = name, table = cs.table.path, column = cs.column)
                }.toMap()
        val maps =
            defs
                .filterIsInstance<Md2dbMapDef>()
                .mapNotNull { d ->
                    val name = d.mapRef?.path?.substringAfterLast('.') ?: return@mapNotNull null
                    name to MapBinding(map = name, table = d.table?.path ?: "", columns = d.columns)
                }.toMap()
        return MdBindings(cubelets, domains, maps)
    }

    private fun shapeOf(s: ShapeSpec?): BindingShape =
        when (s) {
            is ShapeSpec.Long -> BindingShape.Long(s.codeColumn, s.valueColumn)
            else -> BindingShape.Wide // null or Wide
        }

    private fun attrBinding(b: AttrColumnBinding): AttrBinding =
        when (b) {
            is AttrColumnBinding.Column -> AttrBinding.Column(b.column)
            is AttrColumnBinding.Via ->
                AttrBinding.Hop(
                    via = b.via.path.substringAfterLast('.'),
                    fromTable = b.from.table.path,
                    fromColumn = b.from.column,
                )
        }

    private fun measureBinding(b: MeasureColumnBinding): MeasureBinding =
        when (b) {
            is MeasureColumnBinding.Column -> MeasureBinding.Column(b.column)
            is MeasureColumnBinding.Code -> MeasureBinding.Code(b.code)
        }

    private fun journalingOf(j: JournalingSpec?): Journaling =
        when (j) {
            is JournalingSpec.Invalidate -> Journaling.Invalidate(j.validColumn)
            JournalingSpec.Diff -> Journaling.Diff
            else -> Journaling.Overwrite // null or Overwrite
        }
}
