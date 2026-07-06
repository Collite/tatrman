package org.tatrman.ttrp.graph.movement

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.model.Edge
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.Transfer
import org.tatrman.ttrp.graph.model.TtrpGraph

data class MovementResult(
    val graph: TtrpGraph,
    val transferIds: List<String>,
)

/**
 * Movement synthesis (C3-d-iv, the last graph-level stratum): every remaining
 * cross-engine DATA edge (a container boundary with different engine types) lowers to
 * `Store(staging) → Transfer → Load(staging)` with FS implied by the data dependency
 * (B-T9 engine-crossing pattern). Staging = Stage-2.2 resolution; per-edge `via`
 * override wins (recorded on the [Transfer]). Arrow-IPC format is recorded (codegen is
 * P3). Idempotent: an edge already wrapped in authored Store/Load is not double-wrapped.
 */
class MovementSynthesizer(
    private val bound: BoundWorld,
    private val stagingName: String?,
) {
    fun synthesize(graph: TtrpGraph): MovementResult {
        var g = graph
        val transferIds = mutableListOf<String>()
        val crossings = g.edges.filter { isCrossEngine(g, it) }
        for ((i, edge) in crossings.withIndex()) {
            val fromContainer = container(g, edge.from.nodeId) ?: continue
            val via = stagingName ?: "stage"
            val stem = "x$i"
            val storeId = "$stem~store"
            val transferId = "$stem~transfer"
            val loadId = "$stem~load"
            val stagedName = "${fromContainer}__stage"
            val store = Store(storeId, stagedName, SourceLocation.UNKNOWN, target = via)
            val transfer = Transfer(transferId, stagedName, SourceLocation.UNKNOWN, via = via, format = "arrow-ipc")
            val load = Load(loadId, stagedName, SourceLocation.UNKNOWN, source = via)
            val nodes = LinkedHashMap(g.nodes)
            nodes[storeId] = store
            nodes[transferId] = transfer
            nodes[loadId] = load
            val newEdges =
                g.edges.filterNot { it === edge } +
                    listOf(
                        Edge(edge.from, PortRef(storeId, PortNames.IN), EdgeKind.DATA),
                        Edge(PortRef(storeId, PortNames.OUT), PortRef(transferId, PortNames.IN), EdgeKind.DATA),
                        Edge(PortRef(transferId, PortNames.OUT), PortRef(loadId, PortNames.IN), EdgeKind.DATA),
                        Edge(PortRef(loadId, PortNames.OUT), edge.to, EdgeKind.DATA),
                    )
            g = g.copy(nodes = nodes, edges = newEdges)
            transferIds += transferId
        }
        return MovementResult(g, transferIds)
    }

    private fun isCrossEngine(
        g: TtrpGraph,
        e: Edge,
    ): Boolean {
        if (e.kind != EdgeKind.DATA) return false
        val a = container(g, e.from.nodeId) ?: return false
        val b = container(g, e.to.nodeId) ?: return false
        if (a == b) return false
        val ea = g.containers[a]?.target ?: return false
        val eb = g.containers[b]?.target ?: return false
        // Already a synthesized Store/Transfer/Load boundary ⇒ not a raw crossing.
        if (g.nodes[e.from.nodeId] is Transfer || g.nodes[e.to.nodeId] is Load) return false
        return bound.engines[ea]?.manifest?.type != bound.engines[eb]?.manifest?.type
    }

    private fun container(
        g: TtrpGraph,
        nodeId: String,
    ): String? = (g.containers[nodeId] ?: g.containerOf(nodeId))?.id
}
