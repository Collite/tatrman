// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Branch
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.Display
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * Hero component test (T2.1.7): both heroes build into validated graphs whose
 * node/edge inventory is pinned by exact assertions (not snapshots — written out so
 * review reads them). No manifests/rewrites yet — those are Stages 2.2/2.3.
 */
class HeroGraphSpec :
    StringSpec({

        fun containerByLabel(
            g: TtrpGraph,
            label: String,
        ): Container = g.containers.values.first { it.label == label }

        fun members(
            g: TtrpGraph,
            c: Container,
        ): List<Node> = c.memberIds.map { g.nodes.getValue(it) }

        "canonical hero builds a clean two-container graph with the pinned inventory" {
            val r = GraphFixtures.buildFixture("hero.ttrp")
            r.allErrorIds shouldBe emptySet()
            val g = r.graph

            // Two containers: acc_prep (fragment sql on erp_pg) + crunch (flow on polars).
            g.containers.values
                .map { it.label }
                .toSet() shouldBe setOf("acc_prep", "crunch")
            val accPrep = containerByLabel(g, "acc_prep")
            accPrep.target shouldBe "erp_pg"
            accPrep.fragment.shouldNotBeNull()
            accPrep.fragment!!.tag shouldBe "sql" // still carries the raw fragment (emitted verbatim, C2-f)
            // P6: the `"""sql` interior decomposes to Load -> Filter -> Project members.
            members(g, accPrep).map { it::class.simpleName } shouldContainExactly listOf("Load", "Filter", "Project")

            val crunch = containerByLabel(g, "crunch")
            crunch.target shouldBe "polars"
            val kinds = members(g, crunch).map { it::class.simpleName }
            kinds.count { it == "Load" } shouldBe 1
            kinds.count { it == "Filter" } shouldBe 1
            kinds.count { it == "Join" } shouldBe 1
            kinds.count { it == "Aggregate" } shouldBe 1
            kinds.count { it == "Branch" } shouldBe 1
            kinds.size shouldBe 5

            val load = members(g, crunch).filterIsInstance<Load>().single()
            load.source shouldBe "files.sales_2026"
            load.schemaRef shouldBe "sales_csv"

            val join = members(g, crunch).filterIsInstance<Join>().single()
            join.type shouldBe JoinType.INNER

            val agg = members(g, crunch).filterIsInstance<Aggregate>().single()
            agg.groupBy shouldBe listOf("region")
            agg.aggregations.map { it.name } shouldContainExactly listOf("total", "avg_amt")

            val branch = members(g, crunch).filterIsInstance<Branch>().single()
            // Container out/err ports map onto internal node ports (B-T9).
            crunch.portMapping["result"] shouldBe
                org.tatrman.ttrp.graph.model
                    .PortRef(branch.id, "true")
            crunch.portMapping["low"] shouldBe
                org.tatrman.ttrp.graph.model
                    .PortRef(branch.id, "false")
            crunch.portMapping["rejects"] shouldBe
                org.tatrman.ttrp.graph.model
                    .PortRef(join.id, "rejects")

            // Program-level leaves: one Display, two Store.
            g.nodes.values
                .filterIsInstance<Display>()
                .map { it.name } shouldBe listOf("main_result")
            g.nodes.values
                .filterIsInstance<Store>()
                .size shouldBe 2

            // Cross-container data edge acc_prep → crunch.accounts (movement synthesis is 2.3b).
            val crossing =
                g.edges.filter {
                    it.from.nodeId == accPrep.id && it.to.nodeId == crunch.id && it.to.port == "accounts"
                }
            crossing.size shouldBe 1
            crossing.single().kind shouldBe EdgeKind.DATA

            // Zero control edges.
            g.edges.count { it.kind == EdgeKind.CONTROL_FS || it.kind == EdgeKind.CONTROL_SS } shouldBe 0
        }

        "er-hero builds clean with provenance populated on the relation-join and the filter" {
            val r = GraphFixtures.buildFixture("hero-er.ttrp")
            r.allErrorIds shouldBe emptySet()
            val g = r.graph

            val crunch = containerByLabel(g, "crunch")
            val join = members(g, crunch).filterIsInstance<Join>().single()
            // E-d: the relation-join carries provenance from `on: relation customer_sales`.
            join.provenance.shouldNotBeNull()
            join.provenance!!.originQname shouldBe "er.relation.customer_sales"
            // And the synthesized port-qualified condition rode onto the node.
            join.on.shouldNotBeNull()

            // The customerType filter carries er provenance (customerType ← customers.customer_type).
            val filters = members(g, crunch).filterIsInstance<Filter>()
            filters.any { it.provenance != null } shouldBe true
        }
    })
