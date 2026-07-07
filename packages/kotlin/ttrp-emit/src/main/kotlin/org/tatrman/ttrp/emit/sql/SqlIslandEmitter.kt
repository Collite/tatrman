package org.tatrman.ttrp.emit.sql

import org.tatrman.proteus.v1.SqlDialect
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.collapse.Island
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * SQL for one island. [text] is the island's payload SQL; [nodeRanges] maps each emitted
 * node id to its source span (provenance for P4 hover, E-d).
 */
data class SqlEmitResult(
    val text: String,
    val nodeRanges: Map<String, SourceLocation>,
)

/**
 * Public entry: emit a normalized island as Postgres-dialect SQL.
 *
 * Two island shapes reach SQL emit:
 *  - **Fragment islands** (`Container.fragment != null`) — an authored `"""sql … """` block.
 *    Its interior is emitted **verbatim** (C2-f: fragment interiors are never rewritten;
 *    graph-level decomposition of fragments into relational nodes is P6). This is the hero's
 *    `acc_prep` island.
 *  - **Decomposed relational islands** — a container of relational nodes. These route through
 *    [CtePlanner] (CTE-per-node). No v1 hero produces one (fragments stay opaque until P6), so
 *    the graph→[EmitNode] walk here is best-effort over the schema the graph carries
 *    (`Container.declaredPorts`, `Load.schema`); the synthetic golden corpus exercises
 *    [CtePlanner] directly with fully-specified [EmitNode]s.
 */
class SqlIslandEmitter(
    private val world: BoundWorld,
) {
    fun emit(
        island: Island,
        graph: TtrpGraph,
    ): SqlEmitResult {
        val container = graph.containers[island.id]
        val fragment = container?.fragment
        if (fragment != null) {
            return SqlEmitResult(fragment.sourceText.trim(), emptyMap())
        }
        // Decomposed path — not reached by any v1 hero; the golden corpus covers CtePlanner.
        throw org.tatrman.ttrp.emit.TtrpEmitException(
            org.tatrman.ttrp.emit.EmitDiagnosticId.UNSUPPORTED_NODE,
            detail =
                "decomposed relational SQL island '${island.name}' is not emittable in v1 " +
                    "(fragment decomposition is P6); use CtePlanner directly for relational islands",
            island = island.name,
        )
    }

    /** Resolve the dialect for this island's engine from the bound world. */
    fun dialect(island: Island): SqlDialect {
        val engine = world.engines[island.engine]?.engine
        return DialectRegistry.forEngine(
            engineType = engine?.type ?: "postgres",
            version = engine?.version,
        )
    }
}
