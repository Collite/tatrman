// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.cli

import org.tatrman.ttr.lexicon.sha256
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo

/**
 * RV-P3.1 T1, ruling 2 (Bora: **(a) content hash**) — the `modelSnapshotId` for an estate that has
 * no model-snapshot pipeline to take one from.
 *
 * `sha256:<hex>` over every `.ttr`/`.ttrm` under `model/`, in sorted relative-path order, each
 * contributing its path AND its bytes. Spelled exactly like [org.tatrman.ttr.snapshot.SnapshotId],
 * so a reader comparing the two cannot tell them apart by shape — the id is written into the
 * archive manifest's `resolvedFrom` and into the lexicon header, and a reader's only question is
 * "which model was this vocabulary validated against".
 *
 * **Not `LocalFsStorage.fetchVersion()`**, which is the obvious-looking candidate and the wrong
 * one: it hashes `path=<lastModifiedTime>` pairs. Git does not preserve mtimes, so a fresh clone
 * of an unchanged estate produces a different value — that function is a change *detector* for a
 * running refresher, not a content id.
 */
object ModelContentId {
    /** The two model extensions `FileBasedSource` itself walks. */
    val EXTENSIONS: List<String> = listOf(".ttr", ".ttrm")

    /**
     * The path is hashed alongside the content because renaming a file is a model change: two
     * estates whose files differ only in name must not share an id.
     *
     * NUL separates the fields — it cannot occur in a path and does not occur in TTR source. It is
     * written here as an escape, deliberately: `LexiconCompiler.layerHash` embeds the same
     * separator as a **literal** byte, which works and defeats every text-based editing tool.
     */
    fun of(modelRoot: Path): String {
        if (!modelRoot.isDirectory()) return sha256(ByteArray(0))

        val buffer = ByteArrayOutputStream()
        Files.walk(modelRoot).use { stream ->
            stream
                .filter { !it.isDirectory() && EXTENSIONS.any { ext -> it.toString().endsWith(ext) } }
                .sorted()
                .toList()
                .forEach { file ->
                    buffer.write(file.relativeTo(modelRoot).toString().toByteArray(Charsets.UTF_8))
                    buffer.write(0)
                    buffer.write(file.readBytes())
                    buffer.write(0)
                }
        }
        return sha256(buffer.toByteArray())
    }
}
