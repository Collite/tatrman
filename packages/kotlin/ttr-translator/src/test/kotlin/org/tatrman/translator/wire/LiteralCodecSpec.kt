// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.wire

import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import com.google.common.collect.TreeRangeSet
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.calcite.rex.RexCall
import org.apache.calcite.rex.RexLiteral
import org.apache.calcite.rex.RexUnknownAs
import org.apache.calcite.sql.SqlKind
import org.apache.calcite.sql.`fun`.SqlStdOperatorTable
import org.apache.calcite.sql.type.SqlTypeName
import org.apache.calcite.util.Sarg
import org.apache.calcite.util.TimestampString
import org.tatrman.plan.v1.Expression
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

        // ttr-core#159 — the encoder expands SEARCH itself, so a SEARCH reaches the wire as the comparisons it
        // was folded from, wherever it sits. Before, the Sarg operand reached encodeLiteral under the column's
        // type: TIMESTAMP and DOUBLE threw Calcite's AssertionError ("cannot convert SARG literal …"), INTEGER
        // a ClassCastException, and VARCHAR encoded the Sarg's digest as a string — no error at all.
        val typeFactory = rexBuilder.typeFactory

        fun ref(type: SqlTypeName) = rexBuilder.makeInputRef(typeFactory.createSqlType(type), 0)
        val searches =
            mapOf(
                "TIMESTAMP range" to
                    (
                        rexBuilder.makeBetween(
                            rexBuilder.makeInputRef(typeFactory.createSqlType(SqlTypeName.TIMESTAMP, 3), 0),
                            rexBuilder.makeTimestampLiteral(TimestampString("2026-06-01 00:00:00"), 3),
                            rexBuilder.makeTimestampLiteral(TimestampString("2026-10-10 00:00:00"), 3),
                        ) to listOf("and", "ge", "le")
                    ),
                "DOUBLE range" to
                    (
                        rexBuilder.makeBetween(
                            ref(SqlTypeName.DOUBLE),
                            rexBuilder.makeApproxLiteral(BigDecimal("1.0")),
                            rexBuilder.makeApproxLiteral(BigDecimal("10.0")),
                        ) to listOf("and", "ge", "le")
                    ),
                "INTEGER IN-list" to
                    (
                        rexBuilder.makeIn(
                            ref(SqlTypeName.INTEGER),
                            listOf(
                                rexBuilder.makeExactLiteral(BigDecimal("1")),
                                rexBuilder.makeExactLiteral(BigDecimal("4")),
                            ),
                        ) to listOf("or", "eq", "eq")
                    ),
                "VARCHAR IN-list" to
                    (
                        rexBuilder.makeIn(
                            ref(SqlTypeName.VARCHAR),
                            listOf(rexBuilder.makeLiteral("Acme Trading, s.r.o."), rexBuilder.makeLiteral("Globex")),
                        ) to listOf("or", "eq", "eq")
                    ),
            )
        searches.forEach { (shape, case) ->
            val (search, operations) = case
            "a SEARCH over a $shape encodes as the comparisons it was folded from" {
                search.kind shouldBe SqlKind.SEARCH
                val encoded = Expressions.encode(search)
                operationsOf(encoded) shouldBe operations
                literalsOf(encoded).none { it.contains("Sarg") } shouldBe true
            }
        }

        "an expanded VARCHAR IN-list carries each member as its own string literal" {
            val encoded = Expressions.encode(searches.getValue("VARCHAR IN-list").first)
            // Members share the IN-list's common CHAR(n) type, so the shorter one may come back blank-padded.
            literalsOf(encoded).map { it.trimEnd() }.toSet() shouldBe setOf("Acme Trading, s.r.o.", "Globex")
        }

        "an expanded NOT IN is spliced into its enclosing AND, not nested under it" {
            // `NOT IN (-2, 10)` is a SEARCH over the complement of the two points; it expands to `AND(<>, <>)`.
            val n = ref(SqlTypeName.INTEGER)
            val points =
                TreeRangeSet.create<BigDecimal>().apply {
                    add(Range.singleton(BigDecimal("-2")))
                    add(Range.singleton(BigDecimal("10")))
                }
            val notIn =
                rexBuilder.makeCall(
                    SqlStdOperatorTable.SEARCH,
                    n,
                    rexBuilder.makeSearchArgumentLiteral(
                        Sarg.of(RexUnknownAs.UNKNOWN, ImmutableRangeSet.copyOf(points.complement())),
                        n.type,
                    ),
                )
            val condition =
                rexBuilder.makeCall(
                    SqlStdOperatorTable.AND,
                    rexBuilder.makeCall(
                        SqlStdOperatorTable.GREATER_THAN_OR_EQUAL,
                        n,
                        rexBuilder.makeExactLiteral(BigDecimal.ZERO),
                    ),
                    notIn,
                )
            operationsOf(Expressions.encode(condition)) shouldBe listOf("and", "ge", "ne", "ne")
        }

        // The guard under the expansion: a bare Sarg literal (no SEARCH call to expand) still fails as a
        // catchable UnsupportedOperationException, and its message names the type but not the values — they
        // are the user's predicate constants, and the message travels to callers and logs.
        "a bare SARG literal fails encode without echoing its values" {
            val search =
                rexBuilder.makeIn(
                    ref(SqlTypeName.VARCHAR),
                    listOf(rexBuilder.makeLiteral("Acme Trading, s.r.o."), rexBuilder.makeLiteral("Globex")),
                ) as RexCall
            val sarg = search.operands[1] as RexLiteral
            sarg.typeName shouldBe SqlTypeName.SARG
            val ex = shouldThrow<UnsupportedOperationException> { Expressions.encode(sarg) }
            ex.message!! shouldContain "SEARCH"
            ex.message!! shouldNotContain "Acme"
            ex.message!! shouldNotContain "Globex"
        }
    })

/** Every function operation in [expr], depth-first. */
private fun operationsOf(expr: Expression): List<String> =
    if (expr.hasFunction()) {
        listOf(expr.function.operation) + expr.function.operandsList.flatMap(::operationsOf)
    } else {
        emptyList()
    }

/** Every string-valued literal in [expr]. */
private fun literalsOf(expr: Expression): List<String> =
    when {
        expr.hasFunction() -> expr.function.operandsList.flatMap(::literalsOf)
        expr.hasLiteral() && expr.literal.hasStringValue() -> listOf(expr.literal.stringValue)
        else -> emptyList()
    }
