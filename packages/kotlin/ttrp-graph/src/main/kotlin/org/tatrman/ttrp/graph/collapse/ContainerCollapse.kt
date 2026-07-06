package org.tatrman.ttrp.graph.collapse

import org.tatrman.ttrp.graph.capability.InvocationResult
import org.tatrman.ttrp.graph.model.Display
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.Transfer
import org.tatrman.ttrp.graph.model.TtrpGraph

/** An island (collapsed container) in the derived execution graph (B-T6 derived-only). */
data class Island(
    val id: String,
    val name: String,
    val engine: String,
    val invocation: String?,
    val memberIds: List<String>,
)

/** A movement step in the execution graph (contracts §5 `transfers/`). */
data class TransferStep(
    val id: String,
    val fromIsland: String?,
    val toIsland: String?,
    val via: String?,
    val format: String,
)

/**
 * The derived execution/orchestration graph (B-T6): islands + transfers + waves.
 * `waves[n]` = execution nodes whose predecessors all sit in earlier waves (F-a β);
 * SS pairs co-launch (forced into the same wave). Cross-container data ⇒ FS.
 */
data class ExecutionGraph(
    val islands: List<Island>,
    val transfers: List<TransferStep>,
    val displays: List<String>,
    val stores: List<String>,
    val waves: List<List<String>>,
)

class ContainerCollapse(
    private val invocations: InvocationResult,
) {
    fun collapse(graph: TtrpGraph): ExecutionGraph {
        val islands =
            graph.containers.values.map { c ->
                Island(c.id, c.label, c.target, invocations.byContainer[c.id]?.invocation?.delivery, c.memberIds)
            }
        val transfers =
            graph.nodes.values.filterIsInstance<Transfer>().map { t ->
                TransferStep(t.id, fromIsland(graph, t.id), toIsland(graph, t.id), t.via, t.format)
            }
        val displays =
            graph.nodes.values
                .filterIsInstance<Display>()
                .map { it.name }
                .sorted()
        val stores =
            graph.nodes.values
                .filterIsInstance<Store>()
                .filter { !it.id.contains("~store") }
                .map { it.target }
                .sorted()

        // Execution-graph adjacency (B-T6 derived-only): islands + transfers as nodes.
        // Each transfer sits between its from/to islands (island → transfer → island).
        // Same-engine cross-container data edges and explicit control edges collapse to
        // island→island; SS pairs co-launch.
        val execNodes = islands.map { it.id } + transfers.map { it.id }
        val adj = LinkedHashMap<String, MutableSet<String>>()
        val ssPairs = mutableListOf<Pair<String, String>>()
        execNodes.forEach { adj[it] = linkedSetOf() }
        for (t in transfers) {
            if (t.fromIsland != null) adj[t.fromIsland]?.add(t.id)
            if (t.toIsland != null) adj[t.id]?.add(t.toIsland)
        }
        for (e in graph.edges) {
            val fromC = containerIdOf(graph, e.from.nodeId) ?: continue
            val toC = containerIdOf(graph, e.to.nodeId) ?: continue
            if (fromC == toC) continue
            when (e.kind) {
                EdgeKind.CONTROL_FS -> adj[fromC]?.add(toC)
                EdgeKind.CONTROL_SS -> ssPairs += fromC to toC
                EdgeKind.DATA -> adj[fromC]?.add(toC) // same-engine crossing ⇒ FS (no transfer)
            }
        }
        val waves = computeWaves(execNodes, adj, ssPairs)
        return ExecutionGraph(islands, transfers, displays, stores, waves)
    }

    /** The container id for [nodeId] — itself if it IS a container, else its owner. */
    private fun containerIdOf(
        graph: TtrpGraph,
        nodeId: String,
    ): String? = graph.containers[nodeId]?.id ?: graph.containerOf(nodeId)?.id

    private fun fromIsland(
        graph: TtrpGraph,
        transferId: String,
    ): String? {
        val storeId =
            graph.edges
                .firstOrNull { it.to.nodeId == transferId }
                ?.from
                ?.nodeId ?: return null
        val src =
            graph.edges
                .firstOrNull { it.to.nodeId == storeId }
                ?.from
                ?.nodeId ?: return null
        return (graph.containers[src] ?: graph.containerOf(src))?.id
    }

    private fun toIsland(
        graph: TtrpGraph,
        transferId: String,
    ): String? {
        val loadId =
            graph.edges
                .firstOrNull { it.from.nodeId == transferId }
                ?.to
                ?.nodeId ?: return null
        val dst =
            graph.edges
                .firstOrNull { it.from.nodeId == loadId }
                ?.to
                ?.nodeId ?: return null
        return (graph.containers[dst] ?: graph.containerOf(dst))?.id
    }

    /**
     * Topological levels (F-a β longest-path); SS pairs forced into the same wave (max of their
     * natural waves). The SS co-launch raises the lower endpoint, so it must be re-propagated to
     * FS/data dependents — otherwise a node `after` a raised SS island could land in an earlier
     * wave than its predecessor. We therefore relax edges + apply SS co-launch to a fixpoint;
     * levels only ever increase and are bounded by the node count, so this terminates. The graph
     * is acyclic (CTL-002), so the fixpoint is the exact longest-path assignment.
     */
    private fun computeWaves(
        nodes: List<String>,
        adj: Map<String, Set<String>>,
        ssPairs: List<Pair<String, String>>,
    ): List<List<String>> {
        val level = LinkedHashMap<String, Int>()
        nodes.forEach { level[it] = 0 }
        var changed = true
        var guard = nodes.size + 1
        while (changed && guard-- > 0) {
            changed = false
            for ((u, tos) in adj) {
                for (v in tos) {
                    val cand = (level[u] ?: 0) + 1
                    if (cand > (level[v] ?: 0)) {
                        level[v] = cand
                        changed = true
                    }
                }
            }
            // SS co-launch: raise the lower endpoint to the higher (positive co-start).
            for ((a, b) in ssPairs) {
                val m = maxOf(level[a] ?: 0, level[b] ?: 0)
                if ((level[a] ?: 0) != m) {
                    level[a] = m
                    changed = true
                }
                if ((level[b] ?: 0) != m) {
                    level[b] = m
                    changed = true
                }
            }
        }
        val maxLevel = level.values.maxOrNull() ?: -1
        return (0..maxLevel)
            .map { lvl ->
                nodes.filter { (level[it] ?: 0) == lvl }.sorted()
            }.filter { it.isNotEmpty() }
    }
}
