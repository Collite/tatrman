// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import org.tatrman.ttr.semantics.md.MdModel
import java.time.Instant
import java.time.ZoneOffset

/**
 * Evaluation-relative calc tokens (R12, §5 "time is special only in its catalog"). A token like
 * `lastMonth` anchors on [asof] (D17) and lowers to a **computed** coordinate over the model's
 * corresponding time attribute — with `viaCalc` set to the catalog function — working with or without
 * a time-dim table (the value comes from `asof`, not the member snapshot).
 *
 * S2-C subset: the relative month/quarter/year family. The full machinery — arbitrary catalog maps,
 * month-name members (`month.june`), and offsets on any ordered codomain — is a follow-up (noted in
 * the S2-C coder notes); those tokens simply stay unrecognised here and fall through to the search.
 */
object CalcResolver {
    private data class Relative(
        val granularity: String, // "month" | "quarter" | "year"
        val calcFn: String, // catalog function name for viaCalc
        val value: (java.time.LocalDate) -> Int,
    )

    private val TOKENS: Map<String, Relative> =
        mapOf(
            "thisMonth" to Relative("month", "monthOfDate") { it.monthValue },
            "lastMonth" to Relative("month", "monthOfDate") { it.minusMonths(1).monthValue },
            "thisYear" to Relative("year", "yearOfDate") { it.year },
            "lastYear" to Relative("year", "yearOfDate") { it.minusYears(1).year },
            "thisQuarter" to Relative("quarter", "quarterOfMonth") { (it.monthValue - 1) / 3 + 1 },
            "lastQuarter" to Relative("quarter", "quarterOfMonth") { (it.minusMonths(3).monthValue - 1) / 3 + 1 },
        )

    /** Extract relative-calc coordinates from [free], returning them plus the untouched components. */
    fun extract(
        free: List<PathComponent>,
        model: MdModel,
        asof: Instant,
    ): Pair<List<Coordinate>, List<PathComponent>> {
        val date = asof.atZone(ZoneOffset.UTC).toLocalDate()
        val coords = mutableListOf<Coordinate>()
        val remaining = mutableListOf<PathComponent>()
        for (c in free) {
            val rel = (c as? PathComponent.Ident)?.let { TOKENS[it.text] }
            val attr = rel?.let { attributeFor(it.granularity, model) }
            if (rel != null && attr != null) {
                val pinned = Selector.Pinned(MemberRef(rel.value(date).toString()))
                coords += Coordinate(attr.dimension, attr.qname, pinned, rel.calcFn)
            } else {
                remaining += c
            }
        }
        return coords to remaining
    }

    private data class AttrRef(
        val dimension: String,
        val attribute: String,
    ) {
        val qname: String get() = "$dimension.$attribute"
    }

    /** The model attribute at the requested time granularity (by attribute name, e.g. `month`). */
    private fun attributeFor(
        granularity: String,
        model: MdModel,
    ): AttrRef? =
        model.attributes.values
            .firstOrNull { it.name.equals(granularity, ignoreCase = true) }
            ?.let { AttrRef(it.dimension, it.name) }
}
