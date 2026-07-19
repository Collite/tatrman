// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.snapshot

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Immutable-by-id local archive cache (contracts §2 / SZ-8). Layout
 * `<root>/sha256/<first2>/<hex>`; a `put` is a temp-write + atomic move so a crashed write never
 * leaves a partial (and thus mis-addressed) blob. `gc(keep)` drops everything not pinned.
 */
class SnapshotCache(
    private val root: Path,
) {
    private fun pathFor(id: String): Path {
        require(SnapshotId.isValid(id)) { "not a snapshot id: $id" }
        val hex = id.removePrefix("sha256:")
        return root.resolve("sha256").resolve(hex.substring(0, 2)).resolve(hex)
    }

    fun has(id: String): Boolean = Files.isRegularFile(pathFor(id))

    fun get(id: String): ByteArray? = pathFor(id).takeIf { Files.isRegularFile(it) }?.let { Files.readAllBytes(it) }

    /**
     * Store [compressed] under its content id and return it. Verifies the id matches the bytes
     * (immutable-by-id), writes to a sibling temp file, then atomically moves into place. Idempotent.
     */
    fun put(compressed: ByteArray): String {
        val id = SnapshotId.of(compressed)
        val dest = pathFor(id)
        if (Files.isRegularFile(dest)) return id
        Files.createDirectories(dest.parent)
        val tmp = Files.createTempFile(dest.parent, ".tmp-", ".part")
        try {
            Files.write(tmp, compressed)
            try {
                Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
        return id
    }

    /** Evict every cached archive whose id is not in [keep]. Returns the count removed. */
    fun gc(keep: Set<String>): Int {
        val sha = root.resolve("sha256")
        if (!Files.isDirectory(sha)) return 0
        var removed = 0
        Files.walk(sha).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { "sha256:${it.fileName}" !in keep }
                .forEach {
                    Files.deleteIfExists(it)
                    removed++
                }
        }
        return removed
    }
}
