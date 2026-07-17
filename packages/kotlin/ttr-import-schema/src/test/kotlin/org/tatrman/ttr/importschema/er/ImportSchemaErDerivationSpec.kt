// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.er

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.importschema.GoldenSupport
import org.tatrman.ttr.importschema.conventions.ConventionsFile
import org.tatrman.ttr.importschema.conventions.ConventionsLoader
import org.tatrman.ttr.importschema.conventions.ConventionsResolver
import org.tatrman.ttr.importschema.fixtures.heroCatalog
import org.tatrman.ttr.importschema.probe.ProbeCandidate
import org.tatrman.ttr.importschema.probe.ProbeOrigin
import org.tatrman.ttr.importschema.probe.ProbeResult
import org.tatrman.ttr.importschema.probe.Provenance
import org.tatrman.ttr.parser.loader.TtrLoader

/** SV-P4·S4·T1/T2/T3 — the hero er first cut: golden, evidence grades, shaping. */
class ImportSchemaErDerivationSpec :
    StringSpec({
        val conventions: ConventionsFile = ConventionsLoader.parse(ConventionsResolver.loadProfileResource("czech-erp"))
        val pkg = "erp"

        // Every heuristic candidate probes clean (FULL, no orphans) — the "worth keeping" first cut.
        fun cleanProbes(): List<ProbeResult> =
            RelationCascade(conventions)
                .derive(heroCatalog())
                .candidates
                .filter { it.origin == ProbeOrigin.HEURISTIC }
                .map {
                    ProbeResult(
                        candidate = ProbeCandidate(it.child, it.parent, it.origin),
                        provenance = Provenance.FULL,
                        childRowCount = 100,
                        probedRowCount = 100,
                        orphanCount = 0,
                        hasOrphans = false,
                        distinctChildValues = 40,
                        childValuesUnique = false,
                        nullCount = 0,
                    )
                }

        fun derive() = ErDeriver(pkg, conventions).derive(heroCatalog(), cleanProbes())

        "hero er first cut matches the golden bytes" {
            val result = derive()
            val er = ErDeriver(pkg, conventions).render(result, heroCatalog())
            GoldenSupport.assertMatchesGolden(er.content, "er/er.ttrm")
        }

        "the emitted er model parses back with zero diagnostics" {
            val result = derive()
            val er = ErDeriver(pkg, conventions).render(result, heroCatalog())
            val parsed = TtrLoader.parseString(er.content, "er.ttrm")
            parsed.ok.shouldBeTrue()
        }

        "the pure M:N junction collapses — Artikl_Odberatel is not an entity" {
            val result = derive()
            result.entities.map { it.name } shouldNotContain "Artikl_Odberatel"
            result.entities.size shouldBe 6
            result.notes
                .any {
                    it.kind == ChecklistNote.Kind.JUNCTION_COLLAPSED && it.subject == "dbo.Artikl_Odberatel"
                }.shouldBeTrue()
            result.relations.any { it.cardinality == RelationCardinality.MANY_TO_MANY }.shouldBeTrue()
        }

        "declared FKs grade DECLARED; probe-confirmed heuristics grade VERIFIED_FULL" {
            val result = derive()
            val declared = result.relations.single { it.name == "Faktura_Odberatel" }
            declared.evidence.grade shouldBe EvidenceGrade.DECLARED
            val heuristic = result.relations.single { it.name == "Faktura_Ciselnik_StavFaktury" }
            heuristic.evidence.grade shouldBe EvidenceGrade.VERIFIED_FULL
        }

        "codebooks and header/detail are proposed; a dangling id-column is flagged unmatched" {
            val notes = derive().notes
            notes
                .any { it.kind == ChecklistNote.Kind.CODEBOOK_PROPOSED && it.subject == "dbo.Ciselnik_Stat" }
                .shouldBeTrue()
            notes.any { it.kind == ChecklistNote.Kind.HEADER_DETAIL_PROPOSED }.shouldBeTrue()
            notes
                .any {
                    it.kind == ChecklistNote.Kind.UNMATCHED_COLUMN &&
                        it.subject.contains(
                            "IDKategorie",
                        )
                }.shouldBeTrue()
        }
    })
