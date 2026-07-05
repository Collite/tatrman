package org.tatrman.ttr.designer.server.rpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Hand-rolled JSON-RPC 2.0 dispatcher (contracts §4). At six methods + one
 * notification a framework dep is unwarranted. One dispatcher instance per WS
 * connection. Batch (JSON array) is NOT supported in v1 → `-32600` (additive to
 * support later without a protocolVersion bump).
 */
class JsonRpcDispatcher {
    private val log = LoggerFactory.getLogger(JsonRpcDispatcher::class.java)
    private val handlers = mutableMapOf<String, suspend (JsonObject) -> JsonElement>()
    private val json = Json { ignoreUnknownKeys = true }

    fun register(
        method: String,
        handler: suspend (JsonObject) -> JsonElement,
    ) {
        handlers[method] = handler
    }

    /** Dispatch one text frame; returns the response frame text, or null for a dropped inbound notification. */
    suspend fun dispatch(frameText: String): String? {
        val root: JsonElement =
            try {
                json.parseToJsonElement(frameText)
            } catch (e: Exception) {
                return json.encodeToString(
                    JsonObject.serializer(),
                    rpcError(null, RpcCodes.PARSE_ERROR, "parse error", "parse-error"),
                )
            }
        if (root is JsonArray) {
            return json.encodeToString(
                JsonObject.serializer(),
                rpcError(null, RpcCodes.INVALID_REQUEST, "batch not supported", "batch-unsupported"),
            )
        }
        val obj =
            root as? JsonObject
                ?: return json.encodeToString(
                    JsonObject.serializer(),
                    rpcError(null, RpcCodes.INVALID_REQUEST, "not an object", "invalid-request"),
                )

        val id = obj["id"]
        val method = obj["method"]?.jsonPrimitive?.content
        if (method == null) {
            return json.encodeToString(
                JsonObject.serializer(),
                rpcError(id, RpcCodes.INVALID_REQUEST, "missing method", "invalid-request"),
            )
        }
        // Inbound notifications (no id) are dropped in v1 (none expected).
        if (id == null) {
            log.debug("dropping inbound notification: {}", method)
            return null
        }
        val handler =
            handlers[method]
                ?: return json.encodeToString(
                    JsonObject.serializer(),
                    rpcError(id, RpcCodes.METHOD_NOT_FOUND, "unknown method: $method", "method-not-found"),
                )
        val params = obj["params"] as? JsonObject ?: JsonObject(emptyMap())

        return try {
            val result = handler(params)
            json.encodeToString(JsonObject.serializer(), rpcSuccess(id, result))
        } catch (e: TtrmRpcException) {
            json.encodeToString(JsonObject.serializer(), rpcError(id, e.code, e.message, e.kind, e.data))
        } catch (e: Exception) {
            log.warn("handler {} failed", method, e)
            json.encodeToString(JsonObject.serializer(), rpcError(id, RpcCodes.INTERNAL, "internal error", "internal"))
        }
    }
}
