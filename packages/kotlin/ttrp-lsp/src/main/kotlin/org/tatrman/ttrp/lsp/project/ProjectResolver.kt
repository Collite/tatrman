package org.tatrman.ttrp.lsp.project

import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.project.TtrpManifest
import org.tatrman.ttrp.project.TtrpManifestReader
import java.net.URI
import java.nio.file.Path

/**
 * The resolved project context for a document: the `[ttrp]` manifest, the model-repo
 * root, and any `TTRP-CFG-*` manifest diagnostics. This is exactly the triple the CLI
 * computes with `TtrpManifestReader.resolve(parentDir)` — the LSP reuses it so an
 * editor session resolves the world/model exactly as `ttrp check` does.
 */
data class ProjectContext(
    val manifest: TtrpManifest,
    val modelsRoot: Path,
    val manifestDiagnostics: List<TtrpDiagnostic>,
)

/**
 * Maps a document uri to its [ProjectContext]. Injectable so tests bind the shared
 * erp-project world without a real modeler.toml on disk (the Stage-4.1 harness supplies
 * a fixture resolver); production walks up the filesystem.
 */
fun interface ProjectResolver {
    fun resolve(uri: String): ProjectContext
}

/**
 * The production resolver: parse the `file:` uri, walk up from the document's parent
 * to `modeler.toml` (the same walk-up as TTR-M / the CLI), and read the `[ttrp]` table.
 * A non-file uri, or one with no parent, resolves against the current working directory.
 */
class FilesystemProjectResolver : ProjectResolver {
    override fun resolve(uri: String): ProjectContext {
        val startDir = parentDirOf(uri)
        val result = TtrpManifestReader.resolve(startDir)
        return ProjectContext(
            manifest = result.manifest,
            modelsRoot = result.manifest.modelsRoot(),
            manifestDiagnostics = result.diagnostics,
        )
    }

    private fun parentDirOf(uri: String): Path {
        val path =
            runCatching { Path.of(URI(uri)) }.getOrNull()
                ?: runCatching { Path.of(uri) }.getOrNull()
                ?: Path.of("").toAbsolutePath()
        return path.parent ?: Path.of("").toAbsolutePath()
    }
}
