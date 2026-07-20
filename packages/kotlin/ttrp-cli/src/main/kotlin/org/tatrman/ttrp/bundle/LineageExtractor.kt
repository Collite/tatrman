// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.graph.collapse.Island
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * Compile-derived STATIC column lineage (contracts §6, CQ-5) — the `lineage` section of the v2
 * manifest. This first cut walks [Aggregate] nodes: each `name = <agg>(col)` aggregation yields one
 * lineage column tagged `aggregate:<FN>`, its output attributed to the island that hosts the node and
 * its inputs the aggregated columns. Input qnames resolve to the island's `load(...)` source (the
 * common single-source case); richer transform tags (`join-key`, `filter-only`, multi-hop `identity`)
 * extend this as the graph exposes column-flow — the vocabulary is already pinned (§6).
 */
object LineageExtractor {
    fun extract(
        graph: TtrpGraph,
        islands: List<Island>,
    ): org.tatrman.ttrp.bundle.Lineage {
        val nodeToIsland = HashMap<String, String>()
        val islandLoadSource = HashMap<String, String>()
        val islandStoreTarget = HashMap<String, String>()
        for (island in islands) {
            island.memberIds.forEach { nodeToIsland[it] = island.name }
            // A single load(...) in the island is the source object for its columns (hero case).
            island.memberIds
                .mapNotNull { graph.nodes[it] as? Load }
                .firstOrNull()
                ?.let { islandLoadSource[island.name] = it.source }
            // Where the island's output MATERIALIZES: a program-level `store(...)` wired from one of the
            // island's DATA out-ports (the canonical `crunch.low -> store(files.low_regions)` form). Stores
            // are program-level leaves, NOT container members — the old membership lookup found nothing for
            // this shape, so `materialized` was silently null on every real bundle. Error out-ports
            // (err/rejects) are skipped (a rejects sink is not the column's materialization); the first
            // remaining store (edges are insertion-ordered — deterministic) wins.
            island.storeTarget(graph)?.let { islandStoreTarget[island.name] = it }
        }

        val columns = mutableListOf<LineageColumn>()
        for (node in graph.nodes.values) {
            if (node !is Aggregate) continue
            val island = nodeToIsland[node.id] ?: continue
            val sourceQname = islandLoadSource[island] ?: ""
            for (agg in node.aggregations) {
                val call = agg.value as? AggregateCall ?: continue
                val fn = call.function.name.uppercase()
                val inputs =
                    call.args
                        .filterIsInstance<ColumnRef>()
                        .map { LineageInput(qname = sourceQname, column = it.column) }
                if (inputs.isEmpty()) continue
                columns +=
                    LineageColumn(
                        output =
                            LineageOutput(
                                island = island,
                                relation = "out",
                                column = agg.name,
                                materialized = islandStoreTarget[island],
                            ),
                        inputs = inputs,
                        transform = "aggregate:$fn",
                    )
            }
        }
        // Deterministic: sort by (output column, transform).
        columns.sortWith(compareBy({ it.output.column }, { it.transform }))
        return Lineage(version = 1, columns = columns)
    }

    private val ERROR_PORTS = setOf(PortNames.ERR, PortNames.REJECTS)

    /** The target of the first program-level [Store] wired from a DATA out-port of this island, or null. */
    private fun Island.storeTarget(graph: TtrpGraph): String? =
        graph.edges
            .asSequence()
            .filter { e -> islandIdOf(graph, e.from.nodeId) == id && e.from.port !in ERROR_PORTS }
            .mapNotNull { e -> graph.nodes[e.to.nodeId] as? Store }
            .firstOrNull()
            ?.target

    /** The island/container id an edge endpoint belongs to — the container itself, or the owner of a member. */
    private fun islandIdOf(
        graph: TtrpGraph,
        nodeId: String,
    ): String? = graph.containers[nodeId]?.id ?: graph.containerOf(nodeId)?.id
}
