// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.TtrpEmitException
import org.tatrman.ttrp.expr.MdPath
import org.tatrman.ttrp.expr.MdResolution
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.collapse.Island
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * SQL for an md-source island (S4-B4) — the db island the [org.tatrman.ttrp.graph.movement.MdReadHoist]
 * stratum synthesized to compute a Polars container's MD reads. Its lone member is a bare projection
 * of the hoisted `mdPath` nodes; this emitter turns each into a scalar subquery over the §8 relational
 * subtree ([MdPathLowering.lower]) and assembles one row of them:
 *
 *   `SELECT (<subquery-0>)::float8 AS "md_0", (<subquery-1>)::float8 AS "md_1", …`
 *
 * The **`::float8` cast is load-bearing**, not cosmetic: the fact value columns are PG `numeric`, which
 * the ADBC transfer returns as an opaque Arrow extension Polars cannot match (the same reason the
 * fixture world forces `amount` to `float`). Casting to double gives the transfer an Arrow-native
 * `float64` that stages cleanly. The result is emitted as a **`.sql`** island so the existing
 * fragment→transfer path (`SELECT * FROM (<island-sql>) AS _ttrp_src`, ADBC → Arrow) stages it with no
 * new bundle plumbing — identical to how the authored `acc_prep` fragment feeds the hero's Polars island.
 *
 * Reuses the S4-A read lowering verbatim (same [MdPathLowering], same `mdResolutions` keyed by the
 * `mdPath` location, same `referencedTables` → [IslandModelHandle] registration, same [TranslatorFacade]
 * unparse) — the only new shape is wrapping each bare read as a cast scalar subquery in one SELECT.
 */
class MdSourceIslandEmitter(
    private val world: BoundWorld,
    private val mdBindings: MdBindings?,
    private val mdModel: MdModel?,
) {
    fun emit(
        island: Island,
        graph: TtrpGraph,
    ): String {
        val container =
            graph.containers[island.id]
                ?: error("md-source island '${island.name}' has no container")
        val proj =
            container.memberIds.firstNotNullOfOrNull { graph.nodes[it] as? Project }
                ?: error("md-source island '${island.name}' has no projection member")
        val lowering =
            mdBindings?.let { MdPathLowering(it, mdModel) }
                ?: throw TtrpEmitException(
                    EmitDiagnosticId.UNSUPPORTED_NODE,
                    detail = "md-source island reached emit with no MD bindings (md2db bindings not wired)",
                    island = island.name,
                )

        // The hoisted reads, paired with their S3 resolutions, in projection (column) order.
        val reads =
            proj.columns.mapIndexedNotNull { i, c ->
                (c as? MdPath)?.let { mp -> Triple(i, mp, resolution(mp, graph, island)) }
            }
        // One model for the whole island: the union of every read's backing db tables, so the single
        // TranslatorFacade resolves f_plan / f_sales / d_calendar across the SELECT's subqueries.
        // T-L2: union columns per table across reads — two reads of one table can need different columns,
        // and distinctBy-qname would keep only the first read's, failing the decode of the second.
        val model =
            unionMdTables(reads.flatMap { (_, _, res) -> lowering.referencedTables(res.path, res.shape) })
        val engine = world.engines[island.engine]?.engine
        val facade =
            TranslatorFacade(
                IslandModelHandle(model),
                DialectRegistry.forEngine(engine?.type ?: "postgres", engine?.version),
            )

        val cols =
            reads.map { (i, mp, res) ->
                val subquery =
                    try {
                        facade.unparse(lowering.lower(res.path, res.shape), island.name).trim()
                    } catch (ex: MdLoweringException) {
                        throw TtrpEmitException(
                            EmitDiagnosticId.UNSUPPORTED_NODE,
                            detail = ex.message ?: ex.code,
                            location = mp.location,
                            island = island.name,
                        )
                    }
                "($subquery)::float8 AS \"${proj.aliasOf(i) ?: "md_$i"}\""
            }
        return "SELECT " + cols.joinToString(",\n       ") + "\n"
    }

    private fun resolution(
        mp: MdPath,
        graph: TtrpGraph,
        island: Island,
    ): MdResolution =
        graph.mdResolutions[mp.location]
            ?: throw TtrpEmitException(
                EmitDiagnosticId.UNSUPPORTED_NODE,
                detail = "MD dot-path reached md-source emit unresolved (no S3 resolution on the graph)",
                location = mp.location,
                island = island.name,
            )
}
