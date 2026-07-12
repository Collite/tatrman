// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import org.tatrman.ttrp.emit.sql.PgAdbcIslandEmitter
import org.tatrman.ttrp.emit.sql.SqlIslandEmitter
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.collapse.Island
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.Display
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.PortDirection
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * Assembles the Python `adbc_driver_postgresql` runtime script for a **decomposed Postgres island**
 * (S3.5 T3.5.4) by gathering, from the graph, exactly what [PgAdbcIslandEmitter] needs:
 *  - **SQL temps** — each container IN port fed by a *same-engine fragment* (the hero's `accounts`
 *    port fed by the `acc_prep` PG fragment) becomes `CREATE TEMP TABLE <port> AS <fragment sql>`;
 *  - **CSV temps** — each member [Load] of a CSV storage becomes a typed temp from `files/<leaf>.csv`;
 *  - **outputs** — each non-`rejects` OUT port's SQL (via [SqlIslandEmitter.emitOutputs]) written to
 *    its sink (`out/<display>.arrow` / `staging/<port>.arrow`).
 */
object PgIslandScript {
    fun build(
        island: Island,
        graph: TtrpGraph,
        bound: BoundWorld,
        connEnv: String,
    ): String {
        val container = graph.containers.getValue(island.id)
        val outSql = SqlIslandEmitter(bound).emitOutputs(island, graph)

        val outputs =
            container.portMapping.entries.mapNotNull { (port, ref) ->
                if (ref.port == "rejects") return@mapNotNull null
                val sql = outSql[port]?.text ?: return@mapNotNull null
                val sink = sinkPath(container, port, graph) ?: return@mapNotNull null
                PgAdbcIslandEmitter.Output(sql, sink)
            }

        val sqlTemps =
            container.declaredPorts
                .filter { it.direction == PortDirection.IN }
                .mapNotNull { p ->
                    val feed =
                        graph.edges.firstOrNull { it.to == PortRef(container.id, p.name) } ?: return@mapNotNull null
                    val frag = graph.containers[feed.from.nodeId]?.fragment ?: return@mapNotNull null
                    PgAdbcIslandEmitter.SqlTemp(p.name, frag.sourceText.trim())
                }

        val csvTemps =
            container.memberIds
                .mapNotNull { graph.nodes[it] as? Load }
                .mapNotNull { load ->
                    val cols = csvColumns(load, bound) ?: return@mapNotNull null
                    PgAdbcIslandEmitter.CsvTemp(
                        table = load.source.substringAfterLast('.'),
                        csvPath = load.source.replace('.', '/') + ".csv",
                        columns = cols,
                    )
                }

        return PgAdbcIslandEmitter().emit(connEnv, sqlTemps, csvTemps, outputs)
    }

    /** The external sink for an OUT [port]: Display → `out/<name>.arrow`, Store → `staging/<port>.arrow`. */
    private fun sinkPath(
        container: Container,
        port: String,
        graph: TtrpGraph,
    ): String? {
        val leafEdge = graph.edges.firstOrNull { it.from == PortRef(container.id, port) } ?: return null
        return when (val leaf = graph.nodes[leafEdge.to.nodeId]) {
            is Display -> "out/${leaf.name}.arrow"
            is Store -> "staging/$port.arrow"
            else -> null
        }
    }

    /** A member [Load]'s CSV columns from its world-declared schema (D-c), typed for the temp table. */
    private fun csvColumns(
        load: Load,
        bound: BoundWorld,
    ): List<PgAdbcIslandEmitter.PgColumn>? {
        val ref = load.schemaRef ?: return null

        fun matches(name: String) = name == ref || name.substringAfterLast('.') == ref
        val storage = bound.world.storages.firstOrNull { s -> s.schemas.any { matches(it.qname.name) } } ?: return null
        val schema = storage.schemas.first { matches(it.qname.name) }
        return schema.fields.entries.map { PgAdbcIslandEmitter.pgColumn(it.key, it.value) }
    }
}
