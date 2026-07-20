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
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * The `LineageOutput.materialized` field (PL-P1.S9, Bora's ruling): an aggregated output column's
 * lineage carries the **physical target qname** it materializes to (the island's `store(...)` target),
 * so a downstream catalog (OpenMetadata, §18) has a resolvable output entity instead of the
 * island/relation form. Null when the island has no store (a display/in-memory-only output).
 */
class LineageExtractorTest :
    StringSpec({
        val loc = SourceLocation.UNKNOWN

        fun col(name: String): ColumnRef = ColumnRef(null, name, loc)

        fun sum(arg: Expression): AggregateCall = AggregateCall(CatalogId("agg.sum"), listOf(arg), false, loc)

        fun graphOf(vararg nodes: Pair<String, org.tatrman.ttrp.graph.model.Node>): TtrpGraph =
            TtrpGraph(nodes = nodes.toMap(), edges = emptyList(), containers = emptyMap())

        "output.materialized carries the island's store(...) target" {
            val graph =
                graphOf(
                    "l" to Load("l", "load", loc, source = "shop.sales.db.dbo.ORDER_LINE"),
                    "a" to Aggregate("a", "agg", loc, aggregations = listOf(Aggregation("total", sum(col("AMOUNT"))))),
                    "s" to Store("s", "store", loc, target = "shop.sales.db.dbo.MAIN_RESULT"),
                )
            val island = Island("i", "summarize", "erp_pg", "psql", listOf("l", "a", "s"))

            val col = LineageExtractor.extract(graph, listOf(island)).columns.single()
            col.output.column shouldBe "total"
            col.output.materialized shouldBe "shop.sales.db.dbo.MAIN_RESULT"
            col.inputs.single().qname shouldBe "shop.sales.db.dbo.ORDER_LINE"
            col.inputs.single().column shouldBe "AMOUNT"
        }

        "output.materialized is null for a display/in-memory island (no store node)" {
            val graph =
                graphOf(
                    "l" to Load("l", "load", loc, source = "shop.sales.db.dbo.ORDER_LINE"),
                    "a" to Aggregate("a", "agg", loc, aggregations = listOf(Aggregation("total", sum(col("AMOUNT"))))),
                )
            val island = Island("i", "summarize", "erp_pg", "psql", listOf("l", "a"))

            LineageExtractor
                .extract(graph, listOf(island))
                .columns
                .single()
                .output.materialized shouldBe null
        }
    })
