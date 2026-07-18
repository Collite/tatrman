// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rejects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.explain.NormalizedGraphJson
import org.tatrman.ttrp.graph.rewrite.RewriteSupport

/**
 * RJ-P1 / 1.1.2 — the fail-fast byte-identity pin (R-P3): a program with NO wired `rejects`
 * port must normalize byte-identically to pre-feature master. The golden was captured from
 * master before any elaboration code existed; stage 1.3 must not perturb the un-elaborated
 * path (no elaboration, no provenance leak into the serialized form). Green on master too — it
 * pins current behavior forward.
 */
class RejectFailFastSpec :
    StringSpec({
        "1.1.2 — the unwired twin normalizes byte-identically to the committed golden" {
            val g = GraphFixtures.graphOf(GraphFixtures.program("rejects-hero-fastfail.ttrp"))
            val normalized = NormalizedGraphJson.write(RewriteSupport.engine().normalize(g).graph)
            val golden =
                RejectFailFastSpec::class.java
                    .getResourceAsStream("/fixtures/graph/rejects-hero-fastfail.golden")!!
                    .bufferedReader()
                    .readText()
            normalized shouldBe golden
        }
    })
