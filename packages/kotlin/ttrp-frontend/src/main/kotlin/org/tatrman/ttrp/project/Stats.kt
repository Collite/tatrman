// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.project

import kotlinx.serialization.Serializable
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * A statistics entry — the atom, keyed **per object** (contracts §4, BQ-2). A separate,
 * snapshot-pinnable source kind: floats under max-age, **never in the lock**, absent = defined
 * degradation to the static cost model (never an error). `values` is an open map (well-known keys
 * `rowCount`/`byteSize`/`ndv`/… documented, unknown tolerated).
 */
@Serializable
data class StatsEntry(
    val qname: String,
    /** Hash of the object's resolved shape (column set + types) at observation time. */
    val objectSchemaHash: String,
    val observedAt: String,
    val values: Map<String, Double> = emptyMap(),
)

/** Result of a per-object stats lookup: the served entry (or null) plus any degradation diagnostic. */
data class StatsLookup(
    val entry: StatsEntry?,
    val diagnostic: TtrpDiagnostic?,
) {
    val served: Boolean get() = entry != null
}

/**
 * The `StatisticsSource` (contracts §4, B contract). Cache-backed like [MetadataServerSource]; the
 * SZ-1 `stats-max-age` policy is evaluated **at fetch time, not load** (a stale-but-present entry is
 * still served here; fetch is what refreshes it — `// PL-P2:` fetch-side max-age enforcement rides
 * `ttr fetch`). The optimizer (Z) reads stats **only** through this source; on any miss it falls back
 * to the static cost model, so a stats-less compile is not an error.
 */
class StatisticsSource(
    entries: List<StatsEntry>,
) {
    private val byQname: Map<String, StatsEntry> = entries.associateBy { it.qname }
    private val used = LinkedHashMap<String, StatsEntry>()

    /**
     * Serve the stats for [qname] iff the entry's [StatsEntry.objectSchemaHash] matches the object's
     * current resolved shape. Mismatch ⇒ discard + `TTRP-STA-001` (degrade to static). Absent ⇒
     * silent stats-less (no entry, no diagnostic).
     */
    fun forObject(
        qname: String,
        currentSchemaHash: String,
    ): StatsLookup {
        val entry = byQname[qname] ?: return StatsLookup(null, null)
        if (entry.objectSchemaHash != currentSchemaHash) {
            return StatsLookup(
                entry = null,
                diagnostic =
                    TtrpDiagnostic(
                        id = TtrpDiagnosticId.STA_001,
                        severity = Severity.INFO,
                        message =
                            "stats entry discarded: object schema hash mismatch for $qname " +
                                "— object degrades to the static cost model",
                        location = SourceLocation("<stats>", 1, 0, 1, 1, -1, -1),
                        suggestedAlternative = TtrpDiagnosticId.STA_001.suggestedAlternative,
                    ),
            )
        }
        used[qname] = entry
        return StatsLookup(entry, null)
    }

    /** The entries the optimizer actually consumed — recorded VERBATIM on the compile record (§5). */
    fun statsUsed(): List<StatsEntry> = used.values.toList()
}
