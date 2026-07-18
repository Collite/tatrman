// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rejects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.explain.NormalizedGraphJson
import org.tatrman.ttrp.graph.rewrite.RewriteSupport

/**
 * RJ-P1 / 1.1.7 — the elaboration is deterministic and surface-independent. Because the
 * reject stratum runs on the already-built graph (which the decomposers make byte-identical
 * across canonical / embedded-ttrb / bare surfaces via `TtrbGraphIdentitySpec`), the only new
 * identity risk elaboration introduces is non-determinism — incidental map/id ordering. This
 * pins that away: the SAME source, built and fully normalized twice, yields byte-identical
 * normalized graphs INCLUDING the synthesized reject cluster. Red until stage 1.3 makes the
 * elaboration real (until then both runs are trivially equal but the cluster is absent, so the
 * non-vacuity check below fails for the right reason).
 */
class RejectIdentitySpec :
    StringSpec({
        fun normalizedTwice(fixture: String): Pair<String, String> {
            fun once(): String {
                val g = GraphFixtures.graphOf(GraphFixtures.program(fixture))
                return NormalizedGraphJson.write(RewriteSupport.engine().normalize(g).graph)
            }
            return once() to once()
        }

        "1.1.7 — the fully-normalized rejects-wired graph is deterministic and non-vacuous" {
            val (a, b) = normalizedTwice("rejects-hero.ttrp")
            a shouldBe b
            // the elaboration actually happened: the row-level reject code survives into emit-ready form.
            a shouldContain "TTRP-RJ-001"
        }
    })
