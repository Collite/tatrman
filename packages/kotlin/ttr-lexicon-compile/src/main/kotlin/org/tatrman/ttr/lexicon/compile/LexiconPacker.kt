// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import org.tatrman.ttr.lexicon.LexiconArchive
import org.tatrman.ttr.snapshot.SnapshotId
import org.tatrman.ttr.snapshot.SnapshotManifest
import org.tatrman.ttr.snapshot.SnapshotWriter

/** A packed lexicon archive: the bytes plus the content id they hash to. */
data class PackedLexicon(
    val bytes: ByteArray,
    val id: String,
) {
    // ByteArray in a data class — identity is the id, which IS the content hash.
    override fun equals(other: Any?): Boolean = other is PackedLexicon && other.id == id

    override fun hashCode(): Int = id.hashCode()
}

/**
 * RV-P1.2 T4 — pack a compile result into its own `kind: "lexicon"` snapshot archive.
 *
 * No new container: this is `SnapshotWriter` verbatim, so the lexicon archive inherits the
 * determinism rules the model archive is already held to by a golden cross-OS test (USTAR,
 * bytewise order, `mtime=0`, zstd-19). Same inputs ⇒ same bytes ⇒ same id.
 */
object LexiconPacker {
    fun pack(
        result: CompileResult,
        modelSnapshotId: String,
        producedBy: String,
    ): PackedLexicon {
        val manifest =
            SnapshotManifest(
                kind = LexiconArchive.KIND,
                producedBy = producedBy,
                // The model snapshot every ref was validated against. Provenance in the manifest,
                // and independently in the lexicon header — a reader should not have to open the
                // container to learn what the payload was built from.
                resolvedFrom = mapOf(LexiconArchive.RESOLVED_FROM_MODEL to modelSnapshotId),
            )
        val bytes =
            SnapshotWriter.write(
                manifest,
                mapOf(
                    LexiconArchive.LEXICON to result.lexicon.toJson(),
                    LexiconArchive.OPERATORS to result.operators.toJson(),
                ),
            )
        return PackedLexicon(bytes, SnapshotId.of(bytes))
    }
}
