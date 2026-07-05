package org.tatrman.ttr.designer.server

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import org.tatrman.ttr.designer.server.rpc.rpcNotification
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds the per-session `send` callbacks (copy-on-write). A dead — or too-slow —
 * session's send unregisters it. Server→client notifications (`ttrm/modelChanged`)
 * fan out here.
 *
 * Fan-out is concurrent with a per-session timeout: one stalled client (its `outgoing`
 * channel full) must NOT delay `modelChanged` to the others. [broadcast] is the
 * fire-and-forget entry point used by the registry listener — it dispatches on an
 * internal scope so it never blocks the refresh/CIO thread that fired the swap.
 */
class NotificationBroadcaster(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val log = LoggerFactory.getLogger(NotificationBroadcaster::class.java)
    private val json = Json
    private val sessions = CopyOnWriteArrayList<suspend (String) -> Unit>()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun register(send: suspend (String) -> Unit): Registration {
        sessions += send
        return Registration { sessions.remove(send) }
    }

    /** Fire-and-forget fan-out — never blocks the caller (registry listener / CIO thread). */
    fun broadcast(
        method: String,
        params: JsonObject,
    ) {
        scope.launch { notifyAll(method, params) }
    }

    /**
     * Suspending fan-out: each session is notified concurrently, bounded by
     * [SEND_TIMEOUT_MS]; a session that times out or throws is dropped. Suspends until
     * every session has completed or timed out (used directly by the contract tests).
     */
    suspend fun notifyAll(
        method: String,
        params: JsonObject,
    ) = coroutineScope {
        val frame = json.encodeToString(JsonObject.serializer(), rpcNotification(method, params))
        for (send in sessions) {
            launch {
                val ok =
                    try {
                        withTimeoutOrNull(SEND_TIMEOUT_MS) {
                            send(frame)
                            true
                        }
                    } catch (e: Exception) {
                        log.debug("dropping dead session on notify", e)
                        null
                    }
                if (ok == null) sessions.remove(send)
            }
        }
    }

    /** Cancel the fan-out scope (call on ApplicationStopped). */
    fun close() {
        scope.cancel()
    }

    fun interface Registration {
        fun close()
    }

    private companion object {
        const val SEND_TIMEOUT_MS = 5_000L
    }
}
