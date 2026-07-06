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

    /** Topological levels; SS pairs forced into the same wave (max of their natural waves). */
    private fun computeWaves(
        nodes: List<String>,
        adj: Map<String, Set<String>>,
        ssPairs: List<Pair<String, String>>,
    ): List<List<String>> {
        val indeg = LinkedHashMap<String, Int>()
        nodes.forEach { indeg[it] = 0 }
        for ((_, tos) in adj) for (t in tos) indeg[t] = (indeg[t] ?: 0) + 1
        val level = LinkedHashMap<String, Int>()
        val queue = ArrayDeque(nodes.filter { indeg[it] == 0 })
        queue.forEach { level[it] = 0 }
        val work = LinkedHashMap(indeg)
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            for (v in adj[u] ?: emptySet()) {
                level[v] = maxOf(level[v] ?: 0, (level[u] ?: 0) + 1)
                work[v] = (work[v] ?: 0) - 1
                if (work[v] == 0) queue.addLast(v)
            }
        }
        // SS co-launch: raise the lower endpoint to the higher (positive co-start).
        for ((a, b) in ssPairs) {
            val m = maxOf(level[a] ?: 0, level[b] ?: 0)
            level[a] = m
            level[b] = m
        }
        val maxLevel = level.values.maxOrNull() ?: -1
        return (0..maxLevel)
            .map { lvl ->
                nodes.filter { (level[it] ?: 0) == lvl }.sorted()
            }.filter { it.isNotEmpty() }
    }
}
