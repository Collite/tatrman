// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * The content fingerprint of a model root (AG-P0 S5, AG contracts C-21): sha256 over
 * `"<path>\t<sha256(content)>\n"` for every `.ttrm` file under the root, sorted by its `/`-separated
 * path relative to the root. A host that GENERATES a model tree (ai-platform's `ttr-gen`) writes the
 * same function of the same files to `<root>/FINGERPRINT`; a bundle compiled against that tree records
 * it ([ModelRootRef]), so the host can refuse to run a bundle against a model it was not compiled for.
 * Content-only: no timestamps, no absolute paths — a function of the tree.
 */
object ModelFingerprint {
    fun of(root: Path): String {
        val files =
            Files.walk(root).use { s ->
                s
                    .filter { it.isRegularFile() && it.extension == "ttrm" }
                    .toList()
                    .associate { root.relativize(it).toString().replace('\\', '/') to Files.readAllBytes(it) }
            }
        val lines =
            files.entries
                .sortedBy { it.key }
                .joinToString("") { (path, bytes) -> "$path\t${hex(bytes)}\n" }
        return "sha256:" + hex(lines.toByteArray())
    }

    private fun hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
