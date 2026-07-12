// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.jsonPrimitive
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * `installTtrmProtocol` is a route-only seam: it can be mounted alongside another
 * protocol on the same engine (the WebSockets plugin installed once) without a
 * plugin-install clash. Proves a future designer-server can add protocols
 * additively without touching this one.
 */
class CoexistingProtocolInstallersSpec :
    StringSpec({
        val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())

        "ttrm and a second protocol coexist on one engine" {
            testApplication {
                application {
                    install(ServerWebSockets)
                    installTtrmProtocol(deps)
                    routing {
                        webSocket("/other") { send(Frame.Text("hi-from-other")) }
                    }
                }
                val client = createClient { install(WebSockets) }

                client.webSocket("/ttrm") {
                    rpc(1, "ttrm/getStatus").result()["protocolVersion"]!!.jsonPrimitive.content shouldBe "1"
                }
                client.webSocket("/other") {
                    (incoming.receive() as Frame.Text).readText() shouldBe "hi-from-other"
                }
            }
        }
    })
