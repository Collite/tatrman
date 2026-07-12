// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.viewstate

import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * ζ identity keys (C1-c-i): the SSA-qualified node identity the `.ttrl` sidecar keys
 * layout entries by, and the ζ string every `getGraph` node carries.
 *
 * A ζ key is `<container-path>/<label>` for a container member and just `<label>` for a
 * program-level leaf, where `label` is the node's SSA-qualified name (`sales#2`) or the
 * anonymous `~n` form. Because [Node.label] already carries the surviving variable name +
 * generation ordinal (Q7-γ, `GraphBuilder.labelFor`) this is a pure function of the graph
 * — no RNG, no traversal — which is exactly what deterministic orphaning needs.
 *
 * **Canvases.** The orchestration canvas (`program`) is keyed by container names + program
 * leaf ζ; a container drill-in canvas is keyed by the container path, and its member ζ keys
 * are the full `<container>/<label>` form. Derived (fragment) sub-graph canvases get NO ζ
 * keys — they never appear in `.ttrl` (C1-b-iv).
 *
 * **Chain length** per name group (the orphaning discriminator): the SSA count for a base
 * name within a canvas (`sales#1`,`sales#2` ⇒ `sales` → 2). If a base name's current chain
 * length differs from the length the sidecar recorded, ALL its entries orphan (an inserted
 * mid-chain reassignment shifts every later ordinal, so the old positions can no longer be
 * trusted — never re-attach by guess, P2).
 */
object ZetaKeys {
    /** The orchestration canvas key (top level). */
    const val PROGRAM_CANVAS = "program"

    /** ζ for a program-level leaf (Display/Store/Transfer): just its SSA label. */
    fun leaf(node: Node): String = node.label

    /** ζ for a node inside [containerPath]. */
    fun member(
        containerPath: String,
        node: Node,
    ): String = "$containerPath/${node.label}"

    /** ζ for any node id: the container path if it IS a container, else member/leaf ζ. */
    fun of(
        graph: TtrpGraph,
        nodeId: String,
    ): String {
        graph.containers[nodeId]?.let { return it.label }
        val node = graph.node(nodeId) ?: return nodeId
        val owner = graph.containerOf(nodeId)
        return if (owner != null) member(owner.label, node) else leaf(node)
    }

    /** Every ζ key in [graph]: container members qualified by owner, leaves bare (flat map). */
    fun all(graph: TtrpGraph): Map<String, Node> {
        val out = LinkedHashMap<String, Node>()
        for ((canvas, entries) in canvasKeys(graph)) {
            if (canvas == PROGRAM_CANVAS) {
                // Program canvas also lists container ζ (= container name); drop those from the
                // flat node map — they are containers, keyed at the orchestration level only.
                entries.forEach { (k, v) -> if (!graph.containers.values.any { it.label == k }) out[k] = v }
            } else {
                out.putAll(entries)
            }
        }
        return out
    }

    /**
     * ζ keys grouped by canvas: `program` (container names + program leaves) plus one entry
     * per container (its members' `<container>/<label>` ζ). Insertion-ordered, deterministic.
     */
    fun canvasKeys(graph: TtrpGraph): Map<String, Map<String, Node>> {
        val out = LinkedHashMap<String, LinkedHashMap<String, Node>>()
        val program = LinkedHashMap<String, Node>()
        out[PROGRAM_CANVAS] = program

        // Container islands appear on the program canvas keyed by their name.
        for (c in graph.containers.values) program[c.label] = c

        // Program-level leaves (not container members, not containers themselves).
        for ((id, node) in graph.nodes) {
            if (graph.containers.containsKey(id)) continue
            if (graph.containerOf(id) != null) continue
            program[leaf(node)] = node
        }

        // One canvas per container, keyed by its member ζ.
        for (c in graph.containers.values) {
            val members = LinkedHashMap<String, Node>()
            for (mid in c.memberIds) {
                val n = graph.node(mid) ?: continue
                members[member(c.label, n)] = n
            }
            out[c.label] = members
        }
        return out
    }

    /** Chain length per base name (`<zeta>` before `#`) within a set of ζ keys. */
    fun chainLengths(zetas: Collection<String>): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        for (z in zetas) {
            val base = baseOf(z)
            counts[base] = (counts[base] ?: 0) + 1
        }
        return counts
    }

    /** Chain lengths per canvas (used when writing the sidecar `chains` record). */
    fun chainLengthsByCanvas(graph: TtrpGraph): Map<String, Map<String, Int>> =
        canvasKeys(graph).mapValues { (_, entries) -> chainLengths(entries.keys) }

    /** The base name of a ζ key: everything before the SSA `#ordinal` (`crunch/sales#2` → `crunch/sales`). */
    fun baseOf(zeta: String): String = zeta.substringBefore('#')
}
