package org.tatrman.ttrp.lsp.viewstate

import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * ζ identity keys (C1-c-i): the SSA-qualified node identity the `.ttrl` sidecar keys
 * layout entries by, and the ζ string every `getGraph` node carries.
 *
 * A ζ key is `<container-path>/<label>` for a container member and just `<label>` for
 * a program-level leaf, where `label` is the node's SSA-qualified name (`sales#2`) or
 * the anonymous `~n` form. Because [Node.label] already carries the surviving variable
 * name + generation ordinal (Q7-γ, `GraphBuilder.labelFor`) this is a pure function of
 * the graph — no RNG, no traversal — which is exactly what deterministic orphaning
 * (Stage 5.2) needs.
 *
 * The orchestration canvas keys are the container names + program-leaf labels; a
 * container drill-in canvas is keyed by the container path and its member ζ keys are
 * the `<container>/<label>` form. Stage 5.2 layers chain-length + orphaning on top of
 * these keys; it does not change the key spelling.
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

    /** Every ζ key in [graph]: container members qualified by their owner, leaves bare. */
    fun all(graph: TtrpGraph): Map<String, Node> {
        val out = LinkedHashMap<String, Node>()
        for ((id, node) in graph.nodes) {
            // the container itself is keyed by name at the orchestration level, not as a member
            if (graph.containers.containsKey(id)) continue
            val owner = graph.containerOf(id)
            val key = if (owner != null) member(owner.label, node) else leaf(node)
            out[key] = node
        }
        return out
    }
}
