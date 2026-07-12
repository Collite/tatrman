// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttr.designer.server.rpc.RpcCodes
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures

/**
 * The `ttrm/…` error surface (contracts §4): unknown methods, missing objects,
 * and bad graph scopes come back as JSON-RPC errors with the reserved codes and a
 * structured `data.kind` — the library stays id-free, the host mints the shape (MD5).
 */
class JsonRpcErrorShapeSpec :
    StringSpec({
        val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())

        fun withProtocol(block: suspend io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.() -> Unit) =
            testApplication {
                application { designerServerModule(deps, watch = false) }
                val client = createClient { install(WebSockets) }
                client.webSocket("/ttrm") { block() }
            }

        "unknown method -> -32601" {
            withProtocol {
                rpc(1, "ttrm/nope").errorCode() shouldBe RpcCodes.METHOD_NOT_FOUND
            }
        }

        "getObject with an unknown qname -> -32001 not-found" {
            withProtocol {
                val resp = rpc(2, "ttrm/getObject", buildJsonObject { put("qname", "db.nope.nope") })
                resp.errorCode() shouldBe RpcCodes.NOT_FOUND
                resp.errorKind() shouldBe "not-found"
            }
        }

        "getModelGraph with an unknown scope package -> -32002 bad-scope" {
            withProtocol {
                val resp =
                    rpc(
                        3,
                        "ttrm/getModelGraph",
                        buildJsonObject { put("scope", buildJsonObject { put("package", "does.not.exist") }) },
                    )
                resp.errorCode() shouldBe RpcCodes.BAD_SCOPE
                resp.errorKind() shouldBe "bad-scope"
            }
        }

        "getWorld with an unknown qname -> -32001 WorldNotFound" {
            withProtocol {
                val resp = rpc(4, "ttrm/getWorld", buildJsonObject { put("qname", "acme.worlds.world.ghost") })
                resp.errorCode() shouldBe RpcCodes.NOT_FOUND
                resp.errorKind() shouldBe "WorldNotFound"
            }
        }
    })
