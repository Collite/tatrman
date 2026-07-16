// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.methods

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttr.designer.server.DesignerServerDeps
import org.tatrman.ttr.designer.server.layout.TtrmLayoutService
import org.tatrman.ttr.designer.server.rpc.JsonRpcDispatcher
import org.tatrman.ttr.designer.server.rpc.RpcCodes
import org.tatrman.ttr.designer.server.rpc.TtrmRpcException
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TtrlCanvas
import org.tatrman.ttr.parser.model.TtrlMode
import org.tatrman.ttr.parser.model.TtrlNodeEntry

/**
 * `ttrm/getLayout` + `ttrm/setLayout` (T1 + T3.2.9 — TP-5). Delegates to
 * [TtrmLayoutService]; this file is proto-conversion only, matching
 * `TtrmMethods.kt`'s house style (MD2/MD5: no sidecar-parsing logic here).
 */
fun registerTtrmLayoutMethods(
    dispatcher: JsonRpcDispatcher,
    deps: DesignerServerDeps,
) {
    val service = TtrmLayoutService()

    dispatcher.register("ttrm/getLayout") { params ->
        val uri =
            (params["uri"] as? JsonPrimitive)?.content
                ?: throw TtrmRpcException(RpcCodes.INVALID_PARAMS, "invalid-params")
        val snap = deps.registry.read()
        val result = service.getLayout(uri, snap)
        buildJsonObject {
            put("exists", result.exists)
            put("version", result.version)
            put("canvases", JsonArray(result.canvases.map { it.toJson() }))
            put("orphaned", JsonArray(result.orphaned.map { JsonPrimitive(it) }))
            put("errors", JsonArray(result.errors.map { JsonPrimitive(it) }))
        }
    }

    dispatcher.register("ttrm/setLayout") { params ->
        val uri =
            (params["uri"] as? JsonPrimitive)?.content
                ?: throw TtrmRpcException(RpcCodes.INVALID_PARAMS, "invalid-params")
        val canvasesJson =
            (params["canvases"] as? JsonArray)
                ?: throw TtrmRpcException(
                    RpcCodes.INVALID_PARAMS,
                    "invalid-params",
                    buildJsonObject { put("param", "canvases") },
                )
        val canvases = canvasesJson.mapNotNull { it as? JsonObject }.map { it.toCanvas() }
        val ok = service.setLayout(uri, canvases)
        buildJsonObject { put("ok", ok) }
    }
}

private fun JsonObject.toCanvas(): TtrlCanvas {
    val key = (this["key"] as? JsonPrimitive)?.content ?: ""
    val skin = (this["skin"] as? JsonPrimitive)?.contentOrNull
    val mode = if ((this["mode"] as? JsonPrimitive)?.content == "manual") TtrlMode.MANUAL else TtrlMode.AUTO
    val nodes =
        (this["nodes"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.map { n ->
            TtrlNodeEntry(
                zeta = (n["qname"] as? JsonPrimitive)?.content ?: "",
                x = (n["x"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
                y = (n["y"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
            )
        } ?: emptyList()
    val collapsed = (this["collapsed"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
    return TtrlCanvas(
        key = key,
        skin = skin,
        mode = mode,
        nodes = nodes,
        collapsed = collapsed,
        chains = emptyMap(),
        location = SourceLocation.UNKNOWN,
    )
}

private val JsonPrimitive.contentOrNull: String?
    get() = if (this === JsonNull) null else content

private fun TtrlCanvas.toJson(): JsonObject =
    buildJsonObject {
        put("key", key)
        put("skin", skin?.let { JsonPrimitive(it) } ?: JsonNull)
        put("mode", if (mode == TtrlMode.MANUAL) "manual" else "auto")
        put(
            "nodes",
            JsonArray(
                nodes.map { n ->
                    buildJsonObject {
                        put("qname", n.zeta)
                        put("x", n.x)
                        put("y", n.y)
                    }
                },
            ),
        )
        put("collapsed", JsonArray(collapsed.map { JsonPrimitive(it) }))
    }
