package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import java.nio.file.Files

/**
 * The Stage-5.1 WS-LSP contract (T5.1.2/5/6/7): a generic WS JSON-RPC client drives the
 * SAME Phase-4 server over `/lsp` — `initialize` → `didOpen(hero)` → `ttrp/getGraph`
 * (authored graph incl. containers + synthesized transfer + source ranges) →
 * `ttrp/getWorld` (engines/executors/storages/staging from the resolved world). Plus
 * reconnect (S24: single *user*, not single *connection*).
 */
class WsLspTransportSpec :
    StringSpec({

        val heroText = readResource("/fixtures/hero.ttrp")
        val heroUri =
            MetadataFixtures
                .erpProjectRoot()
                .resolve("hero.ttrp")
                .toUri()
                .toString()

        fun withLsp(block: suspend DefaultClientWebSocketSession.() -> Unit) =
            testApplication {
                val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())
                application { designerServerModule(deps, watch = false) }
                val client = createClient { install(WebSockets) }
                client.webSocket("/lsp") { block() }
            }

        suspend fun DefaultClientWebSocketSession.initializeAndOpen() {
            rpc(1, "initialize", buildJsonObject { put("processId", 0) }).result()
            notify(
                "textDocument/didOpen",
                buildJsonObject {
                    put(
                        "textDocument",
                        buildJsonObject {
                            put("uri", heroUri)
                            put("languageId", "ttrp")
                            put("version", 1)
                            put("text", heroText)
                        },
                    )
                },
            )
        }

        "initialize returns server capabilities" {
            withLsp {
                val caps = rpc(1, "initialize", buildJsonObject { put("processId", 0) }).result()
                caps.containsKey("capabilities") shouldBe true
            }
        }

        "ttrp/getGraph returns the authored hero graph with containers, a synthesized transfer, and source ranges" {
            withLsp {
                initializeAndOpen()
                val r =
                    rpc(
                        2,
                        "ttrp/getGraph",
                        buildJsonObject {
                            put("uri", heroUri)
                            put("version", 1)
                        },
                    ).result()

                r.containsKey("graph") shouldBe true
                r.containsKey("provenance") shouldBe true
                r.containsKey("derived") shouldBe true

                val graph = r["graph"]!!.jsonObject
                val containers =
                    graph["containers"]!!.jsonArray.map { it.jsonObject["path"]!!.jsonPrimitive.content }
                containers shouldContain "acc_prep"
                containers shouldContain "crunch"

                // A synthesized cross-engine transfer appears as a program leaf.
                val leaves = graph["leaves"]!!.jsonArray.map { it.jsonObject }
                leaves.any { it["kind"]!!.jsonPrimitive.content == "Transfer" } shouldBe true

                // Nodes carry source ranges: at least one crunch member has a non-null range.
                val crunch =
                    graph["containers"]!!.jsonArray.map { it.jsonObject }.first {
                        it["path"]!!.jsonPrimitive.content == "crunch"
                    }
                val crunchNodes = crunch["nodes"]!!.jsonArray.map { it.jsonObject }
                crunchNodes.shouldNotBeEmpty()
                crunchNodes.any { it["range"] != null && it["range"]!!.jsonObject.containsKey("line") } shouldBe true

                // The orchestration overlay carries islands + waves.
                val orch = r["orchestration"]!!.jsonObject
                orch["islands"]!!.jsonArray.shouldNotBeEmpty()
                orch["waves"]!!.jsonArray.shouldNotBeEmpty()
            }
        }

        "ttrp/getWorld returns engines/executors/storages/staging from the resolved world" {
            withLsp {
                initializeAndOpen()
                val r = rpc(3, "ttrp/getWorld", buildJsonObject { put("uri", heroUri) }).result()

                r["world"]!!.jsonPrimitive.content shouldBe "dev"
                val engineNames = r["engines"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
                engineNames shouldContain "erp_pg"
                engineNames shouldContain "polars"
                r.containsKey("executors") shouldBe true
                r.containsKey("storages") shouldBe true
                r.containsKey("staging") shouldBe true
            }
        }

        "ttrp/setLayout then ttrp/getLayout round-trips the sidecar over WS (Stage 5.2)" {
            val tmpDir = Files.createTempDirectory("ws-ttrl")
            val docUri = tmpDir.resolve("hero.ttrp").toUri().toString()
            withLsp {
                val set =
                    rpc(
                        10,
                        "ttrp/setLayout",
                        buildJsonObject {
                            put("uri", docUri)
                            put(
                                "layout",
                                buildJsonObject {
                                    put("version", 1)
                                    put(
                                        "canvases",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("key", "program")
                                                    put("skin", "alteryx-knime")
                                                    put("mode", "manual")
                                                    put(
                                                        "nodes",
                                                        buildJsonArray {
                                                            add(
                                                                buildJsonObject {
                                                                    put("zeta", "acc_prep")
                                                                    put("x", 120.0)
                                                                    put("y", 80.0)
                                                                },
                                                            )
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    ).result()
                set["ok"]!!.jsonPrimitive.content shouldBe "true"

                val get = rpc(11, "ttrp/getLayout", buildJsonObject { put("uri", docUri) }).result()
                get["exists"]!!.jsonPrimitive.content shouldBe "true"
                val program =
                    get["canvases"]!!.jsonArray.map { it.jsonObject }.first {
                        it["key"]!!.jsonPrimitive.content == "program"
                    }
                program["mode"]!!.jsonPrimitive.content shouldBe "manual"
                val node = program["nodes"]!!.jsonArray.first().jsonObject
                node["zeta"]!!.jsonPrimitive.content shouldBe "acc_prep"
            }
        }

        "reconnect: a second WS session gets an independent, working LSP (S24 single user, not single connection)" {
            testApplication {
                val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())
                application { designerServerModule(deps, watch = false) }
                val client = createClient { install(WebSockets) }
                repeat(2) {
                    client.webSocket("/lsp") {
                        val caps = rpc(1, "initialize", buildJsonObject { put("processId", 0) }).result()
                        caps.containsKey("capabilities") shouldBe true
                    }
                }
            }
        }
    })

/** Send a JSON-RPC notification (no id, no response awaited) — the LSP-over-WS wire rule. */
private suspend fun DefaultClientWebSocketSession.notify(
    method: String,
    params: JsonObject,
) {
    val req =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
    send(Frame.Text(testJson.encodeToString(JsonObject.serializer(), req)))
}

private fun readResource(path: String): String =
    WsLspTransportSpec::class.java
        .getResourceAsStream(path)
        ?.readBytes()
        ?.toString(Charsets.UTF_8)
        ?: error("missing test resource: $path")
