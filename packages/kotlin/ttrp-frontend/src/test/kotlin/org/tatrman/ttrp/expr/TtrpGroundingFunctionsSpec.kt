package org.tatrman.ttrp.expr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.expr.catalog.BuiltinCatalog
import org.tatrman.ttrp.expr.catalog.CatalogEntry
import org.tatrman.ttrp.expr.catalog.FunctionKind
import org.tatrman.ttrp.expr.catalog.NullRule
import org.tatrman.ttrp.expr.catalog.ReturnTypeRule
import org.tatrman.ttrp.parser.TtrpParser

/**
 * Grounding twin functions (grammar 4.2). The signature table below is the
 * cross-repo drift guard — it MUST match ai-platform `feature-grounding-contracts.md`
 * §6 (the normative signature table): change both or neither. period_start/period_end
 * carry the optional `code_format` 2nd arg as two overloads (the catalog has no
 * optional-param marker); geo_distance_m is 4-ary great-circle metres.
 */
class TtrpGroundingFunctionsSpec :
    StringSpec({
        val str = TtrpType.Str
        val num = TtrpType.Number
        val dt = TtrpType.Datetime

        // (name, params, returnType, nullRule) — one row per registered overload.
        val expected: List<CatalogEntry> =
            listOf(
                CatalogEntry(
                    CatalogId("fn.period_start"),
                    "period_start",
                    FunctionKind.SCALAR,
                    listOf(str),
                    ReturnTypeRule.Fixed(dt),
                    NullRule.STRICT,
                ),
                CatalogEntry(
                    CatalogId("fn.period_start"),
                    "period_start",
                    FunctionKind.SCALAR,
                    listOf(str, str),
                    ReturnTypeRule.Fixed(dt),
                    NullRule.STRICT,
                ),
                CatalogEntry(
                    CatalogId("fn.period_end"),
                    "period_end",
                    FunctionKind.SCALAR,
                    listOf(str),
                    ReturnTypeRule.Fixed(dt),
                    NullRule.STRICT,
                ),
                CatalogEntry(
                    CatalogId("fn.period_end"),
                    "period_end",
                    FunctionKind.SCALAR,
                    listOf(str, str),
                    ReturnTypeRule.Fixed(dt),
                    NullRule.STRICT,
                ),
                CatalogEntry(
                    CatalogId("fn.geo_distance_m"),
                    "geo_distance_m",
                    FunctionKind.SCALAR,
                    listOf(num, num, num, num),
                    ReturnTypeRule.Fixed(num),
                    NullRule.STRICT,
                ),
            )

        "T6.1 — the BuiltinCatalog entries match the ai-platform §6 signature table" {
            BuiltinCatalog.resolve("period_start") shouldBe expected.filter { it.name == "period_start" }
            BuiltinCatalog.resolve("period_end") shouldBe expected.filter { it.name == "period_end" }
            BuiltinCatalog.resolve("geo_distance_m") shouldBe expected.filter { it.name == "geo_distance_m" }
        }

        val typechecker = ExpressionTypechecker()
        val schema =
            mapOf(
                "" to
                    listOf(
                        Column("d", TtrpType.Datetime),
                        Column("lat", TtrpType.Decimal()),
                        Column("lon", TtrpType.Decimal()),
                    ),
            )

        "T6.3 — period_start('202605') <= d typechecks to bool" {
            val expr = TtrpParser.parseExpression("period_start('202605') <= d").expression
            val r = typechecker.check(expr, schema)
            r.diagnostics.shouldBeEmpty()
            r.type?.canonical shouldBe "bool"
        }

        "T6.3 — period_start('202605', 'yyyyMM') (2-arg overload) typechecks" {
            val expr = TtrpParser.parseExpression("period_start('202605', 'yyyyMM')").expression
            val r = typechecker.check(expr, schema)
            r.diagnostics.shouldBeEmpty()
            r.type?.canonical shouldBe "datetime"
        }

        "T6.3 — geo_distance_m(lat, lon, 49.19, 16.61) < 20000 typechecks to bool" {
            val expr = TtrpParser.parseExpression("geo_distance_m(lat, lon, 49.19, 16.61) < 20000").expression
            val r = typechecker.check(expr, schema)
            r.diagnostics.shouldBeEmpty()
            r.type?.canonical shouldBe "bool"
        }

        "T6.3 — wrong arity on geo_distance_m is TTRP-FN-002" {
            val expr = TtrpParser.parseExpression("geo_distance_m(lat, lon)").expression
            val r = typechecker.check(expr, schema)
            r.diagnostics.map { it.id.id } shouldContain "TTRP-FN-002"
        }

        // T6.5 — closed alias reject rows.
        "T6.5 — period_from is rejected as TTRP-FN-001 suggesting period_start" {
            val expr = TtrpParser.parseExpression("period_from('202605')").expression
            val r = typechecker.check(expr, schema)
            val err = r.diagnostics.first { it.severity == Severity.ERROR && it.id.id == "TTRP-FN-001" }
            err.suggestedAlternative shouldBe "use period_start"
        }

        "T6.5 — distance is rejected as TTRP-FN-001 suggesting geo_distance_m" {
            val expr = TtrpParser.parseExpression("distance(lat, lon, 49.19, 16.61)").expression
            val r = typechecker.check(expr, schema)
            val err = r.diagnostics.first { it.severity == Severity.ERROR && it.id.id == "TTRP-FN-001" }
            err.suggestedAlternative shouldBe "use geo_distance_m"
        }
    })
