package org.tatrman.ttrp.graph.build

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.graph.model.Union

private const val HDR = "uses world \"acme.worlds.dev\"\n"

private fun crunch(body: String): String =
    HDR + "container c(in accounts, out o, err rejects) target polars {\n$body\n}\n"

private fun members(
    g: TtrpGraph,
    label: String = "c",
) = g.containers.values
    .first { it.label == label }
    .memberIds
    .map { g.nodes.getValue(it) }

/** T2.1.3 — GraphBuilder + SSA desugar (Q7-γ). */
class GraphBuilderSpec :
    StringSpec({

        "reassignment mints a fresh instance carrying the same label stem" {
            val g =
                GraphFixtures
                    .build(
                        crunch(
                            "s = load(files.sales_2026, schema: sales_csv)\n" +
                                "s = filter(s, amount > 0)\n" +
                                "o = s",
                        ),
                    ).graph
            val loads = members(g).filterIsInstance<Load>()
            val filters = members(g).filterIsInstance<Filter>()
            loads.single().label shouldBe "s#1"
            filters.single().label shouldBe "s#2"
        }

        "a chain of three ops creates three nodes and two internal edges" {
            val r =
                GraphFixtures.build(
                    crunch("o = load(files.sales_2026, schema: sales_csv) -> filter(amount > 0) -> limit(10)"),
                )
            val ms = members(r.graph)
            ms.map { it::class.simpleName } shouldBe listOf("Load", "Filter", "Limit")
            // Load→Filter, Filter→Limit (two DATA edges among the three).
            val ids = ms.map { it.id }.toSet()
            r.graph.edges.count { it.from.nodeId in ids && it.to.nodeId in ids } shouldBe 2
        }

        "a multicast variable produces two edges from one out-port" {
            val g =
                GraphFixtures
                    .build(
                        crunch(
                            "s = load(files.sales_2026, schema: sales_csv)\n" +
                                "a = filter(s, amount > 0)\n" +
                                "b = filter(s, amount > 10)\n" +
                                "o = a",
                        ),
                    ).graph
            val load = members(g).filterIsInstance<Load>().single()
            g.edges.count { it.from.nodeId == load.id && it.from.port == PortNames.OUT } shouldBe 2
        }

        "named join args bind left and right ports" {
            val g =
                GraphFixtures
                    .build(
                        crunch(
                            "a = load(files.sales_2026, schema: sales_csv)\n" +
                                "b = load(files.sales_2026, schema: sales_csv)\n" +
                                "o = join(left: a, right: b, type: inner)",
                        ),
                    ).graph
            val join = members(g).filterIsInstance<Join>().single()
            g.edges.count { it.to.nodeId == join.id && it.to.port == PortNames.LEFT } shouldBe 1
            g.edges.count { it.to.nodeId == join.id && it.to.port == PortNames.RIGHT } shouldBe 1
        }

        "union list form binds in1..in3" {
            val g =
                GraphFixtures
                    .build(
                        crunch(
                            "a = load(files.sales_2026, schema: sales_csv)\n" +
                                "b = load(files.sales_2026, schema: sales_csv)\n" +
                                "d = load(files.sales_2026, schema: sales_csv)\n" +
                                "o = union(a, b, d)",
                        ),
                    ).graph
            val union = members(g).filterIsInstance<Union>().single()
            union.arity shouldBe 3
            (1..3).forEach { i ->
                g.edges.count { it.to.nodeId == union.id && it.to.port == "in$i" } shouldBe 1
            }
        }

        "container target string is carried" {
            val g = GraphFixtures.build(crunch("o = load(files.sales_2026, schema: sales_csv)")).graph
            g.containers.values
                .first()
                .target shouldBe "polars"
        }
    })
