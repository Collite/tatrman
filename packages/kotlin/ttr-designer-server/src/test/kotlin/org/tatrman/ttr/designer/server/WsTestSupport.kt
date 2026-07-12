// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal val testJson = Json { ignoreUnknownKeys = true }

/** Send one JSON-RPC request over an open session and return the matching response (skipping notifications). */
internal suspend fun DefaultClientWebSocketSession.rpc(
    id: Int,
    method: String,
    params: JsonObject? = null,
): JsonObject {
    val req =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }
    send(Frame.Text(testJson.encodeToString(JsonObject.serializer(), req)))
    while (true) {
        val frame = incoming.receive()
        if (frame !is Frame.Text) continue
        val obj = testJson.parseToJsonElement(frame.readText()).jsonObject
        if (obj["id"]?.jsonPrimitive?.intOrNull == id) return obj
    }
}

internal fun JsonObject.result(): JsonObject = this["result"]!!.jsonObject

internal fun JsonObject.errorCode(): Int = this["error"]!!.jsonObject["code"]!!.jsonPrimitive.int

internal fun JsonObject.errorKind(): String =
    this["error"]!!
        .jsonObject["data"]!!
        .jsonObject["kind"]!!
        .jsonPrimitive.content
