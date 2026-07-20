// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.FunctionCall
import org.tatrman.plan.v1.Literal

/**
 * MD dot-path S5C-B.4b — the **technical-column** fills a write stamps onto a journaled cubelet (contracts
 * §12 R31 · MDS8). A journaled table declares technical columns via `semantics { role: … }` (S5C-B.4a
 * vocabulary); on write, the run stamps their provenance:
 *   - `authored_by` = the write principal (run identity from the bundle manifest);
 *   - `written_at`  = the write clock (an ISO-8601 instant — the run clock, **not** `asof`).
 *
 * These are constants of the write, so they ride the write row as extra projected columns (like the
 * invalidate live-flag) — no new DML shape. [addTo] appends them to a write-row projection map.
 *
 * `version = max+1 per grain key` (R31) needs a correlated read of the target's current max, so it is a
 * DML-level fill (a follow-up), not a constant projection — it is intentionally not handled here.
 */
data class WriteTechnical(
    /** The `authored_by`-role column of the target table, or null when the table declares none. */
    val authoredByColumn: String? = null,
    /** The `written_at`-role column of the target table, or null when the table declares none. */
    val writtenAtColumn: String? = null,
    /** The write principal (run identity). */
    val authoredBy: String = "",
    /** The write clock as an ISO-8601 instant (e.g. `2026-07-20T12:00:00Z`). */
    val writtenAt: String = "",
) {
    /** Append the declared technical fills to a write-row [projections] map (target column → expression). */
    fun addTo(projections: MutableMap<String, Expression>) {
        authoredByColumn?.let { projections[it] = strLit(authoredBy) }
        writtenAtColumn?.let { projections[it] = timestampLit(writtenAt) }
    }

    private fun strLit(s: String): Expression =
        Expression.newBuilder().setLiteral(Literal.newBuilder().setStringValue(s).setType("text")).build()

    /** An ISO-8601 instant literal cast to a timestamp so it matches a `written_at` (timestamp) column. */
    private fun timestampLit(iso: String): Expression =
        Expression
            .newBuilder()
            .setFunction(FunctionCall.newBuilder().setOperation("cast").addOperands(strLit(iso)))
            .setResultType("datetime")
            .build()

    companion object {
        /** No technical columns declared — the write stamps nothing (the S5-B / non-journaled default). */
        val NONE = WriteTechnical()

        /**
         * Build the technical fills for a target table from its declared role→column map (the `authored_by`
         * and `written_at` roles), stamping [authoredBy] / [writtenAt]. A table with neither role → [NONE].
         */
        fun fromRoleColumns(
            roleColumns: Map<String, String>,
            authoredBy: String,
            writtenAt: String,
        ): WriteTechnical =
            WriteTechnical(
                authoredByColumn = roleColumns["authored_by"],
                writtenAtColumn = roleColumns["written_at"],
                authoredBy = authoredBy,
                writtenAt = writtenAt,
            )
    }
}
