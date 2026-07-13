package org.tatrman.translator.codec.sql

import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.PlanNode
import org.tatrman.translate.v1.Language
import org.tatrman.translate.v1.SqlDialect as SqlDialectProto
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.calcite.rel.RelNode
import org.tatrman.translator.framework.FixtureModel
import org.tatrman.translator.framework.TranslatorFramework
import org.tatrman.translator.orchestrator.Optimizer
import org.tatrman.translator.orchestrator.Translator
import org.tatrman.translator.orchestrator.TranslateResult
import org.tatrman.translator.wire.PlanNodeDecoder
import org.tatrman.translator.wire.PlanNodeEncoder

/**
 * Phase 0b (calcite-ext) — postfix `COLLATE` via the custom parser (`extraBinaryExpressions` hook)
 * + custom [org.tatrman.translator.functions.SqlCollateOperator]. See master-plan §5.5 / tasks-collate.md.
 *
 * COLLATE is modelled as a binary operator: `expr COLLATE <name>` → wire
 * `FunctionCall(operation="collate", operands=[expr, charLiteral(name)])`, return type ARG0.
 */
class CollateSpec :
    StringSpec({

        val collation = "Latin1_General_CI_AI"

        fun parseToRel(sql: String): ValidateResult {
            val fw = TranslatorFramework(FixtureModel.handle())
            return SqlValidator.validateAndConvert(fw.newPlanner(), sql)
        }

        fun relOf(sql: String): RelNode {
            val r = parseToRel(sql)
            r.shouldBeInstanceOf<ValidateResult.Success>()
            return r.rel
        }

        // B1 — parse/validate accepts postfix COLLATE.
        "B1 — a COLLATE comparison validates to a RelNode" {
            val r = parseToRel("SELECT id FROM customers WHERE name COLLATE $collation = 'a'")
            r.shouldBeInstanceOf<ValidateResult.Success>()
        }

        // B3 — the collation name is captured as a literal, NOT resolved as a column.
        "B3 — the collation name does not resolve as a column (no validation error)" {
            val r = parseToRel("SELECT id FROM customers WHERE name COLLATE $collation = 'a'")
            r.shouldBeInstanceOf<ValidateResult.Success>()
        }

        // B2 — precedence: COLLATE binds tighter than LIKE, so the LIKE's left operand is the
        // whole `(name COLLATE ...)`. Proven on the wire shape: condition is LIKE(collate(name), 'a%').
        "B2 — COLLATE binds tighter than LIKE (LIKE's left operand is the collate call)" {
            val rel = relOf("SELECT id FROM customers WHERE name COLLATE $collation LIKE 'a%'")
            val plan = PlanNodeEncoder.encode(rel)
            val cond = plan.project.input.filter.condition.function
            cond.operation shouldBe "like"
            val leftArg = cond.operandsList[0]
            leftArg.function.operation shouldBe "collate"
            leftArg.function.operandsCount shouldBe 2
        }

        "B2 — COLLATE binds tighter than `=`" {
            val rel = relOf("SELECT id FROM customers WHERE name COLLATE $collation = 'a'")
            val plan = PlanNodeEncoder.encode(rel)
            val cond = plan.project.input.filter.condition.function
            cond.operation shouldBe "eq"
            val leftArg = cond.operandsList[0]
            leftArg.function.operation shouldBe "collate"
        }

        // B4 — wire round-trip: operation="collate", operand[0]=column, operand[1]=string literal.
        "B4 — COLLATE encodes as operation=collate with [column, charLiteral]" {
            val rel = relOf("SELECT id FROM customers WHERE name COLLATE $collation = 'a'")
            val plan = PlanNodeEncoder.encode(rel)
            val condFn = plan.project.input.filter.condition.function
            val collate = condFn.operandsList[0].function
            collate.operation shouldBe "collate"
            val arg0 = collate.operandsList[0]
            val arg1 = collate.operandsList[1]
            arg0.exprCase shouldBe Expression.ExprCase.COLUMN_REF
            arg0.columnRef.name shouldBe "name"
            arg1.exprCase shouldBe Expression.ExprCase.LITERAL
            arg1.literal.stringValue shouldBe collation
        }

        "B4 — COLLATE survives a full encode→bytes→decode round-trip" {
            val rel = relOf("SELECT id FROM customers WHERE name COLLATE $collation = 'a'")
            val plan = PlanNode.parseFrom(PlanNodeEncoder.encode(rel).toByteArray())
            val freshFw = TranslatorFramework(FixtureModel.handle())
            val decoded = PlanNodeDecoder.decode(plan, freshFw)
            val out = RelToSqlUnparser.unparse(decoded, SqlDialectProto.MSSQL)
            out.shouldContainIgnoringCase("collate")
            out.shouldContainIgnoringCase(collation)
        }

        // B5 — a Filter carrying COLLATE survives the optimizer (FILTER_REDUCE_EXPRESSIONS).
        "B5 — a COLLATE Filter survives Optimizer.optimize" {
            val rel = relOf("SELECT id FROM customers WHERE name COLLATE $collation LIKE 'a%'")
            val optimized = Optimizer.optimize(rel, SqlDialectProto.MSSQL)
            val out = RelToSqlUnparser.unparse(optimized, SqlDialectProto.MSSQL)
            out.shouldContainIgnoringCase("collate")
        }

        // B6 — MSSQL unparse emits the infix `... COLLATE <name>` (bare name, not a 'quoted' string).
        "B6 — MSSQL unparses the infix COLLATE with a bare collation name" {
            val rel = relOf("SELECT id FROM customers WHERE name COLLATE $collation = 'a'")
            val out = RelToSqlUnparser.unparse(rel, SqlDialectProto.MSSQL)
            out.shouldContainIgnoringCase("COLLATE $collation")
            // The collation must NOT be emitted as a string literal.
            out.shouldContainIgnoringCase("collate")
            (out.contains("'$collation'")) shouldBe false
        }

        // B7 — non-MSSQL targets: v1 emits the same infix COLLATE form (collation-name dialect
        // translation is out of scope — master-plan non-goals). Locked here.
        "B7 — Postgres and DuckDB also emit the infix COLLATE form" {
            val rel = relOf("SELECT id FROM customers WHERE name COLLATE $collation = 'a'")
            RelToSqlUnparser.unparse(rel, SqlDialectProto.POSTGRESQL).shouldContainIgnoringCase("collate")
            RelToSqlUnparser.unparse(rel, SqlDialectProto.DUCKDB).shouldContainIgnoringCase("collate")
        }

        // B8 — the motivating WHERE clause shape: two COLLATE sites + string concatenation,
        // translated SQL→wire→MSSQL end-to-end. Concatenation carries a column operand (`u.name`) so
        // it survives the optimizer's constant folding — an all-literal concat would (correctly)
        // fold to a single string literal, which is why this exercises concat with a column.
        //
        // NB: adapted from the reference's function-form `CONCAT(...)` to the `||` operator — the
        // function-form library operators (CONCAT, LEFT, …) are the wider `functions/` operator
        // surface deliberately left OUT of the CalciteExtParser port (out-of-scope, see plan.md);
        // tatrman's translator resolves the `||` infix form. The COLLATE behaviour under test is
        // unchanged.
        "B8 — the motivating COLLATE + concat WHERE clause translates end-to-end to MSSQL" {
            val sql =
                """
                SELECT u.id
                FROM customers u
                WHERE (u.name COLLATE $collation LIKE u.name || '%'
                   OR u.name COLLATE $collation LIKE '%' || u.name || '%')
                  AND u.name LIKE '%' || u.name || '%'
                """.trimIndent()
            val r =
                Translator(FixtureModel.handle()).translate(
                    source = sql,
                    sourceLanguage = Language.SQL,
                    targetLanguage = Language.SQL,
                    targetDialect = SqlDialectProto.MSSQL,
                )
            r.shouldBeInstanceOf<TranslateResult.Success>()
            r.output.shouldContainIgnoringCase("collate")
        }
    })
