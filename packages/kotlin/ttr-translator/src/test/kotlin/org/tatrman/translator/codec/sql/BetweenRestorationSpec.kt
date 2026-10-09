// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.codec.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.plan.v1.ParameterBinding
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.Value
import org.tatrman.translate.v1.Language
import org.tatrman.translate.v1.SqlDialect
import org.tatrman.translator.framework.FixtureModel
import org.tatrman.translator.orchestrator.ParseResult
import org.tatrman.translator.orchestrator.Translator
import org.tatrman.translator.orchestrator.UnparseResult
import org.tatrman.translator.params.SqlParam

/**
 * ttr-core#159 review — `x BETWEEN 1 AND 10` reaches the unparser as `x >= 1 AND x <= 10` (the converter
 * expands BETWEEN, copying `x`), so a scalar sub-query or expression operand was written — and run — twice.
 * [BetweenRestoration] writes a closed range over a computed operand back as one BETWEEN; a range over a
 * plain column keeps its comparisons.
 */
class BetweenRestorationSpec :
    StringSpec({
        val translator = Translator(FixtureModel.handle())

        fun mssql(
            plan: PlanNode,
            dialect: SqlDialect = SqlDialect.MSSQL,
            parameters: List<ParameterBinding> = emptyList(),
        ): UnparseResult.Success {
            val r = translator.unparseFromRelNode(plan, Language.SQL, dialect, parameters = parameters)
            r.shouldBeInstanceOf<UnparseResult.Success>()
            return r.copy(output = r.output.replace(Regex("\\s+"), " "))
        }

        fun parse(
            sql: String,
            parameters: List<SqlParam> = emptyList(),
        ): PlanNode {
            val r = translator.parseToRelNode(sql, Language.SQL, targetSchema = SchemaCode.DB, parameters = parameters)
            r.shouldBeInstanceOf<ParseResult.Success>()
            return r.plan
        }

        fun reEntered(plan: PlanNode): PlanNode {
            val r =
                translator.parseToRelNode(
                    String(plan.toByteArray(), Charsets.ISO_8859_1),
                    Language.REL_NODE,
                    targetSchema = SchemaCode.DB,
                )
            r.shouldBeInstanceOf<ParseResult.Success>()
            return r.plan
        }

        val scalarBetween = "SELECT id FROM customers WHERE (SELECT MAX(o.total) FROM orders o) BETWEEN 1 AND 10"
        val scalarSql = "(SELECT MAX([total]) FROM [dbo].[orders]))) BETWEEN 1.0 AND 10.0"

        "a scalar sub-query BETWEEN is written once, as BETWEEN" {
            val sql = mssql(parse(scalarBetween)).output
            sql shouldContain scalarSql
            Regex("SELECT MAX").findAll(sql).count() shouldBe 1
        }

        "a scalar sub-query BETWEEN is written once after REL_NODE re-entry too" {
            val sql = mssql(reEntered(parse(scalarBetween))).output
            sql shouldContain scalarSql
            Regex("SELECT MAX").findAll(sql).count() shouldBe 1
        }

        "an arithmetic operand keeps one BETWEEN, next to the other conjuncts" {
            mssql(parse("SELECT id FROM customers WHERE name = 'x' AND id + 1 BETWEEN 1 AND 10")).output shouldContain
                "WHERE [name] = 'x' AND [id] + 1 BETWEEN 1 AND 10"
        }

        "a BETWEEN inside a sub-query's body is restored there" {
            mssql(
                parse(
                    "SELECT name FROM customers WHERE id IN " +
                        "(SELECT o.customer_id FROM orders o WHERE o.id * 2 BETWEEN 2 AND 8)",
                ),
            ).output shouldContain "WHERE [id] * 2 BETWEEN 2 AND 8)"
        }

        "a range over a plain column keeps its two comparisons" {
            val sql = mssql(parse("SELECT id FROM customers WHERE id BETWEEN 1 AND 10")).output
            sql shouldContain "WHERE [id] >= 1 AND [id] <= 10"
            sql shouldNotContain "BETWEEN"
        }

        "a half-open range over a sub-query is left as written" {
            val sql =
                mssql(
                    parse(
                        "SELECT id FROM customers " +
                            "WHERE (SELECT MAX(o.id) FROM orders o) >= 1 AND (SELECT MAX(o.id) FROM orders o) < 10",
                    ),
                ).output
            sql shouldNotContain "BETWEEN"
            sql shouldContain ") >= 1 AND"
        }

        "an IN-list over a sub-query stays an IN-list" {
            val sql = mssql(parse("SELECT id FROM customers WHERE (SELECT MAX(o.id) FROM orders o) IN (2, 3)")).output
            sql shouldContain "))) IN (2, 3)"
            sql shouldNotContain "BETWEEN"
        }

        "parameter bounds keep their positional order" {
            val plan =
                parse(
                    "SELECT id FROM customers WHERE id + 1 BETWEEN {lo} AND {hi}",
                    listOf(SqlParam("lo", "int", 1), SqlParam("hi", "int", 10)),
                )

            fun binding(
                name: String,
                value: Long,
            ) = ParameterBinding
                .newBuilder()
                .setName(name)
                .setType("int")
                .setValue(Value.newBuilder().setIntValue(value))
                .build()
            val r = mssql(plan, parameters = listOf(binding("lo", 1), binding("hi", 10)))
            r.output shouldContain "[id] + 1 BETWEEN ? AND ?"
            r.parameters.map { it.name } shouldBe listOf("lo", "hi")
        }

        "Postgres gets the same BETWEEN" {
            mssql(parse(scalarBetween), SqlDialect.POSTGRESQL).output shouldContain ") BETWEEN 1.0 AND 10.0"
        }
    })
