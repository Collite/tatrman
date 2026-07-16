// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TtrlCanvas
import org.tatrman.ttr.parser.model.TtrlDocument
import org.tatrman.ttr.parser.model.TtrlMode
import org.tatrman.ttr.parser.model.TtrlNodeEntry
import org.tatrman.ttr.writer.TtrlWriter
import java.nio.file.Files

/**
 * `ttrm/getLayout` (T1 — TP-5): sidecar-absent, sidecar-present, and orphan-detection
 * cases. The erp fixture (`MetadataFixtures`) has no `.ttrg` files of its own (it's a
 * `.ttrm`-only model project), so the `.ttrg`/`.ttrl` pair here lives in a throwaway temp
 * dir — `ttrm/getLayout` resolves `uri` directly, it doesn't require the file to sit under
 * the repo's `models/` storage root.
 */
class TtrmLayoutSpec :
    StringSpec({
        val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())

        fun withProtocol(block: suspend DefaultClientWebSocketSession.() -> Unit) =
            testApplication {
                application { designerServerModule(deps, watch = false) }
                val client = createClient { install(WebSockets) }
                client.webSocket("/ttrm") { block() }
            }

        "getLayout on a .ttrg with no sidecar returns exists=false, not an error" {
            withProtocol {
                val tmp = Files.createTempDirectory("ttrm-layout-spec")
                val ttrg = tmp.resolve("no_sidecar.ttrg")
                Files.writeString(ttrg, "graph no_sidecar {\n  model: er,\n  objects: []\n}\n")

                val r = rpc(1, "ttrm/getLayout", buildJsonObject { put("uri", ttrg.toUri().toString()) }).result()
                r["exists"]!!.jsonPrimitive.content shouldBe "false"
                r["canvases"]!!.jsonArray.size shouldBe 0
            }
        }

        "getLayout on a .ttrg with a sidecar returns its canvases, keyed by qname" {
            withProtocol {
                val realQname =
                    rpc(2, "ttrm/getModelGraph")
                        .result()["nodes"]!!
                        .jsonArray
                        .first()
                        .jsonObject["qname"]!!
                        .jsonPrimitive.content

                val tmp = Files.createTempDirectory("ttrm-layout-spec")
                val ttrg = tmp.resolve("with_sidecar.ttrg")
                Files.writeString(
                    ttrg,
                    "graph with_sidecar {\n  model: db,\n  objects: [$realQname]\n}\n",
                )
                val ttrl = tmp.resolve("with_sidecar.ttrl")
                val doc =
                    TtrlDocument(
                        version = 1,
                        canvases =
                            listOf(
                                TtrlCanvas(
                                    key = "with_sidecar",
                                    skin = null,
                                    mode = TtrlMode.MANUAL,
                                    nodes = listOf(TtrlNodeEntry(realQname, 320.0, 180.0)),
                                    collapsed = emptyList(),
                                    chains = emptyMap(),
                                    location = SourceLocation.UNKNOWN,
                                ),
                            ),
                        sourceFile = ttrl.toString(),
                    )
                Files.writeString(ttrl, TtrlWriter.write(doc))

                val r = rpc(3, "ttrm/getLayout", buildJsonObject { put("uri", ttrg.toUri().toString()) }).result()
                r["exists"]!!.jsonPrimitive.content shouldBe "true"
                val canvases = r["canvases"]!!.jsonArray
                canvases.size shouldBe 1
                val canvas = canvases.first().jsonObject
                canvas["key"]!!.jsonPrimitive.content shouldBe "with_sidecar"
                canvas["mode"]!!.jsonPrimitive.content shouldBe "manual"
                val nodes = canvas["nodes"]!!.jsonArray
                nodes.size shouldBe 1
                nodes
                    .first()
                    .jsonObject["qname"]!!
                    .jsonPrimitive.content shouldBe realQname
                r["orphaned"]!!.jsonArray.size shouldBe 0
            }
        }

        "getLayout flags a sidecar node whose qname no longer exists in the model" {
            withProtocol {
                val tmp = Files.createTempDirectory("ttrm-layout-spec")
                val ttrg = tmp.resolve("stale.ttrg")
                Files.writeString(ttrg, "graph stale {\n  model: er,\n  objects: []\n}\n")
                val ttrl = tmp.resolve("stale.ttrl")
                val doc =
                    TtrlDocument(
                        version = 1,
                        canvases =
                            listOf(
                                TtrlCanvas(
                                    key = "stale",
                                    skin = null,
                                    mode = TtrlMode.MANUAL,
                                    nodes = listOf(TtrlNodeEntry("erp.db.nonexistent_orphan", 5.0, 5.0)),
                                    collapsed = emptyList(),
                                    chains = emptyMap(),
                                    location = SourceLocation.UNKNOWN,
                                ),
                            ),
                        sourceFile = ttrl.toString(),
                    )
                Files.writeString(ttrl, TtrlWriter.write(doc))

                val r = rpc(4, "ttrm/getLayout", buildJsonObject { put("uri", ttrg.toUri().toString()) }).result()
                val orphaned = r["orphaned"]!!.jsonArray.map { it.jsonPrimitive.content }
                orphaned shouldBe listOf("erp.db.nonexistent_orphan")
            }
        }

        "setLayout writes a fresh sidecar, and getLayout reads back exactly what was written (T3.2.9)" {
            withProtocol {
                val tmp = Files.createTempDirectory("ttrm-layout-spec")
                val ttrg = tmp.resolve("set.ttrg")
                Files.writeString(ttrg, "graph set {\n  model: er,\n  objects: []\n}\n")
                val uri = ttrg.toUri().toString()

                val canvasesParam =
                    kotlinx.serialization.json.JsonArray(
                        listOf(
                            buildJsonObject {
                                put("key", "set")
                                put("mode", "manual")
                                put(
                                    "nodes",
                                    kotlinx.serialization.json.JsonArray(
                                        listOf(
                                            buildJsonObject {
                                                put("qname", "er.entity.a")
                                                put("x", 10)
                                                put("y", 20)
                                            },
                                        ),
                                    ),
                                )
                            },
                        ),
                    )
                val setResult =
                    rpc(
                        5,
                        "ttrm/setLayout",
                        buildJsonObject {
                            put("uri", uri)
                            put("canvases", canvasesParam)
                        },
                    ).result()
                setResult["ok"]!!.jsonPrimitive.content shouldBe "true"

                val readBack = rpc(6, "ttrm/getLayout", buildJsonObject { put("uri", uri) }).result()
                readBack["exists"]!!.jsonPrimitive.content shouldBe "true"
                val canvas = readBack["canvases"]!!.jsonArray.first().jsonObject
                canvas["key"]!!.jsonPrimitive.content shouldBe "set"
                canvas["mode"]!!.jsonPrimitive.content shouldBe "manual"
                val node = canvas["nodes"]!!.jsonArray.first().jsonObject
                node["qname"]!!.jsonPrimitive.content shouldBe "er.entity.a"
                // x/y are wire-typed Double (unlike the .ttrl TEXT writer, which special-cases
                // integral coords to avoid diff noise in a version-controlled file — the JSON
                // wire has no such concern, and a JS/TS client can't tell 10 from 10.0 anyway).
                node["x"]!!.jsonPrimitive.content shouldBe "10.0"
                node["y"]!!.jsonPrimitive.content shouldBe "20.0"
            }
        }

        "setLayout on an already-sidecar'd graph overwrites wholesale (writer isolation)" {
            withProtocol {
                val tmp = Files.createTempDirectory("ttrm-layout-spec")
                val ttrg = tmp.resolve("overwrite.ttrg")
                Files.writeString(ttrg, "graph overwrite {\n  model: er,\n  objects: []\n}\n")
                val uri = ttrg.toUri().toString()
                val ttrl = tmp.resolve("overwrite.ttrl")
                Files.writeString(
                    ttrl,
                    TtrlWriter.write(
                        TtrlDocument(
                            1,
                            listOf(
                                TtrlCanvas(
                                    "overwrite",
                                    null,
                                    TtrlMode.MANUAL,
                                    listOf(TtrlNodeEntry("er.entity.old", 1.0, 1.0)),
                                    emptyList(),
                                    emptyMap(),
                                    SourceLocation.UNKNOWN,
                                ),
                            ),
                            ttrl.toString(),
                        ),
                    ),
                )

                val newCanvases =
                    kotlinx.serialization.json.JsonArray(
                        listOf(
                            buildJsonObject {
                                put("key", "overwrite")
                                put("mode", "manual")
                                put(
                                    "nodes",
                                    kotlinx.serialization.json.JsonArray(
                                        listOf(
                                            buildJsonObject {
                                                put("qname", "er.entity.new")
                                                put("x", 99)
                                                put("y", 99)
                                            },
                                        ),
                                    ),
                                )
                            },
                        ),
                    )
                rpc(
                    7,
                    "ttrm/setLayout",
                    buildJsonObject {
                        put("uri", uri)
                        put("canvases", newCanvases)
                    },
                )

                val readBack = rpc(8, "ttrm/getLayout", buildJsonObject { put("uri", uri) }).result()
                val qnames =
                    readBack["canvases"]!!
                        .jsonArray
                        .first()
                        .jsonObject["nodes"]!!
                        .jsonArray
                        .map { it.jsonObject["qname"]!!.jsonPrimitive.content }
                qnames shouldBe listOf("er.entity.new")
            }
        }
    })
