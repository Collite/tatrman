// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.member

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.tatrman.ttr.md.resolve.QualifiedName
import org.tatrman.ttr.metadata.members.MemberSource
import org.tatrman.ttr.metadata.members.MemberSourceUnavailable
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [MemberSource] over ttr-designer-server's `ttrm/…` WS protocol (S6-B): `getMemberDomains` →
 * [publishedDomains], `getMembers` (cursor-paged) → [distinctMembers]. Plugged into
 * [org.tatrman.ttr.metadata.members.DegradingMemberCatalog] it reuses the whole S6-A snapshot +
 * GI-19 degradation machinery — the WS transport is the only new piece.
 *
 * The wire `qname` is the domain's qualified ref (`md.Name`); the compiler's resolver keys members by
 * the **simple** domain name (`underlyingDomain(...)` → `Name`), so [publishedDomains] strips to simple
 * and `getMembers` accepts either form (server strips too). Any connection/transport failure surfaces
 * as [MemberSourceUnavailable] so the degradation ladder maps it (hard error at pass start / held
 * snapshot + stale mid-session). Lives in ttrp-cli, not ttr-metadata (whose runtime classpath bans
 * io.ktor). One short-lived WS session per call over a shared client — [close] shuts the client down.
 */
class WsMemberSource(
    private val wsUrl: String,
) : MemberSource,
    AutoCloseable {
    private val client = HttpClient(CIO) { install(WebSockets) }
    private val json = Json { ignoreUnknownKeys = true }
    private val ids = AtomicInteger()

    override fun publishedDomains(): Set<QualifiedName> =
        session { s ->
            rpc(s, "ttrm/getMemberDomains", null)["domains"]!!
                .jsonArray
                .map {
                    it.jsonObject["qname"]!!
                        .jsonPrimitive.content
                        .substringAfterLast('.')
                }.toSet()
        }

    override fun distinctMembers(domain: QualifiedName): List<String> =
        session { s ->
            val out = mutableListOf<String>()
            var cursor: String? = null
            do {
                val params =
                    buildJsonObject {
                        put("domain", domain)
                        cursor?.let { put("cursor", it) }
                    }
                val res = rpc(s, "ttrm/getMembers", params)
                out += res["members"]!!.jsonArray.map { it.jsonPrimitive.content }
                cursor = (res["nextCursor"] as? JsonPrimitive)?.let { if (it.isString) it.content else null }
            } while (cursor != null)
            out
        }

    override fun close() = client.close()

    /** Open one WS session, run [block], return its value; any failure ⇒ [MemberSourceUnavailable]. */
    private fun <T> session(block: suspend (DefaultClientWebSocketSession) -> T): T =
        try {
            runBlocking {
                var captured: T? = null
                client.webSocket(wsUrl) { captured = block(this) }
                @Suppress("UNCHECKED_CAST")
                captured as T
            }
        } catch (e: Exception) {
            throw MemberSourceUnavailable("member catalog unreachable at $wsUrl: ${e.message}", e)
        }

    private suspend fun rpc(
        session: DefaultClientWebSocketSession,
        method: String,
        params: JsonObject?,
    ): JsonObject {
        val id = ids.incrementAndGet()
        val req =
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                if (params != null) put("params", params)
            }
        session.send(Frame.Text(json.encodeToString(JsonObject.serializer(), req)))
        while (true) {
            val frame = session.incoming.receive()
            if (frame !is Frame.Text) continue
            val obj = json.parseToJsonElement(frame.readText()).jsonObject
            if (obj["id"]?.jsonPrimitive?.intOrNull != id) continue // skip notifications / other ids
            obj["error"]?.let { throw MemberSourceUnavailable("$method failed: $it") }
            return obj["result"]!!.jsonObject
        }
    }
}
