// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.model

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.MdResolution
import org.tatrman.ttrp.resolve.Provenance

/**
 * An opaque fragment island (`"""sql` / `"""pandas` / `"""ttrb`) inside a container.
 * Phase 1 keeps fragment interiors VERBATIM (C2-f); clause→node decomposition is
 * Phase 6 (the dialect grammars). In Phase 2 a fragment container therefore has NO
 * decomposed members — it carries the raw fragment and exposes a single default out.
 */
data class FragmentSource(
    val tag: String,
    val sourceText: String,
)

/**
 * A container (B-T9): a closed function grouping ops, no processing value, whose
 * ports map onto internal node ports. In v1 it bears the author-assigned execution
 * target (engine-instance qname string; resolved against the world in Stage 2.2).
 * A fragment container carries its raw fragment ([fragment]) and is opaque in Phase 2.
 */
data class Container(
    override val id: String,
    override val label: String,
    override val location: SourceLocation,
    /** The author-assigned execution target (engine instance qname, e.g. `erp_pg`). */
    val target: String,
    /** Member node ids belonging to this container. */
    val memberIds: List<String>,
    /** Declared container ports (`in …, out …, err …`). */
    val declaredPorts: List<Port>,
    /** Container port name → the internal node port it maps to (B-T9 encapsulation). */
    val portMapping: Map<String, PortRef>,
    /** Non-null for a fragment container (opaque until Phase-6 decomposition). */
    val fragment: FragmentSource? = null,
    override val provenance: Provenance? = null,
) : Node {
    override fun ports(): List<Port> = declaredPorts

    override fun defaultIn(): String? = declaredPorts.firstOrNull { it.direction == PortDirection.IN }?.name

    override fun defaultOut(): String? =
        declaredPorts.firstOrNull { it.direction == PortDirection.OUT && it.kind == PortKind.DATA }?.name
}

/** Edge kinds — DATA flow or the two v1 control constraints (FS/SS). NO FF arm (B-T2-as-amended; FF ⇒ CTL-001). */
enum class EdgeKind { DATA, CONTROL_FS, CONTROL_SS }

/**
 * A connection from an out-port to an in-port. Control edges (`CONTROL_FS`/`_SS`)
 * are hard on their effect (B-T2) — the 2.3 rewriter must preserve them.
 */
data class Edge(
    val from: PortRef,
    val to: PortRef,
    val kind: EdgeKind,
    val location: SourceLocation = SourceLocation.UNKNOWN,
)

/**
 * The one internal graph (B-T4: one document = one program = one acyclic graph).
 * Insertion-ordered collections only (determinism groundwork for Stage 2.3). All
 * nodes — including container members — live in the flat [nodes] map; [containers]
 * records grouping + port mapping. Program-level leaves (Load/Store/Display) are
 * ordinary [nodes] not inside any container.
 */
data class TtrpGraph(
    val nodes: Map<String, Node>,
    val edges: List<Edge>,
    val containers: Map<String, Container>,
    /**
     * MD dot-path resolutions (S3), keyed by the `mdPath` node's source location — the graph-side
     * annotation the S4 read lowering consumes (decision: carry the resolution on the IR, not
     * re-resolve in emit). Empty for programs with no MD paths. See [org.tatrman.ttrp.expr.MdResolution].
     */
    val mdResolutions: Map<SourceLocation, MdResolution> = emptyMap(),
) {
    fun node(id: String): Node? = nodes[id]

    /** The container that owns [nodeId], or null for a program-level node. */
    fun containerOf(nodeId: String): Container? = containers.values.firstOrNull { nodeId in it.memberIds }

    /** Edges leaving any port of [nodeId]. */
    fun edgesFrom(nodeId: String): List<Edge> = edges.filter { it.from.nodeId == nodeId }

    /** Edges entering any port of [nodeId]. */
    fun edgesTo(nodeId: String): List<Edge> = edges.filter { it.to.nodeId == nodeId }

    companion object {
        val EMPTY = TtrpGraph(emptyMap(), emptyList(), emptyMap())
    }
}
