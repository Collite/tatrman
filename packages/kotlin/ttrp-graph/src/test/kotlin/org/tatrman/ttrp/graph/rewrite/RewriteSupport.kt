// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rewrite

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.capability.ClasspathManifestSource
import org.tatrman.ttrp.graph.capability.WorldBinder
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.Distinct
import org.tatrman.ttrp.graph.model.Edge
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Limit
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Port
import org.tatrman.ttrp.graph.model.PortDirection
import org.tatrman.ttrp.graph.model.PortKind
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.Select
import org.tatrman.ttrp.graph.model.Sort
import org.tatrman.ttrp.graph.model.TtrpGraph

private val L = SourceLocation.UNKNOWN

/** Shared Stage-2.3 test scaffolding: a real BoundWorld + programmatic chain graphs. */
object RewriteSupport {
    val bound: BoundWorld by lazy {
        val world = GraphFixtures.report(GraphFixtures.program("hero.ttrp")).world!!
        WorldBinder(ClasspathManifestSource()).bind(world)
    }

    fun engine(): RewriteEngine = RewriteEngine(Rules.ALL, bound)

    /**
     * An engine that runs the reject-elaboration stratum but NOT the sugar lowering nor
     * `branch->filter`, so the un-lowered contracts-§5 shape (guard **Calc** with named
     * `_ttrp_v*` columns, a surviving `Branch`, reject **Calc**) is observable by the RJ-P1
     * shape specs. Excluding these existing rules keeps this compiling on pre-1.3 master (where
     * it is a no-op ⇒ the specs are red for the right reason); once the elaboration rules join
     * `Rules.ALL` in stage 1.3 it yields the raw elaborated cluster. Branch is non-native on both
     * v1 engines, so without this exclusion the synthesized branch would always be lowered away.
     */
    fun elaborationEngine(): RewriteEngine =
        RewriteEngine(Rules.ALL.filterNot { it.stratum == Stratum.SUGAR || it === Rules.BranchToFilters }, bound)

    /** A single-container linear chain: Load then one node per kind, wired m_i.out → m_{i+1}.in. */
    fun chain(
        target: String,
        kinds: List<String>,
    ): TtrpGraph {
        val members = mutableListOf<Node>()
        members += Load("m0", "m0#1", L, source = "files.x")
        kinds.forEachIndexed { i, k ->
            val id = "m${i + 1}"
            members +=
                when (k) {
                    "filter" -> Filter(id, "$id#1", L, predicate = null)
                    "select" -> Select(id, "$id#1", L)
                    "calc" -> Calc(id, "$id#1", L)
                    "distinct" -> Distinct(id, "$id#1", L)
                    "sort" -> Sort(id, "$id#1", L)
                    "limit" -> Limit(id, "$id#1", L)
                    else -> Project(id, "$id#1", L)
                }
        }
        val edges =
            members.zipWithNext().map { (a, b) ->
                Edge(PortRef(a.id, PortNames.OUT), PortRef(b.id, PortNames.IN), EdgeKind.DATA)
            }
        val container =
            Container(
                id = "c0",
                label = "c",
                location = L,
                target = target,
                memberIds = members.map { it.id },
                declaredPorts = listOf(Port("o", PortKind.DATA, PortDirection.OUT)),
                portMapping = linkedMapOf("o" to PortRef(members.last().id, PortNames.OUT)),
            )
        val nodes = LinkedHashMap<String, Node>()
        members.forEach { nodes[it.id] = it }
        nodes["c0"] = container
        return TtrpGraph(nodes, edges, linkedMapOf("c0" to container))
    }

    /** Structural signature for determinism comparison: node kinds by id + edge endpoints. */
    fun signature(g: TtrpGraph): String {
        val nodeSig = g.nodes.entries.joinToString(";") { "${it.key}=${it.value::class.simpleName}" }
        val edgeSig = g.edges.joinToString(";") { "${it.from.nodeId}.${it.from.port}->${it.to.nodeId}.${it.to.port}" }
        return "$nodeSig|$edgeSig"
    }
}
