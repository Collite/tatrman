// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.fixtures

import org.tatrman.ttr.metadata.MetadataLoader
import org.tatrman.ttr.metadata.graph.ModelGraph
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import java.nio.file.Path
import java.time.Instant

/**
 * Single home (contracts §8) for the shared world/model fixture project, consumed
 * by ttrp-frontend via `testFixtures(project(":packages:kotlin:ttr-metadata"))`
 * — so consumers never do classpath-resource gymnastics.
 *
 * Contracts §8 divergence (recorded for the M2.2 API review): the home is
 * `src/testFixtures/resources/fixtures/` (the `test` source set is not consumable
 * cross-project; only `testFixtures` is).
 */
object MetadataFixtures {
    /** Root of the shared fixture project (`modeler.toml` + `models/`). */
    fun erpProjectRoot(): Path = resourceDir("fixtures/erp-project")

    /** The `models/` dir (the LocalFsStorage root — package = dir path). */
    fun erpModelsRoot(): Path = erpProjectRoot().resolve("models")

    /** `models/` root of a negative fixture under `worlds-negative/<name>/`. */
    fun worldsNegativeRoot(name: String): Path = resourceDir("fixtures/worlds-negative/$name").resolve("models")

    /** Load the erp fixture project into a [RegistrySnapshot] (model + graph). */
    fun loadErpSnapshot(): RegistrySnapshot = snapshotOf(erpModelsRoot())

    /** Load an arbitrary models root into a [RegistrySnapshot]. */
    fun snapshotOf(modelsRoot: Path): RegistrySnapshot {
        val storage = LocalFsStorage(id = "fixture", rootPath = modelsRoot)
        val model =
            MetadataLoader(FileBasedSource(sourceId = "fixture", priority = 100, storage = storage)).load().model
                ?: error("fixture model failed to load from $modelsRoot")
        return RegistrySnapshot(
            model = model,
            graph = ModelGraph.build(model),
            swappedAt = Instant.EPOCH,
            warnings = emptyList(),
        )
    }

    // The fixtures are directory trees consumed by LocalFsStorage, which needs a
    // real filesystem path — a `jar:` classpath URL (testFixturesJar) has none. So
    // walk up from the working dir to the source home. Works for the module's own
    // tests (working dir = module) and for the same-repo ttrp-frontend consumer
    // (walk reaches repo root). Same-repo is the contracts-§8 use case.
    private val fixturesHome: Path by lazy {
        val rel = Path.of("packages/kotlin/ttr-metadata/src/testFixtures/resources")
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve(rel)
            if (java.nio.file.Files
                    .isDirectory(candidate)
            ) {
                return@lazy candidate
            }
            // In-module run: working dir already IS the module.
            val local = dir.resolve("src/testFixtures/resources")
            if (java.nio.file.Files
                    .isDirectory(local.resolve("fixtures"))
            ) {
                return@lazy local
            }
            dir = dir.parent
        }
        error("could not locate ttr-metadata testFixtures/resources from ${Path.of("").toAbsolutePath()}")
    }

    private fun resourceDir(path: String): Path = fixturesHome.resolve(path)
}
