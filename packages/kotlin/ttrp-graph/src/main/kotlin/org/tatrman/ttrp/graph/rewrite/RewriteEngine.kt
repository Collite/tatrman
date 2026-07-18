// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rewrite

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.graph.capability.BoundEngine
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.capability.CapabilityChecker
import org.tatrman.ttrp.graph.capability.CapabilityMiss
import org.tatrman.ttrp.graph.model.Edge
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.SugarNode
import org.tatrman.ttrp.graph.model.TtrpGraph

/** The fixed strata order (notes-t8-termination.md §1). Declaration order IS the run order. */
enum class Stratum { SUGAR, FUNCTION_LOWERING, NODE_LOWERING, FISSION, REPLACEMENT, MOVEMENT }

/** One recorded rewrite (fed to `ttrp explain` §S4 and doubling as a T6-e info diagnostic). */
data class AppliedRewrite(
    val rule: String,
    val stratum: Stratum,
    val before: String,
    val after: String,
    val engine: String?,
    val location: SourceLocation,
    val reason: String,
)

sealed interface RewriteResult {
    /** Same reference ⇒ idempotency signal (EXAMPLES.md §7d). */
    data object Unchanged : RewriteResult

    data class Replaced(
        val graph: TtrpGraph,
        val applied: AppliedRewrite,
    ) : RewriteResult
}

/** Context a rule needs: the bound world (engine per container) + capability lookup. */
class RewriteContext(
    val bound: BoundWorld,
) {
    fun engineOf(
        nodeId: String,
        graph: TtrpGraph,
    ): BoundEngine? {
        val container = graph.containerOf(nodeId) ?: return null
        return bound.engines[container.target]
    }
}

interface RewriteRule {
    val name: String
    val stratum: Stratum

    fun apply(
        node: Node,
        graph: TtrpGraph,
        ctx: RewriteContext,
    ): RewriteResult
}

data class NormalizeResult(
    val graph: TtrpGraph,
    val log: List<AppliedRewrite>,
    val iterations: Int,
    /**
     * Rewrite-time diagnostics (RJ-P1): the reject-elaboration stratum surfaces authoring
     * warnings/errors here — dead wire `TTRP-RJ-101`, forced escalation `TTRP-RJ-102`, and the
     * both-sides ON pair-schema fallback `TTRP-RJ-105`. Empty for every graph with no wired
     * rejects, so the fail-fast path is unaffected.
     */
    val diagnostics: List<org.tatrman.ttrp.diagnostics.TtrpDiagnostic> = emptyList(),
)

/**
 * The T8 normalizer (notes-t8-termination.md): stratified fixpoint over a strictly-
 * decreasing lexicographic measure. Each stratum runs to local fixpoint (lowest node-id
 * applicable rule first, deterministic) before the next; every applied rewrite must
 * strictly decrease the measure or the engine hard-fails (internal error). Insertion-
 * ordered throughout ⇒ same input ⇒ byte-identical output + log.
 */
class RewriteEngine(
    private val rules: List<RewriteRule>,
    private val bound: BoundWorld,
) {
    private val ctx = RewriteContext(bound)

    fun normalize(graph: TtrpGraph): NormalizeResult {
        var g = graph
        val log = mutableListOf<AppliedRewrite>()
        var iterations = 0
        val guard = measureBound(graph) + 1
        for (stratum in Stratum.entries) {
            val stratumRules = rules.filter { it.stratum == stratum }
            if (stratumRules.isEmpty()) continue
            var changed = true
            while (changed) {
                changed = false
                for (id in g.nodes.keys.toList()) {
                    val node = g.nodes[id] ?: continue
                    val applied = tryRules(node, g, stratumRules) ?: continue
                    val before = measure(g)
                    val after = measure(applied.graph)
                    check(lexLess(after, before)) {
                        "T8 internal error: rule `${applied.applied.rule}` did not strictly decrease the measure " +
                            "(${before.toList()} -> ${after.toList()})"
                    }
                    g = applied.graph
                    log += applied.applied
                    iterations++
                    check(
                        iterations <= guard,
                    ) { "T8 internal error: exceeded iteration bound at `${applied.applied.rule}`" }
                    changed = true
                    break
                }
            }
        }
        return NormalizeResult(g, log, iterations)
    }

    private fun tryRules(
        node: Node,
        g: TtrpGraph,
        stratumRules: List<RewriteRule>,
    ): RewriteResult.Replaced? {
        for (rule in stratumRules) {
            when (val r = rule.apply(node, g, ctx)) {
                is RewriteResult.Replaced -> return r
                RewriteResult.Unchanged -> Unit
            }
        }
        return null
    }

    // ---- the measure (notes-t8-termination.md §2) ----

    fun measure(g: TtrpGraph): IntArray {
        val misses = CapabilityChecker(bound).check(g)
        return intArrayOf(
            g.nodes.values.count { it is SugarNode || hasHaving(it) },
            misses.count { it is CapabilityMiss.FunctionMiss },
            misses.count { it is CapabilityMiss.NodeMiss },
            crossEngineEdges(g),
        )
    }

    fun measureBound(g: TtrpGraph): Int = measure(g).sum()

    private fun hasHaving(n: Node): Boolean = n is org.tatrman.ttrp.graph.model.Aggregate && n.having != null

    private fun crossEngineEdges(g: TtrpGraph): Int =
        g.edges.count { e ->
            if (e.kind != EdgeKind.DATA) return@count false
            val a = (g.containers[e.from.nodeId] ?: g.containerOf(e.from.nodeId))?.target
            val b = (g.containers[e.to.nodeId] ?: g.containerOf(e.to.nodeId))?.target
            a != null &&
                b != null &&
                a != b &&
                bound.engines[a]?.manifest?.type != bound.engines[b]?.manifest?.type
        }

    companion object {
        fun lexLess(
            a: IntArray,
            b: IntArray,
        ): Boolean {
            for (i in a.indices) {
                if (a[i] != b[i]) return a[i] < b[i]
            }
            return false
        }
    }
}

/**
 * Container-aware graph edits used by rewrite rules. All return a NEW [TtrpGraph]
 * (insertion order preserved); the container that owned a replaced node keeps its
 * membership updated so capability re-checks stay correct.
 */
object GraphOps {
    /** Replace node [id] in place with [newNode] (same id ⇒ edges + membership intact). */
    fun swapNode(
        g: TtrpGraph,
        id: String,
        newNode: Node,
    ): TtrpGraph {
        val nodes = LinkedHashMap(g.nodes)
        nodes[id] = newNode
        return g.copy(nodes = nodes)
    }

    /** Add [node] to the graph as a member of [containerId] (or program-level if null). */
    fun addNode(
        g: TtrpGraph,
        node: Node,
        containerId: String?,
    ): TtrpGraph {
        val nodes = LinkedHashMap(g.nodes)
        nodes[node.id] = node
        var containers = g.containers
        if (containerId != null) {
            val c = g.containers[containerId]
            if (c != null) {
                val updated = c.copy(memberIds = c.memberIds + node.id)
                containers = LinkedHashMap(g.containers).apply { put(containerId, updated) }
                nodes[containerId] = updated
            }
        }
        return g.copy(nodes = nodes, containers = containers)
    }

    fun removeNode(
        g: TtrpGraph,
        id: String,
    ): TtrpGraph {
        val nodes = LinkedHashMap(g.nodes).apply { remove(id) }
        val edges = g.edges.filterNot { it.from.nodeId == id || it.to.nodeId == id }
        val container = g.containerOf(id)
        var containers = g.containers
        if (container != null) {
            val updated = container.copy(memberIds = container.memberIds - id)
            containers = LinkedHashMap(g.containers).apply { put(container.id, updated) }
            nodes[container.id] = updated
        }
        return g.copy(nodes = nodes, edges = edges, containers = containers)
    }

    fun addEdges(
        g: TtrpGraph,
        newEdges: List<Edge>,
    ): TtrpGraph = g.copy(edges = g.edges + newEdges)

    /** Redirect every edge whose source is [from] to originate from [to] instead. */
    fun redirectFrom(
        g: TtrpGraph,
        from: PortRef,
        to: PortRef,
    ): TtrpGraph = g.copy(edges = g.edges.map { if (it.from == from) it.copy(from = to) else it })

    fun edgesInto(
        g: TtrpGraph,
        ref: PortRef,
    ): List<Edge> = g.edges.filter { it.to == ref }

    /** The container id for [nodeId] — itself if it IS a container, else its owner. */
    fun containerIdOf(
        g: TtrpGraph,
        nodeId: String,
    ): String? = g.containers[nodeId]?.id ?: g.containerOf(nodeId)?.id
}
