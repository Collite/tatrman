// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

const val MD_AGENT_VERSION: String = "0.1.0"

/**
 * Build the MCP [Server] exposing the three dot-path tools (contracts §9). Read-only, additive; the
 * server advertises `tools` only. Each handler is a one-liner: call the pure [MdAgentTools] adapter,
 * encode the DTO as `TextContent`, and map a [ToolInputException] to an MCP tool error. MDS6: no logic
 * beyond this adaptation.
 */
fun buildMdAgentServer(tools: MdAgentTools): Server {
    val server =
        Server(
            serverInfo = Implementation(name = "ttr-md-agent", version = MD_AGENT_VERSION),
            options =
                ServerOptions(
                    capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
                ),
        )

    server.addTool(
        name = "md_resolve",
        description =
            "Resolve a dot-path (tokens[] or a raw dotted string) against a model into a canonical MD " +
                "path. Returns status resolved | ambiguous | failed with the path, free-dim shape, " +
                "derivation explanation, ambiguity alternatives, or diagnostics.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        put("tokens", arraySchema("string", "Pre-split path components (one atom each)."))
                        put("raw", stringSchema("A raw dotted path; split on '.' respecting quotes/braces/ranges."))
                        put("model", stringSchema("Name of the model to resolve against."))
                        put("mode", enumSchema(listOf("connected", "disconnected"), "Member resolution mode."))
                        put("asof", stringSchema("ISO-8601 instant for calc/asof tokens; defaults to now."))
                    },
                required = listOf("model"),
            ),
    ) { request ->
        toolResult { AgentJson.instance.encodeToString(serializer<ResolveResult>(), tools.resolve(request.arguments)) }
    }

    server.addTool(
        name = "md_explain",
        description =
            "Explain how a path resolves: return the per-token derivation steps and the free-dim shape. " +
                "Input is either a raw dotted path or a canonical path object.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        put("raw", stringSchema("A raw dotted path to explain."))
                        put("path", objectSchema("A canonical path object (as returned by md_resolve)."))
                        put("model", stringSchema("Name of the model."))
                    },
                required = listOf("model"),
            ),
    ) { request ->
        toolResult { AgentJson.instance.encodeToString(serializer<ExplainResult>(), tools.explain(request.arguments)) }
    }

    server.addTool(
        name = "md_list_members",
        description =
            "List the members of a published domain, filtered by an optional prefix and capped at limit. " +
                "Returns the member page and whether more members exist beyond it.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        put("domain", stringSchema("The domain whose members to list."))
                        put("prefix", stringSchema("Only members starting with this prefix."))
                        put("limit", intSchema("Maximum members to return."))
                    },
                required = listOf("domain"),
            ),
    ) { request ->
        toolResult {
            AgentJson.instance.encodeToString(
                serializer<ListMembersResult>(),
                tools.listMembers(request.arguments),
            )
        }
    }

    return server
}

/** Install the agent's streamable-HTTP MCP endpoint (default path `/mcp`) on this application. */
fun Application.mdAgentModule(tools: MdAgentTools) {
    mcpStreamableHttp { buildMdAgentServer(tools) }
}

/** Boot the agent on [host]:[port] and block. Loopback-only by default (S24 precedent). */
fun serveMdAgent(
    host: String,
    port: Int,
    tools: MdAgentTools,
) {
    embeddedServer(CIO, host = host, port = port) { mdAgentModule(tools) }.start(wait = true)
}

// ---- handler + schema helpers ------------------------------------------------------------------

/** Run [encode], returning its JSON as `TextContent`; a [ToolInputException] becomes an MCP tool error. */
private inline fun toolResult(encode: () -> String): CallToolResult =
    try {
        CallToolResult(content = listOf(TextContent(encode())))
    } catch (e: ToolInputException) {
        CallToolResult(content = listOf(TextContent(e.message ?: "bad request")), isError = true)
    }

private fun stringSchema(description: String): JsonObject =
    buildJsonObject {
        put("type", "string")
        put("description", description)
    }

private fun intSchema(description: String): JsonObject =
    buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

private fun objectSchema(description: String): JsonObject =
    buildJsonObject {
        put("type", "object")
        put("description", description)
    }

private fun arraySchema(
    itemType: String,
    description: String,
): JsonObject =
    buildJsonObject {
        put("type", "array")
        put("items", buildJsonObject { put("type", itemType) })
        put("description", description)
    }

private fun enumSchema(
    values: List<String>,
    description: String,
): JsonObject =
    buildJsonObject {
        put("type", "string")
        put(
            "enum",
            kotlinx.serialization.json.buildJsonArray {
                values.forEach {
                    add(
                        kotlinx.serialization.json.JsonPrimitive(it),
                    )
                }
            },
        )
        put("description", description)
    }
