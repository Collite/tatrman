package org.tatrman.ttrp.graph.capability

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.GraphFixtures

private const val HDR = "uses world \"acme.worlds.dev\"\n"

private fun bindAndCheck(body: String): List<CapabilityMiss> {
    val src = HDR + "container c(out o, err rejects) target polars {\n$body\n}\n"
    val report = GraphFixtures.report(src)
    val bound = WorldBinder(ClasspathManifestSource()).bind(report.world!!)
    val graph =
        org.tatrman.ttrp.graph.build
            .GraphBuilder()
            .build(report)
            .graph
    return CapabilityChecker(bound).check(graph)
}

/** T2.2.5 — capability checker: node/param/function-granular misses, policy-free. */
class CapabilityCheckSpec :
    StringSpec({

        "a right join on polars is reported as a join-type node miss (input-swap lowering is 2.3)" {
            val misses =
                bindAndCheck(
                    "a = load(files.sales_2026, schema: sales_csv)\n" +
                        "b = load(files.sales_2026, schema: sales_csv)\n" +
                        "o = join(left: a, right: b, type: right)",
                )
            val m = misses.filterIsInstance<CapabilityMiss.NodeMiss>().single { it.detail.contains("join type") }
            m.detail shouldBe "join type right"
            m.engine shouldBe "polars"
        }

        "a fully native polars container reports no misses" {
            val misses =
                bindAndCheck(
                    "s = load(files.sales_2026, schema: sales_csv)\n" +
                        "o = filter(s, amount > 0)",
                )
            misses shouldBe emptyList()
        }

        "a branch on polars is reported as a node miss (the hero's one lowering)" {
            val misses =
                bindAndCheck(
                    "s = load(files.sales_2026, schema: sales_csv)\n" +
                        "o = branch(s, amount > 0)",
                )
            misses.filterIsInstance<CapabilityMiss.NodeMiss>().any { it.detail == "node kind Branch" } shouldBe true
        }

        // Grounding twin (grammar 4.2): fn.geo_distance_m is native on postgres but NOT
        // polars, so a node using it on polars is a function miss → node-granularity
        // re-placement (B-T5-b) — exactly the designed behaviour, not an error.
        "geo_distance_m on polars is a function miss (re-placement per B-T5-b)" {
            val misses =
                bindAndCheck(
                    "s = load(files.sales_2026, schema: sales_csv)\n" +
                        "o = filter(s, geo_distance_m(1.0, 2.0, 3.0, 4.0) < 20000)",
                )
            val m =
                misses.filterIsInstance<CapabilityMiss.FunctionMiss>().single {
                    it.functionId == "fn.geo_distance_m"
                }
            m.engine shouldBe "polars"
        }
    })
