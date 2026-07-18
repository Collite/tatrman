// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rejects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.model.Branch
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.graph.rewrite.RewriteSupport

/**
 * RJ-P1 / 1.1.3 — the partition invariant holds by construction (contracts §5): the branch
 * predicate is the conjunction of every `_ttrp_v*`, both sides are fed from the one guard, the
 * guard has no consumer other than the branch, and every validity column is a total boolean.
 */
class RejectPartitionSpec :
    StringSpec({
        fun elaborate(fixture: String): TtrpGraph =
            RewriteSupport.elaborationEngine().normalize(GraphFixtures.graphOf(GraphFixtures.program(fixture))).graph

        "1.1.3 — guard feeds exactly the branch; predicate is the conjunction of all _ttrp_v*" {
            val g = elaborate("rejects-multi.ttrp")
            val guard =
                g.nodes.values.filterIsInstance<Calc>().firstOrNull { c ->
                    c.assignments.any {
                        it.name ==
                            "_ttrp_v1"
                    }
                }
            guard.shouldNotBeNull()
            val branch =
                g.nodes.values
                    .filterIsInstance<Branch>()
                    .single()

            // predicate mentions every validity flag the guard produces (conjunction).
            val validityCols = guard.assignments.map { it.name }.filter { it.startsWith("_ttrp_v") }
            validityCols.forEach { branch.predicate.toString() shouldContain it }

            // the guard's only consumer is the branch (no third reader).
            g.edges
                .filter { it.from == PortRef(guard.id, PortNames.OUT) }
                .map { it.to.nodeId }
                .toSet() shouldBe setOf(branch.id)

            // every validity column is computed by a total, never-null internal boolean fn.
            guard.assignments
                .filter { it.name.startsWith("_ttrp_v") }
                .forEach { (it.value as FunctionCall).function.value shouldContain "internal." }
        }
    })
