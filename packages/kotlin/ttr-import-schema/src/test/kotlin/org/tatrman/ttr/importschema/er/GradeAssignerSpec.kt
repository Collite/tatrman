// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.er

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.importschema.probe.ColumnRef
import org.tatrman.ttr.importschema.probe.ProbeCandidate
import org.tatrman.ttr.importschema.probe.ProbeOrigin
import org.tatrman.ttr.importschema.probe.ProbeResult
import org.tatrman.ttr.importschema.probe.Provenance

/** SV-P4·S4·T1 — the evidence-grade contract, as a table over (origin, probe). */
class GradeAssignerSpec :
    StringSpec({
        val c =
            ProbeCandidate(
                ColumnRef("dbo", "child", listOf("fk")),
                ColumnRef("dbo", "parent", listOf("id")),
                ProbeOrigin.HEURISTIC,
            )

        fun probe(
            p: Provenance,
            orphans: Boolean,
        ) = ProbeResult(c, p, orphanCount = if (orphans) 1 else 0, hasOrphans = orphans)

        "declared FK with no contradicting data ⇒ DECLARED" {
            GradeAssigner.grade(ProbeOrigin.DECLARED, null) shouldBe EvidenceGrade.DECLARED
            GradeAssigner.grade(ProbeOrigin.DECLARED, probe(Provenance.FULL, orphans = false)) shouldBe
                EvidenceGrade.DECLARED
        }

        "declared FK whose data has orphans (WITH NOCHECK) ⇒ CONTRADICTED" {
            GradeAssigner.grade(ProbeOrigin.DECLARED, probe(Provenance.FULL, orphans = true)) shouldBe
                EvidenceGrade.CONTRADICTED
        }

        "heuristic, unprobed ⇒ NAMED_ONLY" {
            GradeAssigner.grade(ProbeOrigin.HEURISTIC, null) shouldBe EvidenceGrade.NAMED_ONLY
        }

        "heuristic, probed clean ⇒ VERIFIED by provenance" {
            GradeAssigner.grade(ProbeOrigin.HEURISTIC, probe(Provenance.FULL, orphans = false)) shouldBe
                EvidenceGrade.VERIFIED_FULL
            GradeAssigner.grade(ProbeOrigin.HEURISTIC, probe(Provenance.SAMPLED, orphans = false)) shouldBe
                EvidenceGrade.VERIFIED_SAMPLED
        }

        "heuristic, probed with orphans ⇒ CONTRADICTED (even from a sample)" {
            GradeAssigner.grade(ProbeOrigin.HEURISTIC, probe(Provenance.FULL, orphans = true)) shouldBe
                EvidenceGrade.CONTRADICTED
            GradeAssigner.grade(ProbeOrigin.HEURISTIC, probe(Provenance.SAMPLED, orphans = true)) shouldBe
                EvidenceGrade.CONTRADICTED
        }

        "heuristic, unprobed for budget ⇒ NAMED_ONLY_UNPROBED_BUDGET" {
            val budget = ProbeResult(c, Provenance.UNPROBED_BUDGET)
            GradeAssigner.grade(ProbeOrigin.HEURISTIC, budget) shouldBe EvidenceGrade.NAMED_ONLY_UNPROBED_BUDGET
        }

        "only CONTRADICTED is withheld from the model" {
            EvidenceGrade.entries.forEach { g ->
                g.emittedAsRelation shouldBe (g != EvidenceGrade.CONTRADICTED)
            }
        }
    })
