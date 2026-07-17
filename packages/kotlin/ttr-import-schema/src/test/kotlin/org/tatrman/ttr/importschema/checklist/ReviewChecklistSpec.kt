// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.checklist

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.importschema.GoldenSupport
import org.tatrman.ttr.importschema.conventions.ConventionsLoader
import org.tatrman.ttr.importschema.conventions.ConventionsResolver
import org.tatrman.ttr.importschema.dbmodel.DbMirror
import org.tatrman.ttr.importschema.er.ErDeriver
import org.tatrman.ttr.importschema.er.RelationCascade
import org.tatrman.ttr.importschema.fixtures.heroCatalog
import org.tatrman.ttr.importschema.probe.ProbeCandidate
import org.tatrman.ttr.importschema.probe.ProbeOrigin
import org.tatrman.ttr.importschema.probe.ProbeResult
import org.tatrman.ttr.importschema.probe.Provenance

/** SV-P4·S4·T4 — the F5-β review checklist (import-review.md + import-review.json). */
class ReviewChecklistSpec :
    StringSpec({
        val conventions = ConventionsLoader.parse(ConventionsResolver.loadProfileResource("czech-erp"))
        val pkg = "erp"

        fun cleanProbes(prov: Provenance = Provenance.FULL): List<ProbeResult> =
            RelationCascade(conventions)
                .derive(heroCatalog())
                .candidates
                .filter { it.origin == ProbeOrigin.HEURISTIC }
                .map {
                    ProbeResult(
                        ProbeCandidate(it.child, it.parent, it.origin),
                        prov,
                        orphanCount = 0,
                        hasOrphans = false,
                    )
                }

        fun coverage(): ReviewChecklist.Coverage {
            val cascade = RelationCascade(conventions).derive(heroCatalog())
            return ReviewChecklist.Coverage(
                tablesInScope = 7,
                tablesIntrospected = 7,
                candidatesTotal = cascade.candidates.size,
                candidatesProbed = cascade.candidates.count { it.origin == ProbeOrigin.HEURISTIC },
                unprobedForBudget = emptyList(),
            )
        }

        "hero review markdown matches the golden" {
            val result = ErDeriver(pkg, conventions).derive(heroCatalog(), cleanProbes())
            val renames = DbMirror(pkg).render(heroCatalog()).renames
            val md = ReviewChecklist.toMarkdown(result, renames, coverage())
            GoldenSupport.assertMatchesGolden(md, "checklist/import-review.md")
        }

        "hero review json matches the golden" {
            val result = ErDeriver(pkg, conventions).derive(heroCatalog(), cleanProbes())
            val renames = DbMirror(pkg).render(heroCatalog()).renames
            val jsonText = ReviewChecklist.toJson(result, renames, coverage())
            GoldenSupport.assertMatchesGolden(jsonText, "checklist/import-review.json")
        }

        "the checklist carries the mangled-identifier rename (Sleva %)" {
            val result = ErDeriver(pkg, conventions).derive(heroCatalog(), cleanProbes())
            val renames = DbMirror(pkg).render(heroCatalog()).renames
            val md = ReviewChecklist.toMarkdown(result, renames, coverage())
            md shouldContain "Sleva_"
            md shouldContain "Sleva %"
        }

        "sampled probe evidence is labelled an estimate" {
            val result = ErDeriver(pkg, conventions).derive(heroCatalog(), cleanProbes(Provenance.SAMPLED))
            val jsonText = ReviewChecklist.toJson(result, DbMirror(pkg).render(heroCatalog()).renames, coverage())
            jsonText shouldContain "\"orphanEstimate\": true"
        }
    })
