// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

/** The closed v1 aggregation set (contracts R5 agg tokens: sum, avg, min, max, count). */
enum class AggKind { SUM, AVG, MIN, MAX, COUNT }

/**
 * Map an aggregation spelling to an [AggKind]; null for an unknown token. Accepts exactly the
 * closed R5 v1 agg set (`sum`/`avg`/`min`/`max`/`count`) — no synonyms, matching the TS side.
 *
 * `latestValid` ("latest valid") is the semi-additive time aggregation; it maps to MAX here only
 * as a coarse stand-in. Real lowering derives the latest-valid value from the `valid_from`/
 * `valid_to` roles (contracts §12 R31/D26), NOT a plain MAX over the measure — S4 must not lower
 * from [AggKind.MAX] alone. The `sum` fallback and `latestValid ⇒ MAX` intent are confirmed against
 * MD feature contracts §6.5 (Additivity consistency: additive ⇒ single fn, default `sum`).
 */
fun aggKindOf(spelling: String): AggKind? =
    when (spelling.lowercase()) {
        "sum" -> AggKind.SUM
        "avg" -> AggKind.AVG
        "min" -> AggKind.MIN
        "max" -> AggKind.MAX
        "count" -> AggKind.COUNT
        "latestvalid" -> AggKind.MAX // "latest valid" ⇒ MAX over validity (D26); see S4 caveat above
        else -> null
    }

/** A cubelet's default measure = its first declared measure (R10 default fill), or null if none. */
val MdCubelet.defaultMeasure: String?
    get() = measures.firstOrNull()

/**
 * A measure's default aggregation: its declared `aggregation: default`, else SUM (the additive
 * fallback, contracts §6.5). See the semantics/lowering caveats on [aggKindOf].
 *
 * WARNING for S2/S3 default-fill (R10): this returns SUM *unconditionally* when there is no
 * declared default OR the declared token is unrecognised — and it does NOT consult the measure's
 * `class`. Per contracts §6.5 a `nonAdditive` measure must never be blind-summed downstream, and an
 * unknown agg token is an `md/…` diagnostic, not a silent SUM. The resolver MUST gate on
 * [MdMeasure.measureClass] / validate the token (TS-side, MDS2) before consuming this value.
 */
val MdMeasure.defaultAgg: AggKind
    get() = aggregation?.default?.let(::aggKindOf) ?: AggKind.SUM

/** The default aggregation for a cubelet's default measure, resolved through [MdModel]. */
fun MdModel.defaultAggOf(cubeletName: String): AggKind? {
    val measureName = cubelets[cubeletName]?.defaultMeasure ?: return null
    return measures[measureName]?.defaultAgg
}
