// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.polars

import org.tatrman.ttrp.emit.core.SsaNames
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.Display
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * Walks a normalized Polars container in the graph and produces the ordered [PolarsStep] list
 * [PolarsIslandEmitter] consumes. Container IN ports become staged Arrow reads (materialized by
 * the upstream transfer, F-c-i β); member Loads read their world storage (CSV via the declared
 * schema, D-c/T7); each container OUT port that maps to a member is written to its sink
 * (Display → `out/`, Store → `staging/`) after the mainline statements.
 *
 * **Rejects flow deferred:** an OUT port mapped to a node's `rejects` port (the C3-f erroneous-rows
 * output) is NOT emitted — the erroneous-rows *producer* semantics are an open v1.x design item
 * (plan.md cross-cutting register). Recorded in progress-phase-03.md.
 */
class PolarsGraphEmitter(
    private val graph: TtrpGraph,
    @Suppress("unused") private val world: BoundWorld,
) {
    /** True if the container OUT port [portName] maps to a member node's `rejects` port. */
    fun isRejectsPort(
        container: Container,
        portName: String,
    ): Boolean = container.portMapping[portName]?.port == "rejects"

    fun steps(container: Container): List<PolarsStep> {
        val members = container.memberIds.mapNotNull { graph.nodes[it] }
        val ordered = topoOrder(container, members)
        val names = SsaNames.assign(ordered)

        // Container IN ports → staged reads. Var name = the port name.
        val inPorts = container.declaredPorts.filter { it.direction.name == "IN" }.map { it.name }
        val portVar = inPorts.associateWith { it }

        val steps = mutableListOf<PolarsStep>()
        // 1. Staged reads for each IN port (fed by an upstream transfer).
        inPorts.forEach { port ->
            steps +=
                PolarsStep(varName = port, node = stagedReadNode(container, port), source = PolarsSource.Staged(port))
        }
        // 2. Mainline member statements.
        ordered.forEach { node ->
            steps +=
                PolarsStep(
                    varName = names.getValue(node.id),
                    node = node,
                    inputVars = inputVarsOf(node, container, names, portVar),
                    source = (node as? Load)?.let { loadSource(it) },
                )
        }
        // 3. Sinks for each mapped OUT port (rejects deferred).
        container.portMapping.forEach { (port, ref) ->
            if (ref.port == "rejects") return@forEach // deferred producer semantics
            val producer = graph.nodes[ref.nodeId] ?: return@forEach
            val producerVar = names[producer.id] ?: return@forEach
            sinkFor(container, port)?.let { steps += it.copy(inputVars = listOf(producerVar)) }
        }
        return steps
    }

    /** Order members by internal DATA edges (Kahn's); container-port inputs count as external. */
    private fun topoOrder(
        container: Container,
        members: List<Node>,
    ): List<Node> {
        val memberIds = members.map { it.id }.toSet()
        val incoming = members.associate { m -> m.id to mutableSetOf<String>() }
        graph.edges.filter { it.kind == EdgeKind.DATA }.forEach { e ->
            if (e.from.nodeId in memberIds && e.to.nodeId in memberIds) {
                incoming.getValue(e.to.nodeId).add(e.from.nodeId)
            }
        }
        val ready = ArrayDeque(members.filter { incoming.getValue(it.id).isEmpty() }.map { it.id })
        val out = mutableListOf<Node>()
        val remaining = incoming.mapValues { it.value.toMutableSet() }.toMutableMap()
        val byId = members.associateBy { it.id }
        while (ready.isNotEmpty()) {
            val id = ready.removeFirst()
            out += byId.getValue(id)
            remaining.forEach { (n, deps) ->
                if (deps.remove(id) && deps.isEmpty() && out.none { it.id == n } && ready.none { it == n }) {
                    ready.addLast(n)
                }
            }
        }
        // Any cycle-residue (shouldn't happen post-validate) appended deterministically.
        members.filter { m -> out.none { it.id == m.id } }.forEach { out += it }
        return out
    }

    private fun inputVarsOf(
        node: Node,
        container: Container,
        names: Map<String, String>,
        portVar: Map<String, String>,
    ): List<String> {
        // Data edges into this node, ordered by the node's declared in-port order (left before right).
        val portOrder = node.ports().filter { it.direction.name == "IN" }.map { it.name }
        val edges =
            graph.edges
                .filter { it.kind == EdgeKind.DATA && it.to.nodeId == node.id }
                .sortedBy { portOrder.indexOf(it.to.port).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        return edges.map { e ->
            when (e.from.nodeId) {
                container.id -> portVar.getValue(e.from.port) // fed by a container IN port
                else -> names[e.from.nodeId] ?: e.from.nodeId
            }
        }
    }

    private fun loadSource(load: Load): PolarsSource {
        // A member Load reads its world storage. Resolve the declared schema (D-c) for read_csv.
        val schema = resolveSchema(load)
        val path = load.source.replace('.', '/') + ".csv"
        return if (schema !=
            null
        ) {
            PolarsSource.Csv(path, schema)
        } else {
            PolarsSource.Staged(load.source.substringAfterLast('.'))
        }
    }

    private fun resolveSchema(load: Load): List<Pair<String, String>>? {
        val ref = load.schemaRef ?: return null

        fun matches(name: String) = name == ref || name.substringAfterLast('.') == ref
        val storage = world.world.storages.firstOrNull { s -> s.schemas.any { matches(it.qname.name) } } ?: return null
        val schema = storage.schemas.first { matches(it.qname.name) }
        return schema.fields.entries.map { it.key to it.value }
    }

    private fun sinkFor(
        container: Container,
        port: String,
    ): PolarsStep? {
        // Find the external leaf consuming this OUT port.
        val leafEdge = graph.edges.firstOrNull { it.from.nodeId == container.id && it.from.port == port } ?: return null
        val leaf = graph.nodes[leafEdge.to.nodeId] ?: return null
        return when (leaf) {
            is Display -> PolarsStep(varName = "_", node = leaf, sinkPath = "out/${leaf.name}.arrow")
            is Store -> PolarsStep(varName = "_", node = leaf, sinkPath = "staging/$port.arrow")
            else -> null
        }
    }

    private fun stagedReadNode(
        container: Container,
        port: String,
    ): Load = Load("${container.id}~$port", port, container.location, source = port)
}
