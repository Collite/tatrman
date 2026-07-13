package org.tatrman.translator.functions

import com.google.common.collect.ImmutableList
import org.apache.calcite.sql.SqlBasicFunction
import org.apache.calcite.sql.SqlFunction
import org.apache.calcite.sql.SqlFunctionCategory
import org.apache.calcite.sql.SqlOperatorTable
import org.apache.calcite.sql.type.OperandTypes
import org.apache.calcite.sql.type.ReturnTypes
import org.apache.calcite.sql.type.SqlOperandCountRanges
import org.apache.calcite.sql.type.SqlReturnTypeInference
import org.apache.calcite.sql.type.SqlTypeFamily
import org.apache.calcite.sql.util.SqlOperatorTables

/**
 * T-SQL numeric helper + conditional functions that Calcite either lacks or only models as a
 * semantic rewrite (master-plan §5.4, tasks-conditional-conversion.md — Phase 3).
 *
 * The recurring Phase-3 principle: this is a faithful **parse/unparse** tool, so we prefer custom
 * **preserved-shape** operators over Calcite's rewrites. `IIF`/`CHOOSE`/`ISNULL` are *not* normalised
 * to `CASE`/`COALESCE` (which would lose the T-SQL spelling and, for `ISNULL`, change semantics) —
 * they are their own `SqlBasicFunction`s whose default `FUNCTION` unparse renders `NAME(args)`
 * faithfully and which auto-enrol into the [FunctionCatalog]. All are parse/unparse only: permissive
 * operand checkers, return-type inference just good enough to validate.
 *
 * Numeric note: `CEILING`/`CEIL`/`ROUND`/`FLOOR`/`LOG10`/`ABS`/`POWER` already validate and unparse
 * faithfully against the loaded union (`MssqlSqlDialect` even renders `CEIL` as `CEILING`), so only
 * `SQUARE` needs a custom operator here.
 */
object ConditionalOperators {
    /**
     * Return-type inference = the least-restrictive type of the operands from [startIndex] onward
     * (the *value* branches), forced nullable. For `IIF(cond, a, b)` / `CHOOSE(i, v1, …)` the
     * branches start at index 1; for `ISNULL(a, b)` they start at 0. Falls back to the first
     * branch's type if Calcite can't unify them.
     */
    private fun branchReturnType(startIndex: Int): SqlReturnTypeInference =
        SqlReturnTypeInference { opBinding ->
            val factory = opBinding.typeFactory
            val branchTypes = (startIndex until opBinding.operandCount).map { opBinding.getOperandType(it) }
            val unified = factory.leastRestrictive(branchTypes) ?: branchTypes.first()
            factory.createTypeWithNullability(unified, true)
        }

    private fun sig(vararg families: SqlTypeFamily) = OperandTypes.family(ImmutableList.copyOf(families))

    /** `SQUARE(x)` — `x * x`; returns float in T-SQL. */
    val SQUARE: SqlFunction =
        SqlBasicFunction.create("SQUARE", ReturnTypes.DOUBLE_NULLABLE, sig(SqlTypeFamily.NUMERIC))

    /** `IIF(cond, whenTrue, whenFalse)` — preserved, *not* expanded to `CASE`. */
    val IIF: SqlFunction =
        SqlBasicFunction
            .create(
                "IIF",
                branchReturnType(1),
                sig(SqlTypeFamily.BOOLEAN, SqlTypeFamily.ANY, SqlTypeFamily.ANY),
            ).withFunctionType(SqlFunctionCategory.SYSTEM)

    /** `CHOOSE(index, v1, v2, …)` — 1-based pick; variadic (index + at least one value). */
    val CHOOSE: SqlFunction =
        SqlBasicFunction
            .create(
                "CHOOSE",
                branchReturnType(1),
                OperandTypes.variadic(SqlOperandCountRanges.from(2)),
            ).withFunctionType(SqlFunctionCategory.SYSTEM)

    /** `ISNULL(check, replacement)` — T-SQL 2-arg null-coalesce; preserved (distinct from `COALESCE`). */
    val ISNULL: SqlFunction =
        SqlBasicFunction
            .create(
                "ISNULL",
                branchReturnType(0),
                sig(SqlTypeFamily.ANY, SqlTypeFamily.ANY),
            ).withFunctionType(SqlFunctionCategory.SYSTEM)

    /** All custom numeric/conditional operators, as one chainable [SqlOperatorTable]. */
    val table: SqlOperatorTable = SqlOperatorTables.of(SQUARE, IIF, CHOOSE, ISNULL)
}
