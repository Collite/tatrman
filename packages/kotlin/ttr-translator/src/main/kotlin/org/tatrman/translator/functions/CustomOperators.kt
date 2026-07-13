package org.tatrman.translator.functions

import org.apache.calcite.sql.SqlOperatorTable
import org.apache.calcite.sql.util.SqlOperatorTables

/**
 * Operators the platform defines itself because Calcite ships none (master-plan §5.4/§5.5, D5/D7).
 *
 * Today this holds:
 *  - the custom postfix [SqlCollateOperator] (Phase 0b);
 *  - the Phase 1 MS SQL string functions ([StringOperators] — faithful `LTRIM`/`RTRIM` plus `LEN`,
 *    `SPACE`, `REVERSE`, `REPLICATE`, `CHARINDEX`, `STUFF`, `PATINDEX`, `QUOTENAME`, `STR`);
 *  - the Phase 3 numeric/conditional operators ([ConditionalOperators] — `SQUARE`, `IIF`, `CHOOSE`,
 *    `ISNULL`) and the faithful conversion operators ([ConvertOperators] — `CONVERT`/`TRY_CONVERT`);
 *  - the faithful date/time operators ([DateOperators] — `GETDATE`).
 *
 * Later phases add DuckDB-only functions (`list_*`, `strftime`, …) and other special-syntax
 * operators here. Chained into the framework operator table alongside the standard + library tables
 * ([CalciteOperatorTables]).
 */
object CustomOperators {
    val table: SqlOperatorTable =
        SqlOperatorTables.chain(
            SqlOperatorTables.of(SqlCollateOperator),
            StringOperators.table,
            ConditionalOperators.table,
            ConvertOperators.table,
            DateOperators.table,
        )
}
