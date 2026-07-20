// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.TtrpFrontend
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.MdContext

/**
 * S5-A — cubelet-assignment statements (`mdPath = expr` / `+=`) at the frontend (contracts §5). Over
 * the sales fixture; `plan`'s grain is Customer.name × Time.month, measures net/gross. Covers R19
 * strict LHS (MD-009), R20 context overlay on the RHS, and R21 reconciliation spread → MD-010.
 */
class MdCubeletStmtSpec :
    StringSpec({
        val connected: MdContext = MdCheckerFixtures.mdContext()

        fun check(src: String) = TtrpFrontend.check(src, md = connected)

        fun mdIds(src: String) = check(src).diagnostics.map { it.id }.filter { it.name.startsWith("MD_") }

        "a complete strict LHS (both grain dims pinned + measure) is accepted" {
            mdIds("plan.Kaufland.month.3.net = 42") shouldBe emptyList()
        }

        "a strict LHS is order-free — a permutation resolves identically" {
            mdIds("net.month.3.plan.Kaufland = 42") shouldBe emptyList()
        }

        "`+=` (merge) on a slice LHS checks like `=`" {
            mdIds("plan.Kaufland.month.3.net += 42") shouldBe emptyList()
        }

        "a missing grain dimension is MD-009 (no default-fill on the LHS)" {
            mdIds("plan.Kaufland.net = 42") shouldContain TtrpDiagnosticId.MD_009 // month omitted
        }

        "a missing measure is MD-009" {
            mdIds("plan.Kaufland.month.3 = 42") shouldContain TtrpDiagnosticId.MD_009
        }

        "a free grain dimension on the LHS with a scalar RHS is a spread → MD-010" {
            mdIds("plan.Kaufland.month.*.net = 42") shouldContain TtrpDiagnosticId.MD_010
        }

        "an aligned free dim on both sides is not a spread (no MD-010)" {
            // Both sides free on month ⇒ vectorized assignment, shapes align (R16/R21).
            mdIds("plan.Kaufland.month.*.net = plan.Kaufland.month.*.net * 1.1") shouldNotContain
                TtrpDiagnosticId.MD_010
        }

        "R20: the RHS inherits the LHS customer coordinate via context" {
            // RHS `plan.month.6.net` omits the customer; the resolved LHS context supplies Kaufland,
            // so the recorded RHS canonical pins customer.name to Kaufland rather than leaving it free.
            val res = check("plan.Kaufland.month.3.net = plan.month.6.net")
            res.diagnostics.map { it.id }.filter { it.name.startsWith("MD_") } shouldBe emptyList()
            val rhs = res.mdResolutions.firstOrNull { it.canonical.contains("6") } // the RHS (month 6)
            rhs!!.canonical shouldContain "Kaufland" // inherited from the LHS (R20)
        }
    })
