package org.tatrman.ttrp.graph.rewrite

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Branch
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.SugarNode
import org.tatrman.ttrp.graph.model.TtrpGraph

/** T2.3a.3–.5 — the stratified fixpoint engine + sugar + capability-lowering strata. */
class RewriteEngineSpec :
    StringSpec({

        "the hero normalizes with exactly one Branch→Filter lowering on polars" {
            val g = GraphFixtures.graphOf(GraphFixtures.program("hero.ttrp"))
            val r = RewriteSupport.engine().normalize(g)
            r.log.map { it.rule } shouldBe listOf("branch->filter")
            r.log.single().engine shouldBe "polars"
            r.graph.nodes.values
                .filterIsInstance<Branch>() shouldBe emptyList()
            // The Branch became two Filters (true partition + 3VL-correct false partition),
            // on top of the hero's one authored `sales` filter ⇒ 3 filters total.
            r.graph.nodes.values
                .filterIsInstance<Filter>()
                .size shouldBe 3
            r.graph.nodes.values.filterIsInstance<Filter>().count {
                it.id.endsWith(
                    "~t",
                ) ||
                    it.id.endsWith("~f")
            } shouldBe
                2
        }

        "an already-normal native graph reaches fixpoint applying zero rewrites" {
            val g = RewriteSupport.chain("erp_pg", listOf("filter", "sort", "limit"))
            val r = RewriteSupport.engine().normalize(g)
            r.log shouldBe emptyList()
            r.iterations shouldBe 0
        }

        "the rewrite log records rule, before-label, engine, and reason" {
            val g = GraphFixtures.graphOf(GraphFixtures.program("hero.ttrp"))
            val entry =
                RewriteSupport
                    .engine()
                    .normalize(g)
                    .log
                    .single()
            entry.rule shouldBe "branch->filter"
            entry.before shouldContain "b" // the SSA label of the branch (b#1)
            entry.reason shouldContain "Branch not native"
        }

        "a rule that fails to decrease the measure hard-fails (internal error)" {
            val broken =
                object : RewriteRule {
                    override val name = "broken"
                    override val stratum = Stratum.SUGAR

                    override fun apply(
                        node: Node,
                        graph: TtrpGraph,
                        ctx: RewriteContext,
                    ): RewriteResult =
                        if (node is Project) {
                            // Replace with an identical Project: measure unchanged → must hard-fail.
                            RewriteResult.Replaced(
                                GraphOps.swapNode(graph, node.id, node.copy(label = node.label)),
                                AppliedRewrite(
                                    "broken",
                                    Stratum.SUGAR,
                                    node.label,
                                    node.label,
                                    null,
                                    node.location,
                                    "noop",
                                ),
                            )
                        } else {
                            RewriteResult.Unchanged
                        }
                }
            val g = RewriteSupport.chain("erp_pg", listOf("project"))
            val engine = RewriteEngine(listOf(broken), RewriteSupport.bound)
            shouldThrow<IllegalStateException> { engine.normalize(g) }
        }

        "sugar expands: Select/Calc→Project, Distinct→Aggregate, and no SugarNode survives" {
            val g = RewriteSupport.chain("polars", listOf("select", "calc", "distinct"))
            val r = RewriteSupport.engine().normalize(g)
            r.graph.nodes.values
                .filterIsInstance<SugarNode>() shouldBe emptyList()
            r.graph.nodes["m1"]!!.shouldBeInstanceOf<Project>()
            r.graph.nodes["m2"]!!.shouldBeInstanceOf<Project>()
            r.graph.nodes["m3"]!!.shouldBeInstanceOf<Aggregate>()
        }

        "HAVING splits into a having-less Aggregate plus a downstream Filter" {
            val agg =
                Aggregate(
                    "m1",
                    "m1#1",
                    org.tatrman.ttrp.ast.SourceLocation.UNKNOWN,
                    having =
                        org.tatrman.ttrp.expr.Literal(
                            org.tatrman.ttrp.expr.LiteralValue
                                .Bool(true),
                            org.tatrman.ttrp.ast.SourceLocation.UNKNOWN,
                        ),
                )
            val base = RewriteSupport.chain("polars", listOf("project"))
            val withAgg = GraphOps.swapNode(base, "m1", agg)
            val r = RewriteSupport.engine().normalize(withAgg)
            (r.graph.nodes["m1"] as Aggregate).having shouldBe null
            r.graph.nodes.values
                .filterIsInstance<Filter>()
                .any { it.id == "m1~having" } shouldBe true
        }
    })
