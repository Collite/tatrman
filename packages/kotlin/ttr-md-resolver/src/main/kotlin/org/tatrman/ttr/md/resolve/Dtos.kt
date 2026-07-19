// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import kotlinx.serialization.Serializable
import org.tatrman.ttr.semantics.md.AggKind

/**
 * A model symbol name. Contracts §3 types this `QualifiedName`; in this module it is a plain simple
 * name within the single `model md` namespace — [org.tatrman.ttr.semantics.md.MdModel] resolves all
 * cross-refs (`md.Money`, `Customer.name`) to simple names on the way in, so the resolver never
 * carries a package prefix. Kept as `String` deliberately: the field *names* below are the public
 * contract, and a `String` serializes identically to any value-class QualifiedName while avoiding a
 * dependency on ttr-metadata / plan-proto (MDS1 keeps this module standalone). Attribute references
 * use the `Dimension.attribute` dotted form (e.g. `Customer.name`).
 */
typealias QualifiedName = String

/** How a dimension is constrained in a path: pinned to one member, restricted, or free (§2). */
@Serializable
sealed interface Selector {
    /** Pinned to exactly one member. */
    @Serializable
    data class Pinned(
        val member: MemberRef,
    ) : Selector

    /** Restricted free dimension — an explicit member set (D15, braces compulsory). */
    @Serializable
    data class MemberSet(
        val members: List<MemberRef>,
    ) : Selector

    /** Restricted free dimension — an inclusive range over an ordered domain. */
    @Serializable
    data class Range(
        val lo: MemberRef,
        val hi: MemberRef,
    ) : Selector

    /** Fully free (`dim.*`) — becomes a group-by key / result dimension. */
    @Serializable
    data object Star : Selector
}

/** A member value. [deferred] marks a disconnected-mode coordinate whose existence is unchecked (R13). */
@Serializable
data class MemberRef(
    val text: String,
    val deferred: Boolean = false,
)

/**
 * One resolved dimension coordinate: which [dimension]/[attribute] is constrained and how
 * ([selector]). [viaCalc] names the calc-catalog map when the attribute is computed (R12), else null.
 */
@Serializable
data class Coordinate(
    val dimension: QualifiedName,
    val attribute: QualifiedName,
    val selector: Selector,
    val viaCalc: QualifiedName? = null,
)

/**
 * The canonical (desugared) form of a path: cubelet, its coordinates (rendered in the cubelet's
 * declared dimension order), the measure, and the aggregation. Text form (§3):
 * `sales[customer.name: "Kaufland", time.year: 2025].net @ sum`.
 */
@Serializable
data class CanonicalPath(
    val cubelet: QualifiedName,
    val coordinates: List<Coordinate>,
    val measure: QualifiedName,
    val agg: AggKind,
)

/** Inferred result shape = the free dimensions (D10): 0 = scalar, 1 = vector, n = sub-cubelet. */
@Serializable
data class PathShape(
    val freeDims: List<QualifiedName>,
)

/** The hover/agent payload (R14): one step per component, plus one per filled default. */
@Serializable
data class Explanation(
    val steps: List<ExplainStep>,
)

/** One explanation step. [token] is null for a filled default (R10). [via] names the source. */
@Serializable
data class ExplainStep(
    val token: String?,
    val slot: String,
    val via: String,
)

/** The result of [MdPathResolver.resolve] (§3). */
@Serializable
sealed interface ResolutionOutcome {
    /** A single consistent assignment. */
    @Serializable
    data class Resolved(
        val path: CanonicalPath,
        val shape: PathShape,
        val explanation: Explanation,
    ) : ResolutionOutcome

    /** >1 consistent assignment (R9) — all alternatives, deterministically ordered. */
    @Serializable
    data class Ambiguous(
        val alternatives: List<Resolved>,
    ) : ResolutionOutcome

    /** No consistent assignment, or a structural error — one or more diagnostics. */
    @Serializable
    data class Failed(
        val diagnostics: List<MdDiagnostic>,
    ) : ResolutionOutcome
}
