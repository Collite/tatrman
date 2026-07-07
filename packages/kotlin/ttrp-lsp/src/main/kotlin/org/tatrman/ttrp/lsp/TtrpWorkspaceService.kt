package org.tatrman.ttrp.lsp

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.services.WorkspaceService

/**
 * Workspace surface. Stage 4.1 has no workspace-level behavior beyond acknowledging the
 * notifications the host sends (world / `[ttrp]` changes are picked up by re-resolving
 * per request; a watched-files-driven invalidation lands with Stage 4.2's methods).
 */
class TtrpWorkspaceService : WorkspaceService {
    override fun didChangeConfiguration(params: DidChangeConfigurationParams) = Unit

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) = Unit
}
