package org.tatrman.ttr.designer.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the current program's bundle `out/` directory. The browser cannot read the
 * filesystem, so the Designer fetches run outputs (the Arrow files under out/) over the loopback
 * [installOutRoute]; a completed `ttrp/run` sets this to the run's bundle `out/` (Stage 5.4).
 */
class OutDirHolder {
    private val ref = AtomicReference<Path?>(null)

    fun set(dir: Path) {
        ref.set(dir.toAbsolutePath().normalize())
    }

    fun get(): Path? = ref.get()
}

/**
 * Loopback HTTP `GET /out/{name}` (contracts §4 notes, S24): serves a single file from the
 * current bundle `out/` directory ONLY — the browser's transport for "the Designer watches
 * `out/`". Path-traversal guarded (the resolved file must stay inside the out dir); no
 * directory listing. Loopback-only per the S24 host bind.
 */
fun Application.installOutRoute(holder: OutDirHolder) {
    routing {
        get("/out/{name}") {
            val base = holder.get()
            if (base == null) {
                call.respondText("no run output available", status = HttpStatusCode.NotFound)
                return@get
            }
            val name = call.parameters["name"].orEmpty()
            val resolved = base.resolve(name).normalize()
            // Path-traversal guard: the resolved file must stay strictly inside the out dir.
            if (!resolved.startsWith(base) || resolved == base) {
                call.respondText("forbidden", status = HttpStatusCode.Forbidden)
                return@get
            }
            if (!Files.isRegularFile(resolved)) {
                call.respondText("not found", status = HttpStatusCode.NotFound)
                return@get
            }
            // Arrow IPC + everything under out/ is binary; the client reads it via apache-arrow.
            call.respondBytes(Files.readAllBytes(resolved), ContentType.Application.OctetStream)
        }
    }
}
