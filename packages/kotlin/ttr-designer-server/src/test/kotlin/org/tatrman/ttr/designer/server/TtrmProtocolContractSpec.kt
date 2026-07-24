// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures

/**
 * End-to-end contract for the `ttrm/…` read methods over a real WebSocket route
 * (contracts §4): handshake, index, graph, object lookup, search, and world
 * resolution against the erp fixture. Every method delegates to
 * MetadataQuery/WorldResolver (MD2/MD5).
 */
class TtrmProtocolContractSpec :
    StringSpec({
        val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())

        fun withProtocol(block: suspend io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.() -> Unit) =
            testApplication {
                application { designerServerModule(deps, watch = false) }
                val client = createClient { install(WebSockets) }
                client.webSocket("/ttrm") { block() }
            }

        "getStatus returns protocolVersion 1 and the repo root" {
            withProtocol {
                val r = rpc(1, "ttrm/getStatus").result()
                r["protocolVersion"]!!.jsonPrimitive.content shouldBe "1"
                r["repoRoot"]!!.jsonPrimitive.content.shouldStartWith("/")
            }
        }

        "getModelIndex lists the erp package and db/er schemas" {
            withProtocol {
                val r = rpc(2, "ttrm/getModelIndex").result()
                val packages = r["packages"]!!.jsonArray.map { it.jsonPrimitive.content }
                packages shouldContainPkg "erp"
                r["schemas"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainPkg "db"
            }
        }

        "getModelIndex carries the §7 writability payload stamped with the index modelVersion" {
            withProtocol {
                val r = rpc(9, "ttrm/getModelIndex").result()
                val w = r["writability"]!!.jsonObject
                // one fingerprint, two payloads — the §7 modelVersion equals the index's own.
                w["modelVersion"]!!.jsonPrimitive.content shouldBe r["modelVersion"]!!.jsonPrimitive.content
                // erp has entities, so the classifier renders a per-entity verdict list with stable qnames.
                val entities = w["entities"]!!.jsonArray
                entities.shouldNotBeEmpty()
                entities.first().jsonObject.containsKey("qname") shouldBe true
            }
        }

        "getModelGraph returns nodes with qname/kind/schema" {
            withProtocol {
                val r = rpc(3, "ttrm/getModelGraph").result()
                val nodes = r["nodes"]!!.jsonArray
                nodes.map { it.jsonObject }.shouldNotBeEmpty()
                val first = nodes.first().jsonObject
                first["qname"]!!.jsonPrimitive.content.isNotEmpty() shouldBe true
                first.containsKey("kind") shouldBe true
                first.containsKey("schema") shouldBe true
            }
        }

        "getObject round-trips a node's qname" {
            withProtocol {
                val someQname =
                    rpc(
                        4,
                        "ttrm/getModelGraph",
                    ).result()["nodes"]!!.jsonArray.first().jsonObject["qname"]!!.jsonPrimitive.content
                val r = rpc(5, "ttrm/getObject", buildJsonObject { put("qname", someQname) }).result()
                r["object"]!!.jsonObject["qname"]!!.jsonPrimitive.content shouldBe someQname
            }
        }

        "search returns hits for a known term" {
            withProtocol {
                val r = rpc(6, "ttrm/search", buildJsonObject { put("query", "acc") }).result()
                r.containsKey("hits") shouldBe true
            }
        }

        "getWorld lists worlds and resolves one to a fingerprint" {
            withProtocol {
                val worlds = rpc(7, "ttrm/getWorld").result()["worlds"]!!.jsonArray.map { it.jsonPrimitive.content }
                worlds.shouldNotBeEmpty()
                val r = rpc(8, "ttrm/getWorld", buildJsonObject { put("qname", worlds.first()) }).result()
                r["fingerprint"]!!.jsonPrimitive.content.shouldStartWith("sha256:")
            }
        }
    })

private infix fun List<String>.shouldContainPkg(value: String) {
    if (value !in this) throw AssertionError("expected $this to contain \"$value\"")
}
