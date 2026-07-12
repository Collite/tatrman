// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.viewstate

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Deterministic auto-layout (T5.2.6): longest-path layers + exact-rational barycenter
 * ordering. Identical output across runs and across any node-insertion-order permutation
 * of the same graph (P2-mandatory), plus the hero snapshot.
 */
class AutoLayoutSpec :
    StringSpec({

        // A → B → D, A → C → D  (diamond): layers 0,1,1,2.
        val nodes = listOf("A", "B", "C", "D")
        val edges = listOf("A" to "B", "A" to "C", "B" to "D", "C" to "D")

        "longest-path layering ranks the diamond 0/1/1/2" {
            val out = AutoLayout.layout(nodes, edges)
            out.getValue("A").layer shouldBe 0
            out.getValue("B").layer shouldBe 1
            out.getValue("C").layer shouldBe 1
            out.getValue("D").layer shouldBe 2
        }

        "identical output across 100 runs" {
            val first = AutoLayout.layout(nodes, edges)
            repeat(100) { AutoLayout.layout(nodes, edges) shouldBe first }
        }

        "insertion-order permutations of the same graph give identical coordinates" {
            val canonical = AutoLayout.layout(nodes, edges)
            listOf(
                listOf("D", "C", "B", "A"),
                listOf("C", "A", "D", "B"),
                listOf("B", "D", "A", "C"),
            ).forEach { perm ->
                AutoLayout.layout(perm, edges.shuffledDeterministic()) shouldBe canonical
            }
        }

        "in-layer order is barycenter-then-ζ (B before C at layer 1)" {
            val out = AutoLayout.layout(nodes, edges)
            // B and C share layer 1; both have the single upstream A (index 0) → tie broken by ζ.
            out.getValue("B").index shouldBe 0
            out.getValue("C").index shouldBe 1
        }

        "hero canvases each produce a stable, non-empty layout" {
            val graph = ViewStateFixtures.heroGraph()
            val layouts = CanvasGraphs.autoLayouts(graph)
            val program = layouts.getValue(ZetaKeys.PROGRAM_CANVAS)
            program shouldNotBe emptyMap<String, AbstractCoord>()
            // Deterministic: recomputing yields byte-identical coordinates.
            CanvasGraphs.autoLayouts(graph) shouldBe layouts
        }
    })

/** A fixed reordering (no RNG in workflow scripts / tests that must stay deterministic). */
private fun <T> List<T>.shuffledDeterministic(): List<T> = asReversed().toList()
