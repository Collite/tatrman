// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.codec.sql

import org.tatrman.plan.v1.PlanNode
import org.tatrman.translate.v1.SqlDialect as SqlDialectProto
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.kotest.matchers.string.shouldNotContainIgnoringCase
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.calcite.rel.RelNode
import org.tatrman.translator.framework.FixtureModel
import org.tatrman.translator.framework.TranslatorFramework
import org.tatrman.translator.wire.PlanNodeDecoder
import org.tatrman.translator.wire.PlanNodeEncoder

/**
 * CalciteExtParser (CEP-P2) — faithful T-SQL `CONVERT` / `TRY_CONVERT` via the custom parser's
 * `TryConvertFunctionCall()` production + the [org.tatrman.translator.functions.ConvertOperators]
 * operators, normalised by the post-parse `ConvertRewriter` shuttle wired into [SqlValidator].
 * `CONVERT`/`TRY_CONVERT` stay `CONVERT` (not the lossy stock CAST rewrite) with their style arg,
 * surviving parse→wire→unparse.
 *
 * Scoped to the CONVERT family only — the wider T-SQL numeric/conditional functions (IIF, CHOOSE,
 * ISNULL, SQUARE, TRY_CAST, …) are the `functions/` operator surface deliberately left out of this
 * port (see plan.md "Not in scope"); the reference `ConditionalConversionSpec` also exercises those.
 */
class ConvertSpec :
    StringSpec({

        fun validate(sql: String): ValidateResult {
            val fw = TranslatorFramework(FixtureModel.handle())
            return SqlValidator.validateAndConvert(fw.newPlanner(), sql)
        }

        fun relOf(sql: String): RelNode {
            val r = validate(sql)
            r.shouldBeInstanceOf<ValidateResult.Success>()
            return r.rel
        }

        fun mssql(expr: String): String =
            RelToSqlUnparser.unparse(relOf("SELECT $expr AS x FROM customers"), SqlDialectProto.MSSQL)

        fun roundTripMssql(expr: String): String {
            val plan =
                PlanNode.parseFrom(
                    PlanNodeEncoder.encode(relOf("SELECT $expr AS x FROM customers")).toByteArray(),
                )
            val freshFw = TranslatorFramework(FixtureModel.handle())
            return RelToSqlUnparser.unparse(PlanNodeDecoder.decode(plan, freshFw), SqlDialectProto.MSSQL)
        }

        fun encodedFn(expr: String): org.tatrman.plan.v1.FunctionCall {
            val proj = PlanNodeEncoder.encode(relOf("SELECT $expr AS x FROM customers")).project
            return proj.expressionsList[0].expression.function
        }

        // B-conv — CONVERT / TRY_CONVERT faithful (not CAST), style preserved.

        "B-conv — CONVERT(type, expr) unparses as CONVERT, not CAST" {
            val out = mssql("CONVERT(VARCHAR(10), name)")
            out.shouldContainIgnoringCase("CONVERT(VARCHAR(10), [name])")
            out.shouldNotContainIgnoringCase("CAST(")
        }

        "B-conv — CONVERT with a style code preserves all three operands through the wire" {
            encodedFn("CONVERT(VARCHAR(10), signup, 120)").let {
                it.operation shouldBe "convert"
                it.operandsCount shouldBe 3
            }
            mssql("CONVERT(VARCHAR(10), signup, 120)")
                .shouldContainIgnoringCase("CONVERT(VARCHAR(10), [signup], 120)")
            roundTripMssql("CONVERT(VARCHAR(10), signup, 120)")
                .shouldContainIgnoringCase("CONVERT(VARCHAR(10), [signup], 120)")
        }

        "B-conv — TRY_CONVERT(type, expr [, style]) parses, unparses, and round-trips" {
            validate("SELECT TRY_CONVERT(VARCHAR(10), name) AS x FROM customers")
                .shouldBeInstanceOf<ValidateResult.Success>()
            encodedFn("TRY_CONVERT(VARCHAR(10), signup, 120)").let {
                it.operation shouldBe "try_convert"
                it.operandsCount shouldBe 3
            }
            mssql("TRY_CONVERT(VARCHAR(10), name)").shouldContainIgnoringCase("TRY_CONVERT(VARCHAR(10), [name])")
            roundTripMssql("TRY_CONVERT(VARCHAR(10), signup, 120)")
                .shouldContainIgnoringCase("TRY_CONVERT(VARCHAR(10), [signup], 120)")
        }

        // B-neg — a bad type in CONVERT fails validation cleanly (strict, D1). The stock CONVERT
        // production resolves an unknown type-position token as an expression, so it fails as an
        // unresolved column rather than silently passing.
        "B-neg — a bad type in CONVERT fails validation cleanly" {
            val r = validate("SELECT CONVERT(NOTATYPE, id) AS x FROM customers")
            r.shouldBeInstanceOf<ValidateResult.Failure>()
            r.error.code shouldBe "validation_failed"
        }

        // Documented asymmetry: TRY_CONVERT parses its type via the custom `DataType()` production,
        // which accepts an arbitrary user-defined type name — so an unknown type is taken verbatim and
        // round-trips as-is (the engine, not Calcite, rejects it at execution). Parse/unparse-tool
        // posture (permissive operand checking); pinned so a future tightening is a visible change.
        "TRY_CONVERT accepts an unknown type name verbatim (permissive, parse-only)" {
            validate("SELECT TRY_CONVERT(NOTATYPE, id) AS x FROM customers")
                .shouldBeInstanceOf<ValidateResult.Success>()
            mssql("TRY_CONVERT(NOTATYPE, id)").shouldContainIgnoringCase("TRY_CONVERT(NOTATYPE, [id])")
        }
    })
