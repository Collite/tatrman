package org.tatrman.ttrp.lsp.viewstate

import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * Projects the authored graph into per-canvas ζ-space node sets + data edges — the input
 * [AutoLayout] ranks. The `program` canvas is the orchestration level (containers + program
 * leaves + cross-container edges); each container has a drill-in canvas (its members +
 * internal edges). Control edges are excluded from layout ranking (C1-b / AutoLayout §1).
 */
object CanvasGraphs {
    data class CanvasGraph(
        val nodes: List<String>,
        val edges: List<Pair<String, String>>,
    )

    fun perCanvas(graph: TtrpGraph): Map<String, CanvasGraph> {
        val canvasNodes = ZetaKeys.canvasKeys(graph)
        val memberIds =
            graph.containers.values
                .flatMap { it.memberIds }
                .toSet()
        val out = LinkedHashMap<String, CanvasGraph>()

        // Program canvas: edges whose endpoints are both program-level (containers / leaves).
        val programEdges =
            graph.edges
                .filter { it.kind == EdgeKind.DATA && it.from.nodeId !in memberIds && it.to.nodeId !in memberIds }
                .map { ZetaKeys.of(graph, it.from.nodeId) to ZetaKeys.of(graph, it.to.nodeId) }
        out[ZetaKeys.PROGRAM_CANVAS] =
            CanvasGraph(canvasNodes[ZetaKeys.PROGRAM_CANVAS]?.keys?.toList().orEmpty(), programEdges)

        // One canvas per container: its members + internal data edges.
        for (c in graph.containers.values) {
            val members = c.memberIds.toSet()
            val internalEdges =
                graph.edges
                    .filter { it.kind == EdgeKind.DATA && it.from.nodeId in members && it.to.nodeId in members }
                    .map { ZetaKeys.of(graph, it.from.nodeId) to ZetaKeys.of(graph, it.to.nodeId) }
            out[c.label] = CanvasGraph(canvasNodes[c.label]?.keys?.toList().orEmpty(), internalEdges)
        }
        return out
    }

    /** Deterministic abstract auto-layout coordinates per canvas (the `autoLayout` contract field, C1-b). */
    fun autoLayouts(graph: TtrpGraph): Map<String, Map<String, AbstractCoord>> =
        perCanvas(graph).mapValues { (_, cg) -> AutoLayout.layout(cg.nodes, cg.edges) }
}
