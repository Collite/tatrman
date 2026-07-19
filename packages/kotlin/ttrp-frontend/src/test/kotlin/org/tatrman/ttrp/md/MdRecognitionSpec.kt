// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.tatrman.ttrp.TtrpFrontend
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.Column
import org.tatrman.ttrp.expr.ExpressionTypechecker
import org.tatrman.ttrp.expr.MdPath
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.parser.TtrpParser

/**
 * S3-A1/A6 — MD dot-paths become live in TTR-P expression position: column-first precedence with the
 * `TTRP-MD-012` shadow warning (R23), resolver invocation surfacing a canonical marker + shape +
 * explanation, and the per-token MD diagnostics on a non-resolving chain. The resolver core itself is
 * proven in `ttr-md-resolver`; this spec proves the frontend *wiring* (adapter, precedence,
 * diagnostic bridge, marker exposure).
 */
class MdRecognitionSpec :
    StringSpec({
        val tc = ExpressionTypechecker()
        val md = MdCheckerFixtures.mdContext() // connected (member snapshot present)

        fun mdPathOf(src: String): MdPath {
            val parsed = TtrpParser.parseExpression(src)
            parsed.diagnostics.filter { it.severity == Severity.ERROR } shouldBe emptyList()
            return parsed.expression as MdPath
        }

        val kaufland2025 = "sales[customer.name: \"Kaufland\", time.year: 2025].net @ sum"

        "(a) a connected MD path resolves and records a canonical marker" {
            val result = tc.check(mdPathOf("Kaufland.sales.2025.net"), inputSchema = null, md = md)
            result.diagnostics.shouldBeEmpty()
            result.mdResolutions shouldHaveSize 1
            result.mdResolutions.single().canonical shouldBe kaufland2025
        }

        "(a') any-order permutation resolves to the identical canonical marker" {
            val result = tc.check(mdPathOf("2025.net.sales.Kaufland"), inputSchema = null, md = md)
            result.diagnostics.shouldBeEmpty()
            result.mdResolutions.single().canonical shouldBe kaufland2025
        }

        "(b) a leading component that is an in-scope input column shadows the path — column wins + MD-012" {
            val schema = mapOf("" to listOf(Column("Kaufland", TtrpType.Decimal())))
            val result = tc.check(mdPathOf("Kaufland.sales.2025.net"), inputSchema = schema, md = md)
            result.mdResolutions.shouldBeEmpty() // column wins — no MD marker
            val warn = result.diagnostics.single()
            warn.id shouldBe TtrpDiagnosticId.MD_012
            warn.severity shouldBe Severity.WARNING
        }

        "(c) qualifying the member as a dimension pair forces MD under the same shadow — no warning" {
            val schema = mapOf("" to listOf(Column("Kaufland", TtrpType.Decimal())))
            val result = tc.check(mdPathOf("customer.Kaufland.sales.2025.net"), inputSchema = schema, md = md)
            result.diagnostics.shouldBeEmpty()
            result.mdResolutions.single().canonical shouldBe kaufland2025
        }

        "(d) a chain that is neither a column nor a resolvable MD path surfaces MD diagnostics, not EXP-001" {
            val schema = mapOf("" to listOf(Column("other", TtrpType.Str)))
            val result = tc.check(mdPathOf("nope.sales.2025.net"), inputSchema = schema, md = md)
            result.mdResolutions.shouldBeEmpty()
            result.diagnostics.none { it.id == TtrpDiagnosticId.EXP_001 } shouldBe true
            result.diagnostics.any { it.id == TtrpDiagnosticId.MD_001 } shouldBe true // 'nope': no candidate slot
        }

        "(A6) the resolved marker carries a populated shape and explanation for the frontend API" {
            val resolution =
                tc
                    .check(
                        mdPathOf("Kaufland.sales.2025.net"),
                        inputSchema = null,
                        md = md,
                    ).mdResolutions
                    .single()
            resolution.explanation.steps.shouldNotBe(emptyList<Any>())
            resolution.shape shouldNotBe null // scalar shape (no free dims) — the algebra is asserted in S3-B
        }

        "no MD context (null model) leaves the path unresolved — deferred, no diagnostics" {
            val result = tc.check(mdPathOf("Kaufland.sales.2025.net"), inputSchema = null, md = null)
            result.diagnostics.shouldBeEmpty()
            result.mdResolutions.shouldBeEmpty()
        }

        "(end-to-end) the orchestrator threads the MD context and surfaces the resolution on the check result" {
            // An MD path inside a filter predicate — the orchestrator (`checkExpressions`) must thread
            // the MdContext into the per-expression typecheck and bubble the resolution up.
            val program = "result = filter(sales, Kaufland.sales.2025.net > 100)"
            val checked = TtrpFrontend.check(program, md = md)
            checked.mdResolutions.single().canonical shouldBe kaufland2025
        }
    })
