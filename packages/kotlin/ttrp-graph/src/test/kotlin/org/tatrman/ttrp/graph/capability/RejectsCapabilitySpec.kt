// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.capability

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.graph.model.Aggregation
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.Edge
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Port
import org.tatrman.ttrp.graph.model.PortDirection
import org.tatrman.ttrp.graph.model.PortKind
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.graph.movement.MovementSynthesizer
import org.tatrman.ttrp.graph.rewrite.RejectEscalation
import org.tatrman.ttrp.project.RejectsInSql

private val L = SourceLocation.UNKNOWN

/**
 * RJ-P2 2.1.4/2.1.6 — rejects capability check + the `[ttrp] rejects-in-sql` knob resolution.
 * A hand-built three-container, two-engine world: the reject cluster's container `rj` sits between
 * an upstream `up` and a downstream `sink`, all initially on `erp_pg`.
 */
class RejectsCapabilitySpec :
    StringSpec({

        val realBound = org.tatrman.ttrp.graph.rewrite.RewriteSupport.bound

        // The elaborated cluster: an authored calc `n_calc` + its synth guard carrying the
        // `internal.is_castable(customer, "int64")` validity call (text->int64 ⇒ TTRP-RJ-001).
        fun guardCall(): FunctionCall =
            FunctionCall(
                CatalogId("internal.is_castable"),
                listOf(ColumnRef(null, "customer", L), Literal(LiteralValue.Str("int64"), L)),
                L,
            )

        fun world(): TtrpGraph {
            val up = Project("u1", "up#1", L)
            val calc = Calc("n_calc", "checked#1", L)
            val guard =
                Calc("n_calc_guard", "checked_guard", L, assignments = listOf(Aggregation("_ttrp_v1", guardCall())))
            val sink = Project("s1", "sink#1", L)

            fun container(
                id: String,
                member: List<String>,
            ) = Container(
                id = id,
                label = id,
                location = L,
                target = "erp_pg",
                memberIds = member,
                declaredPorts =
                    listOf(
                        Port(PortNames.IN, PortKind.DATA, PortDirection.IN),
                        Port(PortNames.OUT, PortKind.DATA, PortDirection.OUT),
                    ),
                portMapping = emptyMap(),
            )
            val cUp = container("up", listOf("u1"))
            val cRj = container("rj", listOf("n_calc", "n_calc_guard"))
            val cSink = container("sink", listOf("s1"))

            val edges =
                listOf(
                    // up -> rj (cross-container boundary)
                    Edge(PortRef("u1", PortNames.OUT), PortRef("n_calc_guard", PortNames.IN), EdgeKind.DATA),
                    // guard -> calc (internal to rj)
                    Edge(PortRef("n_calc_guard", PortNames.OUT), PortRef("n_calc", PortNames.IN), EdgeKind.DATA),
                    // rj -> sink (cross-container boundary)
                    Edge(PortRef("n_calc", PortNames.OUT), PortRef("s1", PortNames.IN), EdgeKind.DATA),
                )
            val nodes = LinkedHashMap<String, Node>()
            listOf(up, guard, calc, sink, cUp, cRj, cSink).forEach { nodes[it.id] = it }
            return TtrpGraph(nodes, edges, linkedMapOf("up" to cUp, "rj" to cRj, "sink" to cSink))
                .copy(synthProvenance = mapOf("n_calc_guard" to "n_calc"))
        }

        /** A copy of [bound] with one engine forced to `rejects.produces = false`. */
        fun withProducesFalse(
            bound: BoundWorld,
            engine: String,
        ): BoundWorld =
            bound.copy(
                engines =
                    bound.engines.mapValues { (name, be) ->
                        if (name == engine) be.copy(manifest = be.manifest.copy(rejects = RejectsSupport.NONE)) else be
                    },
            )

        "a reject cluster on a produces:true engine is not a miss (canonical guard is implementable)" {
            RejectsCapabilityChecker(realBound).check(world()) shouldBe emptyList()
        }

        "a reject cluster on a produces:false engine is a miss keyed to the authored node" {
            val bound = withProducesFalse(realBound, "erp_pg")
            val misses = RejectsCapabilityChecker(bound).check(world())
            misses shouldHaveSize 1
            misses.single().authoredNodeId shouldBe "n_calc"
            misses.single().engine shouldBe "erp_pg"
            misses.single().function shouldBe "cast"
            misses.single().typePair shouldBe "text->int64"
        }

        "knob=produce turns a miss into a TTRP-RJ-106 compile error (guard unimplementable)" {
            val bound = withProducesFalse(realBound, "erp_pg")
            val misses = RejectsCapabilityChecker(bound).check(world())
            val r = RejectEscalation.resolve(world(), bound, RejectsInSql.PRODUCE, misses)
            r.graph shouldBe world() // no graph change
            r.diagnostics.map { it.id.id } shouldBe listOf("TTRP-RJ-106")
            r.diagnostics.single().severity shouldBe Severity.ERROR
        }

        "knob=error turns a miss into a TTRP-RJ-106 compile error (always)" {
            val bound = withProducesFalse(realBound, "erp_pg")
            val misses = RejectsCapabilityChecker(bound).check(world())
            val r = RejectEscalation.resolve(world(), bound, RejectsInSql.ERROR, misses)
            r.diagnostics.map { it.id.id } shouldBe listOf("TTRP-RJ-106")
            r.diagnostics.single().severity shouldBe Severity.ERROR
        }

        "knob=escalate retargets the whole cluster container to a capable engine + TTRP-RJ-102" {
            val bound = withProducesFalse(realBound, "erp_pg") // polars stays produces:true ⇒ the fallback
            val misses = RejectsCapabilityChecker(bound).check(world())
            val r = RejectEscalation.resolve(world(), bound, RejectsInSql.ESCALATE, misses)

            r.diagnostics.map { it.id.id } shouldBe listOf("TTRP-RJ-102")
            r.diagnostics.single().severity shouldBe Severity.WARNING
            // The reject cluster's container moved as one unit; the others stayed.
            r.graph.containers["rj"]!!.target shouldBe "polars"
            r.graph.containers["up"]!!.target shouldBe "erp_pg"
            r.graph.containers["sink"]!!.target shouldBe "erp_pg"
        }

        "escalation makes movement wrap the cluster at its container boundary, not its internal nodes" {
            val bound = withProducesFalse(realBound, "erp_pg")
            val misses = RejectsCapabilityChecker(bound).check(world())
            val escalated = RejectEscalation.resolve(world(), bound, RejectsInSql.ESCALATE, misses).graph

            val moved = MovementSynthesizer(bound, "stage").synthesize(escalated)
            // Exactly the two cross-engine container boundaries (up->rj, rj->sink) are wrapped —
            // NOT the internal guard->calc edge (both now on polars).
            moved.transferIds shouldHaveSize 2
            val transfers =
                moved.graph.nodes.values
                    .filterIsInstance<org.tatrman.ttrp.graph.model.Transfer>()
            transfers shouldHaveSize 2
        }
    })
