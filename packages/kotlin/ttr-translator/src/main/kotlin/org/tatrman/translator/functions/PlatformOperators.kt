// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.functions

import org.apache.calcite.sql.SqlFunction
import org.apache.calcite.sql.SqlFunctionCategory
import org.apache.calcite.sql.SqlKind
import org.apache.calcite.sql.SqlOperator
import org.apache.calcite.sql.SqlOperatorTable
import org.apache.calcite.sql.`fun`.SqlStdOperatorTable
import org.apache.calcite.sql.type.OperandTypes
import org.apache.calcite.sql.type.ReturnTypes
import org.apache.calcite.sql.type.SqlTypeFamily
import org.apache.calcite.sql.type.SqlTypeName
import org.apache.calcite.sql.util.SqlOperatorTables

/**
 * Platform-specific Calcite catalog functions for deterministic grounding (feature-grounding,
 * contracts §6). These join the auto-enumerated Calcite standard operators; per-dialect lowering
 * lives in the dialect subclasses (`MssqlSqlDialectWithFloatCast`, `PostgresqlSqlDialectWithGrounding`).
 *
 * Signature table (change both or neither vs the grounding recipe catalog):
 *   period_start(code: text[, fmt: text]) -> datetime     -- fmt default 'yyyyMM'
 *   period_end(code: text[, fmt: text])   -> datetime     -- EXCLUSIVE end (start of next period)
 *   geo_distance_m(lat1, lon1, lat2, lon2 : numeric) -> float   -- meters, WGS84
 *
 * NULL policy is STRICT (any null operand -> null result) — enforced at execution by the engines,
 * not the type system.
 *
 * Operator names are UPPERCASE so the wire encoder's `operator.name.lowercase()` fallback
 * round-trips them to the lower-case wire tokens `period_start` / `period_end` / `geo_distance_m`.
 *
 * (RG-P3: restored into `ttr-translator` — the extraction from ai-platform's `query-translator`
 * omitted the whole `functions/` package, so grounding recipes failed to parse with
 * "No match found for function signature period_start(...)".)
 */
object PlatformOperators {
    private val CODE_OR_CODE_FMT =
        OperandTypes.or(
            OperandTypes.family(SqlTypeFamily.CHARACTER),
            OperandTypes.family(SqlTypeFamily.CHARACTER, SqlTypeFamily.CHARACTER),
        )

    val PERIOD_START: SqlFunction =
        SqlFunction(
            "PERIOD_START",
            SqlKind.OTHER_FUNCTION,
            ReturnTypes.explicit(SqlTypeName.TIMESTAMP),
            null,
            CODE_OR_CODE_FMT,
            SqlFunctionCategory.TIMEDATE,
        )

    val PERIOD_END: SqlFunction =
        SqlFunction(
            "PERIOD_END",
            SqlKind.OTHER_FUNCTION,
            ReturnTypes.explicit(SqlTypeName.TIMESTAMP),
            null,
            CODE_OR_CODE_FMT,
            SqlFunctionCategory.TIMEDATE,
        )

    val GEO_DISTANCE_M: SqlFunction =
        SqlFunction(
            "GEO_DISTANCE_M",
            SqlKind.OTHER_FUNCTION,
            ReturnTypes.DOUBLE,
            null,
            OperandTypes.family(
                SqlTypeFamily.NUMERIC,
                SqlTypeFamily.NUMERIC,
                SqlTypeFamily.NUMERIC,
                SqlTypeFamily.NUMERIC,
            ),
            SqlFunctionCategory.NUMERIC,
        )

    /** All platform operators, in registration order. */
    val ALL: List<SqlFunction> = listOf(PERIOD_START, PERIOD_END, GEO_DISTANCE_M)

    /** Operator table exposed to the parser/validator (chained after the Calcite standard table). */
    val OPERATOR_TABLE: SqlOperatorTable = SqlOperatorTables.of(ALL)

    /** wire op-name (lower-case) -> operator, for the decode path. */
    fun byWireName(name: String): SqlOperator? =
        when (name.lowercase()) {
            "period_start" -> PERIOD_START
            "period_end" -> PERIOD_END
            "geo_distance_m" -> GEO_DISTANCE_M
            else -> null
        }

    /**
     * Names in [ALL] that already exist in [other] (case-insensitive) — a collision that would make
     * function resolution ambiguous/first-wins-silently. Empty means safe to merge.
     */
    fun collisionsWith(other: SqlOperatorTable): List<String> {
        val existing = other.operatorList.map { it.name.uppercase() }.toSet()
        return ALL.map { it.name }.filter { it.uppercase() in existing }
    }

    init {
        // Fail loudly at class-load if a platform name shadows a Calcite standard operator,
        // rather than resolving ambiguously at query time.
        val collisions = collisionsWith(SqlStdOperatorTable.instance())
        require(collisions.isEmpty()) {
            "Platform catalog function name(s) collide with Calcite standard operators: $collisions"
        }
    }
}
