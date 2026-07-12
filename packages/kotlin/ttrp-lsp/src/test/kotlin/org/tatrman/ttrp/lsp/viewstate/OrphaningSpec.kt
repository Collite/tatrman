// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.viewstate

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TtrlCanvas
import org.tatrman.ttr.parser.model.TtrlDocument
import org.tatrman.ttr.parser.model.TtrlMode
import org.tatrman.ttr.parser.model.TtrlNodeEntry

/**
 * Deterministic orphaning + pair integrity (T5.2.5, C1-c-i): an entry keeps its position
 * only if it provably still identifies the same node. Chain-length change orphans the whole
 * name group (the insert-in-middle mis-attach), a missing key orphans that entry, a vanished
 * canvas orphans all its entries + flags the unknown canvas. Never a silent mis-attach.
 */
class OrphaningSpec :
    StringSpec({

        val graph = ViewStateFixtures.heroGraph()

        fun canvas(
            key: String,
            nodes: List<TtrlNodeEntry>,
            chains: Map<String, Int>,
        ) = TtrlCanvas(key, "enso", TtrlMode.MANUAL, nodes, emptyList(), chains, SourceLocation.UNKNOWN)

        fun sidecar(vararg canvases: TtrlCanvas) = TtrlDocument(1, canvases.toList(), "hero.ttrl")

        "unchanged sidecar: nothing orphans" {
            val doc =
                sidecar(
                    canvas(
                        "crunch",
                        listOf(TtrlNodeEntry("crunch/sales#1", 0.0, 0.0), TtrlNodeEntry("crunch/sales#2", 0.0, 0.0)),
                        mapOf("crunch/sales" to 2),
                    ),
                )
            Orphaning.analyze(graph, doc).orphaned.shouldBeEmptyOrphans()
        }

        "chain-length change orphans the WHOLE name group (insert-in-middle mis-attach guard)" {
            // Sidecar recorded chain length 1 for sales, but the graph has 2 → both sales entries orphan,
            // even the one whose exact key (crunch/sales#1) still exists.
            val doc =
                sidecar(
                    canvas(
                        "crunch",
                        listOf(TtrlNodeEntry("crunch/sales#1", 0.0, 0.0)),
                        mapOf("crunch/sales" to 1),
                    ),
                )
            Orphaning.analyze(graph, doc).orphaned shouldContainAll listOf("crunch/sales#1")
        }

        "a missing exact key orphans that entry (rename/delete)" {
            val doc =
                sidecar(
                    canvas(
                        "crunch",
                        listOf(TtrlNodeEntry("crunch/ghost#1", 0.0, 0.0)),
                        mapOf("crunch/ghost" to 1),
                    ),
                )
            Orphaning.analyze(graph, doc).orphaned shouldContainAll listOf("crunch/ghost#1")
        }

        "a vanished canvas orphans all its entries and flags the unknown canvas (TTRP-LAY-003)" {
            val doc =
                sidecar(
                    canvas(
                        "nonexistent_container",
                        listOf(TtrlNodeEntry("nonexistent_container/x#1", 0.0, 0.0)),
                        mapOf("nonexistent_container/x" to 1),
                    ),
                )
            val r = Orphaning.analyze(graph, doc)
            r.unknownCanvases shouldContainAll listOf("nonexistent_container")
            r.orphaned shouldContainAll listOf("nonexistent_container/x#1")
        }

        "never mis-attach: a surviving entry keeps its position, only changed ones orphan" {
            // crunch/sales chain unchanged (2) but a bogus sibling entry is present → only the bogus one orphans.
            val doc =
                sidecar(
                    canvas(
                        "crunch",
                        listOf(
                            TtrlNodeEntry("crunch/sales#2", 0.0, 0.0),
                            TtrlNodeEntry("crunch/bogus#9", 0.0, 0.0),
                        ),
                        mapOf("crunch/sales" to 2, "crunch/bogus" to 1),
                    ),
                )
            val orphaned = Orphaning.analyze(graph, doc).orphaned
            orphaned shouldNotContain "crunch/sales#2"
            orphaned shouldContainAll listOf("crunch/bogus#9")
        }
    })

private fun Set<String>.shouldBeEmptyOrphans() {
    this.isEmpty() shouldBe true
}
