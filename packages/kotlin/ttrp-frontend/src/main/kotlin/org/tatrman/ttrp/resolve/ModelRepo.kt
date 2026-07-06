package org.tatrman.ttrp.resolve

import org.tatrman.ttr.metadata.MetadataLoader
import org.tatrman.ttr.metadata.graph.ModelGraph
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Loads a TTR-M model repo into a ttr-metadata [RegistrySnapshot] — offline, no
 * service (D-g). ALL `.ttrm` reading in ttrp-frontend goes through ttr-metadata;
 * this is the one place that touches the filesystem for models. Mirrors the shared
 * `MetadataFixtures.snapshotOf`, kept in main so the CLI can load a project's models
 * without depending on test fixtures.
 */
object ModelRepo {
    /** Loads [modelsRoot] (the `models/` dir). Returns null when the dir is absent. */
    fun snapshotOf(modelsRoot: Path): RegistrySnapshot? {
        if (!Files.isDirectory(modelsRoot)) return null
        // Resolve symlinks: `Files.walk` does not descend a symlink used as the walk
        // root, so a `models/` symlink (the test fixture's link to the shared models)
        // must be canonicalised first.
        val realRoot = runCatching { modelsRoot.toRealPath() }.getOrDefault(modelsRoot)
        val storage = LocalFsStorage(id = "ttrp", rootPath = realRoot)
        val model =
            MetadataLoader(FileBasedSource(sourceId = "ttrp", priority = 100, storage = storage)).load().model
                ?: return null
        return RegistrySnapshot(
            model = model,
            graph = ModelGraph.build(model),
            swappedAt = Instant.EPOCH,
            warnings = emptyList(),
        )
    }
}
