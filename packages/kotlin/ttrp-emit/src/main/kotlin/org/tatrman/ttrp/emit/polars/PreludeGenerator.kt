package org.tatrman.ttrp.emit.polars

import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.CaseWhen
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.InList
import org.tatrman.ttrp.expr.IsNull
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.Project

/**
 * Generates the inline Polars prelude — **only** the enforcement helpers the island actually
 * references (Q9 items 4–6, E-c). Needs-analysis scans the island's expression IR for explicit
 * [Cast]s to decimal / datetime types (the emit-boundary enforcement points); nothing special →
 * zero prelude lines. Helpers are pure Python over `polars` only (dependency-free), emitted in a
 * deterministic (name-sorted) order.
 */
class PreludeGenerator {
    private enum class Helper(
        val fnName: String,
        val body: List<String>,
    ) {
        DECIMAL(
            "_ttrp_decimal",
            listOf(
                "def _ttrp_decimal(df, col, precision, scale):",
                "    return df.with_columns(pl.col(col).cast(pl.Decimal(precision, scale)))",
            ),
        ),
        DT_UTC_US(
            "_ttrp_dt_utc_us",
            listOf(
                "def _ttrp_dt_utc_us(df, cols):",
                "    return df.with_columns([pl.col(c).cast(pl.Datetime(\"us\", \"UTC\")) for c in cols])",
            ),
        ),
    }

    fun forSteps(steps: List<PolarsStep>): List<String> {
        val needed = sortedSetOf<Helper>(compareBy { it.fnName })
        steps.forEach { step -> expressionsOf(step.node).forEach { scan(it, needed) } }
        return needed.flatMap { it.body }
    }

    private fun scan(
        e: Expression,
        acc: MutableSet<Helper>,
    ) {
        when (e) {
            is Cast -> {
                when (e.target) {
                    is TtrpType.Decimal -> acc += Helper.DECIMAL
                    TtrpType.Datetime, TtrpType.Timestamp -> acc += Helper.DT_UTC_US
                    else -> {}
                }
                scan(e.expr, acc)
            }
            is FunctionCall -> e.args.forEach { scan(it, acc) }
            is AggregateCall -> e.args.forEach { scan(it, acc) }
            is IsNull -> scan(e.expr, acc)
            is InList -> {
                scan(e.expr, acc)
                e.items.forEach { scan(it, acc) }
            }
            is CaseWhen -> {
                e.branches.forEach {
                    scan(it.first, acc)
                    scan(it.second, acc)
                }
                e.elseExpr?.let { scan(it, acc) }
            }
            else -> {}
        }
    }

    private fun expressionsOf(node: org.tatrman.ttrp.graph.model.Node): List<Expression> =
        when (node) {
            is Filter -> listOfNotNull(node.predicate)
            is Project -> node.columns
            is Aggregate -> node.aggregations.map { it.value } + listOfNotNull(node.having)
            is Join -> listOfNotNull(node.on)
            else -> emptyList()
        }
}
