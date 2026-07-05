package org.tatrman.ttr.designer.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import org.tatrman.ttr.designer.server.rpc.rpcNotification
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds the per-session `send` callbacks (copy-on-write). A dead session's failed
 * send unregisters it. Server→client notifications (`ttrm/modelChanged`) fan out here.
 */
class NotificationBroadcaster {
    private val log = LoggerFactory.getLogger(NotificationBroadcaster::class.java)
    private val json = Json
    private val sessions = CopyOnWriteArrayList<suspend (String) -> Unit>()

    fun register(send: suspend (String) -> Unit): Registration {
        sessions += send
        return Registration { sessions.remove(send) }
    }

    suspend fun notifyAll(
        method: String,
        params: JsonObject,
    ) {
        val frame = json.encodeToString(JsonObject.serializer(), rpcNotification(method, params))
        for (send in sessions) {
            try {
                send(frame)
            } catch (e: Exception) {
                log.debug("dropping dead session on notify", e)
                sessions.remove(send)
            }
        }
    }

    fun interface Registration {
        fun close()
    }
}
