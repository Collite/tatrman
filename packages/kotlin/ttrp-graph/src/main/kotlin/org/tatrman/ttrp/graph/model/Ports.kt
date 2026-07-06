package org.tatrman.ttrp.graph.model

/**
 * Port model (B-T2): connections use PORTS, not edges — semantics live in the port.
 * Ports are typed (data-bearing | control-bearing), named, directional, and every
 * node has a DEFAULT port. Two reserved error ports per node: `err` (control-shaped
 * signal) + `rejects` (data-shaped rows) (C3-f). Reserved port names are S10's seven.
 */
enum class PortKind { DATA, CONTROL }

enum class PortDirection { IN, OUT }

data class Port(
    val name: String,
    val kind: PortKind,
    val direction: PortDirection,
    /**
     * Static port schema (T7). Null ONLY for `Display` inputs — the Q11 dynamic-schema
     * exception ("sink accepts any"). Absent anywhere else is an internal error.
     */
    val schema: List<PortColumn>? = null,
)

/** A single static port-schema column (name + db-schema type spelling, S23). */
data class PortColumn(
    val name: String,
    val type: String,
)

/** A reference to one port of one node — the endpoint of an [Edge]. */
data class PortRef(
    val nodeId: String,
    val port: String,
)

/** The seven reserved port names (S10, lowercase). Column lexical rules apply. */
object ReservedPorts {
    val ALL: List<String> = listOf("in", "out", "err", "rejects", "true", "false", "else")

    fun isReserved(name: String): Boolean = name in ALL
}

/** Well-known port names used across the node roster. */
object PortNames {
    const val IN = "in"
    const val OUT = "out"
    const val ERR = "err"
    const val REJECTS = "rejects"
    const val LEFT = "left"
    const val RIGHT = "right"
    const val TRUE = "true"
    const val FALSE = "false"
    const val ELSE = "else"

    /** The two reserved error out-ports every node exposes (C3-f). */
    fun errorPorts(): List<Port> =
        listOf(
            Port(ERR, PortKind.CONTROL, PortDirection.OUT),
            Port(REJECTS, PortKind.DATA, PortDirection.OUT),
        )
}
