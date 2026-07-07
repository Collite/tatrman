package org.tatrman.ttrp.lsp.test

import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.lsp.project.ProjectContext
import org.tatrman.ttrp.lsp.project.ProjectResolver
import org.tatrman.ttrp.project.TtrpManifest

/**
 * Binds every in-memory test document to the shared erp-project world (contracts §8),
 * so the LSP resolves the hero exactly as `ttrp check` resolves a real project — without
 * a `modeler.toml` on disk for the `file:///hero.ttrp` fixtures.
 */
class FixtureProjectResolver : ProjectResolver {
    private val context: ProjectContext by lazy {
        ProjectContext(
            manifest = TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
            modelsRoot = MetadataFixtures.erpModelsRoot(),
            manifestDiagnostics = emptyList(),
        )
    }

    override fun resolve(uri: String): ProjectContext = context
}
