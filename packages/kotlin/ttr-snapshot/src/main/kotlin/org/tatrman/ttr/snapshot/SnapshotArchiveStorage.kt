// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.snapshot

import org.tatrman.ttr.metadata.source.ModelStorage
import org.tatrman.ttr.metadata.source.StorageFile
import java.nio.file.Path

/**
 * A [ModelStorage] backed by a snapshot archive (contracts §2). Behaves **identically** to
 * `LocalFsStorage` over the same `docs/` tree — the archive is only the transport for the connected
 * binding (B-1), so `MetadataLoader.load()` over an archive == over the repo it was packed from.
 *
 * Reads are served from the decoded in-memory tree (no temp files). Each [StorageFile] carries a
 * **synthetic** `rootPath` so `FileBasedSource`'s package-from-path computation (pure URI math, no
 * I/O) reproduces the same package as the on-disk tree; [read] resolves the entry back out of the map.
 */
class SnapshotArchiveStorage private constructor(
    override val id: String,
    private val archiveId: String,
    val manifest: SnapshotManifest,
    /** relPath under `docs/` → UTF-8 content. */
    private val docs: Map<String, String>,
    private val root: Path,
) : ModelStorage {
    /** Content id of the archive — immutable, so it is the version (change ⇒ different archive). */
    override fun fetchVersion(): String = archiveId

    override fun listFiles(
        extensions: List<String>,
        prefixes: List<String>,
    ): List<StorageFile> =
        docs.keys
            .filter { rel ->
                val name = rel.substringAfterLast('/')
                extensions.any { ext -> name.endsWith(".$ext") } &&
                    (prefixes.isEmpty() || prefixes.any { p -> name.startsWith(p) })
            }.sorted()
            .map { rel ->
                StorageFile(
                    path = root.resolve(rel).toString(),
                    sizeBytes =
                        docs
                            .getValue(rel)
                            .toByteArray(Charsets.UTF_8)
                            .size
                            .toLong(),
                    rootPath = root,
                )
            }

    override fun read(file: StorageFile): String {
        val rel = root.relativize(Path.of(file.path)).toString()
        return docs[rel] ?: error("snapshot archive $archiveId has no entry '$rel'")
    }

    companion object {
        /**
         * Open an archive as a storage. Returns [Result.failure] (never throws) on a corrupt
         * archive — the structured reason is the exception message (S1.T2).
         */
        fun of(
            storageId: String,
            compressed: ByteArray,
        ): Result<SnapshotArchiveStorage> =
            when (val r = SnapshotReader.read(compressed)) {
                is SnapshotReadResult.Ok ->
                    Result.success(
                        SnapshotArchiveStorage(
                            id = storageId,
                            archiveId = SnapshotId.of(compressed),
                            manifest = r.contents.manifest,
                            docs = r.contents.docs,
                            root = Path.of("/__ttr_snapshot__", storageId),
                        ),
                    )

                is SnapshotReadResult.Failure -> Result.failure(IllegalStateException(r.reason))
            }
    }
}
