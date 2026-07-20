#!/usr/bin/env kotlin
// SPDX-License-Identifier: Apache-2.0
//
// S7-A6 — a standalone MCP-client demo of the planning-agent loop against a running ttr-md-agent.
// Self-contained (Maven Central deps only); it does NOT depend on the local module. Point it at a
// running agent (see the module README for how to boot one over the shared `sales` model):
//
//     ./gradlew :packages:kotlin:ttr-md-agent:run   # in one shell (MD_AGENT_MODEL_ROOT=…)
//     kotlin packages/kotlin/ttr-md-agent/scripts/agent-loop-demo.main.kts   # in another
//
// The agent ships disconnected by default (live member sourcing is a follow-up), so this demo uses
// `name`-qualified members that resolve offline. A committed sample of its output is in the README.

@file:Repository("https://repo1.maven.org/maven2")
@file:DependsOn("io.modelcontextprotocol:kotlin-sdk-client:0.12.0")
@file:DependsOn("io.ktor:ktor-client-cio:3.3.3")

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking

val url = System.getenv("MD_AGENT_URL") ?: "http://127.0.0.1:3535/mcp"
val model = System.getenv("MD_AGENT_DEFAULT_MODEL") ?: "md"

fun textOf(result: io.modelcontextprotocol.kotlin.sdk.types.CallToolResult): String =
    (result.content.firstOrNull() as? TextContent)?.text ?: "<no text content>"

runBlocking {
    val http = HttpClient(CIO) { install(SSE) }
    val client = http.mcpStreamableHttp(url)
    println("connected to $url")
    println("tools: " + client.listTools().tools.joinToString { it.name })

    // The planning loop: agent tokens → resolve → read back the canonical path + why.
    val path = "sales.name.Kaufland.net"
    println("\n# md_resolve  ($model)  $path")
    println(textOf(client.callTool("md_resolve", mapOf("model" to model, "raw" to path))))

    println("\n# md_explain  ($model)  $path")
    println(textOf(client.callTool("md_explain", mapOf("model" to model, "raw" to path))))

    http.close()
}
