// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rewrite

import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.capability.RejectClusterMiss
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * Resolves rejects capability misses ([RejectClusterMiss]) per the `[ttrp] rejects-in-sql` knob
 * (R-E2-γ, contracts §4):
 *
 * - `produce` / `error` ⇒ compile error `TTRP-RJ-106` naming the offending validity site (for our
 *   v1 roster the two coincide — a `produces = false` engine makes the canonical guard
 *   unimplementable, so `produce` errors exactly as `error` does; they diverge only on a future
 *   engine where a guard is implementable without a native form).
 * - `escalate` ⇒ the reject cluster's **container** is retargeted, as one unit, to a
 *   rejects-capable fallback engine, and `MovementSynthesizer` then wraps the whole container at
 *   its cross-engine boundary (not its individual nodes) + warning `TTRP-RJ-102`. If no capable
 *   engine exists in the world, `escalate` falls back to the same `TTRP-RJ-106` error.
 *
 * **Granularity note (RJ-P2):** the task's "T5-b node-escalation path" is not yet an executable
 * re-placement engine (`CapabilityChecker` is report-only; there is no sub-container placement).
 * Cross-engine movement works at CONTAINER granularity, so escalation moves the reject cluster's
 * whole container — a superset of the strict provenance cluster, but the smallest unit movement
 * can wrap. Recorded as a conscious contracts §5 clarification in the design log.
 *
 * **Known limitation (RJ-P5 review, C1 — deferred):** [selectFallback] chooses the first
 * rejects-capable engine WITHOUT verifying the fallback can host the container's OTHER nodes
 * (kinds/functions). A blind host-ability check cannot be done here: `CapabilityChecker` misses are
 * report-only and many are *lowerable*, so rejecting a fallback on a raw miss would wrongly refuse
 * legitimate escalations — the correct fix needs the T5-b node-replacement engine that decides
 * native?→rewrite?→re-place. Unreachable in the shipped world (every data engine has
 * `produces = true`, so escalation only fires for a rejects cluster mistakenly targeting a
 * non-data engine like `bash`); tracked for when sub-container placement lands.
 */
object RejectEscalation {
    data class Result(
        val graph: TtrpGraph,
        val diagnostics: List<TtrpDiagnostic>,
    )

    fun resolve(
        graph: TtrpGraph,
        bound: BoundWorld,
        knob: org.tatrman.ttrp.project.RejectsInSql,
        misses: List<RejectClusterMiss>,
    ): Result {
        if (misses.isEmpty()) return Result(graph, emptyList())
        return when (knob) {
            org.tatrman.ttrp.project.RejectsInSql.PRODUCE,
            org.tatrman.ttrp.project.RejectsInSql.ERROR,
            -> Result(graph, misses.map { cannotProduce(it) })
            org.tatrman.ttrp.project.RejectsInSql.ESCALATE -> escalate(graph, bound, misses)
        }
    }

    private fun escalate(
        graph: TtrpGraph,
        bound: BoundWorld,
        misses: List<RejectClusterMiss>,
    ): Result {
        val diags = mutableListOf<TtrpDiagnostic>()
        var g = graph
        // Group by the container owning each missed authored node — one retarget per cluster's container.
        val byContainer = misses.groupBy { g.containerOf(it.authoredNodeId)?.id }
        for ((containerId, clusterMisses) in byContainer) {
            val container = containerId?.let { g.containers[it] }
            if (container == null) {
                diags += clusterMisses.map { cannotProduce(it) }
                continue
            }
            val fallback = selectFallback(bound, container.target)
            if (fallback == null) {
                diags += clusterMisses.map { cannotProduce(it) }
                continue
            }
            val from = container.target
            g = retarget(g, container.id, fallback)
            diags +=
                TtrpDiagnostic(
                    TtrpDiagnosticId.RJ_102,
                    Severity.WARNING,
                    "rejects cluster `${container.label}` escalated off `$from` to `$fallback` " +
                        "(engine cannot produce rejects; knob=escalate)",
                    container.location,
                )
        }
        return Result(g, diags)
    }

    /** The first world engine (insertion order) that can produce rejects and is not the current target. */
    fun selectFallback(
        bound: BoundWorld,
        currentTarget: String,
    ): String? =
        bound.engines.entries
            .firstOrNull { (name, be) -> name != currentTarget && be.manifest.rejectsSupport().produces }
            ?.key

    /** Retarget a container (and its flat-node mirror) to [fallbackTarget]; members move with it. */
    private fun retarget(
        g: TtrpGraph,
        containerId: String,
        fallbackTarget: String,
    ): TtrpGraph {
        val container = g.containers[containerId] ?: return g
        val updated = container.copy(target = fallbackTarget)
        val containers = LinkedHashMap(g.containers).apply { put(containerId, updated) }
        val nodes = LinkedHashMap<String, Node>(g.nodes).apply { put(containerId, updated) }
        return g.copy(nodes = nodes, containers = containers)
    }

    private fun cannotProduce(m: RejectClusterMiss): TtrpDiagnostic =
        TtrpDiagnostic(
            TtrpDiagnosticId.RJ_106,
            Severity.ERROR,
            "engine `${m.engine}` cannot produce rejects for `${m.function} ${m.typePair}`",
            org.tatrman.ttrp.ast.SourceLocation.UNKNOWN,
        )
}
