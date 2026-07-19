// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.snapshot

import com.github.luben.zstd.ZstdInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayInputStream

/** Structured read result — a corrupt archive yields [Failure], never a thrown exception (S1.T2). */
sealed interface SnapshotReadResult {
    data class Ok(
        val contents: SnapshotContents,
    ) : SnapshotReadResult

    data class Failure(
        val reason: String,
    ) : SnapshotReadResult
}

/** Inverse of [SnapshotWriter]: decompress + untar → [SnapshotContents]. */
object SnapshotReader {
    fun read(compressed: ByteArray): SnapshotReadResult {
        val raw = mutableMapOf<String, ByteArray>()
        try {
            TarArchiveInputStream(ZstdInputStream(ByteArrayInputStream(compressed))).use { tar ->
                while (true) {
                    val e = tar.nextEntry ?: break
                    if (e.isDirectory) continue
                    raw[e.name] = tar.readBytes()
                }
            }
        } catch (ex: Exception) {
            return SnapshotReadResult.Failure("archive read failed: ${ex.message}")
        }

        val manifestBytes =
            raw["snapshot.json"]
                ?: return SnapshotReadResult.Failure("snapshot.json missing from archive")
        val manifest =
            try {
                SnapshotManifest.fromJson(manifestBytes.toString(Charsets.UTF_8))
            } catch (ex: Exception) {
                return SnapshotReadResult.Failure("snapshot.json unparseable: ${ex.message}")
            }

        val docs =
            raw
                .filterKeys { it.startsWith("docs/") && it != "docs/" }
                .map { (k, v) -> k.removePrefix("docs/") to v.toString(Charsets.UTF_8) }
                .toMap()
                .toSortedMap()
        return SnapshotReadResult.Ok(SnapshotContents(manifest, docs))
    }
}
