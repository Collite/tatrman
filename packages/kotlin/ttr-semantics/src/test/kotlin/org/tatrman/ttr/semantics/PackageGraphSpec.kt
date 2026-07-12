// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * Mirrors `packages/semantics/src/__tests__/package-graph.test.ts`.
 */
class PackageGraphSpec :
    StringSpec({
        val a = "pkgA/a.ttr" to "package pkgA\nmodel er schema entity\ndef entity a { attributes: [] }"
        val b =
            "pkgB/b.ttr" to "package pkgB\nimport pkgA.*\nmodel er schema entity\ndef entity b { attributes: [] }"
        val c =
            "pkgC/c.ttr" to "package pkgC\nimport pkgB.*\nmodel er schema entity\ndef entity c { attributes: [] }"

        "A→B→C: build() returns 3 nodes and 2 edges" {
            val g = Fixtures.packageGraph(a, b, c).build()
            g.nodes shouldHaveSize 3
            g.edges shouldHaveSize 2
        }

        "getDependencies(C) is transitive (B and A)" {
            val deps = Fixtures.packageGraph(a, b, c).getDependencies("pkgC")
            deps shouldContain "pkgB"
            deps shouldContain "pkgA"
        }

        "getDependents(A) includes B; dependents of B include C" {
            val builder = Fixtures.packageGraph(a, b, c)
            builder.getDependents("pkgA") shouldContain "pkgB"
            builder.getDependents("pkgB") shouldContain "pkgC"
        }

        "cycle A→B→A is one cycle of size 2" {
            val cycA =
                "pkgA/a.ttr" to
                    "package pkgA\nimport pkgB.*\nmodel er schema entity\ndef entity a { attributes: [] }"
            val cycB =
                "pkgB/b.ttr" to
                    "package pkgB\nimport pkgA.*\nmodel er schema entity\ndef entity b { attributes: [] }"
            val cycles = Fixtures.packageGraph(cycA, cycB).findCycles()
            cycles shouldHaveSize 1
            cycles[0].toSet() shouldBe setOf("pkgA", "pkgB")
        }

        "self-import is not a cycle" {
            val self =
                "pkgA/a.ttr" to
                    "package pkgA\nimport pkgA.*\nmodel er schema entity\ndef entity a { attributes: [] }"
            Fixtures.packageGraph(self).findCycles() shouldHaveSize 0
        }
    })
