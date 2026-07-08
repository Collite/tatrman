package org.tatrman.ttrp.dialect.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.lang.KeywordTable
import org.tatrman.ttrp.parser.TtrpParser
import org.tatrman.ttrp.parser.generated.TTRSqlLexer
import org.tatrman.ttrp.parser.generated.TTRSqlParser

/**
 * S16 — the dialect grammar SKINS the one expression IR: TTR-SQL operator spellings fold
 * back to the SAME [KeywordTable]/`CatalogId` ids as canonical TTR-P (this is the
 * "sibling drift spec against this SAME object" the [KeywordTable] doc promises). Never a
 * second operator table.
 */
class TtrSqlKeywordDriftSpec :
    StringSpec({
        fun fold(sqlExpr: String): Expression {
            val interior = SourceLocation("<expr>", 1, 0, 1, 0, 0, sqlExpr.length)
            val lexer = TTRSqlLexer(CharStreams.fromString(sqlExpr))
            val parser = TTRSqlParser(CommonTokenStream(lexer))
            return TtrSqlExpr(TtrSqlLoc(interior), TtrpParser.catalog).fold(parser.expr())
        }

        fun opId(sqlExpr: String): String = (fold(sqlExpr) as FunctionCall).function.value

        val cases =
            mapOf(
                "a = b" to "op.eq",
                "a <> b" to "op.neq",
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

        for ((sql, id) in cases) {
            "`$sql` folds to $id (shared catalogue id)" { opId(sql) shouldBe id }
        }

        "every symbol operator in KeywordTable is reachable through the SQL skin" {
            val symbolIds =
                KeywordTable.operators.values
                    .filterNotNull()
                    .toSet() // excludes `->`
            val reached = cases.values.toSet() + "op.not"
            symbolIds.all { it in reached } shouldBe true
        }
    })
