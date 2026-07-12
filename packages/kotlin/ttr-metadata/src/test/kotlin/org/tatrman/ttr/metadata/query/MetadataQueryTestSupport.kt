// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.query

import org.tatrman.ttr.metadata.MetadataLoader
import org.tatrman.ttr.metadata.graph.ModelGraph
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import java.nio.file.Path
import java.time.Instant

/** Builds a [MetadataQuery] over a model loaded from [root] (LocalFsStorage). */
internal fun queryFor(root: Path): MetadataQuery {
    val storage = LocalFsStorage(id = "t", rootPath = root)
    val model =
        MetadataLoader(FileBasedSource(sourceId = "t", priority = 100, storage = storage)).load().model
            ?: error("model failed to load from $root")
    val snap =
        RegistrySnapshot(
            model = model,
            graph = ModelGraph.build(model),
            swappedAt = Instant.EPOCH,
            warnings = emptyList(),
        )
    return MetadataQuery(snap)
}
