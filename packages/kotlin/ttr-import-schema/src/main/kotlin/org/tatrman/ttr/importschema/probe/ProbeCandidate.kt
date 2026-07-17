// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.probe

/** A (possibly multi-column) column reference within a schema — the child or parent side of a probe. */
data class ColumnRef(
    val schema: String,
    val table: String,
    val columns: List<String>,
) {
    /** Bytewise-sortable qualified key `schema.table(col,col)` — drives the pinned probe order. */
    val qkey: String get() = "$schema.$table(${columns.joinToString(",")})"
}

enum class ProbeOrigin {
    /** A declared foreign key — verified first (Q-5 pinned order). */
    DECLARED,

    /** A conventions/name-heuristic candidate — probed after all declared, by sorted qname pair. */
    HEURISTIC,
}

/**
 * A candidate relation to verify: does every non-null `child` value appear as a `parent` value?
 * (Inclusion-dependency / orphan probe.) Declared FKs are re-verified (data can contradict a
 * declared constraint); heuristic candidates are proposed by the S4 name cascade.
 */
data class ProbeCandidate(
    val child: ColumnRef,
    val parent: ColumnRef,
    val origin: ProbeOrigin,
) {
    /** Stable ordering key: declared before heuristic, then by (child, parent) qname pair. */
    val orderKey: String get() = "${origin.ordinal}|${child.qkey}->${parent.qkey}"
}
