package org.tatrman.translator.functions

import com.google.common.collect.ImmutableList
import org.apache.calcite.sql.SqlBasicFunction
import org.apache.calcite.sql.SqlFunction
import org.apache.calcite.sql.SqlFunctionCategory
import org.apache.calcite.sql.SqlOperatorTable
import org.apache.calcite.sql.type.OperandTypes
import org.apache.calcite.sql.type.ReturnTypes
import org.apache.calcite.sql.type.SqlOperandTypeChecker
import org.apache.calcite.sql.type.SqlReturnTypeInference
import org.apache.calcite.sql.type.SqlTypeFamily
import org.apache.calcite.sql.util.SqlOperatorTables

/**
 * Custom MS SQL string functions Calcite either ships under no library we load, or rewrites lossily
 * (master-plan §5.4, tasks-string-functions.md — Phase 1).
 *
 * Empirically (against the loaded Calcite 1.41 STANDARD+MSSQL+POSTGRESQL union) the string family
 * splits three ways; this file owns the second and third groups:
 *  - **Group B — present but lossy.** `LTRIM`/`RTRIM` (1-arg) resolve to Calcite's POSTGRESQL
 *    library operators, but [org.apache.calcite.sql2rel.StandardConvertletTable]'s `TrimConvertlet`
 *    rewrites those *specific operator instances* to `TRIM(LEADING/TRAILING ' ' FROM s)` during
 *    sql-to-rel — not faithful T-SQL. We define **fresh** `SqlBasicFunction` instances (kind
 *    `OTHER_FUNCTION`); the identity-keyed convertlet never fires for them, so the call survives to
 *    the RelNode and unparses as `LTRIM(s)`/`RTRIM(s)`. These are chained **before** the library
 *    table ([CalciteOperatorTables.permissiveUnion]) so validation and the [FunctionCatalog]
 *    name-collision policy both resolve `ltrim`/`rtrim` to our faithful operators.
 *  - **Group C — absent.** `LEN`, `SPACE`, `REVERSE`, `REPLICATE`, `CHARINDEX`, `STUFF`, `PATINDEX`,
 *    `QUOTENAME`, `STR` have no operator in the loaded set. Plain `SqlBasicFunction`s with
 *    `FUNCTION` syntax: the default function unparse renders `NAME(args)` faithfully, they
 *    auto-enrol into the [FunctionCatalog] (which enumerates `FUNCTION`-syntax operators), and the
 *    wire decode resolves them by name for free.
 *
 * All operators are **parse/unparse only** — return-type inference is just enough to validate and
 * the operand checkers are deliberately permissive (the engine, not Calcite, executes the query).
 * Group A (`LEFT`, `RIGHT`, `CONCAT_WS`, `SUBSTRING`, `REPLACE`, `UPPER`, `LOWER`) is already in the
 * loaded tables and needs no entry here.
 */
object StringOperators {
    /** A fixed-arity [SqlOperandTypeChecker]: each operand must be a member of the given family. */
    private fun sig(vararg families: SqlTypeFamily): SqlOperandTypeChecker =
        OperandTypes.family(ImmutableList.copyOf(families))

    /**
     * A variable-arity checker built as the union of fixed-arity [sig]natures (one per accepted
     * arity). We compose explicit arities rather than `family(list, optional)` because the latter
     * is documented buggy with optional parameters in this Calcite (CALCITE-6984 / -6976).
     */
    private fun anyOf(vararg checkers: SqlOperandTypeChecker): SqlOperandTypeChecker = OperandTypes.or(*checkers)

    /**
     * Create a custom string function tagged [SqlFunctionCategory.STRING] (the
     * `SqlBasicFunction.create(name, …)` overload defaults to `NUMERIC`, which is only cosmetic but
     * shows up in the coverage checklist).
     */
    private fun strFn(
        name: String,
        returnType: SqlReturnTypeInference,
        checker: SqlOperandTypeChecker,
    ): SqlFunction = SqlBasicFunction.create(name, returnType, checker).withFunctionType(SqlFunctionCategory.STRING)

    // ---- Group B: faithful 1-arg LTRIM / RTRIM (fresh instances dodge the TRIM convertlet) ----

    /** `LTRIM(s)` — T-SQL left-trim; preserved as `LTRIM(s)` (not rewritten to `TRIM`). */
    val LTRIM: SqlFunction =
        strFn("LTRIM", ReturnTypes.ARG0_NULLABLE_VARYING, sig(SqlTypeFamily.STRING))

    /** `RTRIM(s)` — T-SQL right-trim; preserved as `RTRIM(s)`. */
    val RTRIM: SqlFunction =
        strFn("RTRIM", ReturnTypes.ARG0_NULLABLE_VARYING, sig(SqlTypeFamily.STRING))

    // ---- Group C: functions Calcite doesn't provide for our dialects ----

    /** `LEN(s)` — T-SQL string length (MS spelling of `CHAR_LENGTH`). */
    val LEN: SqlFunction =
        strFn("LEN", ReturnTypes.INTEGER_NULLABLE, sig(SqlTypeFamily.STRING))

    /** `SPACE(n)` — a string of `n` spaces. */
    val SPACE: SqlFunction =
        strFn("SPACE", ReturnTypes.VARCHAR_NULLABLE, sig(SqlTypeFamily.INTEGER))

    /** `REVERSE(s)` — reverse a string. */
    val REVERSE: SqlFunction =
        strFn("REVERSE", ReturnTypes.ARG0_NULLABLE_VARYING, sig(SqlTypeFamily.STRING))

    /** `REPLICATE(s, n)` — repeat `s` `n` times. */
    val REPLICATE: SqlFunction =
        strFn(
            "REPLICATE",
            ReturnTypes.ARG0_NULLABLE_VARYING,
            sig(SqlTypeFamily.STRING, SqlTypeFamily.INTEGER),
        )

    /** `CHARINDEX(needle, haystack [, start])` — 1-based position; arg order differs from `POSITION`. */
    val CHARINDEX: SqlFunction =
        strFn(
            "CHARINDEX",
            ReturnTypes.INTEGER_NULLABLE,
            anyOf(
                sig(SqlTypeFamily.STRING, SqlTypeFamily.STRING),
                sig(SqlTypeFamily.STRING, SqlTypeFamily.STRING, SqlTypeFamily.INTEGER),
            ),
        )

    /** `STUFF(s, start, len, replaceWith)` — delete `len` chars at `start` and insert `replaceWith`. */
    val STUFF: SqlFunction =
        strFn(
            "STUFF",
            ReturnTypes.ARG0_NULLABLE_VARYING,
            sig(SqlTypeFamily.STRING, SqlTypeFamily.INTEGER, SqlTypeFamily.INTEGER, SqlTypeFamily.STRING),
        )

    /** `PATINDEX('%pattern%', s)` — 1-based position of the first pattern match. */
    val PATINDEX: SqlFunction =
        strFn(
            "PATINDEX",
            ReturnTypes.INTEGER_NULLABLE,
            sig(SqlTypeFamily.STRING, SqlTypeFamily.STRING),
        )

    /** `QUOTENAME(s [, quoteChar])` — delimited identifier. */
    val QUOTENAME: SqlFunction =
        strFn(
            "QUOTENAME",
            ReturnTypes.VARCHAR_NULLABLE,
            anyOf(
                sig(SqlTypeFamily.STRING),
                sig(SqlTypeFamily.STRING, SqlTypeFamily.STRING),
            ),
        )

    /** `STR(num [, length [, decimals]])` — numeric → string. */
    val STR: SqlFunction =
        strFn(
            "STR",
            ReturnTypes.VARCHAR_NULLABLE,
            anyOf(
                sig(SqlTypeFamily.NUMERIC),
                sig(SqlTypeFamily.NUMERIC, SqlTypeFamily.INTEGER),
                sig(SqlTypeFamily.NUMERIC, SqlTypeFamily.INTEGER, SqlTypeFamily.INTEGER),
            ),
        )

    /** All custom string operators, as one chainable [SqlOperatorTable]. */
    val table: SqlOperatorTable =
        SqlOperatorTables.of(
            LTRIM,
            RTRIM,
            LEN,
            SPACE,
            REVERSE,
            REPLICATE,
            CHARINDEX,
            STUFF,
            PATINDEX,
            QUOTENAME,
            STR,
        )
}
