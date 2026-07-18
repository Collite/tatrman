// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import kotlinx.serialization.Serializable

/**
 * The MD dot-path diagnostic roster (contracts §6). Ids `TTRP-MD-001…014` — the resolver-core
 * range; the statement/writeback ids (`015…023`) arrive with S5/S5C. The `text` is the canonical
 * one-line meaning from §6; call sites append a `detail` (the offending token, missing dims, …).
 *
 * Serialized by name (`UNKNOWN_COMPONENT`, …); the stable public field is [MdDiagnostic.code].
 */
@Serializable
enum class MdDiagId(
    val code: String,
    val text: String,
) {
    UNKNOWN_COMPONENT("TTRP-MD-001", "unknown path component (no candidate slot)"),
    UNRESOLVABLE("TTRP-MD-002", "unresolvable path (no consistent assignment)"),
    AMBIGUOUS("TTRP-MD-003", "ambiguous path (alternatives listed as canonical paths)"),
    UNBINDABLE_SELECTOR("TTRP-MD-004", "`*`/set/range not bindable to an attribute"),
    MULTIPLE_MEASURES("TTRP-MD-005", "more than one measure in a path"),
    SAME_ATTR_REPETITION("TTRP-MD-006", "bare same-attribute repetition — use `{a, b}`"),
    BARE_MEMBER_DISCONNECTED("TTRP-MD-007", "bare member token in disconnected mode — qualify (`dim.member`)"),
    NON_SCALAR_IN_SCALAR_POS("TTRP-MD-008", "non-scalar path in scalar-only position"),
    INCOMPLETE_STRICT_LHS("TTRP-MD-009", "incomplete strict LHS (missing grain dimensions / measure)"),
    SPREAD_WITHOUT_STRATEGY("TTRP-MD-010", "spread without a declared allocation strategy"),
    UNKNOWN_MEMBER("TTRP-MD-011", "unknown member (connected compile, or bind time)"),
    SHADOWED_BY_COLUMN("TTRP-MD-012", "path shadowed by input column — column wins; qualify to force MD"),
    CATALOG_LOST("TTRP-MD-013", "member catalog lost mid-session — held snapshot in use (stale)"),
    SEARCH_BOUND_EXCEEDED("TTRP-MD-014", "path search bound exceeded (pathological input)"),
}

/**
 * One diagnostic occurrence. [detail] carries the case-specific payload (echoed token, per-token
 * failure reason, listed alternatives, …); [token] is the raw component text when a single one is
 * to blame (MD-001, MD-006), else null.
 */
@Serializable
data class MdDiagnostic(
    val id: MdDiagId,
    val detail: String = "",
    val token: String? = null,
) {
    /** The `TTRP-MD-0NN` public code, for message rendering and cross-target parity. */
    val code: String get() = id.code
}
