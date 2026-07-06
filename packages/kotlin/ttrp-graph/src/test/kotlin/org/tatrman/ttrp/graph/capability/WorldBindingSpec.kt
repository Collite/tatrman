package org.tatrman.ttrp.graph.capability

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.build.GraphBuilder
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.graph.world.StagingResolver

/**
 * Stage-2.2 component test (T2.2.5–T2.2.7 DONE bar): the hero + acme.worlds.dev bind
 * end-to-end — engines/executor bound to shipped manifests, capability check reports
 * EXACTLY {Branch@polars}, invocation bindings {acc_prep→psql, crunch→python3,
 * display→file-drop}, staging = stage and feasible, zero error diagnostics.
 */
class WorldBindingSpec :
    StringSpec({

        val manifests = ClasspathManifestSource()

        fun setup(): Triple<BoundWorld, TtrpGraph, Container> {
            val report = GraphFixtures.report(GraphFixtures.program("hero.ttrp"))
            val world = report.world.shouldNotBeNull()
            val bound = WorldBinder(manifests).bind(world)
            val graph = GraphBuilder().build(report).graph
            val crunch = graph.containers.values.first { it.label == "crunch" }
            return Triple(bound, graph, crunch)
        }

        "the world binds erp_pg → postgres-16, polars → polars, sh → bash with no errors" {
            val (bound, _, _) = setup()
            bound.diagnostics shouldBe emptyList()
            bound.engines["erp_pg"]!!.manifest.id shouldBe "postgres-16"
            bound.engines["polars"]!!.manifest.id shouldBe "polars"
            bound.executors["sh"]!!.manifest.id shouldBe "bash"
        }

        "capability check reports exactly the Branch-on-polars node miss" {
            val (bound, graph, _) = setup()
            val misses = CapabilityChecker(bound).check(graph)
            misses.size shouldBe 1
            val m = misses.single() as CapabilityMiss.NodeMiss
            m.detail shouldBe "node kind Branch"
            m.engine shouldBe "polars"
        }

        "invocation bindings resolve pg→psql, polars→python3, display→file-drop" {
            val (bound, graph, _) = setup()
            val r = InvocationBindingResolver(bound).resolve(graph)
            r.diagnostics shouldBe emptyList()
            r.byContainer.values.map { it.invocation.delivery } shouldContainExactlyInAnyOrder listOf("psql", "python3")
            r.displayBinding!!.delivery shouldBe "file-drop"
        }

        "staging resolves to `stage` and is feasible for the acc_prep→crunch crossing" {
            val (bound, graph, _) = setup()
            val staging = StagingResolver(bound).resolve(graph)
            staging.staging!!.qname.name shouldBe "stage"
            staging.diagnostics shouldBe emptyList()
        }
    })
