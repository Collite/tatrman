package org.tatrman.ttrp.lsp.format

import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.CaseWhen
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.InList
import org.tatrman.ttrp.expr.IsNull
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue

/**
 * Renders the one PL expression IR back to canonical TTR-P surface text (C3-a). Operators
 * are catalogue ids over [FunctionCall]; this maps them to infix/prefix spellings with
 * precedence-minimal parentheses. The formatter never *rewrites* an expression (S9
 * `==`→`=` is a diagnostic, not a format fix) — it only re-lays-out; so the canonical
 * spelling here matches what the parser accepted.
 */
object ExprRenderer {
    private val infix =
        mapOf(
            CatalogId.OR.value to "or",
            CatalogId.AND.value to "and",
            CatalogId.EQ.value to "=",
            CatalogId.NEQ.value to "!=",
            CatalogId.LT.value to "<",
            CatalogId.LTE.value to "<=",
            CatalogId.GT.value to ">",
            CatalogId.GTE.value to ">=",
            CatalogId.ADD.value to "+",
            CatalogId.SUB.value to "-",
            CatalogId.MUL.value to "*",
            CatalogId.DIV.value to "/",
        )

    fun render(expr: Expression): String = render(expr, 0)

    private fun render(
        expr: Expression,
        parentPrec: Int,
    ): String {
        val prec = precedence(expr)
        val text = raw(expr)
        return if (prec < parentPrec) "($text)" else text
    }

    private fun raw(expr: Expression): String =
        when (expr) {
            is ColumnRef -> if (expr.port != null) "${expr.port}.${expr.column}" else expr.column
            is Literal -> literal(expr.value)
            is IsNull -> "${render(expr.expr, precedence(expr) + 1)} is ${if (expr.negated) "not " else ""}null"
            is InList -> {
                val items = expr.items.joinToString(", ") { render(it, 0) }
                "${render(expr.expr, precedence(expr) + 1)} ${if (expr.negated) "not in" else "in"} ($items)"
            }
            is Cast -> "cast(${render(expr.expr, 0)} as ${expr.target})"
            is CaseWhen -> caseWhen(expr)
            is AggregateCall -> {
                val d = if (expr.distinct) "distinct " else ""
                "${expr.function.name}($d${expr.args.joinToString(", ") { render(it, 0) }})"
            }
            is FunctionCall -> functionCall(expr)
        }

    private fun functionCall(fc: FunctionCall): String {
        val op = infix[fc.function.value]
        if (op != null && fc.args.size == 2) {
            val p = precedence(fc)
            // Left-associative: left child at p, right child at p+1 (forces parens on same-prec right nesting).
            return "${render(fc.args[0], p)} $op ${render(fc.args[1], p + 1)}"
        }
        if (fc.function == CatalogId.NOT && fc.args.size == 1) {
            return "not ${render(fc.args[0], precedence(fc))}"
        }
        if (fc.function == CatalogId.NEG && fc.args.size == 1) {
            return "-${render(fc.args[0], precedence(fc))}"
        }
        // Ordinary function.
        return "${fc.function.name}(${fc.args.joinToString(", ") { render(it, 0) }})"
    }

    private fun caseWhen(cw: CaseWhen): String =
        buildString {
            append("case")
            for ((cond, result) in cw.branches) append(" when ${render(cond, 0)} then ${render(result, 0)}")
            cw.elseExpr?.let { append(" else ${render(it, 0)}") }
            append(" end")
        }

    private fun literal(v: LiteralValue): String =
        when (v) {
            is LiteralValue.Str -> "\"${v.value}\""
            is LiteralValue.Num -> v.raw
            is LiteralValue.Bool -> v.value.toString()
            is LiteralValue.Null -> "null"
        }

    private fun precedence(expr: Expression): Int =
        when (expr) {
            is FunctionCall ->
                when (expr.function) {
                    CatalogId.OR -> 1
                    CatalogId.AND -> 2
                    CatalogId.NOT -> 3
                    CatalogId.EQ, CatalogId.NEQ, CatalogId.LT, CatalogId.LTE, CatalogId.GT, CatalogId.GTE -> 4
                    CatalogId.ADD, CatalogId.SUB -> 5
                    CatalogId.MUL, CatalogId.DIV -> 6
                    CatalogId.NEG -> 7
                    else -> 8 // ordinary function call binds tightest
                }
            is IsNull, is InList -> 4
            is CaseWhen -> 8
            else -> 8
        }
}
