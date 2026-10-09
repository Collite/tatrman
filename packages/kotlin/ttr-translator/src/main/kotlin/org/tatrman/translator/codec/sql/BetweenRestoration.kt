// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.codec.sql

import com.google.common.collect.BoundType
import org.apache.calcite.rel.RelNode
import org.apache.calcite.rex.RexBuilder
import org.apache.calcite.rex.RexCall
import org.apache.calcite.rex.RexDynamicParam
import org.apache.calcite.rex.RexLiteral
import org.apache.calcite.rex.RexNode
import org.apache.calcite.rex.RexUnknownAs
import org.apache.calcite.rex.RexUtil
import org.apache.calcite.sql.SqlCall
import org.apache.calcite.sql.SqlKind
import org.apache.calcite.sql.SqlNode
import org.apache.calcite.sql.SqlSpecialOperator
import org.apache.calcite.sql.SqlWriter
import org.apache.calcite.sql.type.ReturnTypes
import org.apache.calcite.util.Sarg
import org.tatrman.translator.framework.RelTreeRexShuttle

/**
 * Write a closed range over a computed operand as one `x BETWEEN lo AND hi`, so the operand is
 * evaluated once.
 *
 * Calcite never keeps `BETWEEN`: `SqlToRelConverter` turns `x BETWEEN 1 AND 10` into
 * `AND(>=(x, 1), <=(x, 10))` (the simplifier may fold that into a `SEARCH(x, Sarg)` over `1..10`), and
 * RelToSql writes either form back as `x >= 1 AND x <= 10`. For a column that costs nothing. For a
 * scalar sub-query, a `CASE` or arithmetic it doubles the work — `(SELECT MAX(…)) BETWEEN 1 AND 10`
 * reached the engine as two copies of the sub-query (ttr-core#159 review).
 *
 * Runs on the rel RelToSql receives (sub-query bodies included). Only rewrites a pair whose operand
 * is a [RexCall] (a column ref stays `col >= lo AND col <= hi`, as before) and whose bounds are
 * literals or parameters. `x BETWEEN lo AND hi` is exactly `x >= lo AND x <= hi`, NULLs included.
 */
internal object BetweenRestoration {
    fun apply(rel: RelNode): RelNode {
        val rexBuilder = rel.cluster.rexBuilder
        return object : RelTreeRexShuttle() {
            override fun visitCall(call: RexCall): RexNode {
                val visited = super.visitCall(call)
                if (visited !is RexCall) return visited
                return when (visited.kind) {
                    SqlKind.SEARCH -> closedRangeAsBetween(rexBuilder, visited) ?: visited
                    SqlKind.AND -> pairRanges(rexBuilder, visited)
                    else -> visited
                }
            }
        }.rewrite(rel)
    }

    /**
     * `SEARCH(x, Sarg)` over one closed range `lo..hi` → `BETWEEN(x, lo, hi)` for a computed `x`; null
     * for any other Sarg, which then keeps Calcite's own rendering (`IN`, `NOT IN`, the comparisons of an
     * open range, …).
     */
    private fun closedRangeAsBetween(
        rexBuilder: RexBuilder,
        search: RexCall,
    ): RexNode? {
        if (search.operands[0] !is RexCall) return null
        val sarg = (search.operands[1] as? RexLiteral)?.getValueAs(Sarg::class.java) ?: return null
        val range = sarg.rangeSet.asRanges().singleOrNull() ?: return null
        val closed =
            range.hasLowerBound() &&
                range.hasUpperBound() &&
                range.lowerBoundType() == BoundType.CLOSED &&
                range.upperBoundType() == BoundType.CLOSED
        if (!closed || sarg.nullAs != RexUnknownAs.UNKNOWN) return null
        // A single closed range expands to AND(>=(x, lo), <=(x, hi)) (a point, to =(x, v)).
        val expanded = RexUtil.expandSearch(rexBuilder, null, search) as? RexCall ?: return null
        if (expanded.kind != SqlKind.AND) return null
        return pairRanges(rexBuilder, expanded).takeIf { it !== expanded }
    }

    /** Replace each `x >= lo` / `x <= hi` pair in [and] over the same computed `x` with `BETWEEN(x, lo, hi)`. */
    private fun pairRanges(
        rexBuilder: RexBuilder,
        and: RexCall,
    ): RexNode {
        val operands = and.operands.toMutableList()
        var changed = false
        var i = 0
        while (i < operands.size) {
            val lower = operands[i].boundOf(SqlKind.GREATER_THAN_OR_EQUAL)
            val j =
                if (lower == null) {
                    -1
                } else {
                    operands.indices.firstOrNull { k ->
                        k != i && operands[k].boundOf(SqlKind.LESS_THAN_OR_EQUAL)?.first == lower.first
                    } ?: -1
                }
            if (lower != null && j >= 0) {
                val upper = operands[j].boundOf(SqlKind.LESS_THAN_OR_EQUAL)!!
                operands[i] = rexBuilder.makeCall(PlainBetweenOperator, lower.first, lower.second, upper.second)
                operands.removeAt(j)
                changed = true
                if (j < i) i--
            }
            i++
        }
        return when {
            !changed -> and
            operands.size == 1 -> operands[0]
            else -> rexBuilder.makeCall(and.type, and.op, operands)
        }
    }

    /** `(x, bound)` when this is `x <kind> bound` with a computed `x` and a literal / parameter bound. */
    private fun RexNode.boundOf(kind: SqlKind): Pair<RexNode, RexNode>? {
        if (this !is RexCall || this.kind != kind || operands.size != 2) return null
        val (operand, bound) = operands
        if (operand !is RexCall) return null
        if (bound !is RexLiteral && bound !is RexDynamicParam) return null
        return operand to bound
    }

    /**
     * `value BETWEEN lower AND upper`. Calcite's own BETWEEN operator also prints its
     * `ASYMMETRIC`/`SYMMETRIC` flag, which T-SQL does not accept; ASYMMETRIC is the default meaning.
     */
    private object PlainBetweenOperator : SqlSpecialOperator(
        "BETWEEN",
        SqlKind.OTHER,
        32,
        true,
        ReturnTypes.BOOLEAN_NULLABLE,
        null,
        null,
    ) {
        override fun unparse(
            writer: SqlWriter,
            call: SqlCall,
            leftPrec: Int,
            rightPrec: Int,
        ) {
            val frame = writer.startList(SqlWriter.FrameTypeEnum.SIMPLE, "", "")
            call.operand<SqlNode>(0).unparse(writer, getLeftPrec(), 0)
            writer.sep("BETWEEN")
            call.operand<SqlNode>(1).unparse(writer, 0, 0)
            writer.sep("AND")
            call.operand<SqlNode>(2).unparse(writer, 0, getRightPrec())
            writer.endList(frame)
        }
    }
}
