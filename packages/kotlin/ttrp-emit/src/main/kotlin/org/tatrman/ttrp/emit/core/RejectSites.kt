// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.core

import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * One elaborated reject site's structural anchors in the graph, as node ids (RJ-P5). Both emit
 * paths — Polars ([org.tatrman.ttrp.emit.polars.PolarsGraphEmitter.partitions]) and PG
 * ([org.tatrman.ttrp.emit.sql.SqlIslandEmitter.countQueries]) — resolve the same three partition
 * frames from these anchors, so the `counts.json` triples they emit agree by construction (the
 * cross-engine agreement the eighth conform point checks).
 *
 *  - [rejectsNodeId] — the reject producer (the `rejects` port's re-wired `.out`, RJ-P1);
 *  - [guardId]       — the guard Project (computes a `_ttrp_v*` validity flag);
 *  - [inFrom]        — the guard's input edge source (`in`): a member node, or the container itself
 *                      when the guard reads an IN port ([PortRef.nodeId] `== container.id`);
 *  - [cleanNodeId]   — the guard's **clean output** (`processed`): the branch-true child, the
 *                      authored op applied to the rows that passed the guard. Counted here — not at
 *                      the terminal OUT ports — so a downstream row-dropping op (the hero's
 *                      `join`+`aggregate`) cannot make `in == processed + rejects` diverge.
 */
data class RejectSitePartition(
    val site: String,
    val rejectsNodeId: String,
    val guardId: String,
    val inFrom: PortRef,
    val cleanNodeId: String,
)

/** Resolves a container's elaborated reject sites (RJ-P1 rewire + T8 branch-lowering) to [RejectSitePartition]s. */
object RejectSites {
    fun of(
        graph: TtrpGraph,
        container: Container,
    ): List<RejectSitePartition> {
        val members = container.memberIds.mapNotNull { graph.nodes[it] }
        return container.portMapping.values.mapNotNull { ref ->
            val authored = graph.synthProvenance[ref.nodeId] ?: return@mapNotNull null
            val guard =
                members.firstOrNull { n ->
                    n is Project && graph.synthProvenance[n.id] == authored && isGuard(n)
                } ?: return@mapNotNull null
            val inEdge =
                graph.edges.firstOrNull { it.kind == EdgeKind.DATA && it.to.nodeId == guard.id }
                    ?: return@mapNotNull null
            val clean = cleanOutputNode(graph, guard.id, ref.nodeId) ?: return@mapNotNull null
            RejectSitePartition(
                site = graph.nodes[authored]?.label?.substringBefore('#') ?: authored,
                rejectsNodeId = ref.nodeId,
                guardId = guard.id,
                inFrom = inEdge.from,
                cleanNodeId = clean,
            )
        }
    }

    /** A guard Project computes at least one `_ttrp_v*` validity flag. */
    private fun isGuard(n: Project): Boolean =
        n.columns.indices.any { (n.aliases.getOrNull(it) ?: "").startsWith("_ttrp_v") }

    /**
     * The guard's clean-output node: the child of the branch-true filter. The guard feeds two
     * branch filters (T8-lowered to `.filter(v)` / `.filter(~v)`); the one **not** on the path to
     * the reject producer [rejectNode] is the true (clean) filter, whose child is the processed frame.
     */
    private fun cleanOutputNode(
        graph: TtrpGraph,
        guardId: String,
        rejectNode: String,
    ): String? {
        val filtersFromGuard =
            graph.edges.filter { it.kind == EdgeKind.DATA && it.from.nodeId == guardId }.map { it.to.nodeId }
        val rejectFilter =
            graph.edges
                .firstOrNull { it.kind == EdgeKind.DATA && it.to.nodeId == rejectNode }
                ?.from
                ?.nodeId
        val cleanFilter = filtersFromGuard.firstOrNull { it != rejectFilter } ?: return null
        return graph.edges
            .firstOrNull { it.kind == EdgeKind.DATA && it.from.nodeId == cleanFilter }
            ?.to
            ?.nodeId
    }
}
