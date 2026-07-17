// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.functions

import org.apache.calcite.sql.SqlCall
import org.apache.calcite.sql.SqlDataTypeSpec
import org.apache.calcite.sql.SqlFunction
import org.apache.calcite.sql.SqlFunctionCategory
import org.apache.calcite.sql.SqlKind
import org.apache.calcite.sql.SqlLiteral
import org.apache.calcite.sql.SqlNode
import org.apache.calcite.sql.SqlOperatorTable
import org.apache.calcite.sql.SqlWriter
import org.apache.calcite.sql.`fun`.SqlLibraryOperators
import org.apache.calcite.sql.type.OperandTypes
import org.apache.calcite.sql.type.SqlOperandCountRanges
import org.apache.calcite.sql.type.SqlReturnTypeInference
import org.apache.calcite.sql.type.SqlTypeName
import org.apache.calcite.sql.util.SqlOperatorTables
import org.apache.calcite.sql.util.SqlShuttle

/**
 * Faithful T-SQL `CONVERT(type, expr [, style])` / `TRY_CONVERT(...)` operators (master-plan
 * §5.4, tasks-conditional-conversion.md — Phase 3).
 *
 * **Why custom (decision: CONVERT-not-CAST).** Calcite ships `SqlLibraryOperators.MSSQL_CONVERT`,
 * but its operand handler rewrites the call to `CAST(expr AS type)` at sql-to-rel time, **dropping
 * the style code and the `CONVERT` spelling**. For a faithful parse/unparse tool that is lossy
 * (`CONVERT(VARCHAR(10), d, 120)` → `CAST(d AS VARCHAR(10))`). So we model CONVERT as our own
 * operator that preserves all operands and unparses back to T-SQL `CONVERT`.
 *
 * **Type as a bare string-literal operand (decision: no wire-codec change).** The first argument is
 * a *data type*, not an expression, so it can't ride as an ordinary `RexNode` operand. Rather than
 * extend the wire format, we capture the type the same way [SqlCollateOperator] captures a collation
 * name: as a **CHARACTER string literal** holding the type's text (e.g. `"VARCHAR(10)"`), unparsed
 * **bare**. This keeps full fidelity — precision/scale survive verbatim as literal text — with no
 * change to the `FunctionCall` wire shape (it is just `[typeLiteral, expr, style?]`). The
 * `getOperandLiteralValue(0)`-derived return type is best-effort (enough to validate).
 *
 * **Parser handoff (two paths, one rewrite).** The stock parser already parses the type-first MSSQL
 * `CONVERT(type, …)` form into an `MSSQL_CONVERT` call (operand 0 = `SqlDataTypeSpec`); `TRY_CONVERT`
 * has no stock production, so a custom grammar production (parserImpls.ftl, the
 * `builtinFunctionCallMethods` hook) builds a [TRY_CONVERT] call with a raw `SqlDataTypeSpec` operand.
 * [ConvertRewriter] — run post-parse in [org.tatrman.translator.codec.sql.SqlValidator] — normalises both:
 * `MSSQL_CONVERT(spec, …)` → [CONVERT] and the raw [TRY_CONVERT], converting the `SqlDataTypeSpec`
 * operand into the bare type literal. This avoids a JavaCC choice conflict with the core `CONVERT`
 * production (which we cannot remove without forking the grammar, against D7).
 */
class SqlConvertOperator(
    name: String,
) : SqlFunction(
        name,
        SqlKind.OTHER_FUNCTION,
        RETURN_TYPE,
        null,
        // Parse-only permissive: 2 operands (type, expr) or 3 (type, expr, style).
        OperandTypes.variadic(SqlOperandCountRanges.between(2, 3)),
        SqlFunctionCategory.SYSTEM,
    ) {
    override fun unparse(
        writer: SqlWriter,
        call: SqlCall,
        leftPrec: Int,
        rightPrec: Int,
    ) {
        val frame = writer.startFunCall(name)
        // Operand 0 is the target type captured as a string literal; render it BARE (never quoted)
        // so it reads as a T-SQL type, mirroring SqlCollateOperator's bare collation name. Drive the
        // separators through the frame's `sep(",")` (the first call is suppressed but initialises the
        // frame's item + whitespace state, so the rest render as a clean `, `).
        for (i in 0 until call.operandCount()) {
            writer.sep(",")
            if (i == 0) {
                val type = call.operand<SqlNode>(0)
                writer.print((type as? SqlLiteral)?.toValue() ?: type.toString())
            } else {
                call.operand<SqlNode>(i).unparse(writer, 0, 0)
            }
        }
        writer.endFunCall(frame)
    }

    companion object {
        /**
         * Return type best-effort: the SqlTypeName named by the leading word of the type literal
         * (e.g. `VARCHAR(10)` → VARCHAR), nullable; ANY when it can't be resolved. Enough to validate
         * a parse/unparse round-trip — we don't execute, so exact precision in the type isn't needed.
         */
        private val RETURN_TYPE =
            SqlReturnTypeInference { opBinding ->
                val factory = opBinding.typeFactory
                val typeText =
                    runCatching { opBinding.getOperandLiteralValue(0, String::class.java) }.getOrNull()
                val typeName =
                    typeText
                        ?.substringBefore('(')
                        ?.trim()
                        ?.let { SqlTypeName.get(it.uppercase()) }
                val base =
                    if (typeName != null) factory.createSqlType(typeName) else factory.createSqlType(SqlTypeName.ANY)
                factory.createTypeWithNullability(base, true)
            }
    }
}

/**
 * Post-parse [SqlShuttle] that normalises both convert flavours into the faithful [ConvertOperators]
 * operators with the target type carried as a bare string literal (see [SqlConvertOperator]).
 */
class ConvertRewriter : SqlShuttle() {
    override fun visit(call: SqlCall): SqlNode? {
        // Both operators are singletons; match by reference identity (=== ) consistently.
        val target =
            when {
                call.operator === SqlLibraryOperators.MSSQL_CONVERT -> ConvertOperators.CONVERT
                call.operator === ConvertOperators.TRY_CONVERT -> ConvertOperators.TRY_CONVERT
                else -> return super.visit(call)
            }
        val operands = call.operandList
        val typeNode = operands[0]
        val typeLiteral =
            if (typeNode is SqlDataTypeSpec) {
                SqlLiteral.createCharString(renderType(typeNode), typeNode.parserPosition)
            } else {
                // Already a literal (idempotent re-visit) — leave it be.
                typeNode
            }
        val rest = operands.drop(1).map { it.accept(this) ?: it }
        return target.createCall(call.parserPosition, listOf(typeLiteral) + rest)
    }

    /**
     * Render a [SqlDataTypeSpec] to its bare T-SQL text. `SqlDataTypeSpec.toString()` uses the
     * default dialect, which leaves built-in types clean (`VARCHAR(10)`, `DATETIME`) but back-tick
     * quotes non-keyword (user-defined) type names — we strip those identifier quotes so the type
     * unparses bare, like the collation in [SqlCollateOperator].
     */
    private fun renderType(type: SqlDataTypeSpec): String = type.toString().replace("`", "").replace("\"", "")
}

/** Holder for the two convert operators (so the generated parser can reference them statically). */
object ConvertOperators {
    @JvmField val CONVERT: SqlConvertOperator = SqlConvertOperator("CONVERT")

    @JvmField val TRY_CONVERT: SqlConvertOperator = SqlConvertOperator("TRY_CONVERT")

    /** Custom convert operators as one chainable [SqlOperatorTable]. */
    val table: SqlOperatorTable = SqlOperatorTables.of(CONVERT, TRY_CONVERT)

    /** Fresh rewriter per call (SqlShuttle is cheap and not thread-safe to share). */
    fun rewriter(): ConvertRewriter = ConvertRewriter()
}
