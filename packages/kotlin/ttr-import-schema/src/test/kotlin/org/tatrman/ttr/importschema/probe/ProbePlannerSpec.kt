// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.probe

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.importschema.conventions.BudgetConfig
import org.tatrman.ttr.importschema.conventions.ProbeConfig
import org.tatrman.ttr.importschema.conventions.ProbeOverride

/** SV-P4·S3·T5 — the PURE probe plan: pinned order (Q-5), tiering (Q-2), budget → UNPROBED. */
class ProbePlannerSpec :
    StringSpec({
        fun cand(
            child: String,
            parent: String,
            origin: ProbeOrigin,
        ) = ProbeCandidate(ColumnRef("dbo", child, listOf("fk")), ColumnRef("dbo", parent, listOf("id")), origin)

        "declared FKs are verified before heuristics; heuristics ordered by qname pair" {
            val candidates =
                listOf(
                    cand("t2", "p2", ProbeOrigin.HEURISTIC),
                    cand("t0", "p0", ProbeOrigin.HEURISTIC),
                    cand("t1", "p1", ProbeOrigin.DECLARED),
                )
            val counts = mapOf("dbo.t0" to 1L, "dbo.t1" to 1L, "dbo.t2" to 1L)
            val plan = ProbePlanner.plan(candidates, counts, ProbeConfig())
            plan.map { it.candidate.child.table } shouldContainExactly listOf("t1", "t0", "t2")
        }

        "row count decides the tier at the full-scan threshold" {
            val config = ProbeConfig(fullScanThresholdRows = 100)
            val small = ProbePlanner.plan(listOf(cand("t", "p", ProbeOrigin.DECLARED)), mapOf("dbo.t" to 100L), config)
            val big = ProbePlanner.plan(listOf(cand("t", "p", ProbeOrigin.DECLARED)), mapOf("dbo.t" to 101L), config)
            small.single().tier shouldBe ProbeTier.FULL
            big.single().tier shouldBe ProbeTier.SAMPLED
        }

        "budget exhaustion marks the remaining candidates UNPROBED_BUDGET (deterministic partial)" {
            val config = ProbeConfig(budget = BudgetConfig(maxCandidates = 1, maxProbeRows = Long.MAX_VALUE))
            val candidates =
                listOf(
                    cand("a", "p", ProbeOrigin.DECLARED),
                    cand("b", "p", ProbeOrigin.DECLARED),
                )
            val counts = mapOf("dbo.a" to 10L, "dbo.b" to 10L)
            val plan = ProbePlanner.plan(candidates, counts, config)
            plan.map { it.tier } shouldContainExactly listOf(ProbeTier.FULL, ProbeTier.UNPROBED_BUDGET)
        }

        "a probes.overrides skip forces the table's candidates to SKIP" {
            val config = ProbeConfig(overrides = listOf(ProbeOverride("dbo.t", "skip")))
            val plan = ProbePlanner.plan(listOf(cand("t", "p", ProbeOrigin.DECLARED)), mapOf("dbo.t" to 10L), config)
            plan.single().tier shouldBe ProbeTier.SKIP
        }
    })
