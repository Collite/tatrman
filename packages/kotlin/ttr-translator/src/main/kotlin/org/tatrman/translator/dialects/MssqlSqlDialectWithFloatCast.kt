// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.dialects

import org.apache.calcite.rel.type.RelDataType
import org.apache.calcite.sql.SqlAlienSystemTypeNameSpec
import org.apache.calcite.sql.SqlCall
import org.apache.calcite.sql.SqlDataTypeSpec
import org.apache.calcite.sql.SqlDialect
import org.apache.calcite.sql.SqlHint
import org.apache.calcite.sql.SqlNode
import org.apache.calcite.sql.SqlNodeList
import org.apache.calcite.sql.SqlWriter
import org.apache.calcite.sql.dialect.MssqlSqlDialect
import org.apache.calcite.sql.parser.SqlParserPos
import org.apache.calcite.sql.type.SqlTypeName

/**
 * MS SQL Server dialect that fixes CAST target type names Calcite renders with
 * ANSI names SQL Server rejects.
 *
 * Calcite's [MssqlSqlDialect] emits `CAST(... AS DOUBLE)` for [SqlTypeName.DOUBLE],
 * but SQL Server has no `DOUBLE` type — its 8-byte double-precision type is
 * `FLOAT`. The bad cast produced `Incorrect syntax near ')'` at the database.
 * We override [getCastSpec] to render `DOUBLE` as `FLOAT`; every other type
 * defers to the stock dialect.
 *
 * Reach this only through the [Dialects] registry (Calcite engagement rule #1).
 */
class MssqlSqlDialectWithFloatCast(
    context: SqlDialect.Context,
) : MssqlSqlDialect(context) {
    override fun getCastSpec(type: RelDataType): SqlNode? =
        when (type.sqlTypeName) {
            SqlTypeName.DOUBLE ->
                SqlDataTypeSpec(
                    SqlAlienSystemTypeNameSpec("FLOAT", type.sqlTypeName, SqlParserPos.ZERO),
                    SqlParserPos.ZERO,
                )
            else -> super.getCastSpec(type)
        }

    // RG-P3 — lower the platform grounding functions to MSSQL-native SQL (DATEFROMPARTS/DATEADD,
    // geography::Point.STDistance); everything else defers to the stock dialect.
    override fun unparseCall(
        writer: SqlWriter,
        call: SqlCall,
        leftPrec: Int,
        rightPrec: Int,
    ) {
        val rendered = GroundingFunctionUnparse.render(this, call, GroundingFunctionUnparse.Flavor.MSSQL)
        if (rendered != null) {
            writer.print(rendered)
            writer.setNeedWhitespace(true)
        } else {
            super.unparseCall(writer, call, leftPrec, rightPrec)
        }
    }

    /**
     * NX-A.S4 (calcite-ext, D9) — render T-SQL table hints in their native post-alias position:
     * `[mu] AS [m] WITH (NOLOCK, ROWLOCK)`.
     *
     * `RelToSqlConverter.visit(TableScan)` wraps a hinted scan in a `SqlTableRef` whose `unparse`
     * delegates hint rendering here — the stock [SqlDialect] impl is a no-op (Postgres/DuckDB drop
     * the hint) and `AnsiSqlDialect` emits the `/*+ … */` comment form; SQL Server wants the
     * bracketed `WITH (…)` form. Option-bearing hints (`INDEX(0)`) already read as `INDEX(0)` in
     * `getName()` because [org.tatrman.translator.wire.PlanNodeDecoder] folds options into the name
     * (Calcite's `toSqlHint` drops list-options before they reach here).
     */
    override fun unparseTableScanHints(
        writer: SqlWriter,
        hints: SqlNodeList,
        leftPrec: Int,
        rightPrec: Int,
    ) {
        if (hints.isEmpty()) return
        // Render the whole `WITH (NOLOCK, INDEX(0))` clause as one keyword token: `keyword` manages
        // the leading separator space and prints internal spaces verbatim → the conventional
        // `… WITH (NOLOCK)` form (a FUN_CALL frame would emit the space-less `WITH(NOLOCK)`).
        val clause = hints.joinToString(", ", prefix = "WITH (", postfix = ")") { (it as SqlHint).name }
        writer.keyword(clause)
    }
}
