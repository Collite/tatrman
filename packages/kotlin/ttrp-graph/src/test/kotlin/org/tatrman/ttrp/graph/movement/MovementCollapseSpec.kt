package org.tatrman.ttrp.graph.movement

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.build.GraphBuilder
import org.tatrman.ttrp.graph.capability.ClasspathManifestSource
import org.tatrman.ttrp.graph.capability.InvocationBindingResolver
import org.tatrman.ttrp.graph.capability.InvocationResult
import org.tatrman.ttrp.graph.capability.WorldBinder
import org.tatrman.ttrp.graph.collapse.ContainerCollapse
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.Edge
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.Transfer
import org.tatrman.ttrp.graph.model.TtrpGraph
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

        "the hero crossing synthesizes Store + Transfer + Load named by the accounts boundary (Arrow IPC)" {
            val (g, moved) = heroMoved()
            moved.transferIds.size shouldBe 1
            g.nodes.values
                .filterIsInstance<Transfer>()
                .single()
                .via shouldBe "accounts" // the destination container IN-port (the staged-file token)
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

        "an SS co-launch raise re-propagates to FS dependents (no wave inversion)" {
            // Five single-node polars containers: S0 → S1 → C (FS chain, C at wave 2),
            // A → B (FS), and `A with C` (SS). Raising A to co-launch with C at wave 2 must
            // push its FS-dependent B past it — regression for the un-repropagated SS raise.
            val loc = SourceLocation.UNKNOWN

            fun container(
                id: String,
                member: String,
            ): Pair<Container, Node> {
                val m = Filter(member, "$member#1", loc, predicate = null)
                val c = Container(id, id, loc, "polars", listOf(member), emptyList(), emptyMap())
                return c to m
            }

            val defs =
                listOf("S0" to "n0", "S1" to "n1", "C" to "nc", "A" to "na", "B" to "nb")
                    .map { (cid, mid) -> container(cid, mid) }

            fun ctrl(
                from: String,
                to: String,
                kind: EdgeKind,
            ) = Edge(PortRef(from, PortNames.OUT), PortRef(to, PortNames.IN), kind)

            val edges =
                listOf(
                    ctrl("n0", "n1", EdgeKind.CONTROL_FS), // S0 → S1
                    ctrl("n1", "nc", EdgeKind.CONTROL_FS), // S1 → C
                    ctrl("na", "nb", EdgeKind.CONTROL_FS), // A → B
                    ctrl("na", "nc", EdgeKind.CONTROL_SS), // A with C
                )
            val nodes = LinkedHashMap<String, Node>()
            defs.forEach { (c, m) ->
                nodes[m.id] = m
                nodes[c.id] = c
            }
            val graph = TtrpGraph(nodes, edges, defs.associate { (c, _) -> c.id to c }.let { LinkedHashMap(it) })

            val exec = ContainerCollapse(InvocationResult(emptyMap(), null, emptyList())).collapse(graph)

            fun waveOf(id: String) = exec.waves.indexOfFirst { id in it }
            waveOf("A") shouldBe waveOf("C") // SS co-launch: same wave.
            waveOf("B") shouldBeGreaterThan waveOf("A") // FS order preserved after the raise.
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
