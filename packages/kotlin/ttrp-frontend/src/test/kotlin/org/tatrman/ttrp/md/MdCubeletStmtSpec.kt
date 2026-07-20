// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.semantics.md.AllocationStrategy
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

        "a spread over a declared (equal) strategy is legal — no MD-010/MD-011 (v0.10)" {
            // `plan` binding declares `allocation: { Time: equal }`; Month's restrict (1..12) enumerates the
            // members, so the spread over Time is legal and the members are known.
            mdIds("plan.Kaufland.month.*.net = 42") shouldNotContain TtrpDiagnosticId.MD_010
            mdIds("plan.Kaufland.month.*.net = 42") shouldNotContain TtrpDiagnosticId.MD_011
        }

        "a spread over an UN-declared dimension is still MD-010" {
            // `plan` declares a strategy only for Time; a free Customer has none.
            mdIds("plan.name.*.month.6.net = 42") shouldContain TtrpDiagnosticId.MD_010
        }

        "an equal spread with no enumerable member set is MD-011 (deferred/unknown members)" {
            // A cubelet declaring `equal` over Time, but Time.day's Date domain has no restrict members and
            // the context is disconnected (no catalog) ⇒ the finer members can't be produced (R22/D13).
            val sales = MdCheckerFixtures.bindings.cubelets.getValue("sales")
            val equalSales =
                MdCheckerFixtures.bindings.copy(
                    cubelets =
                        MdCheckerFixtures.bindings.cubelets +
                            (
                                "sales" to
                                    sales.copy(
                                        allocation = mapOf("Time" to AllocationStrategy.Equal),
                                        uniformAllocation = null,
                                    )
                            ),
                )
            val disconnected = MdCheckerFixtures.mdContext(members = null).copy(bindings = equalSales)
            // Disconnected mode ⇒ qualify the member (`name.Kaufland`, R13/MD-007); `day.*` frees Time.
            val ids =
                TtrpFrontend
                    .check("sales.name.Kaufland.day.*.net = 42", md = disconnected)
                    .diagnostics
                    .map { it.id }
            ids shouldContain TtrpDiagnosticId.MD_011
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
