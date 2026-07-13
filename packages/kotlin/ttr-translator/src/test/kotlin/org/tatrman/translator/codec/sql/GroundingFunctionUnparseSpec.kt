// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.codec.sql

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.translate.v1.SqlDialect as SqlDialectProto
import org.tatrman.translator.framework.FixtureModel
import org.tatrman.translator.framework.TranslatorFramework

/**
 * RG-P3 — golden dialect-lowering for the grounding catalog functions (restored with
 * [org.tatrman.translator.functions.PlatformOperators] + the grounding dialects).
 * `period_end` is EXCLUSIVE (start of next period); MSSQL uses `geography::Point(lat, lon, …)`
 * while PostGIS uses `ST_MakePoint(lon, lat)` — the axis swap is asserted explicitly.
 */
class GroundingFunctionUnparseSpec :
    StringSpec({

        fun parseToRel(sql: String): org.apache.calcite.rel.RelNode {
            val fw = TranslatorFramework(FixtureModel.handle())
            val r = SqlValidator.validateAndConvert(fw.newPlanner(), sql)
            r.shouldBeInstanceOf<ValidateResult.Success>()
            return r.rel
        }

        fun mssql(sql: String) = RelToSqlUnparser.unparse(parseToRel(sql), SqlDialectProto.MSSQL)

        fun postgres(sql: String) = RelToSqlUnparser.unparse(parseToRel(sql), SqlDialectProto.POSTGRESQL)

        // -- period_start --

        "period_start('202605') lowers to DATEFROMPARTS (MSSQL)" {
            mssql("SELECT period_start('202605') AS s FROM customers") shouldContain
                "DATEFROMPARTS(CAST(SUBSTRING('202605', 1, 4) AS INT), CAST(SUBSTRING('202605', 5, 2) AS INT), 1)"
        }

        "period_start('202605') lowers to make_date (PostgreSQL)" {
            postgres("SELECT period_start('202605') AS s FROM customers") shouldContain
                "make_date(CAST(SUBSTRING('202605' FROM 1 FOR 4) AS INTEGER), " +
                "CAST(SUBSTRING('202605' FROM 5 FOR 2) AS INTEGER), 1)"
        }

        "period_start(code, 'yyyyMMdd') extracts the day component (MSSQL)" {
            mssql("SELECT period_start('20260515', 'yyyyMMdd') AS s FROM customers") shouldContain
                "CAST(SUBSTRING('20260515', 7, 2) AS INT)"
        }

        "period_start(code, 'yyyyMMdd') extracts the day component (PostgreSQL)" {
            postgres("SELECT period_start('20260515', 'yyyyMMdd') AS s FROM customers") shouldContain
                "CAST(SUBSTRING('20260515' FROM 7 FOR 2) AS INTEGER)"
        }

        "an unsupported code_format is rejected at translate time" {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    mssql("SELECT period_start('2026Q2', 'yyyyQq') AS s FROM customers")
                }
            ex.message!! shouldContain "Unsupported period code_format"
        }

        // -- period_end — EXCLUSIVE end (start of next period) --

        "period_end('202605') is start + 1 month (MSSQL)" {
            mssql("SELECT period_end('202605') AS e FROM customers") shouldContain
                "DATEADD(MONTH, 1, DATEFROMPARTS(CAST(SUBSTRING('202605', 1, 4) AS INT), "
        }

        "period_end('202605') is start + interval '1 month' (PostgreSQL)" {
            val out = postgres("SELECT period_end('202605') AS e FROM customers")
            out shouldContain "+ INTERVAL '1 month')"
            out shouldContain "make_date("
        }

        "period_end(code, 'yyyyMMdd') advances by one day, not one month (MSSQL)" {
            mssql("SELECT period_end('20260515', 'yyyyMMdd') AS e FROM customers") shouldContain
                "DATEADD(DAY, 1, "
        }

        // -- geo_distance_m — axis order is the trap --

        "geo_distance_m lowers to geography::Point(lat, lon, 4326).STDistance (MSSQL)" {
            mssql("SELECT geo_distance_m(49.2, 16.6, 50.0, 14.4) AS d FROM customers") shouldContain
                "geography::Point(49.2, 16.6, 4326).STDistance(geography::Point(50.0, 14.4, 4326))"
        }

        "geo_distance_m lowers to ST_Distance with lon/lat-swapped ST_MakePoint (PostgreSQL)" {
            postgres("SELECT geo_distance_m(49.2, 16.6, 50.0, 14.4) AS d FROM customers") shouldContain
                "ST_Distance(ST_SetSRID(ST_MakePoint(16.6, 49.2), 4326)::geography, " +
                "ST_SetSRID(ST_MakePoint(14.4, 50.0), 4326)::geography)"
        }
    })
