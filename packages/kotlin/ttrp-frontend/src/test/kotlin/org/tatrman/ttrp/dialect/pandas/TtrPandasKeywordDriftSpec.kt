package org.tatrman.ttrp.dialect.pandas

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.dialect.sql.TtrSqlLoc
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.lang.KeywordTable
import org.tatrman.ttrp.parser.TtrpParser
import org.tatrman.ttrp.parser.generated.TTRPandasLexer
import org.tatrman.ttrp.parser.generated.TTRPandasParser

/**
 * S16 — TTR-pandas skins the ONE expression IR: operator spellings (incl. the `==`
 * synonym) fold to the SAME KeywordTable/CatalogId ids as canonical. Sibling drift spec
 * against the SAME object.
 */
class TtrPandasKeywordDriftSpec :
    StringSpec({
        fun opId(expr: String): String {
            val interior = SourceLocation("<e>", 1, 0, 1, 0, 0, expr.length)
            val parser = TTRPandasParser(CommonTokenStream(TTRPandasLexer(CharStreams.fromString(expr))))
            return (
                TtrPandasExpr(
                    TtrSqlLoc(interior),
                    TtrpParser.catalog,
                ).fold(parser.expr()) as FunctionCall
            ).function.value
        }

        val cases =
            mapOf(
                "a = b" to "op.eq",
                "a == b" to "op.eq", // S9 synonym → SAME id
                "a != b" to "op.neq",
                "a < b" to "op.lt",
                "a <= b" to "op.lte",
                "a > b" to "op.gt",
                "a >= b" to "op.gte",
                "a + b" to "op.add",
                "a - b" to "op.sub",
                "a * b" to "op.mul",
                "a / b" to "op.div",
                "a and b" to "op.and",
                "a or b" to "op.or",
                "not a" to "op.not",
            )

        for ((expr, id) in cases) {
            "`$expr` folds to $id" { opId(expr) shouldBe id }
        }

        "every symbol operator in KeywordTable is reachable through the pandas skin" {
            val symbolIds =
                KeywordTable.operators.values
                    .filterNotNull()
                    .toSet()
            symbolIds.all { it in cases.values.toSet() } shouldBe true
        }
    })
