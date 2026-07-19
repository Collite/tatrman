// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.ExpressionTypechecker
import org.tatrman.ttrp.parser.TtrpParser

/**
 * S3-B1/B2/B3 — MD shape typing (R15), broadcast (R16), and no-implicit-collapse / scalar-position
 * (R17 → TTRP-MD-008). Shapes are the resolver's free-dim sets, unioned across binary ops; an
 * explicit agg token collapses a path to scalar. Note (S0-B grammar): a `*` only stays inside the
 * path when a non-identifier token precedes it — the expressions here lead with a quoted/numeric
 * member so the star is not eaten by `mulExpr`.
 */
class MdShapeSpec :
    StringSpec({
        val tc = ExpressionTypechecker()
        val md = MdCheckerFixtures.mdContext()

        fun exprOf(src: String): Expression {
            val parsed = TtrpParser.parseExpression(src)
            parsed.diagnostics.filter { it.severity == Severity.ERROR } shouldBe emptyList()
            return parsed.expression
        }

        fun shapeDims(
            src: String,
            predicate: Boolean = false,
        ) = tc.check(exprOf(src), inputSchema = null, predicateExpected = predicate, md = md)

        // ---- R15 shape ----------------------------------------------------------------------
        "a fully-pinned path is scalar" {
            shapeDims("Kaufland.sales.2025.net").shape.freeDims.shouldBeEmpty()
        }

        "a single free dimension is a vector" {
            shapeDims("\"Kaufland\".sales.month.*.net").shape.freeDims shouldContainExactlyInAnyOrder
                listOf("Time.month")
        }

        "two free dimensions is a sub-cubelet" {
            shapeDims("sales.name.{Kaufland, Lidl}.month.*.net").shape.freeDims shouldContainExactlyInAnyOrder
                listOf("Customer.name", "Time.month")
        }

        // ---- R16 broadcast ------------------------------------------------------------------
        "vector * scalar broadcasts to the vector" {
            shapeDims("\"Kaufland\".sales.month.*.net * 2").shape.freeDims shouldContainExactlyInAnyOrder
                listOf("Time.month")
        }

        "vector[month] * vector[month] aligns to a single vector[month]" {
            shapeDims(
                "\"Kaufland\".sales.month.*.net * \"Lidl\".sales.month.*.gross",
            ).shape.freeDims shouldContainExactlyInAnyOrder
                listOf("Time.month")
        }

        "vector[month] * vector[customer] is the sub-cubelet union of both free dims" {
            shapeDims(
                "\"Kaufland\".sales.month.*.net * sales.2025.name.*.gross",
            ).shape.freeDims shouldContainExactlyInAnyOrder
                listOf("Time.month", "Customer.name")
        }

        "alignment is by dimension member, not cubelet — cross-cubelet same-dim stays that vector" {
            shapeDims(
                "\"Kaufland\".sales.month.*.net + \"Kaufland\".plan.month.*.net",
            ).shape.freeDims shouldContainExactlyInAnyOrder
                listOf("Time.month")
        }

        // ---- R17 collapse / scalar position -------------------------------------------------
        "a vector path in a scalar-only predicate position is TTRP-MD-008" {
            val result = shapeDims("\"Kaufland\".sales.month.*.net > 100", predicate = true)
            result.diagnostics.map { it.id } shouldBe listOf(TtrpDiagnosticId.MD_008)
        }

        "an explicit agg token collapses the free dim — the predicate is then scalar and legal" {
            val result = shapeDims("\"Kaufland\".sales.month.*.net.sum > 100", predicate = true)
            result.diagnostics.shouldBeEmpty()
            result.shape.freeDims.shouldBeEmpty()
        }

        "a binary op never implicitly collapses — the vector shape survives the multiply" {
            shapeDims("\"Kaufland\".sales.month.*.net * 2").shape.freeDims shouldContainExactlyInAnyOrder
                listOf("Time.month")
        }
    })
