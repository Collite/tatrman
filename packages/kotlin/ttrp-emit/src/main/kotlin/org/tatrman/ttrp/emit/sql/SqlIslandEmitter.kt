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
        val outputs = emitOutputs(island, graph)
        return outputs.values.singleOrNull()
            ?: throw org.tatrman.ttrp.emit.TtrpEmitException(
                org.tatrman.ttrp.emit.EmitDiagnosticId.UNSUPPORTED_NODE,
                detail =
                    "SQL island '${island.name}' has ${outputs.size} outputs (${outputs.keys.joinToString()}); " +
                        "use emitOutputs() for multi-output decomposed islands",
                island = island.name,
            )
    }

    /**
     * Emit a SQL island as one statement **per (non-`rejects`) container OUT port** (the SQL
     * counterpart of the Polars multi-sink container). A fragment island yields its verbatim
     * interior under the island name (C2-f); a decomposed relational island routes each output's
     * dependency-cone through [SqlGraphEmitter] → [CtePlanner]. This is the entry the bundle uses
     * for a PG-targeted `crunch` (S3.5): the lowered Branch gives `result` (b.true) + `low`
     * (b.false), each a self-contained CTE chain.
     */
    fun emitOutputs(
        island: Island,
        graph: TtrpGraph,
    ): Map<String, SqlEmitResult> {
        val container = graph.containers[island.id]
        container?.fragment?.let { return mapOf(island.name to SqlEmitResult(it.sourceText.trim(), emptyMap())) }
        requireNotNull(container) { "SQL island '${island.name}' has no container" }
        val dialect = dialect(island)
        val planner = CtePlanner { model -> TranslatorFacade(IslandModelHandle(model), dialect) }
        return SqlGraphEmitter(graph, world).plansByOutput(container).mapValues { (_, plan) ->
            SqlEmitResult(planner.emit(plan, island.name), emptyMap())
        }
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
