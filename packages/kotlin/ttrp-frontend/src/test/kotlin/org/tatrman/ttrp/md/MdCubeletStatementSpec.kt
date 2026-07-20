// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.semantics.md.Journaling
import org.tatrman.ttrp.TtrpFrontend
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.MdContext

/**
 * S5C-A — the cubelet-statement family beyond the S5 slice assignment (contracts §11): R24 dispatch,
 * R25 virtual cubelets (session namespace, SSA), R26/R27 materialize checking, R28/R29 merge & delete
 * checking. Over the shared sales-model fixture (`plan` grain Customer.name × Time.month, invalidate;
 * `sales` grain Customer.name × Time.day, overwrite).
 */
class MdCubeletStatementSpec :
    StringSpec({
        val connected: MdContext = MdCheckerFixtures.mdContext()

        fun ids(
            src: String,
            md: MdContext = connected,
        ) = TtrpFrontend.check(src, md = md).diagnostics

        fun mdIds(
            src: String,
            md: MdContext = connected,
        ) = ids(src, md).map { it.id }.filter { it.name.startsWith("MD_") }

        // ---- R24 dispatch: operator × LHS legality --------------------------------------------------

        "`:=` on a slice LHS is MD-020 (needs a bare-identifier target)" {
            mdIds("plan.name.Kaufland.month.3.net := 42") shouldContain TtrpDiagnosticId.MD_020
        }

        "`-=` on a slice LHS is MD-020" {
            mdIds("plan.name.Kaufland.month.3.net -= 42") shouldContain TtrpDiagnosticId.MD_020
        }

        "`+=` on a fresh bare name is MD-021 (nothing to merge into)" {
            mdIds("Fresh += plan.name.Kaufland.month.*.net") shouldContain TtrpDiagnosticId.MD_021
        }

        "`-=` on a fresh bare name is MD-021" {
            mdIds("Fresh -= plan.name.Kaufland.month.*.net") shouldContain TtrpDiagnosticId.MD_021
        }

        // ---- R25 virtual cubelets -------------------------------------------------------------------

        "`V = e` binds a virtual cubelet whose dot-path resolves in a later statement (session namespace)" {
            // First statement binds V (grain Time.month, measure net); the second reads V.month.6.net.
            mdIds("V = plan.name.Kaufland.month.*\nW = V.month.6.net") shouldBe emptyList()
        }

        "an SSA rebind reads the prior binding" {
            // V rebinds to a scalar read of itself — the second `V` sees the first (Q7-γ).
            mdIds("V = plan.name.Kaufland.month.*\nV = V.month.6.net") shouldBe emptyList()
        }

        "a virtual cubelet shadowing a model cubelet name is an MD-022 warning" {
            val diags = ids("sales = plan.name.Kaufland.month.*")
            diags.map { it.id } shouldContain TtrpDiagnosticId.MD_022
            diags.first { it.id == TtrpDiagnosticId.MD_022 }.severity shouldBe Severity.WARNING
        }

        // ---- R26/R27 materialize checking -----------------------------------------------------------

        "materializing a fresh cubelet without `with { shape }` is MD-015" {
            mdIds("Fresh := plan.name.Kaufland.month.*.net") shouldContain TtrpDiagnosticId.MD_015
        }

        "materializing a fresh cubelet with `with { shape: wide }` is legal" {
            mdIds("Fresh := plan.name.Kaufland.month.*.net with { shape: wide }") shouldNotContain
                TtrpDiagnosticId.MD_015
        }

        "an unknown `with` key is MD-015" {
            mdIds("Fresh := plan.name.Kaufland.month.*.net with { shape: wide, bogus: x }") shouldContain
                TtrpDiagnosticId.MD_015
        }

        "an invalid `shape` value is MD-015" {
            mdIds("Fresh := plan.name.Kaufland.month.*.net with { shape: sideways }") shouldContain
                TtrpDiagnosticId.MD_015
        }

        // ---- R28/R29 merge & delete checking --------------------------------------------------------

        "`+=` on a model cubelet with a grain-covering RHS is legal" {
            mdIds("plan += plan.name.Kaufland.month.*.net") shouldNotContain TtrpDiagnosticId.MD_023
        }

        "`-=` with a measure token in the RHS is an MD-016 warning (delete is keys-only)" {
            val diags = ids("plan -= plan.name.Kaufland.month.6.net")
            diags.map { it.id } shouldContain TtrpDiagnosticId.MD_016
            diags.first { it.id == TtrpDiagnosticId.MD_016 }.severity shouldBe Severity.WARNING
        }

        "`-=` on a diff-journaled cubelet is MD-017" {
            // Override `plan`'s binding to diff journaling for this case.
            val plan = MdCheckerFixtures.bindings.cubelets.getValue("plan")
            val diffBindings =
                MdCheckerFixtures.bindings.copy(
                    cubelets =
                        MdCheckerFixtures.bindings.cubelets + (
                            "plan" to
                                plan.copy(
                                    journaling = Journaling.Diff,
                                )
                        ),
                )
            val md = MdCheckerFixtures.mdContext().copy(bindings = diffBindings)
            mdIds("plan -= plan.name.Kaufland.month.6.net", md) shouldContain TtrpDiagnosticId.MD_017
        }

        // ---- regression: ordinary (non-MD) variable assignment is untouched -------------------------

        "a plain slice `=` assignment still checks as before (no dispatch regression)" {
            mdIds("plan.name.Kaufland.month.3.net = 42") shouldBe emptyList()
        }
    })
