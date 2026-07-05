package org.tatrman.ttrp.expr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
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
        val exprCases = listOf("eq-001.expr", "fn-001.expr", "fn-002.expr", "typ-001.expr", "typ-002.expr")

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
    })

private fun assertNamed(
    diagnostics: List<TtrpDiagnostic>,
    expectedId: String,
) {
    val errors = diagnostics.filter { it.severity == Severity.ERROR }
    errors.map { it.id.id } shouldContain expectedId
    val match = errors.first { it.id.id == expectedId }
    match.suggestedAlternative.shouldNotBeBlank()
    // The id itself is what the assist-repair vocabulary keys on.
    match.id.id shouldBe expectedId
}
