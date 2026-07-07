package org.tatrman.ttrp.lsp

import org.eclipse.lsp4j.launch.LSPLauncher
import kotlin.system.exitProcess

/**
 * Stdio transport entry point (VS Code / IntelliJ hosts). **Invariant:** nothing may
 * write to `System.out` except the launcher — stdout IS the JSON-RPC wire; all logging
 * goes to stderr (LSP4J / JUL default). The transport stays injectable: this `main` is a
 * thin wrapper the WS transport (Stage 5.1) mirrors over its own streams. The `exit`
 * notification terminates this process (standard LSP); the in-process harness injects a
 * no-op instead so it never kills the shared test JVM.
 */
fun main() {
    val server = TtrpLanguageServer(exitHandler = { exitProcess(0) })
    val launcher = LSPLauncher.createServerLauncher(server, System.`in`, System.out)
    server.connect(launcher.remoteProxy)
    launcher.startListening().get()
}
