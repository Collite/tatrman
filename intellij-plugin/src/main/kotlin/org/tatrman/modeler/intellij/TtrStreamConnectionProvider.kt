package org.tatrman.modeler.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import java.nio.file.Path

/**
 * Launches the bundled LSP server over stdio (contracts §4):
 *
 *     <node>  <pluginHome>/server/server-stdio.mjs  --stdio
 *
 * The working directory is the project base so the server's project-root
 * walk-up (to `modeler.toml`) starts from the right place. The server reads
 * stdin/stdout directly and ignores argv, so `--stdio` is a harmless marker
 * kept for parity with how the server is invoked elsewhere.
 */
class TtrStreamConnectionProvider(project: Project) : OSProcessStreamConnectionProvider() {
    init {
        commandLine = buildCommandLine(
            node = NodeResolver.resolve(),
            serverEntry = PluginResources.serverEntry(),
            workDir = project.basePath,
        )
    }

    companion object {
        const val STDIO_FLAG = "--stdio"

        fun buildCommandLine(node: String, serverEntry: Path, workDir: String?): GeneralCommandLine =
            GeneralCommandLine(node, serverEntry.toString(), STDIO_FLAG).apply {
                if (!workDir.isNullOrBlank()) withWorkDirectory(workDir)
            }
    }
}
