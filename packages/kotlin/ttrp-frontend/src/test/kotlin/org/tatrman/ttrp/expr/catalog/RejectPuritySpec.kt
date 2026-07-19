// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.expr.catalog

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.ExpressionTypechecker
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.TtrpType

/**
 * RJ-P1 / 1.2.5 — the RJ-104 purity check (R-C2-b). The v1 catalogue is all-pure, so the check
 * is exercised with a test-only volatile entry: a volatile call inside a `cast` (a reject-capable
 * position) is an error; the same call outside one, and a pure cast, are not.
 */
class RejectPuritySpec :
    StringSpec({
        val l = SourceLocation.UNKNOWN

        // A catalogue with one volatile function `fn.now` (surface name `now`).
        val volatileCatalog =
            object : FunctionCatalog {
                override val catalogId = "test.volatile"
                private val now =
                    CatalogEntry(
                        CatalogId("fn.now"),
                        "now",
                        FunctionKind.SCALAR,
                        emptyList(),
                        ReturnTypeRule.Fixed(TtrpType.Datetime),
                        NullRule.CUSTOM,
                        pure = false,
                    )

                override fun resolve(name: String) = if (name == "now") listOf(now) else emptyList()
            }

        fun now() = FunctionCall(CatalogId("fn.now"), emptyList(), l)

        "TTRP-RJ-104 — a volatile fn inside a cast (reject-capable) is an error" {
            val expr = Cast(now(), TtrpType.Integer, l)
            RejectPurityCheck.check(expr, volatileCatalog).map { it.id.id } shouldContain "TTRP-RJ-104"
        }

        "TTRP-RJ-104 — a volatile fn in the op.div denominator is an error" {
            val expr = FunctionCall(CatalogId("op.div"), listOf(ColumnRef(null, "a", l), now()), l)
            RejectPurityCheck.check(expr, volatileCatalog).map { it.id.id } shouldContain "TTRP-RJ-104"
        }

        "no RJ-104 for a volatile fn OUTSIDE any reject-capable position" {
            val expr = FunctionCall(CatalogId("fn.coalesce"), listOf(now(), ColumnRef(null, "b", l)), l)
            RejectPurityCheck.check(expr, volatileCatalog) shouldBe emptyList()
        }

        "no RJ-104 for a pure cast" {
            val expr = Cast(ColumnRef(null, "customer", l), TtrpType.Integer, l)
            RejectPurityCheck.check(expr, volatileCatalog) shouldBe emptyList()
        }

        // RJ-P5 review: the check is now WIRED — ExpressionTypechecker runs it on every checked
        // expression (it was previously unreachable dead code). Drive it through the typechecker with
        // the volatile catalogue to prove the wiring, not just the checker in isolation.
        "TTRP-RJ-104 — the typechecker surfaces the purity error (wiring, not just the checker)" {
            val expr = Cast(now(), TtrpType.Integer, l)
            val diags = ExpressionTypechecker(volatileCatalog).check(expr, inputSchema = null).diagnostics
            diags.map { it.id.id } shouldContain "TTRP-RJ-104"
        }
    })
