package org.tatrman.ttrp.lsp.viewstate

import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Path

/** Builds the authored hero graph against the shared erp-project world for view-state specs. */
object ViewStateFixtures {
    private val heroPath: Path by lazy {
        val rel = Path.of("packages/kotlin/ttrp-lsp/src/test/resources/fixtures/hero.ttrp")
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve(rel)
            if (Files.isRegularFile(candidate)) return@lazy candidate
            val local = dir.resolve("src/test/resources/fixtures/hero.ttrp")
            if (Files.isRegularFile(local)) return@lazy local
            dir = dir.parent
        }
        error("could not locate hero.ttrp")
    }

    val heroText: String by lazy { Files.readString(heroPath) }

    fun heroGraph(): TtrpGraph {
        val manifest = TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot())
        val plan = TtrpPipeline(manifest, MetadataFixtures.erpModelsRoot()).plan(heroText, "hero.ttrp")
        return plan.authoredGraph ?: error("hero did not compile: ${plan.diagnostics}")
    }
}
