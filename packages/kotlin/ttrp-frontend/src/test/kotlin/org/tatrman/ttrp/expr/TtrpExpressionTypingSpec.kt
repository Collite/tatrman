package org.tatrman.ttrp.expr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.parser.TtrpParser

/**
 * Table-driven typing: every `golden.exprs` line types to exactly its annotated S23
 * type against the hand-declared [TestSchema], with zero diagnostics. Comparison
 * operators return `bool` for any well-typed operands (a join key `integer = string`
 * is legal); arithmetic widens `integer -> decimal` only.
 */
class TtrpExpressionTypingSpec :
    StringSpec({
        val typechecker = ExpressionTypechecker()

        for ((source, expected) in ExprFixtures.goldenExprs()) {
            "types `$source` :: $expected" {
                val expr = TtrpParser.parseExpression(source).expression
                val result = typechecker.check(expr, TestSchema.schema)
                result.diagnostics.shouldBeEmpty()
                result.type?.canonical shouldBe expected
            }
        }
    })
