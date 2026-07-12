// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rewrite

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import org.tatrman.ttrp.graph.model.SugarNode

/**
 * T2.3a.6 — property tests (io.kotest.property): termination (bounded), determinism
 * (double-run equality), idempotence (fixed point is fixed), and sugar-freedom. Chains
 * mix sugar (Select/Calc/Distinct) with native ops over polars + erp_pg.
 */
class RewritePropertySpec :
    StringSpec({

        val kinds = listOf("filter", "select", "calc", "distinct", "sort", "limit", "project")
        val chains = Arb.list(Arb.of(kinds), 1..10)
        val targets = Arb.of("polars", "erp_pg")

        "normalization terminates within the measure bound" {
            checkAll(200, chains, targets) { seq, target ->
                val g = RewriteSupport.chain(target, seq)
                val engine = RewriteSupport.engine()
                val bound = engine.measureBound(g)
                engine.normalize(g).iterations shouldBeLessThanOrEqual bound + 1
            }
        }

        "normalization is deterministic (two fresh engines, identical output)" {
            checkAll(200, chains, targets) { seq, target ->
                val g = RewriteSupport.chain(target, seq)
                val a = RewriteSupport.engine().normalize(g)
                val b = RewriteSupport.engine().normalize(g)
                RewriteSupport.signature(a.graph) shouldBe RewriteSupport.signature(b.graph)
                a.log.map { it.rule } shouldBe b.log.map { it.rule }
            }
        }

        "normalization is idempotent (fixed point applies zero further rewrites)" {
            checkAll(200, chains, targets) { seq, target ->
                val g = RewriteSupport.chain(target, seq)
                val once = RewriteSupport.engine().normalize(g)
                val twice = RewriteSupport.engine().normalize(once.graph)
                twice.log shouldBe emptyList()
                RewriteSupport.signature(twice.graph) shouldBe RewriteSupport.signature(once.graph)
            }
        }

        "no SugarNode survives normalization" {
            checkAll(200, chains, targets) { seq, target ->
                val g = RewriteSupport.chain(target, seq)
                RewriteSupport
                    .engine()
                    .normalize(g)
                    .graph.nodes.values
                    .filterIsInstance<SugarNode>() shouldBe
                    emptyList()
            }
        }
    })
