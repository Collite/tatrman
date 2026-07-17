// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.functions

import org.apache.calcite.sql.SqlBinaryOperator
import org.apache.calcite.sql.SqlCall
import org.apache.calcite.sql.SqlKind
import org.apache.calcite.sql.SqlLiteral
import org.apache.calcite.sql.SqlNode
import org.apache.calcite.sql.SqlWriter
import org.apache.calcite.sql.type.OperandTypes
import org.apache.calcite.sql.type.ReturnTypes

/**
 * Custom T-SQL postfix `COLLATE` operator (master-plan §5.5, tasks-collate.md).
 *
 * Modelled as a binary operator `expr COLLATE <collation-name>` so it rides the v1 wire
 * `FunctionCall` path with two operands: `[expr, charLiteral(collationName)]`. The custom parser's
 * `Collate` method (parserImpls.ftl, wired via the `extraBinaryExpressions` hook) builds calls to
 * this operator and captures the collation as a **string literal** — so the name is never resolved
 * as a column.
 *
 * Precedence (40, even — required by `SqlOperator`) is higher than LIKE (32) and comparison (30),
 * so `x COLLATE c LIKE 'a%'` parses as `(x COLLATE c) LIKE 'a%'` (tasks-collate.md B2). Return type
 * is ARG0 (the collated value keeps the operand's type) so the surrounding LIKE/comparison still
 * type-checks. The operand checker is permissive (any, any) — this is a parse/unparse tool, not an
 * executor.
 *
 * Unparse emits the infix `expr COLLATE <name>` with the collation rendered as a **bare** identifier
 * (not a `'quoted'` string), mirroring Calcite's own `SqlCollation.unparse`. For v1 the same form is
 * emitted for every dialect; translating MS SQL collation names to Postgres/DuckDB equivalents is
 * out of scope (master-plan non-goals) — tasks-collate.md B7.
 *
 * Implemented as a [SqlBinaryOperator] (not [org.apache.calcite.sql.SqlSpecialOperator]) so the
 * parser's flat operator-list reduction (`SqlParserUtil.toTree`) groups it generically by
 * precedence — SPECIAL operators would need a hand-written `reduceExpr`, binary operators don't.
 */
object SqlCollateOperator : SqlBinaryOperator(
    "COLLATE",
    SqlKind.OTHER,
    COLLATE_PRECEDENCE,
    true, // left-associative
    ReturnTypes.ARG0,
    null,
    OperandTypes.ANY_ANY,
) {
    override fun unparse(
        writer: SqlWriter,
        call: SqlCall,
        leftPrec: Int,
        rightPrec: Int,
    ) {
        val operands: List<SqlNode> = call.operandList
        operands[0].unparse(writer, leftPrec, getLeftPrec())
        writer.keyword("COLLATE")
        val collation = operands[1]
        // Render the collation name as a bare token (e.g. `Latin1_General_CI_AI`) — never a
        // `'quoted string'` and never a dialect-quoted identifier (`[...]` in MSSQL). `literal`
        // emits the raw text with the pending whitespace from the COLLATE keyword.
        val name = (collation as? SqlLiteral)?.toValue() ?: collation.toString()
        writer.literal(name)
    }
}

/** Even precedence (required by [org.apache.calcite.sql.SqlOperator]); above LIKE (32) / `=` (30). */
private const val COLLATE_PRECEDENCE = 40
