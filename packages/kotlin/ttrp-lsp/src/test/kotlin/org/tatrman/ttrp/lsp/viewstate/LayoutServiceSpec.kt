package org.tatrman.ttrp.lsp.viewstate

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.lsp.protocol.CanvasLayoutView
import org.tatrman.ttrp.lsp.protocol.LayoutPayload
import org.tatrman.ttrp.lsp.protocol.NodePosView
import java.nio.file.Files

/**
 * `ttrp/getLayout` / `ttrp/setLayout` behind [LayoutService] (T5.2.4): filename pairing,
 * missing-sidecar ⇒ empty, wholesale byte-stable write, round-trip read, and the
 * reject rules (nodes on an auto canvas).
 */
class LayoutServiceSpec :
    StringSpec({

        val service = LayoutService()

        "sidecar pairing: x.ttrp → x.ttrl, report.ttr.sql → report.ttrl" {
            service.sidecarPath("file:///p/hero.ttrp")!!.fileName.toString() shouldBe "hero.ttrl"
            service.sidecarPath("file:///p/report.ttr.sql")!!.fileName.toString() shouldBe "report.ttrl"
            service.sidecarPath("file:///p/x.ttr.py")!!.fileName.toString() shouldBe "x.ttrl"
        }

        "missing sidecar → empty layout (all canvases implicitly auto)" {
            val dir = Files.createTempDirectory("ttrl-missing")
            val uri = dir.resolve("hero.ttrp").toUri().toString()
            val r = service.getLayout(uri, null)
            r.exists shouldBe false
            r.canvases.isEmpty() shouldBe true
        }

        "setLayout writes wholesale; getLayout reads it back byte-stably" {
            val graph = ViewStateFixtures.heroGraph()
            val dir = Files.createTempDirectory("ttrl-rt")
            val uri = dir.resolve("hero.ttrp").toUri().toString()
            val payload =
                LayoutPayload(
                    version = 1,
                    canvases =
                        listOf(
                            CanvasLayoutView(
                                key = "program",
                                skin = "alteryx-knime",
                                mode = "manual",
                                nodes =
                                    listOf(
                                        NodePosView("acc_prep", 120.0, 80.0),
                                        NodePosView("crunch", 420.0, 80.0),
                                    ),
                            ),
                            CanvasLayoutView(key = "crunch", skin = "enso", mode = "auto"),
                        ),
                )
            service.setLayout(uri, payload, graph).ok shouldBe true

            val back = service.getLayout(uri, graph)
            back.exists shouldBe true
            val program = back.canvases.first { it.key == "program" }
            program.mode shouldBe "manual"
            program.nodes.first { it.zeta == "acc_prep" }.x shouldBe 120.0

            // Byte-stable: writing the round-tripped payload again yields identical file bytes.
            val bytes1 = Files.readString(service.sidecarPath(uri)!!)
            service.setLayout(uri, payload, graph)
            val bytes2 = Files.readString(service.sidecarPath(uri)!!)
            bytes2 shouldBe bytes1
        }

        "setLayout rejects nodes on an auto canvas" {
            val dir = Files.createTempDirectory("ttrl-reject")
            val uri = dir.resolve("hero.ttrp").toUri().toString()
            val payload =
                LayoutPayload(
                    canvases =
                        listOf(
                            CanvasLayoutView(
                                key = "crunch",
                                mode = "auto",
                                nodes = listOf(NodePosView("crunch/sales#1", 1.0, 2.0)),
                            ),
                        ),
                )
            val r = service.setLayout(uri, payload, ViewStateFixtures.heroGraph())
            r.ok shouldBe false
            r.diagnostics.joinToString(" ") { it.message } shouldContain "auto"
        }
    })
