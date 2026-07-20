// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.params

import org.apache.calcite.rel.RelNode
import org.apache.calcite.rel.type.RelDataTypeFactory
import org.apache.calcite.rex.RexBuilder
import org.apache.calcite.rex.RexCall
import org.apache.calcite.rex.RexDynamicParam
import org.apache.calcite.rex.RexNode
import org.apache.calcite.rex.RexShuttle
import org.apache.calcite.sql.SqlKind
import org.apache.calcite.sql.`fun`.SqlStdOperatorTable
import org.apache.calcite.sql.type.SqlTypeName

/**
 * Case-insensitive matching for **text parameters** — the "normalization pass" of a
 * parametrized query.
 *
 * A named parameter states the value the *requestor* wants to match; the exact letter case
 * they type ("Marketplace" vs "marketplace") is not part of that intent. So an equality
 * comparison that involves a text parameter is folded to compare case-insensitively:
 *
 * ```
 *   channel = {channel}        →   LOWER(channel) = LOWER(?)
 *   {channel} <> col           →   LOWER(?) <> LOWER(col)
 * ```
 *
 * This runs on the validated RelNode, **after** [ParameterTyper] has stamped each
 * `RexDynamicParam` with its declared type (so "is this a text param?" is answerable), and
 * before the plan is encoded — see [org.tatrman.translator.schema.Resolve]. The `?`'s
 * positional index is unchanged (LOWER wraps it, does not renumber it), so name restoration
 * and value binding are unaffected.
 *
 * Scope (deliberately narrow to avoid changing unrelated semantics):
 *   - Only `=` and `<>` are folded; ordering (`<`, `>`) and `LIKE` keep their own semantics.
 *   - Only comparisons that reference a **character-typed** parameter are touched, and only
 *     the character-typed operands are wrapped (never a non-text side).
 *   - A comparison with no text parameter is left byte-identical.
 *
 * Note: folding the column side means a plain B-tree index on that column no longer applies
 * (a functional `LOWER(col)` index would). Acceptable for the entity/pattern-query workloads
 * this serves; revisit if a hot parametrized filter needs case-sensitive index use.
 */
object CaseFoldingParams {
    /**
     * Walk [rel] and case-fold every `=` / `<>` comparison that references a text parameter.
     * Returns the rewritten rel — structurally identical to the input when nothing matched.
     *
     * @param rel the typed rel (post-[ParameterTyper])
     * @param typeFactory the Calcite type factory (same one the planner used)
     */
    fun apply(
        rel: RelNode,
        typeFactory: RelDataTypeFactory,
    ): RelNode {
        val rexBuilder = RexBuilder(typeFactory)
        val shuttle =
            object : RexShuttle() {
                override fun visitCall(call: RexCall): RexNode {
                    // Recurse into operands first (bottom-up), so nested comparisons are folded too.
                    val visited = super.visitCall(call)
                    if (visited !is RexCall) return visited
                    if (visited.kind != SqlKind.EQUALS && visited.kind != SqlKind.NOT_EQUALS) return visited
                    val operands = visited.operands
                    if (operands.size != 2) return visited
                    // Only fold when a text PARAMETER is involved — a literal-vs-column equality is
                    // left alone (its case sensitivity is the author's, not a requestor's, choice).
                    if (operands.none { it is RexDynamicParam && isCharacter(it) }) return visited
                    val left = foldIfCharacter(operands[0], rexBuilder)
                    val right = foldIfCharacter(operands[1], rexBuilder)
                    if (left === operands[0] && right === operands[1]) return visited
                    return rexBuilder.makeCall(visited.op, left, right)
                }
            }

        // RelNode.accept(RexShuttle) rewrites only a node's OWN expressions, not its inputs — so a
        // filter/join condition below the top node would be missed. Descend the whole tree (mirrors
        // ParameterTyper).
        fun rewrite(node: RelNode): RelNode {
            val newInputs = node.inputs.map { rewrite(it) }
            val withInputs = if (newInputs == node.inputs) node else node.copy(node.traitSet, newInputs)
            return withInputs.accept(shuttle)
        }
        return rewrite(rel)
    }

    private fun isCharacter(node: RexNode): Boolean =
        when (node.type.sqlTypeName) {
            SqlTypeName.CHAR, SqlTypeName.VARCHAR -> true
            else -> false
        }

    private fun foldIfCharacter(
        node: RexNode,
        rexBuilder: RexBuilder,
    ): RexNode = if (isCharacter(node)) rexBuilder.makeCall(SqlStdOperatorTable.LOWER, node) else node
}
