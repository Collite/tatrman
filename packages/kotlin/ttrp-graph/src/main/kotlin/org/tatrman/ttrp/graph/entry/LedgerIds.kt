// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.entry

/**
 * The F3 ledger derived-id scheme (contracts §5, ⚑EN-2 CONFIRMED): reversal/replacement ids are
 * `<originalEntryRowId>-rev<n>` / `-rep<n>` where `n` = 1 + the count of existing reversals linked to
 * that original **in committed state**. A pure function of (original id, existing count) — deterministic,
 * unique under repeated corrections, and replay-stable (replay reruns from the same reset state). The
 * FO-P2 fixture's `-rev`/`-rep` collided on a second correction of one original; this scheme fixes it.
 */
object LedgerIds {
    /** The reversal row id for the [existingReversals]-th prior correction of [originalId] (n = count+1). */
    fun reversalId(
        originalId: String,
        existingReversals: Int,
    ): String = "$originalId-rev${existingReversals + 1}"

    /** The replacement row id for the [existingReversals]-th prior correction of [originalId] (n = count+1). */
    fun replacementId(
        originalId: String,
        existingReversals: Int,
    ): String = "$originalId-rep${existingReversals + 1}"
}
