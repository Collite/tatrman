// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures

/**
 * The server binds a real socket on the loopback interface only (S24: no auth, so
 * loopback is the security boundary) and serves the protocol end-to-end —
 * `getStatus` + `getWorld` green over a real WebSocket to `127.0.0.1`. Uses an
 * OS-assigned port (`port = 0`) to avoid clashes.
 */
class LoopbackBindingSpec :
    StringSpec({
        "binds 127.0.0.1 and serves getStatus + getWorld over a real socket" {
            val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())
            val server =
                embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                    designerServerModule(deps, watch = false)
                }
            server.start(wait = false)
            try {
                val connector =
                    runBlocking {
                        server.engine
                            .resolvedConnectors()
                            .single()
                    }
                connector.host shouldBe "127.0.0.1" // S24: loopback only, never 0.0.0.0
                val port = connector.port
                val client = HttpClient(ClientCIO) { install(WebSockets) }
                runBlocking {
                    client.webSocket(host = "127.0.0.1", port = port, path = "/ttrm") {
                        rpc(1, "ttrm/getStatus").result()["protocolVersion"]!!.jsonPrimitive.content shouldBe "1"
                        val worlds = rpc(2, "ttrm/getWorld").result()["worlds"]!!.jsonArray
                        val firstWorld = worlds.first().jsonPrimitive.content
                        val fp =
                            rpc(3, "ttrm/getWorld", buildJsonObject { put("qname", firstWorld) })
                                .result()["fingerprint"]!!
                                .jsonPrimitive.content
                        fp.shouldStartWith("sha256:")
                    }
                }
                client.close()
            } finally {
                server.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
            }
        }
    })
