// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.graph.collapse.Island
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Aggregation
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.Edge
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Port
import org.tatrman.ttrp.graph.model.PortDirection
import org.tatrman.ttrp.graph.model.PortKind
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * The `LineageOutput.materialized` field (PL-P1.S9, Bora's ruling): an aggregated output column's
 * lineage carries the **physical target qname** it materializes to (the island's `store(...)` target),
 * so a downstream catalog (OpenMetadata, §18) has a resolvable output entity instead of the
 * island/relation form. Null when the island has no store (a display/in-memory-only output).
 *
 * The store is a PROGRAM-LEVEL leaf wired from the island's out-port (`crunch.low -> store(...)`), NOT
 * a container member — so these fixtures model the real collapsed shape (a [Container] island + an
 * [Edge] to a program-level [Store]), which is the shape `ContainerCollapse` actually produces.
 */
class LineageExtractorTest :
    StringSpec({
        val loc = SourceLocation.UNKNOWN

        fun col(name: String): ColumnRef = ColumnRef(null, name, loc)

        fun sum(arg: Expression): AggregateCall = AggregateCall(CatalogId("agg.sum"), listOf(arg), false, loc)

        // A container island `i` (members l+a) with a `low` DATA out-port, plus any program-level nodes.
        fun graphWith(
            edges: List<Edge>,
            vararg extra: Pair<String, Node>,
        ): TtrpGraph {
            val members =
                mapOf(
                    "l" to Load("l", "load", loc, source = "shop.sales.db.dbo.ORDER_LINE"),
                    "a" to Aggregate("a", "agg", loc, aggregations = listOf(Aggregation("total", sum(col("AMOUNT"))))),
                )
            val container =
                Container(
                    id = "i",
                    label = "summarize",
                    location = loc,
                    target = "erp_pg",
                    memberIds = listOf("l", "a"),
                    declaredPorts =
                        listOf(
                            Port("low", PortKind.DATA, PortDirection.OUT),
                            Port("rejects", PortKind.DATA, PortDirection.OUT),
                        ),
                    portMapping = emptyMap(),
                )
            return TtrpGraph(nodes = members + extra.toMap(), edges = edges, containers = mapOf("i" to container))
        }

        val island = Island("i", "summarize", "erp_pg", "psql", listOf("l", "a"))

        "output.materialized carries the program-level store(...) target wired from the island's out-port" {
            val graph =
                graphWith(
                    edges = listOf(Edge(PortRef("i", "low"), PortRef("s", "in"), EdgeKind.DATA)),
                    "s" to Store("s", "store", loc, target = "shop.sales.db.dbo.MAIN_RESULT"),
                )
            val col = LineageExtractor.extract(graph, listOf(island)).columns.single()
            col.output.column shouldBe "total"
            col.output.materialized shouldBe "shop.sales.db.dbo.MAIN_RESULT"
            col.inputs.single().qname shouldBe "shop.sales.db.dbo.ORDER_LINE"
            col.inputs.single().column shouldBe "AMOUNT"
        }

        "a rejects-port store is NOT the materialization; the data-port store wins" {
            val graph =
                graphWith(
                    edges =
                        listOf(
                            Edge(PortRef("i", "rejects"), PortRef("errs", "in"), EdgeKind.DATA),
                            Edge(PortRef("i", "low"), PortRef("s", "in"), EdgeKind.DATA),
                        ),
                    "errs" to Store("errs", "store", loc, target = "files.join_errors"),
                    "s" to Store("s", "store", loc, target = "files.low_regions"),
                )
            LineageExtractor
                .extract(graph, listOf(island))
                .columns
                .first()
                .output.materialized shouldBe "files.low_regions"
        }

        "output.materialized is null for a display/in-memory island (no store wired from it)" {
            val graph = graphWith(edges = emptyList())
            LineageExtractor
                .extract(graph, listOf(island))
                .columns
                .single()
                .output.materialized shouldBe null
        }
    })
