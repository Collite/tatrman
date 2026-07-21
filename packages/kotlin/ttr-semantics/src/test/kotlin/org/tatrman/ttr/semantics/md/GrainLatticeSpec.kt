// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures

/**
 * S1-A3 — grain lattice over the shared fixture (TS md stage 2E parity). Leaf = no incoming N:1
 * (a 1:1 map does not demote); partial order = N:1 closure; co-leaves via 1:1.
 */
class GrainLatticeSpec :
    StringSpec({
        val lattice = GrainLattice.of(MdFixtures.salesModel())

        "leaves are domains with no incoming N:1 map (1:1 does not demote)" {
            // Name has only an incoming 1:1 (code_to_name) ⇒ still a leaf; Code is a co-leaf.
            lattice.leaves shouldContainExactlyInAnyOrder
                setOf("Code", "Name", "Date", "ProductCode", "Money")
            // Region/Month/Quarter are N:1 targets ⇒ not leaves.
            (setOf("Region", "Month", "Quarter") intersect lattice.leaves) shouldBe emptySet()
        }

        "grainReachable follows the N:1 transitive closure" {
            lattice.grainReachable("Date", "Quarter") shouldBe true // Date→Month→Quarter
            lattice.grainReachable("Name", "Region") shouldBe true
            lattice.grainReachable("Date", "Region") shouldBe false // different chains
            lattice.grainReachable("Name", "Name") shouldBe true // reflexive
        }

        "reachableFrom returns the coarser domains" {
            lattice.reachableFrom("Date") shouldContainExactlyInAnyOrder setOf("Month", "Quarter", "Year")
            lattice.reachableFrom("Name") shouldContainExactlyInAnyOrder setOf("Region")
            lattice.reachableFrom("Quarter") shouldBe emptySet()
        }

        "1:1 maps connect co-leaves" {
            val codeClass = lattice.coLeafClasses().single { "Code" in it }
            codeClass shouldContain "Name" // code_to_name is 1:1
        }

        "inferStep uniquely resolves a hierarchy step, or reports none/ambiguous" {
            lattice.inferStep("Date", "Month").shouldBeInstanceOf<StepResult.Ok>().mapName shouldBe "date_to_month"
            lattice.inferStep("Date", "Region").shouldBeInstanceOf<StepResult.None>()
            // name_to_region AND region_from_attr both coarsen Name→Region ⇒ ambiguous without via.
            lattice.inferStep("Name", "Region").shouldBeInstanceOf<StepResult.Ambiguous>()
        }

        "a dangling map ref drops its edge — Kotlin is canonical (review-071 T-P2)" {
            val defs =
                org.tatrman.ttr.parser.loader.TtrLoader
                    .parseString(
                        "model md\ndef domain A { type: int }\ndef domain B { type: int }\n" +
                            "def map good { from: md.A, to: md.B }\ndef map dangling { from: md.A, to: md.Nope }",
                        "dangling.ttrm",
                    ).definitions
            val l = GrainLattice.of(MdModel.from(defs))
            l.edges.map { "${it.from}->${it.to}" } shouldBe listOf("A->B") // the dangling A->Nope edge is dropped
            l.nodes shouldNotContain "Nope"
        }
    })
