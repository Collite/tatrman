// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.dialects

import org.apache.calcite.sql.SqlCall
import org.apache.calcite.sql.SqlDialect
import org.apache.calcite.sql.SqlLiteral
import org.apache.calcite.sql.SqlNode
import org.tatrman.translator.functions.PlatformOperators

/**
 * Per-dialect lowering for the platform grounding catalog functions (feature-grounding A6).
 *
 * `period_end` is EXCLUSIVE — the start of the next period (conditions use `< period_end(...)`).
 * The increment unit follows `code_format`: `yyyyMM` -> +1 month, `yyyyMMdd` -> +1 day.
 *
 * Axis order is the classic trap: MSSQL `geography::Point(lat, lon, srid)` takes **lat first**;
 * PostGIS `ST_MakePoint(x, y)` takes **lon first** — so lat/lon are swapped between dialects.
 *
 * Rendering builds the operands in the target [SqlDialect] via `toSqlString` and splices them into
 * the engine-native template. Only `yyyyMM` / `yyyyMMdd` code formats are supported.
 *
 * (RG-P3: restored into `ttr-translator` alongside [PlatformOperators] — the extraction from
 * ai-platform's `query-translator` omitted the grounding dialects.)
 */
internal object GroundingFunctionUnparse {
    enum class Flavor { MSSQL, POSTGRES }

    /** Rendered engine SQL for a grounding call, or null if [call] is not a grounding operator. */
    fun render(
        dialect: SqlDialect,
        call: SqlCall,
        flavor: Flavor,
    ): String? =
        when (call.operator) {
            PlatformOperators.PERIOD_START -> period(dialect, call, flavor, exclusiveEnd = false)
            PlatformOperators.PERIOD_END -> period(dialect, call, flavor, exclusiveEnd = true)
            PlatformOperators.GEO_DISTANCE_M -> geoDistance(dialect, call, flavor)
            else -> null
        }

    private fun opSql(
        dialect: SqlDialect,
        node: SqlNode,
    ): String = node.toSqlString { c -> c.withDialect(dialect) }.sql

    private fun readFmt(call: SqlCall): String {
        if (call.operandList.size < 2) return "yyyyMM"
        val fmtNode = call.operandList[1]
        val fmt =
            (fmtNode as? SqlLiteral)?.toValue()
                ?: throw IllegalArgumentException(
                    "period function code_format must be a string literal, got: $fmtNode",
                )
        require(fmt == "yyyyMM" || fmt == "yyyyMMdd") {
            "Unsupported period code_format '$fmt' (supported: yyyyMM, yyyyMMdd)"
        }
        return fmt
    }

    private fun period(
        dialect: SqlDialect,
        call: SqlCall,
        flavor: Flavor,
        exclusiveEnd: Boolean,
    ): String {
        val fmt = readFmt(call)
        val code = opSql(dialect, call.operandList[0])
        val start =
            when (flavor) {
                Flavor.MSSQL -> {
                    val day = if (fmt == "yyyyMMdd") "CAST(SUBSTRING($code, 7, 2) AS INT)" else "1"
                    "DATEFROMPARTS(" +
                        "CAST(SUBSTRING($code, 1, 4) AS INT), " +
                        "CAST(SUBSTRING($code, 5, 2) AS INT), " +
                        "$day)"
                }
                Flavor.POSTGRES -> {
                    val day = if (fmt == "yyyyMMdd") "CAST(SUBSTRING($code FROM 7 FOR 2) AS INTEGER)" else "1"
                    "make_date(" +
                        "CAST(SUBSTRING($code FROM 1 FOR 4) AS INTEGER), " +
                        "CAST(SUBSTRING($code FROM 5 FOR 2) AS INTEGER), " +
                        "$day)"
                }
            }
        if (!exclusiveEnd) return start
        val unit = if (fmt == "yyyyMMdd") "DAY" else "MONTH"
        val pgInterval = if (fmt == "yyyyMMdd") "1 day" else "1 month"
        return when (flavor) {
            Flavor.MSSQL -> "DATEADD($unit, 1, $start)"
            Flavor.POSTGRES -> "($start + INTERVAL '$pgInterval')"
        }
    }

    private fun geoDistance(
        dialect: SqlDialect,
        call: SqlCall,
        flavor: Flavor,
    ): String {
        val lat1 = opSql(dialect, call.operandList[0])
        val lon1 = opSql(dialect, call.operandList[1])
        val lat2 = opSql(dialect, call.operandList[2])
        val lon2 = opSql(dialect, call.operandList[3])
        return when (flavor) {
            // MSSQL geography::Point takes (lat, lon, srid).
            Flavor.MSSQL ->
                "geography::Point($lat1, $lon1, 4326).STDistance(geography::Point($lat2, $lon2, 4326))"
            // PostGIS ST_MakePoint takes (x=lon, y=lat); cast to geography for metric distance.
            Flavor.POSTGRES ->
                "ST_Distance(" +
                    "ST_SetSRID(ST_MakePoint($lon1, $lat1), 4326)::geography, " +
                    "ST_SetSRID(ST_MakePoint($lon2, $lat2), 4326)::geography)"
        }
    }
}
