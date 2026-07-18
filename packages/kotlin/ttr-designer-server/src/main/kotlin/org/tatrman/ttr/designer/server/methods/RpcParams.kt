// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.methods

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttr.designer.server.rpc.RpcCodes
import org.tatrman.ttr.designer.server.rpc.TtrmRpcException
import java.net.URI
import java.nio.file.Path

// Shared JSON-RPC param helpers (FO-21, FO-P0.S2.T5). Extracted from
// registerTtrmWriteMethods so both the read handlers (registerTtrmReadMethods)
// and the edit handlers (registerTtrmEditMethods) reuse them across the split.

/** Resolve a `file://` (or bare) URI param to a [Path], or raise `invalid-params`. */
internal fun uriToPath(uri: String): Path =
    runCatching { Path.of(URI(uri)) }.getOrNull() ?: runCatching { Path.of(uri) }.getOrNull()
        ?: throw TtrmRpcException(RpcCodes.INVALID_PARAMS, "invalid-params", buildJsonObject { put("uri", uri) })

/** Read a required string param, or raise `invalid-params` naming the missing param. */
internal fun requireParam(
    params: JsonObject,
    name: String,
): String =
    (params[name] as? JsonPrimitive)?.content
        ?: throw TtrmRpcException(RpcCodes.INVALID_PARAMS, "invalid-params", buildJsonObject { put("param", name) })
