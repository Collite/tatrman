// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import org.tatrman.ttr.md.resolve.fixtures.InMemoryMemberSnapshot
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures

/**
 * S7-A3(b) — the tools over a **real** MCP streamable-HTTP socket. Boots the agent on an ephemeral
 * port and drives it with the SDK's own MCP client (the caller's transport, not a hand-rolled one),
 * proving the whole loop: MCP client → streamable HTTP → tool handler → resolver → DTO on the wire.
 */
class MdAgentHttpSpec :
    FunSpec({
        val models = ModelProvider { name -> if (name == "md") MdFixtures.salesModel() else null }
        val members =
            MemberProvider { name, _ ->
                if (name ==
                    "md"
                ) {
                    InMemoryMemberSnapshot(mapOf("Name" to listOf("Aldi", "Kaufland", "Lidl", "Metro")))
                } else {
                    null
                }
            }
        val tools = MdAgentTools(models, members, defaultModel = "md")

        val server = embeddedServer(ServerCIO, port = 0) { mdAgentModule(tools) }
        val http = HttpClient(ClientCIO) { install(SSE) }
        lateinit var client: Client

        beforeSpec {
            server.start(wait = false)
            val port =
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            client = http.mcpStreamableHttp("http://127.0.0.1:$port/mcp")
        }
        afterSpec {
            http.close()
            server.stop(0, 0)
        }

        /** Call a tool and return its single TextContent payload. */
        suspend fun call(
            name: String,
            args: Map<String, Any?>,
        ): String {
            val result = client.callTool(name, args)
            result.isError shouldBe null
            return (result.content.first() as TextContent).text
        }

        test("tools/list advertises the three md tools") {
            val listed = runBlocking { client.listTools().tools.map { it.name } }
            listed.toSet() shouldBe setOf("md_resolve", "md_explain", "md_list_members")
        }

        test("md_resolve over the wire resolves a member to a canonical path") {
            val json =
                runBlocking {
                    call("md_resolve", mapOf("model" to "md", "mode" to "connected", "raw" to "sales.Kaufland.net"))
                }
            val dto = AgentJson.instance.decodeFromString(ResolveResult.serializer(), json)
            dto.status shouldBe "resolved"
            dto.path?.measure shouldBe "net"
        }

        test("md_list_members over the wire pages a published domain") {
            val json = runBlocking { call("md_list_members", mapOf("domain" to "Name", "limit" to 2)) }
            val dto = AgentJson.instance.decodeFromString(ListMembersResult.serializer(), json)
            dto.members shouldBe listOf("Aldi", "Kaufland")
            dto.truncated shouldBe true
        }

        test("md_explain over the wire returns derivation steps for a raw qualified path") {
            val json = runBlocking { call("md_explain", mapOf("model" to "md", "raw" to "sales.name.Kaufland.net")) }
            val dto = AgentJson.instance.decodeFromString(ExplainResult.serializer(), json)
            dto.explanation.shouldNotBeEmpty()
        }
    })
