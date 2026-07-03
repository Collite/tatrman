package org.tatrman.modeler.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.ServerTrace
import org.tatrman.modeler.intellij.settings.TtrSettings
import java.nio.file.Path

/**
 * Launches the bundled LSP server over stdio (contracts §4):
 *
 *     <node>  <pluginHome>/server/server-stdio.mjs  --stdio
 *
 * Resolution happens in [start] (not the constructor) so a missing Node is
 * reported through an actionable balloon and a clean [CannotStartProcessException]
 * rather than crashing factory instantiation (contracts §3). The working
 * directory is the project base so the server's project-root walk-up (to
 * `modeler.toml`) starts from the right place. The server reads stdin/stdout
 * directly and ignores argv, so `--stdio` is a harmless parity marker.
 */
class TtrStreamConnectionProvider(private val project: Project) : OSProcessStreamConnectionProvider() {

    override fun start() {
        val node = try {
            NodeResolver.resolve()
        } catch (e: NodeNotFoundException) {
            TtrNotifications.nodeMissing(project)
            throw CannotStartProcessException(e)
        }

        NodeResolver.detectVersion(node)?.let { version ->
            if (!NodeResolver.isSupported(version)) TtrNotifications.nodeTooOld(project, version)
        }

        commandLine = buildCommandLine(node, resolveServerEntry(), project.basePath)
        applyTraceLevel()
        super.start()
    }

    /** Settings override (if non-blank) → bundled server. */
    private fun resolveServerEntry(): Path {
        val override = TtrSettings.getInstance().state.serverPathOverride
        return if (override.isNotBlank()) Path.of(override) else PluginResources.serverEntry()
    }

    /**
     * Maps the plugin's [TtrSettings.State.traceLevel] onto LSP4IJ's own
     * per-server trace (contracts §7) — no custom logging channel. Best-effort:
     * never let a trace-wiring hiccup block the server from starting.
     */
    private fun applyTraceLevel() {
        try {
            val trace = ServerTrace.get(TtrSettings.getInstance().state.traceLevel)
            val store = ProjectLanguageServerSettings.getInstance(project)
            val current = store.getLanguageServerSettings(SERVER_ID)
                ?: LanguageServerSettings.LanguageServerDefinitionSettings()
            current.serverTrace = trace
            store.updateSettings(SERVER_ID, current)
        } catch (e: Exception) {
            thisLogger().warn("Could not apply TTR LSP trace level", e)
        }
    }

    companion object {
        const val STDIO_FLAG = "--stdio"

        /** The server id registered in plugin.xml (`<server id="…">`). */
        const val SERVER_ID = "ttrLanguageServer"

        fun buildCommandLine(node: String, serverEntry: Path, workDir: String?): GeneralCommandLine =
            GeneralCommandLine(node, serverEntry.toString(), STDIO_FLAG).apply {
                if (!workDir.isNullOrBlank()) withWorkDirectory(workDir)
            }
    }
}
