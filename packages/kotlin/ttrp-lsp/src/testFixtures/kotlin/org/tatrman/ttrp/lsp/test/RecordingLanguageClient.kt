package org.tatrman.ttrp.lsp.test

import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Client end of the paired harness: records `publishDiagnostics` / `logMessage` per uri. */
class RecordingLanguageClient : LanguageClient {
    private val diagnostics = ConcurrentHashMap<String, PublishDiagnosticsParams>()
    private val latches = ConcurrentHashMap<String, CountDownLatch>()
    val logs = java.util.concurrent.CopyOnWriteArrayList<String>()

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        diagnostics[params.uri] = params
        latchFor(params.uri).countDown()
    }

    override fun telemetryEvent(`object`: Any?) = Unit

    override fun showMessage(params: MessageParams) = Unit

    override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
        CompletableFuture.completedFuture(null)

    override fun logMessage(params: MessageParams) {
        logs += params.message
    }

    /** Blocks until at least one diagnostics set has arrived for [uri]; returns the latest. */
    fun awaitDiagnostics(
        uri: String,
        timeoutMs: Long = 5_000,
    ): PublishDiagnosticsParams {
        latchFor(uri).await(timeoutMs, TimeUnit.MILLISECONDS)
        return diagnostics[uri] ?: error("no diagnostics published for $uri within ${timeoutMs}ms")
    }

    /** Re-arm the latch before a change so the next publish is awaited (not the previous one). */
    fun resetLatch(uri: String) {
        latches[uri] = CountDownLatch(1)
    }

    fun latest(uri: String): PublishDiagnosticsParams? = diagnostics[uri]

    private fun latchFor(uri: String): CountDownLatch = latches.getOrPut(uri) { CountDownLatch(1) }
}
