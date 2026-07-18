// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.capability

import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * A rejects capability miss (RJ-P2, contracts §3/§4): a reject-elaborated cluster (guard/branch/
 * reject synthesized by [org.tatrman.ttrp.graph.rewrite.RejectElaboration]) placed on an engine
 * whose manifest declares `rejects.produces = false` — the engine cannot host a rejects output
 * stream, so the canonical guard is unimplementable there. This is what the `[ttrp] rejects-in-sql`
 * knob resolves (§4): produce/error ⇒ compile error, escalate ⇒ move the cluster's container.
 *
 * The miss is keyed to the **authored** node (the reject cluster's provenance root, RS-3) so the
 * whole cluster escalates as one unit. [function]/[typePair] name the offending validity site for
 * the diagnostic.
 */
data class RejectClusterMiss(
    val authoredNodeId: String,
    val engine: String,
    val function: String,
    val typePair: String,
)

/**
 * Checks every reject-elaborated cluster against its engine's manifest `rejects` capability
 * (contracts §3). A guard Calc (synthesized, carrying `internal.*` validity assignments) whose
 * container targets an engine with `produces = false` is a miss. When `produces = true` the guard
 * is always implementable from the §2 validity spec (regex/bounds), even for a `domain: unknown`
 * or missing entry — so those pass (task 2.1.4). Report-only, like [CapabilityChecker]; the knob
 * policy lives in [org.tatrman.ttrp.graph.rewrite.RejectEscalation].
 */
class RejectsCapabilityChecker(
    private val bound: BoundWorld,
) {
    fun check(graph: TtrpGraph): List<RejectClusterMiss> {
        val out = mutableListOf<RejectClusterMiss>()
        for (node in graph.nodes.values) {
            if (node !is Calc) continue
            val authored = graph.synthProvenance[node.id] ?: continue // synth nodes only
            val sites = guardSites(node)
            if (sites.isEmpty()) continue // guard calcs carry internal.* validity assignments
            val target = graph.containerOf(node.id)?.target ?: continue
            val engine = bound.engines[target] ?: continue
            if (engine.manifest.rejectsSupport().produces) continue // engine can host rejects ⇒ no miss
            for ((function, typePair) in sites) {
                out += RejectClusterMiss(authored, target, function, typePair)
            }
        }
        return out.distinct()
    }

    /** The (function, typePair) validity sites a guard Calc computes, decoded from its `internal.*` calls. */
    private fun guardSites(calc: Calc): List<Pair<String, String>> =
        calc.assignments.mapNotNull { decodeInternal(it.value) }

    private fun decodeInternal(e: Expression): Pair<String, String>? {
        if (e !is FunctionCall) return null
        return when (e.function.value) {
            "internal.is_castable" -> "cast" to canonicalPair(strArg(e.args.getOrNull(1)))
            "internal.is_nonzero" -> "op.div" to "numeric,numeric->numeric"
            "internal.is_parseable_dt" -> "datetime-parse" to "text->datetime"
            else -> null
        }
    }

    private fun strArg(e: Expression?): String? = ((e as? Literal)?.value as? LiteralValue.Str)?.value

    /** Map a cast-target suffix (`internal.is_castable`'s 2nd arg) to the canonical §2 type-pair spelling. */
    private fun canonicalPair(suffix: String?): String =
        when (suffix) {
            null -> "text->?"
            "decimal18_4" -> "text->decimal(18,4)"
            else -> "text->$suffix"
        }
}
