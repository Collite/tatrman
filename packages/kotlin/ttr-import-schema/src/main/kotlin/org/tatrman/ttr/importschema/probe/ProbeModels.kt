// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.probe

/** How a probe result was obtained — drives the `verified (full)` vs `verified (sampled)` grade. */
enum class Provenance {
    /** Exact full-scan over the whole child table (table ≤ threshold). */
    FULL,

    /** Keyed sample (`hash(pk) mod M < K`); orphan counts are estimates, but any orphan is real. */
    SAMPLED,

    /** Not probed — logical-unit budget exhausted (Q-5). Grade: `named-only, unprobed (budget)`. */
    UNPROBED_BUDGET,

    /** Not probed — a conventions `probes.overrides` entry set the table's mode to `skip`. */
    UNPROBED_OVERRIDE,
}

/** The tier a candidate is planned at (before execution). */
enum class ProbeTier { FULL, SAMPLED, UNPROBED_BUDGET, SKIP }

/** A candidate paired with its planned tier + the child row count used to plan it. */
data class PlannedProbe(
    val candidate: ProbeCandidate,
    val tier: ProbeTier,
    val childRowCount: Long,
)

/**
 * The evidence a probe gathered. `orphanCount` is exact when [provenance] is FULL and an estimate
 * when SAMPLED; `hasOrphans` is always exact (a violation seen in a sample is a genuine
 * counterexample → the relation is `contradicted`, per Q-2). UNPROBED_BUDGET carries no numbers.
 */
data class ProbeResult(
    val candidate: ProbeCandidate,
    val provenance: Provenance,
    val childRowCount: Long = 0,
    val probedRowCount: Long = 0,
    /** Estimate under SAMPLED; exact under FULL. */
    val orphanCount: Long = 0,
    /** Always exact: were any orphan (unmatched non-null child) rows observed at all? */
    val hasOrphans: Boolean = false,
    /** Distinct non-null child values probed — feeds the S4 cardinality grade. */
    val distinctChildValues: Long = 0,
    /** True when non-null child values are unique among those probed (1:1 vs 1:N evidence). */
    val childValuesUnique: Boolean = false,
    val nullCount: Long = 0,
) {
    companion object {
        fun unprobed(
            candidate: ProbeCandidate,
            rowCount: Long,
        ) = ProbeResult(candidate, Provenance.UNPROBED_BUDGET, childRowCount = rowCount)
    }
}
