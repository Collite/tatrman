// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.expr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.tatrman.ttrp.DeclaredSchemaSource
import org.tatrman.ttrp.TtrpFrontend
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.parser.TtrpParser

/**
 * Every expression negative fixture produces its named `TTRP-…` id (with a non-blank
 * suggested alternative). Bare-expression fixtures parse via `parseExpression` and
 * typecheck against [TestSchema]; the two program snippets (`agg-001`, `var-001`)
 * run through [TtrpFrontend].check.
 */
class TtrpExpressionNegativeSpec :
    StringSpec({
        val typechecker = ExpressionTypechecker()

        // Bare-expression fixtures → parse + typecheck against the hand-declared schema.
        val exprCases =
            listOf("eq-001.expr", "fn-001.expr", "fn-002.expr", "typ-001.expr", "typ-002.expr", "typ-widen.expr")

        for (fixture in exprCases) {
            "$fixture → its named id with a suggested alternative" {
                val (source, expected) = ExprFixtures.negative(fixture)
                val parsed = TtrpParser.parseExpression(source)
                val typed = typechecker.check(parsed.expression, TestSchema.schema)
                val diagnostics = parsed.diagnostics + typed.diagnostics
                assertNamed(diagnostics, expected)
            }
        }

        // Program-snippet fixtures → the wired front-half check.
        val programCases = listOf("agg-001.expr", "var-001.ttrp")

        for (fixture in programCases) {
            "$fixture → its named id with a suggested alternative" {
                val (source, expected) = ExprFixtures.negative(fixture)
                val result = TtrpFrontend.check(source, fileName = fixture)
                assertNamed(result.diagnostics, expected)
            }
        }

        // review-001 1.2-A: predicate-bool (TYP-001) enforced through the WIRED pipeline
        // (`TtrpFrontend.check`), not only via a direct typechecker call. `filter`'s
        // non-source arg must type `bool`; a decimal formula there is TTRP-TYP-001.
        "filter with a non-bool predicate → TTRP-TYP-001 (wired pipeline)" {
            val schema = DeclaredSchemaSource(mapOf("s" to listOf(Column("amount", TtrpType.Decimal()))))
            val result = TtrpFrontend.check("f = filter(s, amount + 1)", schema, fileName = "typ-pred.ttrp")
            assertNamed(result.diagnostics, "TTRP-TYP-001")
        }

        // review-001 1.2-F: aggregates are legal only inside an aggregating op's config
        // (`aggregate`/`pivot`), not e.g. `sort { … }`.
        "aggregate in a non-aggregating op's config → TTRP-AGG-001 (wired pipeline)" {
            val result = TtrpFrontend.check("x = sort { total = sum(amount) }", fileName = "agg-sort.ttrp")
            assertNamed(result.diagnostics, "TTRP-AGG-001")
        }
    })

/** Asserts the ERROR diagnostics are EXACTLY the expected id (no spurious extras — review-001 1.2-D). */
private fun assertNamed(
    diagnostics: List<TtrpDiagnostic>,
    expectedId: String,
) {
    val errors = diagnostics.filter { it.severity == Severity.ERROR }
    errors.shouldNotBeEmpty()
    errors.map { it.id.id }.toSet() shouldBe setOf(expectedId)
    errors.first { it.id.id == expectedId }.suggestedAlternative.shouldNotBeBlank()
}
