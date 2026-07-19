// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rejects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.model.Calc
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

        "B1 (RJ-P5 review) — TTRP-RJ-107: rejects wired on an unsupported cast type is a fail-closed error" {
            val g = GraphFixtures.graphOf(GraphFixtures.program("rejects-neg-unsupported.ttrp"))
            val r = RewriteSupport.engine().normalize(g)
            // fail-closed: a hard ERROR, and NO guard/reject elaboration happened (no _ttrp_v1 synthesized).
            r.diagnostics.filter { it.severity == Severity.ERROR }.map { it.id.id } shouldContain "TTRP-RJ-107"
            r.graph.nodes.values
                .filterIsInstance<Calc>()
                .none { c -> c.assignments.any { it.name.startsWith("_ttrp_") } } shouldBe true
        }
    })
