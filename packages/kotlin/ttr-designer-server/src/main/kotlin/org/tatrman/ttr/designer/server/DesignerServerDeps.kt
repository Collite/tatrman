package org.tatrman.ttr.designer.server

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttr.metadata.graph.ModelGraph
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.reconcile.ModelReconciler
import org.tatrman.ttr.metadata.refresh.MetadataRefresher
import org.tatrman.ttr.metadata.registry.MetadataRegistry
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import org.tatrman.ttr.metadata.source.ModelSource
import java.nio.file.Files
import java.nio.file.Path

/**
 * Composition root (host, not a brain — MD5): LocalFsStorage → FileBasedSource →
 * MetadataLoader/ModelReconciler → initial swap → MetadataRefresher, plus the
 * notification broadcaster. Load issues are reported via `getStatus.issues`, never
 * fatal (LoadResult never throws on model errors, contracts §2).
 */
class DesignerServerDeps(
    val repoRoot: Path,
    val storageRoot: Path,
    val registry: MetadataRegistry,
    val refresher: MetadataRefresher,
    val broadcaster: NotificationBroadcaster = NotificationBroadcaster(),
) {
    companion object {
        private const val SOURCE_ID = "repo"

        /**
         * Build deps for a model repo. Resolves the storage root: if `<repoRoot>/models`
         * exists it is the storage root (package = dir under models, the modeler.toml
         * convention); otherwise `repoRoot` itself.
         */
        fun forRepo(repoRoot: Path): DesignerServerDeps {
            val storageRoot = repoRoot.resolve("models").takeIf { Files.isDirectory(it) } ?: repoRoot
            val storage = LocalFsStorage(id = SOURCE_ID, rootPath = storageRoot)
            val source: ModelSource = FileBasedSource(sourceId = SOURCE_ID, priority = 100, storage = storage)
            val reconciler = ModelReconciler(ModelDescriptor(id = "repo", name = "repo"))
            val registry = MetadataRegistry()

            val initial = source.load()
            val result = reconciler.reconcile(listOf(initial))
            registry.swap(result.model, ModelGraph.build(result.model), result.warnings + result.errors)

            val refresher = MetadataRefresher(listOf(source), listOf(SOURCE_ID), reconciler, registry)
            refresher.recordInitial(listOf(initial))

            val deps = DesignerServerDeps(repoRoot, storageRoot, registry, refresher)
            // file-watch reload → notify (architecture §5: registry listener drives modelChanged).
            registry.addListener { snap ->
                // Fire-and-forget: the broadcaster fans out on its own scope with a
                // per-session timeout, so this never blocks the refresh/swap thread on a
                // slow WS client (no runBlocking head-of-line stall).
                deps.broadcaster.broadcast(
                    "ttrm/modelChanged",
                    buildJsonObject { put("modelVersion", JsonPrimitive(snap.model.version.value)) },
                )
            }
            return deps
        }
    }
}
