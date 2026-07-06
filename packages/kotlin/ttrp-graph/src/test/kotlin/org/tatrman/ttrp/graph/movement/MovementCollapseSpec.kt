package org.tatrman.ttrp.graph.movement

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.build.GraphBuilder
import org.tatrman.ttrp.graph.capability.ClasspathManifestSource
import org.tatrman.ttrp.graph.capability.InvocationBindingResolver
import org.tatrman.ttrp.graph.capability.WorldBinder
import org.tatrman.ttrp.graph.collapse.ContainerCollapse
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.Transfer
import org.tatrman.ttrp.graph.world.StagingResolver

/** T2.3b.4/.5 — movement synthesis (Store+Transfer+Load) + container-collapse + waves. */
class MovementCollapseSpec :
    StringSpec({

        fun heroMoved(): Pair<org.tatrman.ttrp.graph.model.TtrpGraph, MovementResult> {
            val report = GraphFixtures.report(GraphFixtures.program("hero.ttrp"))
            val bound = WorldBinder(ClasspathManifestSource()).bind(report.world!!)
            val g = GraphBuilder().build(report).graph
            val norm =
                org.tatrman.ttrp.graph.rewrite
                    .RewriteEngine(org.tatrman.ttrp.graph.rewrite.Rules.ALL, bound)
                    .normalize(g)
            val staging = StagingResolver(bound).resolve(norm.graph)
            val moved = MovementSynthesizer(bound, staging.staging?.qname?.name).synthesize(norm.graph)
            return moved.graph to moved
        }

        "the hero crossing synthesizes Store + Transfer + Load via stage (Arrow IPC)" {
            val (g, moved) = heroMoved()
            moved.transferIds.size shouldBe 1
            g.nodes.values
                .filterIsInstance<Transfer>()
                .single()
                .via shouldBe "stage"
            g.nodes.values
                .filterIsInstance<Transfer>()
                .single()
                .format shouldBe "arrow-ipc"
            // Exactly one movement Store + one movement Load added.
            g.nodes.values
                .filterIsInstance<Store>()
                .count { it.id.endsWith("~store") } shouldBe 1
            g.nodes.values
                .filterIsInstance<Load>()
                .count { it.id.endsWith("~load") } shouldBe 1
        }

        "the hero collapses to two islands, one transfer, and waves acc_prep→transfer→crunch" {
            val (g, _) = heroMoved()
            val report = GraphFixtures.report(GraphFixtures.program("hero.ttrp"))
            val bound = WorldBinder(ClasspathManifestSource()).bind(report.world!!)
            val inv = InvocationBindingResolver(bound).resolve(g)
            val exec = ContainerCollapse(inv).collapse(g)
            exec.islands.map { it.name }.toSet() shouldBe setOf("acc_prep", "crunch")
            exec.transfers.size shouldBe 1
            exec.transfers.single().fromIsland shouldBe exec.islands.first { it.name == "acc_prep" }.id
            exec.transfers.single().toIsland shouldBe exec.islands.first { it.name == "crunch" }.id
            // Three waves in order: source island, transfer, sink island.
            exec.waves.size shouldBe 3
            exec.waves[0].single() shouldBe exec.islands.first { it.name == "acc_prep" }.id
            exec.waves[2].single() shouldBe exec.islands.first { it.name == "crunch" }.id
        }

        "invocation bindings ride the islands (psql / python3)" {
            val (g, _) = heroMoved()
            val report = GraphFixtures.report(GraphFixtures.program("hero.ttrp"))
            val bound = WorldBinder(ClasspathManifestSource()).bind(report.world!!)
            val exec = ContainerCollapse(InvocationBindingResolver(bound).resolve(g)).collapse(g)
            exec.islands.first { it.name == "acc_prep" }.invocation shouldBe "psql"
            exec.islands.first { it.name == "crunch" }.invocation shouldBe "python3"
        }

        "a same-engine crossing is not double-wrapped (no transfer)" {
            // Two erp_pg fragment containers wired directly — same engine ⇒ no movement.
            val src =
                "uses world \"acme.worlds.dev\"\n" +
                    "container a target erp_pg \"\"\"sql\n    select 1\n\"\"\"\n" +
                    "container b(in x, out o) target erp_pg {\n    o = filter(x, 1 > 0)\n}\n" +
                    "a -> b.x\n"
            val report = GraphFixtures.report(src)
            val bound = WorldBinder(ClasspathManifestSource()).bind(report.world!!)
            val g = GraphBuilder().build(report).graph
            val moved = MovementSynthesizer(bound, "stage").synthesize(g)
            moved.transferIds shouldBe emptyList()
        }
    })
