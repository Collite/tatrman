package org.tatrman.ttrp.graph.build

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.model.Branch
import org.tatrman.ttrp.graph.model.PortDirection
import org.tatrman.ttrp.graph.model.PortRef

/** T2.1.4 — containers: closed functions, port mapping, program wiring, fragment shape. */
class ContainerMappingSpec :
    StringSpec({

        "a decomposed fragment container carries its raw fragment AND its lowered members (P6)" {
            val g = GraphFixtures.buildFixture("hero.ttrp").graph
            val accPrep = g.containers.values.first { it.label == "acc_prep" }
            accPrep.fragment!!.tag shouldBe "sql" // raw interior kept (verbatim emit + C2-f)
            accPrep.memberIds.map { g.nodes.getValue(it)::class.simpleName } shouldBe
                listOf("Load", "Filter", "Project") // clause→node decomposition (C2-a-β)
            accPrep.defaultOut() shouldBe "out" // synthetic default out (port-less fragment)
        }

        "container out/err ports map onto internal node ports" {
            val g = GraphFixtures.buildFixture("hero.ttrp").graph
            val crunch = g.containers.values.first { it.label == "crunch" }
            val branch =
                crunch.memberIds
                    .map { g.nodes.getValue(it) }
                    .filterIsInstance<Branch>()
                    .single()
            crunch.portMapping["result"] shouldBe PortRef(branch.id, "true")
            crunch.declaredPorts.filter { it.direction == PortDirection.IN }.map { it.name } shouldBe listOf("accounts")
        }

        "program wiring binds default ports (a -> b.port elision)" {
            val g = GraphFixtures.buildFixture("hero.ttrp").graph
            val accPrep = g.containers.values.first { it.label == "acc_prep" }
            val crunch = g.containers.values.first { it.label == "crunch" }
            // acc_prep -> crunch.accounts : from acc_prep's default out to the named in-port.
            val edge = g.edges.single { it.from.nodeId == accPrep.id && it.to.nodeId == crunch.id }
            edge.from.port shouldBe "out"
            edge.to.port shouldBe "accounts"
        }
    })
