// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

/** The closed v1 aggregation set (contracts R5 agg tokens: sum, avg, min, max, count). */
enum class AggKind { SUM, AVG, MIN, MAX, COUNT }

/**
 * Map an aggregation spelling to an [AggKind]; null for an unknown token. The semi-additive
 * `latestValid` "latest valid" time aggregation resolves to MAX over the validity ordering (D26).
 *
 * NOTE (verify at review): the exact fallback/`latestValid` mapping is governed by the MD feature
 * contracts §measures (lives under `project/tatrman/features/md/`, not the code repo) — this
 * mirrors the design-note intent; confirm the rule id when the contracts are to hand.
 */
fun aggKindOf(spelling: String): AggKind? =
    when (spelling.lowercase()) {
        "sum" -> AggKind.SUM
        "avg", "average", "mean" -> AggKind.AVG
        "min" -> AggKind.MIN
        "max" -> AggKind.MAX
        "count" -> AggKind.COUNT
        "latestvalid" -> AggKind.MAX // "latest valid" ⇒ MAX over validity (D26)
        else -> null
    }

/** A cubelet's default measure = its first declared measure (R10 default fill), or null if none. */
val MdCubelet.defaultMeasure: String?
    get() = measures.firstOrNull()

/**
 * A measure's default aggregation: its declared `aggregation: default`, else SUM (the additive
 * fallback). See the verify-at-review note on [aggKindOf].
 */
val MdMeasure.defaultAgg: AggKind
    get() = aggregation?.default?.let(::aggKindOf) ?: AggKind.SUM

/** The default aggregation for a cubelet's default measure, resolved through [MdModel]. */
fun MdModel.defaultAggOf(cubeletName: String): AggKind? {
    val measureName = cubelets[cubeletName]?.defaultMeasure ?: return null
    return measures[measureName]?.defaultAgg
}
