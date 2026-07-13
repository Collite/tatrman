// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.functions

import java.util.EnumSet
import org.apache.calcite.sql.SqlOperatorTable
import org.apache.calcite.sql.`fun`.SqlLibrary
import org.apache.calcite.sql.`fun`.SqlLibraryOperatorTableFactory
import org.apache.calcite.sql.util.SqlOperatorTables

/**
 * The Calcite [SqlOperatorTable]s the translator loads for validation.
 *
 * Calcite already defines each engine's non-standard functions, correctly typed, in its per-library
 * operator tables ([SqlLibrary.MSSQL], [SqlLibrary.POSTGRESQL], …). Rather than hand-register one
 * function at a time we load the built-in library tables wholesale (decision D2), so the full T-SQL
 * function surface — `CONCAT`, `IIF`, `ISNULL`, `CHOOSE`, `LEFT`/`RIGHT`, `LEN`, `CHARINDEX`,
 * `DATEADD`/`DATEDIFF`/`DATEPART`, `TRY_CAST`, … — resolves and validates. (Ported from ai-platform
 * `query-translator` `CalciteOperatorTables`, trimmed to the operators tatrman actually overrides.)
 *
 * **Dialect scoping.** The design goal is to load only the *source* engine's operators; until the
 * source dialect is threaded into [org.tatrman.translator.framework.TranslatorFramework],
 * [permissiveUnion] loads STANDARD + MSSQL + POSTGRESQL together (POSTGRESQL also covers the
 * Postgres-compatible DuckDB target we emit).
 */
object CalciteOperatorTables {
    /**
     * The permissive parse-time operator set: our custom overrides first, then standard SQL + the
     * MS SQL and PostgreSQL library operators.
     *
     * **Custom-first ordering (the collision policy).** [CustomOperators] (postfix `COLLATE`, the
     * faithful `CONVERT`/`TRY_CONVERT`, and the faithful T-SQL `IIF`/`ISNULL`/`CHOOSE`/`LEN`/… that
     * preserve their spelling over Calcite's rewrites) and [PlatformOperators] (the grounding
     * catalog) chain BEFORE the library tables, so that where a name collides with a Calcite built-in
     * — chiefly our
     * faithful `CONVERT` vs the standard SQL `CONVERT(e USING charset)` and the library's lossy
     * `MSSQL_CONVERT` — validation overload resolution picks ours.
     *
     * **STANDARD is loaded via the factory, not chained separately.** The factory chains
     * [org.apache.calcite.sql.`fun`.SqlStdOperatorTable] exactly once when STANDARD is requested;
     * chaining it a second time would list each standard operator twice, and Calcite's early
     * resolution of builtins (the `overloads.size() == 1` guard) silently stops firing on a
     * duplicated name — which breaks the `COUNT(*)` star special-casing. So we never chain
     * `SqlStdOperatorTable` ourselves.
     */
    val permissiveUnion: SqlOperatorTable by lazy {
        SqlOperatorTables.chain(
            CustomOperators.table,
            PlatformOperators.OPERATOR_TABLE,
            libraryTableFor(EnumSet.of(SqlLibrary.STANDARD, SqlLibrary.MSSQL, SqlLibrary.POSTGRESQL)),
        )
    }

    /**
     * Construct the operator table for the given [libraries] via Calcite's caching
     * [SqlLibraryOperatorTableFactory] (repeated calls with the same set are cheap). Include
     * [SqlLibrary.STANDARD] to get standard SQL chained in (exactly once) alongside the library ops.
     */
    fun libraryTableFor(libraries: Set<SqlLibrary>): SqlOperatorTable =
        SqlLibraryOperatorTableFactory.INSTANCE.getOperatorTable(libraries)
}
