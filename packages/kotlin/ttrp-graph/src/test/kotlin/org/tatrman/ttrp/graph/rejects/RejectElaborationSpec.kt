// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rejects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.expr.CaseWhen
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.model.Branch
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.graph.rewrite.RewriteSupport

/**
 * RJ-P1 / 1.1.1 + 1.1.4 — the contracts-§5 guard-and-branch elaboration. TDD-first: red on
 * pre-1.3 master (no elaboration ⇒ no `_ttrp_v1` guard node), green once stage 1.3 lands the
 * `REJECT_ELABORATION` stratum. Observed through [RewriteSupport.elaborationEngine] so the
 * un-lowered branch/guard/reject Calcs are visible (they are lowered away by full `normalize`).
 */
class RejectElaborationSpec :
    StringSpec({
        fun elaborate(fixture: String): TtrpGraph =
            RewriteSupport.elaborationEngine().normalize(GraphFixtures.graphOf(GraphFixtures.program(fixture))).graph

        // The authored (surviving) calc: the one Calc carrying no synthesized `_ttrp_` column.
        fun authored(g: TtrpGraph): Calc =
            g.nodes.values
                .filterIsInstance<Calc>()
                .single { c -> c.assignments.none { it.name.startsWith("_ttrp_") } }

        fun guard(g: TtrpGraph): Calc? =
            g.nodes.values
                .filterIsInstance<Calc>()
                .firstOrNull { c -> c.assignments.any { it.name == "_ttrp_v1" } }

        fun rejectProj(g: TtrpGraph): Calc? =
            g.nodes.values.filterIsInstance<Calc>().firstOrNull { c ->
                c.assignments.any { it.name == "_ttrp_reject_code" }
            }

        fun outEdge(
            g: TtrpGraph,
            node: String,
            port: String,
        ): Node? = g.edges.firstOrNull { it.from == PortRef(node, port) }?.let { g.nodes[it.to.nodeId] }

        "1.1.1 — a wired reject site elaborates to guard → branch → {original | reject-project}" {
            val g = elaborate("rejects-hero.ttrp")

            // guard: a synthesized Calc adding a boolean `_ttrp_v1` via an internal.* validity fn.
            val guard = guard(g)
            guard.shouldNotBeNull()
            val v1 = guard.assignments.single { it.name == "_ttrp_v1" }.value
            (v1 as FunctionCall).function.value shouldContain "internal."

            // branch: exactly one, predicate over the validity flag; guard feeds it.
            val branch =
                g.nodes.values
                    .filterIsInstance<Branch>()
                    .single()
            branch.predicate.toString() shouldContain "_ttrp_v1"
            outEdge(g, guard.id, PortNames.OUT)!!.id shouldBe branch.id

            // true side → the original op; false side → the reject projection.
            outEdge(g, branch.id, PortNames.TRUE)!!.id shouldBe authored(g).id
            val reject = rejectProj(g)
            reject.shouldNotBeNull()
            outEdge(g, branch.id, PortNames.FALSE)!!.id shouldBe reject.id

            // reject projection carries the row-level code + the failing-expr id (contracts §1).
            reject.assignments.map { it.name } shouldContainExactly listOf("_ttrp_reject_code", "_ttrp_reject_expr")
            reject.assignments
                .single { it.name == "_ttrp_reject_code" }
                .value
                .toString() shouldContain "TTRP-RJ-001"

            // container `rejects` port rewired from the authored calc to the reject projection.
            val c = g.containers.values.single()
            c.portMapping[PortNames.REJECTS] shouldBe PortRef(reject.id, PortNames.OUT)

            // provenance: every synthesized node points back to the authored node (RS-5).
            val authoredId = authored(g).id
            listOf(guard.id, branch.id, reject.id).forEach { g.synthProvenance[it] shouldBe authoredId }
        }

        "1.1.4 — two reject-capable exprs → _ttrp_v1,_ttrp_v2 and a document-order code ladder" {
            val g = elaborate("rejects-multi.ttrp")
            val guard = guard(g)
            guard.shouldNotBeNull()
            guard.assignments.map { it.name } shouldContainExactly listOf("_ttrp_v1", "_ttrp_v2")

            // branch predicate = conjunction of both validity flags.
            val branch =
                g.nodes.values
                    .filterIsInstance<Branch>()
                    .single()
            branch.predicate.toString().let {
                it shouldContain "_ttrp_v1"
                it shouldContain "_ttrp_v2"
            }

            // code CASE ladder in DOCUMENT order: cast (001) before div (007).
            val code = rejectProj(g)!!.assignments.single { it.name == "_ttrp_reject_code" }.value as CaseWhen
            val s = code.toString()
            (s.indexOf("TTRP-RJ-001") < s.indexOf("TTRP-RJ-007")) shouldBe true
        }
    })
