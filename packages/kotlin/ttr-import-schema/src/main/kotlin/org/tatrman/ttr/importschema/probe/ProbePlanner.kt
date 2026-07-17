// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.probe

import org.tatrman.ttr.importschema.conventions.ProbeConfig

/**
 * The PURE, deterministic probe plan (Q-2 tiering + Q-5 order & budget) — no DB access, so the
 * determinism that matters most is unit-testable in isolation.
 *
 * Order (Q-5, pinned): declared-FK verifications first, then heuristic candidates by sorted
 * (child, parent) qname pair — so "the first K candidates" is well-defined.
 *
 * Tier (Q-2): a table's child row count (exact `COUNT(*)`, supplied by the caller) at or below
 * `probes.full-scan-threshold-rows` ⇒ FULL (exact); above ⇒ SAMPLED (keyed). A `probes.overrides`
 * entry forces full/sample/skip for its table.
 *
 * Budget (Q-5, logical units only): walking the pinned order, each probed candidate spends one
 * `max-candidates` unit and its probed-row estimate against `max-probe-rows`. When either is
 * exhausted, every remaining candidate is SKIPPED as UNPROBED_BUDGET — a successful deterministic
 * partial, never a guess. Wall-clock is deliberately absent (it would break GI-2 across machines).
 */
object ProbePlanner {
    fun plan(
        candidates: List<ProbeCandidate>,
        childRowCounts: Map<String, Long>,
        config: ProbeConfig,
    ): List<PlannedProbe> {
        val overrides = config.overrides.associate { it.table.lowercase() to it.mode }
        val ordered = candidates.sortedBy { it.orderKey }

        var candidatesSpent = 0L
        var rowsSpent = 0L
        val out = ArrayList<PlannedProbe>(ordered.size)

        for (c in ordered) {
            val childTableKey = "${c.child.schema}.${c.child.table}"
            val rowCount = childRowCounts[childTableKey] ?: 0L
            val forced = overrides[childTableKey.lowercase()]

            val naturalTier =
                when (forced) {
                    "skip" -> ProbeTier.SKIP
                    "full" -> ProbeTier.FULL
                    "sample" -> ProbeTier.SAMPLED
                    else -> if (rowCount <= config.fullScanThresholdRows) ProbeTier.FULL else ProbeTier.SAMPLED
                }

            if (naturalTier == ProbeTier.SKIP) {
                out += PlannedProbe(c, ProbeTier.SKIP, rowCount)
                continue
            }

            // Probed-row cost estimate in logical units: full scans cost the row count; sampled
            // scans cost the sample size keep/modulus of the row count (deterministic, count-based).
            val cost =
                if (naturalTier == ProbeTier.SAMPLED) {
                    (rowCount * config.sample.keep) / config.sample.modulus.coerceAtLeast(1)
                } else {
                    rowCount
                }

            val overBudget =
                candidatesSpent >= config.budget.maxCandidates ||
                    rowsSpent + cost > config.budget.maxProbeRows

            if (overBudget) {
                out += PlannedProbe(c, ProbeTier.UNPROBED_BUDGET, rowCount)
            } else {
                candidatesSpent += 1
                rowsSpent += cost
                out += PlannedProbe(c, naturalTier, rowCount)
            }
        }
        return out
    }
}
