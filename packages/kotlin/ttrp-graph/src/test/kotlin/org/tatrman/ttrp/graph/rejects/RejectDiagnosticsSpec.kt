// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rejects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.rewrite.RewriteSupport

/**
 * RJ-P1 / 1.1.6 — the authoring diagnostics (contracts §9). `TTRP-RJ-103` (reserved `_ttrp_`
 * prefix) is a frontend validation error (stage 1.2.6); `TTRP-RJ-101` (dead wire) is a
 * rewrite-time warning (stage 1.3.5). `TTRP-RJ-104` (volatile fn in a reject-capable position)
 * needs a test-only impure catalogue entry and is exercised in the 1.2 catalogue spec.
 */
class RejectDiagnosticsSpec :
    StringSpec({
        "1.1.6 — TTRP-RJ-103: a user column using the reserved _ttrp_ prefix is an error" {
            val r = GraphFixtures.build(GraphFixtures.program("rejects-neg-reserved.ttrp"))
            r.allErrorIds shouldContain "TTRP-RJ-103"
        }

        "1.1.6 — TTRP-RJ-101: rejects wired on a node that can never reject warns (dead wire)" {
            val g = GraphFixtures.graphOf(GraphFixtures.program("rejects-neg-deadwire.ttrp"))
            val diags = RewriteSupport.engine().normalize(g).diagnostics
            diags.filter { it.severity == Severity.WARNING }.map { it.id.id } shouldContain "TTRP-RJ-101"
        }
    })
