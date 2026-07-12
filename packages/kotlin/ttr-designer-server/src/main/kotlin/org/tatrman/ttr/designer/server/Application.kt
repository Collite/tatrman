// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.launch.LSPLauncher
import org.slf4j.LoggerFactory
import java.net.URI
import org.tatrman.ttr.designer.server.methods.registerTtrmMethods
import org.tatrman.ttr.designer.server.rpc.JsonRpcDispatcher
import org.tatrman.ttr.designer.server.watch.DebouncedRefreshTrigger
import org.tatrman.ttr.designer.server.watch.NioRepoWatcher
import org.tatrman.ttrp.lsp.TtrpLanguageServer
import org.tatrman.ttrp.lsp.project.FilesystemProjectResolver
import java.nio.file.Path

private val log = LoggerFactory.getLogger("org.tatrman.ttr.designer.server.Application")

/** Parsed CLI options. `--repo` is required; `--port` defaults to 7270. */
data class CliOptions(
    val repo: Path,
    val port: Int = DEFAULT_PORT,
) {
    companion object {
        const val DEFAULT_PORT = 7270

        fun parse(argv: Array<String>): CliOptions {
            val m = mutableMapOf<String, String>()
            var i = 0
            while (i < argv.size) {
                val a = argv[i]
                require(a.startsWith("--")) { "unexpected argument: $a" }
                val key = a.removePrefix("--")
                val value = argv.getOrNull(i + 1) ?: error("missing value for --$key")
                m[key] = value
                i += 2
            }
            val repo = m["repo"] ?: error("--repo <path> is required")
            return CliOptions(repo = Path.of(repo), port = m["port"]?.toInt() ?: DEFAULT_PORT)
        }
    }
}

/**
 * Guard against cross-site WebSocket hijacking (CSWSH). The loopback bind stops
 * remote-network peers, but WS handshakes are exempt from same-origin policy: any web
 * page the user visits could otherwise `new WebSocket("ws://127.0.0.1:<port>/ttrm")`
 * and read the whole model (incl. on-disk paths) with no auth. We allow only requests
 * with no `Origin` (non-browser clients — CLI, in-process tests) or a loopback origin
 * (the locally-served Designer). Any non-loopback browser origin is refused.
 */
internal fun isAllowedOrigin(origin: String?): Boolean {
    if (origin == null) return true // non-browser client; browsers always send Origin
    if (origin == "null") return false // opaque origin (sandboxed iframe, file://) — refuse
    val host =
        try {
            URI(origin).host
        } catch (_: Exception) {
            return false
        } ?: return false
    return host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]"
}

/**
 * The `ttrm` protocol installer (route-only, S24): a single `webSocket("/ttrm")`
 * route. Kept separate from [designerServerModule] so a future designer-server can
 * mount other protocols on the same engine without a plugin-install clash — proven
 * by CoexistingProtocolInstallersSpec.
 */
fun Application.installTtrmProtocol(deps: DesignerServerDeps) {
    routing {
        webSocket("/ttrm") {
            val origin = call.request.headers["Origin"]
            if (!isAllowedOrigin(origin)) {
                log.warn("rejecting /ttrm connection from disallowed origin: {}", origin)
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "origin not allowed"))
                return@webSocket
            }
            val dispatcher = JsonRpcDispatcher()
            registerTtrmMethods(dispatcher, deps)
            val registration = deps.broadcaster.register { frame -> send(Frame.Text(frame)) }
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val response = dispatcher.dispatch(frame.readText())
                    if (response != null) send(Frame.Text(response))
                }
            } finally {
                registration.close()
            }
        }
    }
}

/**
 * The `ttrp` LSP protocol installer (MD8/S24): a single `webSocket("/lsp")` route that
 * hosts the **same** Phase-4 [TtrpLanguageServer] over the WS wire — one LSP across
 * hosts (G-b), no forked handler logic here. Each connection gets its own server
 * session + [WsJsonRpcBridge] (S24 allows reconnect — single *user*, not single
 * *connection*). The project root is resolved by walk-up from each document URI (same
 * as the stdio LSP), so the repo-attached posture is inherited from the open files.
 *
 * Kept a route-only installer (no `install(...)`) so it composes on the shared engine
 * beside `/ttrm` — the seam `CoexistingProtocolInstallersSpec` proves.
 */
fun Application.installTtrpLsp() {
    routing {
        webSocket("/lsp") {
            val origin = call.request.headers["Origin"]
            if (!isAllowedOrigin(origin)) {
                log.warn("rejecting /lsp connection from disallowed origin: {}", origin)
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "origin not allowed"))
                return@webSocket
            }
            // Decouple the launcher thread (which calls send synchronously) from WS
            // backpressure: an unbounded channel drained by a coroutine that forwards to
            // `outgoing` in order. trySend on UNLIMITED never fails until closed.
            val outbound = Channel<String>(Channel.UNLIMITED)
            val bridge = WsJsonRpcBridge { body -> outbound.trySend(body) }
            val server = TtrpLanguageServer(FilesystemProjectResolver())
            val launcher = LSPLauncher.createServerLauncher(server, bridge.inputStream, bridge.outputStream)
            server.connect(launcher.remoteProxy)
            val listening = launcher.startListening()
            val pump =
                launch {
                    for (body in outbound) send(Frame.Text(body))
                }
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) bridge.onMessage(frame.readText())
                }
            } finally {
                bridge.closeInbound()
                listening.cancel(true)
                outbound.close()
                pump.cancel()
                runCatching { server.shutdown().get() }
            }
        }
    }
}

/** Shared holder: the current program's bundle `out/` dir the loopback `/out/{name}` route serves (Stage 5.4). */
val designerOutDir = OutDirHolder()

/** The composed module: install the WebSockets plugin once, mount both protocols + the out route, start the watcher. */
fun Application.designerServerModule(
    deps: DesignerServerDeps,
    watch: Boolean = true,
    outDir: OutDirHolder = designerOutDir,
) {
    install(WebSockets)
    installTtrmProtocol(deps)
    installTtrpLsp()
    installOutRoute(outDir)
    monitor.subscribe(ApplicationStopped) { deps.broadcaster.close() }
    if (watch) {
        val trigger = DebouncedRefreshTrigger(scope = this) { deps.refresher.refresh(sourceId = "", force = false) }
        val watcher = NioRepoWatcher(root = deps.storageRoot, scope = this, trigger = trigger)
        watcher.start()
        monitor.subscribe(ApplicationStopped) { watcher.stop() }
    }
}

fun main(argv: Array<String>) {
    val opts = CliOptions.parse(argv)
    val deps = DesignerServerDeps.forRepo(opts.repo)
    log.info(
        "ttr-designer-server: repo={} storage={} port={} (127.0.0.1 only, no auth — S24)",
        opts.repo,
        deps.storageRoot,
        opts.port,
    )
    embeddedServer(CIO, host = "127.0.0.1", port = opts.port) {
        designerServerModule(deps)
    }.start(wait = true)
}
