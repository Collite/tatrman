// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader
import java.io.File

/**
 * review-071 T-P2 / S1-B5 — the Kotlin half of the TS↔Kotlin Layer-A parity harness. Parses the SAME
 * `md-parity.ttrm` as the TS `md-parity.test.ts`, builds the grain lattice + defaults, and renders one
 * canonical line-based summary that must equal `md-parity.golden.txt`. Kotlin is canonical, so if the
 * two ever drift, the side that changed fails the golden. Line format (not JSON) → byte-equality is
 * trivial across the two serializers.
 *
 * The leaf / co-leaf comparison is over the EDGE-ENDPOINT node set: Kotlin also seeds nodes with every
 * declared domain (so a measure's isolated `Money` domain would surface as a leaf), which is the
 * documented-benign node-set divergence — excluded here so parity tests the behavioural lattice.
 */
class MdParitySpec :
    StringSpec({
        val reachPairs =
            listOf("Code" to "Name", "Day" to "Month", "Day" to "Region", "Day" to "Year", "Name" to "Region")

        fun render(): String {
            val defs = TtrLoader.parseString(repoFixture("md-parity.ttrm"), "md-parity.ttrm").definitions
            val model = MdModel.from(defs)
            val lattice = GrainLattice.of(model)
            val endpoints = lattice.edges.flatMap { listOf(it.from, it.to) }.toSet()

            val leaves = lattice.leaves.filter { it in endpoints }.sorted()
            val edgeLines =
                lattice.edges.map { "${it.from} -> ${it.to} (${if (it.oneToOne) "1:1" else "N:1"})" }.sorted()
            val classes =
                lattice
                    .coLeafClasses()
                    .map { c -> c.filter { it in endpoints }.sorted() }
                    .filter { it.isNotEmpty() }
                    .map { it.joinToString(",") }
                    .sorted()
            val reachLines = reachPairs.map { (a, b) -> "$a -> $b : ${lattice.grainReachable(a, b)}" }.sorted()
            val cdm =
                model.cubelets.values
                    .map { "${it.name} = ${it.defaultMeasure}" }
                    .sorted()
            val mda =
                model.measures.values
                    .map { "${it.name} = ${it.defaultAgg.name.lowercase()}" }
                    .sorted()

            return (
                listOf("== leaves ==") + leaves +
                    listOf("== edges ==") + edgeLines +
                    listOf("== coLeafClasses ==") + classes +
                    listOf("== reachable ==") + reachLines +
                    listOf("== defaults.cubeletDefaultMeasure ==") + cdm +
                    listOf("== defaults.measureDefaultAgg ==") + mda
            ).joinToString("\n") + "\n"
        }

        "the Kotlin lattice + defaults summary matches the canonical golden (byte-identical to TS)" {
            render() shouldBe repoFixture("md-parity.golden.txt")
        }
    })

/** Read a shared parity fixture from the TS package's test dir by walking up from the working dir. */
private fun repoFixture(name: String): String {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        val f = File(dir, "packages/semantics/src/__tests__/fixtures/$name")
        if (f.isFile) return f.readText()
        dir = dir.parentFile
    }
    error("parity fixture '$name' not found from ${System.getProperty("user.dir")}")
}
