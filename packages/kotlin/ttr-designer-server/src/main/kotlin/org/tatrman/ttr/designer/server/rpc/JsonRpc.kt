// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.rpc

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JSON-RPC 2.0 error codes used by the `ttrm/…` protocol (contracts §4).
 */
object RpcCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL = -32603
    const val MODEL_NOT_LOADED = -32000
    const val NOT_FOUND = -32001
    const val BAD_SCOPE = -32002
}

/**
 * A handler-thrown error carrying a JSON-RPC code, a structured `data.kind`, and
 * optional structured fields (MD5 — the server maps the library's id-free results
 * to these; the library never mints ids).
 */
class TtrmRpcException(
    val code: Int,
    val kind: String,
    val data: JsonObject = JsonObject(emptyMap()),
    override val message: String = kind,
) : RuntimeException(message)

/** Build a JSON-RPC success envelope. `id` is echoed verbatim. */
fun rpcSuccess(
    id: JsonElement?,
    result: JsonElement,
): JsonObject =
    buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put("result", result)
    }

/** Build a JSON-RPC error envelope with `error.data = { kind, …fields }`. */
fun rpcError(
    id: JsonElement?,
    code: Int,
    message: String,
    kind: String,
    data: JsonObject = JsonObject(emptyMap()),
): JsonObject =
    buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put(
            "error",
            buildJsonObject {
                put("code", code)
                put("message", message)
                put(
                    "data",
                    JsonObject(mapOf("kind" to JsonPrimitive(kind)) + data),
                )
            },
        )
    }

/** Build a server→client notification frame (no id). */
fun rpcNotification(
    method: String,
    params: JsonObject,
): JsonObject =
    buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", method)
        put("params", params)
    }
