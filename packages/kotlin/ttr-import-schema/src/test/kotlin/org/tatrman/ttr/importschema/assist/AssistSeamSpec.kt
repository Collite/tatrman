// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.assist

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import org.tatrman.ttr.importschema.conventions.ConventionsLoader
import org.tatrman.ttr.importschema.conventions.ConventionsResolver
import org.tatrman.ttr.importschema.er.ErDeriver
import org.tatrman.ttr.importschema.er.EvidenceGrade
import org.tatrman.ttr.importschema.er.RelationCandidate
import org.tatrman.ttr.importschema.fixtures.heroCatalog
import org.tatrman.ttr.importschema.probe.ColumnRef
import org.tatrman.ttr.importschema.probe.ProbeCandidate
import org.tatrman.ttr.importschema.probe.ProbeOrigin
import org.tatrman.ttr.importschema.probe.ProbeResult
import org.tatrman.ttr.importschema.probe.Provenance

/** SV-P4·S4·T6 — the F1-δ assist seam: v1 proposes nothing; proposals pass the probe gate; OFF is byte-identical. */
class AssistSeamSpec :
    StringSpec({
        val conventions = ConventionsLoader.parse(ConventionsResolver.loadProfileResource("czech-erp"))
        val pkg = "erp"

        // An assist proposal over the otherwise-unmatched Artikl.IDKategorie column.
        val proposal =
            RelationCandidate(
                child = ColumnRef("dbo", "Artikl", listOf("IDKategorie")),
                parent = ColumnRef("dbo", "Ciselnik_Stat", listOf("IDStat")),
                origin = ProbeOrigin.HEURISTIC,
                rule = "assist",
            )

        fun probeFor(orphans: Boolean) =
            ProbeResult(
                ProbeCandidate(proposal.child, proposal.parent, ProbeOrigin.HEURISTIC),
                Provenance.FULL,
                orphanCount = if (orphans) 3 else 0,
                hasOrphans = orphans,
            )

        "the v1 shipped proposer proposes nothing" {
            NoopRelationAssistProposer().propose(heroCatalog(), conventions).shouldBeEmpty()
        }

        "assist OFF (no proposals) is byte-identical to the no-assist er first cut" {
            val deriver = ErDeriver(pkg, conventions)
            val off = deriver.render(deriver.derive(heroCatalog(), emptyList(), emptyList()), heroCatalog())
            val noAssist = deriver.render(deriver.derive(heroCatalog(), emptyList()), heroCatalog())
            (off.content == noAssist.content).shouldBeTrue()
        }

        "an assist proposal that probes clean enters the model as an assist-graded relation" {
            val result =
                ErDeriver(
                    pkg,
                    conventions,
                ).derive(heroCatalog(), listOf(probeFor(orphans = false)), listOf(proposal))
            result.relations
                .any { it.evidence.rule == "assist" && it.evidence.grade == EvidenceGrade.VERIFIED_FULL }
                .shouldBeTrue()
        }

        "an assist proposal the data contradicts is quarantined — advisory-only, never in the model" {
            val result =
                ErDeriver(
                    pkg,
                    conventions,
                ).derive(heroCatalog(), listOf(probeFor(orphans = true)), listOf(proposal))
            result.relations.any { it.evidence.rule == "assist" }.shouldBeFalse()
            result.notes.any { it.kind.name == "CONTRADICTED" }.shouldBeTrue()
        }
    })
