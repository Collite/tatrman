package org.tatrman.ttrp.emit.polars

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.emit.sql.EmitFixtures.col
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.graph.model.Project

/** T3.2.3 — prelude needs-analysis is minimal + per-helper granular (Q9 items 4–6). */
class PreludeGeneratorTest :
    FunSpec({
        val loc = SourceLocation.UNKNOWN

        fun stepWith(vararg columns: Expression) =
            PolarsStep(varName = "v", node = Project("p", "p", loc, columns.toList()), inputVars = listOf("in"))

        fun prelude(vararg columns: Expression) = PreludeGenerator().forSteps(listOf(stepWith(*columns)))

        test("no special types → empty prelude") {
            prelude(col("x")) shouldBe emptyList()
        }

        test("decimal cast → only the decimal helper") {
            val p = prelude(Cast(col("amount"), TtrpType.Decimal(19, 2), loc))
            p shouldContainInOrder listOf("def _ttrp_decimal(df, col, precision, scale):")
            p.none { it.contains("_ttrp_dt_utc_us") } shouldBe true
        }

        test("datetime cast → only the UTC-µs helper") {
            val p = prelude(Cast(col("ts"), TtrpType.Datetime, loc))
            p shouldContainInOrder listOf("def _ttrp_dt_utc_us(df, cols):")
            p.none { it.contains("_ttrp_decimal") } shouldBe true
        }

        test("both types → both helpers, sorted by name (decimal before dt_utc)") {
            val p =
                prelude(
                    Cast(col("ts"), TtrpType.Timestamp, loc),
                    Cast(col("amount"), TtrpType.Decimal(10, 2), loc),
                )
            val decimalAt = p.indexOfFirst { it.contains("_ttrp_decimal") }
            val dtAt = p.indexOfFirst { it.contains("_ttrp_dt_utc_us") }
            (decimalAt in 0 until dtAt) shouldBe true
        }
    })
