package org.tatrman.ttr.designer.server

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Bridges the two JSON-RPC framings the `/lsp` endpoint straddles: lsp4j's
 * `LSPLauncher` speaks `Content-Length`-framed byte streams, while the WebSocket wire
 * speaks **one JSON-RPC message per text frame** (the LSP-over-WS convention — no
 * headers on the wire). The bridge is deterministic, no buffering heuristics (P2): it
 * frames inbound frames with an exact `Content-Length` and parses outbound bytes back
 * to whole message bodies by that same length.
 *
 * - **Inbound** (`onMessage`): each WS text frame → `Content-Length: <utf8-bytes>\r\n\r\n`
 *   + payload, written to [inputStream]'s paired pipe for the launcher to read.
 * - **Outbound** ([outputStream]): the launcher writes `Content-Length`-framed bytes;
 *   the bridge accumulates, and for every complete message body calls [send] with the
 *   body text (wired to `outgoing.send(Frame.Text(body))` in the WS session).
 *
 * One bridge per WS connection. The pipe buffer is generous (64 KiB) so a large inbound
 * message rarely blocks the WS coroutine before the launcher thread drains it.
 */
class WsJsonRpcBridge(
    private val send: (String) -> Unit,
) {
    private val toLauncher = PipedOutputStream()

    /** The launcher's input: framed inbound messages arrive here. */
    val inputStream: PipedInputStream = PipedInputStream(toLauncher, PIPE_SIZE)

    /** The launcher's output: it writes `Content-Length`-framed bytes; we de-frame to [send]. */
    val outputStream: OutputStream = DeframingOutputStream()

    /** Frame one inbound JSON-RPC message for the launcher (called per WS text frame). */
    fun onMessage(text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        val header = "Content-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.UTF_8)
        synchronized(toLauncher) {
            toLauncher.write(header)
            toLauncher.write(body)
            toLauncher.flush()
        }
    }

    /** Close the inbound pipe (WS connection ended) so the launcher's read loop sees EOF. */
    fun closeInbound() {
        runCatching { toLauncher.close() }
    }

    /**
     * Parses `Content-Length`-framed output into whole message bodies. Bytes may arrive
     * split across arbitrary `write()` boundaries; we accumulate and drain every complete
     * message. `Content-Length` is a byte count of the UTF-8 body.
     */
    private inner class DeframingOutputStream : OutputStream() {
        private val buffer = ByteArrayOutputStream()

        @Synchronized
        override fun write(b: Int) {
            buffer.write(b)
            drain()
        }

        @Synchronized
        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            buffer.write(b, off, len)
            drain()
        }

        private fun drain() {
            var bytes = buffer.toByteArray()
            var consumedAny = false
            while (true) {
                val headerEnd = indexOfHeaderEnd(bytes) ?: break
                val headerText = String(bytes, 0, headerEnd, Charsets.UTF_8)
                val contentLength = contentLengthOf(headerText) ?: break
                val bodyStart = headerEnd + HEADER_TERMINATOR.size
                if (bytes.size - bodyStart < contentLength) break // body not fully arrived
                val body = String(bytes, bodyStart, contentLength, Charsets.UTF_8)
                send(body)
                bytes = bytes.copyOfRange(bodyStart + contentLength, bytes.size)
                consumedAny = true
            }
            if (consumedAny) {
                buffer.reset()
                buffer.write(bytes)
            }
        }
    }

    private fun indexOfHeaderEnd(bytes: ByteArray): Int? {
        outer@ for (i in 0..bytes.size - HEADER_TERMINATOR.size) {
            for (j in HEADER_TERMINATOR.indices) {
                if (bytes[i + j] != HEADER_TERMINATOR[j]) continue@outer
            }
            return i
        }
        return null
    }

    private fun contentLengthOf(headers: String): Int? {
        for (line in headers.split("\r\n")) {
            val idx = line.indexOf(':')
            if (idx < 0) continue
            if (line.substring(0, idx).trim().equals("Content-Length", ignoreCase = true)) {
                return line.substring(idx + 1).trim().toIntOrNull()
            }
        }
        return null
    }

    companion object {
        private const val PIPE_SIZE = 1 shl 16
        private val HEADER_TERMINATOR = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
    }
}
