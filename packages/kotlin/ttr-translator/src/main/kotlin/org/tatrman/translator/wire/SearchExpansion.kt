// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.wire

import org.apache.calcite.rex.RexBuilder
import org.apache.calcite.rex.RexCall
import org.apache.calcite.rex.RexNode
import org.apache.calcite.rex.RexShuttle
import org.apache.calcite.rex.RexUtil
import org.apache.calcite.sql.SqlKind

/**
 * Expand every Calcite `SEARCH` call in an expression back into the `OR`/`AND` of comparisons it was
 * folded from. [Expressions.encode] runs this on every expression it is handed, so no `SEARCH` reaches
 * the wire, whichever path built the rel.
 *
 * `SqlToRelConverter` (via `RexSimplify`) collapses an `IN`-list of literals and comparison ranges
 * into a single `SEARCH($ref, Sarg[…])` call: `VLPODTYP_SLOZ IN (1, 4)` becomes
 * `SEARCH($col, Sarg[1; 4])`. On REL_NODE re-entry `PlanNodeDecoder` rebuilds each Filter through
 * `RelBuilder.filter`, whose simplifier folds `col >= a AND col < b`, `BETWEEN` and `IN`-lists the same
 * way. The `Sarg` rides as the *value* of a `RexLiteral` whose declared type is the column type
 * (e.g. `INTEGER`), and the `plan.v1` wire format has no `SEARCH` operator and no `Sarg` literal shape.
 *
 * [RexUtil.expandSearch] is Calcite's own inverse of that fold: it rewrites `SEARCH($col, Sarg[1; 4])`
 * into `OR(=($col, 1), =($col, 4))` (and ranges into `AND`/`OR` of `</<=/>/>=`), all of which the
 * wire format already carries (`or`, `and`, `eq`, `lt`, `le`, `gt`, `ge`). The result is
 * semantically identical and unparses back to an equivalent predicate. No-op on expressions without a
 * `SEARCH` (the common case): it returns the same instance.
 *
 * The expansion is per expression, not per rel tree: a sub-query's body is encoded through
 * [PlanNodeEncoder], whose Filters/Projects/Joins hand their own expressions to [Expressions.encode].
 * ttr-core#159 was a rel-level pass that missed those bodies.
 */
internal object SearchExpansion {
    fun expand(
        rex: RexNode,
        rexBuilder: RexBuilder,
    ): RexNode = rex.accept(Expander(rexBuilder))

    private class Expander(
        private val rexBuilder: RexBuilder,
    ) : RexShuttle() {
        override fun visitCall(call: RexCall): RexNode {
            // Expand any nested SEARCH in the operands first, then expand this node if it is itself a
            // SEARCH — so a SEARCH under NOT/AND/OR, or inside another SEARCH's left operand (which
            // RexUtil.expandSearch copies into each comparison without visiting), is reached too.
            val visited = super.visitCall(call)
            return when {
                visited is RexCall && visited.kind == SqlKind.SEARCH ->
                    RexUtil.expandSearch(rexBuilder, null, visited)
                // TF-P1.S1 — an expanded `NOT IN (-2, 10)` is `AND(<>, <>)`; left nested inside the
                // enclosing AND the condition is non-flat (Calcite's Filter asserts `RexUtil.isFlat`
                // when the plan is decoded). Re-flatten AND/OR parents.
                visited !== call &&
                    visited is RexCall &&
                    (visited.kind == SqlKind.AND || visited.kind == SqlKind.OR) ->
                    RexUtil.flatten(rexBuilder, visited)
                else -> visited
            }
        }
    }
}
