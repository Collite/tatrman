package org.tatrman.ttrp.lsp.viewstate

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.lsp.protocol.CanvasLayoutView
import org.tatrman.ttrp.lsp.protocol.LayoutPayload
import org.tatrman.ttrp.lsp.protocol.NodePosView
import java.nio.file.Files

/**
 * Atomic ζ pair rewrite (T5.2.5): an LSP rename's ζ remaps migrate the sidecar's node keys
 * (and their chains base) in the same operation, positions preserved — no window where text
 * and sidecar disagree.
 */
class PairIntegritySpec :
    StringSpec({

        val service = LayoutService()

        "rename migrates ζ keys + chains base, preserving positions" {
            val graph = ViewStateFixtures.heroGraph()
            val dir = Files.createTempDirectory("ttrl-pair")
            val uri = dir.resolve("hero.ttrp").toUri().toString()

            // Seed a manual sidecar with sales#1/#2 positions.
            service.setLayout(
                uri,
                LayoutPayload(
                    canvases =
                        listOf(
                            CanvasLayoutView(
                                key = "crunch",
                                skin = "enso",
                                mode = "manual",
                                nodes =
                                    listOf(
                                        NodePosView("crunch/sales#1", 10.0, 20.0),
                                        NodePosView("crunch/sales#2", 30.0, 40.0),
                                    ),
                            ),
                        ),
                ),
                graph,
            )

            // Rename sales → sales2 (both generations).
            service.migrateKeys(
                uri,
                listOf("crunch/sales#1" to "crunch/sales2#1", "crunch/sales#2" to "crunch/sales2#2"),
            )

            val back = service.getLayout(uri, null) // null graph: read the raw migrated keys
            val crunch = back.canvases.first { it.key == "crunch" }
            val keys = crunch.nodes.map { it.zeta }
            keys shouldContain "crunch/sales2#1"
            keys shouldContain "crunch/sales2#2"
            keys shouldNotContain "crunch/sales#1"
            // Positions preserved through the migration.
            crunch.nodes.first { it.zeta == "crunch/sales2#2" }.x shouldBe 30.0
        }
    })
