// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.validate

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.graph.model.Display
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.PortDirection
import org.tatrman.ttrp.graph.model.PortKind
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * Post-build structural validation (B-T2): the v1 graph is acyclic (`TTRP-CTL-002`),
 * every data in-port takes exactly one edge (no implicit union, `TTRP-CTL-003`), and
 * `Display` is a sink-only leaf (`TTRP-CTL-005`). Messages are document-deterministic
 * (P2). Reserved-port-name (`TTRP-CTL-006`), FF (`TTRP-CTL-001`) and cross-container
 * `err` (`TTRP-CTL-004`) are emitted at build time (GraphBuilder), not here.
 */
class StructureValidator {
    fun validate(graph: TtrpGraph): List<TtrpDiagnostic> {
        val out = mutableListOf<TtrpDiagnostic>()
        checkAcyclic(graph, out)
        checkSingleIn(graph, out)
        checkDisplaySinks(graph, out)
        return out
    }

    private fun checkAcyclic(
        graph: TtrpGraph,
        out: MutableList<TtrpDiagnostic>,
    ) {
        val adj = LinkedHashMap<String, MutableList<String>>()
        for (id in graph.nodes.keys) adj[id] = mutableListOf()
        for (e in graph.edges) {
            if (e.from.nodeId in adj && e.to.nodeId in graph.nodes) adj[e.from.nodeId]!!.add(e.to.nodeId)
        }
        val state = HashMap<String, Int>() // 0=unvisited,1=in-stack,2=done
        val stack = ArrayDeque<String>()
        var cycle: List<String>? = null

        fun dfs(u: String): Boolean {
            state[u] = 1
            stack.addLast(u)
            for (v in adj[u] ?: emptyList()) {
                if (cycle != null) break
                when (state[v] ?: 0) {
                    0 -> if (dfs(v)) return true
                    1 -> {
                        val idx = stack.indexOf(v)
                        cycle = stack.toList().subList(idx, stack.size)
                        return true
                    }
                }
            }
            stack.removeLast()
            state[u] = 2
            return false
        }

        for (id in graph.nodes.keys) {
            if ((state[id] ?: 0) == 0 && dfs(id)) break
        }
        cycle?.let { c ->
            val labels = c.mapNotNull { graph.nodes[it]?.label }.joinToString(" → ")
            val loc = c.firstNotNullOfOrNull { graph.nodes[it]?.location } ?: SourceLocation.UNKNOWN
            out += diag(TtrpDiagnosticId.CTL_002, "cycle among nodes: $labels", loc)
        }
    }

    private fun checkSingleIn(
        graph: TtrpGraph,
        out: MutableList<TtrpDiagnostic>,
    ) {
        // Count incoming DATA edges per (nodeId, port).
        val counts = LinkedHashMap<Pair<String, String>, Int>()
        for (e in graph.edges) {
            if (e.kind != EdgeKind.DATA) continue
            val key = e.to.nodeId to e.to.port
            counts[key] = (counts[key] ?: 0) + 1
        }
        for ((key, n) in counts) {
            if (n <= 1) continue
            val (nodeId, portName) = key
            val node = graph.nodes[nodeId] ?: continue
            val isDataIn =
                node.ports().any { it.name == portName && it.kind == PortKind.DATA && it.direction == PortDirection.IN }
            if (isDataIn) {
                out +=
                    diag(
                        TtrpDiagnosticId.CTL_003,
                        "data in-port `${node.label}.$portName` has $n incoming edges",
                        node.location,
                    )
            }
        }
    }

    private fun checkDisplaySinks(
        graph: TtrpGraph,
        out: MutableList<TtrpDiagnostic>,
    ) {
        for (node in graph.nodes.values) {
            if (node !is Display) continue
            if (graph.edgesFrom(node.id).any { it.kind == EdgeKind.DATA }) {
                out +=
                    diag(TtrpDiagnosticId.CTL_005, "`display` node `${node.label}` is used as a source", node.location)
            }
        }
    }

    private fun diag(
        id: TtrpDiagnosticId,
        message: String,
        loc: SourceLocation,
    ) = TtrpDiagnostic(id, Severity.ERROR, message, loc)
}
