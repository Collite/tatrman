// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.TtrpEmitException
import org.tatrman.ttrp.expr.TtrpType

/**
 * The translator's cast target codes for TTR-P types. A cast rides `plan.v1` in the translator's FUNCTION
 * form — `FunctionCall(operation = "cast")` with the target as the wrapping expression's `result_type` — and
 * the translator reads that result type as a physical code `<kind>[:<precision>[,<scale>]]` (`varchar:max`,
 * `decimal:12,2`, `int`, `date`, `datetime`, …; contracts §3.3 of the translator). (The `Expression.cast`
 * oneof is NOT decoded by the translator — lowering to it was the `CastExpression decoding is TODO` failure.)
 *
 * Text is `varchar:max` (T-SQL `VARCHAR(MAX)`): an unsized `VARCHAR` is `VARCHAR(30)` in SQL Server, which
 * truncates long strings and overflows on wide numerics. Object/list/custom types have no SQL cast.
 */
object SqlCastTypes {
    fun codeOf(t: TtrpType): String =
        when (t) {
            is TtrpType.Integer -> "int"
            is TtrpType.Decimal ->
                when {
                    t.precision == null -> "decimal"
                    t.scale == null -> "decimal:${t.precision}"
                    else -> "decimal:${t.precision},${t.scale}"
                }
            is TtrpType.Float, is TtrpType.Double -> "float"
            is TtrpType.Number -> "decimal"
            is TtrpType.Bool -> "bit"
            is TtrpType.Str -> "varchar:max"
            is TtrpType.Date -> "date"
            is TtrpType.Datetime -> "datetime"
            is TtrpType.Timestamp -> "datetime2"
            is TtrpType.Obj, is TtrpType.Lst, is TtrpType.Named ->
                throw TtrpEmitException(
                    EmitDiagnosticId.UNSUPPORTED_NODE,
                    detail =
                        "no SQL cast to `${t.canonical}` — cast to a scalar type " +
                            "(text, int, decimal, float, bool, date, datetime)",
                )
        }

    /** The cast code for a TTR-M / TTR-P type spelling (`text`, `decimal(12,2)`, `int`, …). */
    fun codeOf(spelling: String): String {
        val base = spelling.substringBefore('(').trim()
        val args =
            spelling
                .substringAfter(
                    '(',
                    "",
                ).substringBefore(')')
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
        return codeOf(TtrpType.parse(base, args.getOrNull(0), args.getOrNull(1)))
    }
}
