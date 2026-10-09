// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.wire

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.calcite.rex.RexLiteral
import org.apache.calcite.sql.SqlKind
import org.apache.calcite.sql.type.SqlTypeName
import org.apache.calcite.util.TimestampString
import org.tatrman.plan.v1.Literal
import org.tatrman.translator.framework.FixtureModel
import org.tatrman.translator.framework.TranslatorFramework
import java.math.BigDecimal

/**
 * TF-P1.S1 (G A1) — a DECIMAL literal's `RexLiteral.value2` is the *unscaled* value (`1.5` → `15`), so
 * encoding it made `x * 100.0` into `x * 1000` on the wire: silent wrong results in every percentage
 * pattern. The wire carries the numeric value; decode restores it exactly.
 */
class LiteralCodecSpec :
    StringSpec({
        val builder = TranslatorFramework(FixtureModel.handle()).newRelBuilder()
        val rexBuilder = builder.rexBuilder

        for (text in listOf("1.5", "0.25", "100.0", "12.75", "3.333", "1E-3")) {
            "DECIMAL literal $text encodes to its numeric value and decodes back to it" {
                val lit = rexBuilder.makeExactLiteral(BigDecimal(text))
                val encoded = Expressions.encode(lit)

                encoded.literal.valueCase shouldBe Literal.ValueCase.FLOAT_VALUE
                encoded.literal.floatValue shouldBe BigDecimal(text).toDouble()

                val decoded = Expressions.decode(builder, encoded)
                decoded.shouldBeInstanceOf<RexLiteral>()
                decoded.getValueAs(BigDecimal::class.java)!! shouldBeEqualComparingTo BigDecimal(text)
            }
        }

        "an integer literal still encodes as int_value" {
            val encoded = Expressions.encode(rexBuilder.makeExactLiteral(BigDecimal("7")))
            encoded.literal.valueCase shouldBe Literal.ValueCase.INT_VALUE
            encoded.literal.intValue shouldBe 7L
        }

        // ttr-core#159 — the encoder's own guard, under SearchExpander: an unexpanded SEARCH must fail as a
        // catchable UnsupportedOperationException. Before, a TIMESTAMP / DECIMAL Sarg threw Calcite's
        // AssertionError ("cannot convert SARG literal …") and an INTEGER one a ClassCastException.
        val typeFactory = rexBuilder.typeFactory
        val unexpandedSearches =
            mapOf(
                "TIMESTAMP range" to
                    rexBuilder.makeBetween(
                        rexBuilder.makeInputRef(typeFactory.createSqlType(SqlTypeName.TIMESTAMP, 3), 0),
                        rexBuilder.makeTimestampLiteral(TimestampString("2026-06-01 00:00:00"), 3),
                        rexBuilder.makeTimestampLiteral(TimestampString("2026-10-10 00:00:00"), 3),
                    ),
                "DOUBLE range" to
                    rexBuilder.makeBetween(
                        rexBuilder.makeInputRef(typeFactory.createSqlType(SqlTypeName.DOUBLE), 0),
                        rexBuilder.makeApproxLiteral(BigDecimal("1.0")),
                        rexBuilder.makeApproxLiteral(BigDecimal("10.0")),
                    ),
                "INTEGER IN-list" to
                    rexBuilder.makeIn(
                        rexBuilder.makeInputRef(typeFactory.createSqlType(SqlTypeName.INTEGER), 0),
                        listOf(
                            rexBuilder.makeExactLiteral(BigDecimal("1")),
                            rexBuilder.makeExactLiteral(BigDecimal("4")),
                        ),
                    ),
            )
        unexpandedSearches.forEach { (shape, search) ->
            "an unexpanded SEARCH over a $shape fails encode with UnsupportedOperationException" {
                search.kind shouldBe SqlKind.SEARCH
                val ex = shouldThrow<UnsupportedOperationException> { Expressions.encode(search) }
                ex.message!! shouldContain "SEARCH"
            }
        }
    })
