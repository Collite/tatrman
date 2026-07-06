package org.tatrman.ttrp.resolve

import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test-side locator for the TTR-P-specific resolution fixtures (programs + negatives)
 * that live under `ttrp-frontend/src/test/resources/resolution/`. The models/world
 * come from the SHARED erp-project fixture via [MetadataFixtures] (contracts §8) —
 * never duplicated here.
 */
object ResolutionFixtures {
    /** Shared erp-project models root (the `models/` dir), from ttr-metadata testFixtures. */
    fun modelsRoot(): Path = MetadataFixtures.erpModelsRoot()

    /** Root of the resolution fixture tree (walked up from the working dir). */
    val root: Path by lazy {
        val rel = Path.of("packages/kotlin/ttrp-frontend/src/test/resources/resolution")
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve(rel)
            if (Files.isDirectory(candidate)) return@lazy candidate
            val local = dir.resolve("src/test/resources/resolution")
            if (Files.isDirectory(local)) return@lazy local
            dir = dir.parent
        }
        error("could not locate resolution fixtures from ${Path.of("").toAbsolutePath()}")
    }

    fun program(name: String): String = Files.readString(root.resolve("project/programs/$name"))

    fun projectDir(): Path = root.resolve("project")

    /** A manifest with the shared world pinned, anchored at the fixture project dir. */
    fun manifest(world: String? = "acme.worlds.dev"): TtrpManifest =
        TtrpManifest(world = world, manifestDir = projectDir())

    /** A checker over the shared models with the given (or default) world manifest. */
    fun checker(world: String? = "acme.worlds.dev"): TtrpChecker = TtrpChecker(manifest(world), modelsRoot())
}
