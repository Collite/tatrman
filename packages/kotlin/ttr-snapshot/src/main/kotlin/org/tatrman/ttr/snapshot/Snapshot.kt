// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.snapshot

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * `snapshot.json` — the archive's self-description (contracts §2). One archive = one source
 * kind's content set. `resolvedFrom` is **informative provenance only** — never hashed into any
 * fingerprint (world archives carry the RESOLVED composed world, so re-resolution is the identity).
 */
@Serializable
data class SnapshotManifest(
    val formatVersion: Int = 1,
    /** `world` | `models` | `manifests`. */
    val kind: String,
    val qnames: List<String> = emptyList(),
    /** `veles <semver>` (or the standalone toolchain when packed locally). */
    val producedBy: String,
    /** Informative inputs, e.g. `{"platformWorldCommit":"<git sha>"}`. Not fingerprinted. */
    val resolvedFrom: Map<String, String> = emptyMap(),
) {
    companion object {
        /** Stable, strict — `snapshot.json` bytes are inside the content-addressed archive. */
        val JSON =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
                encodeDefaults = true
                ignoreUnknownKeys = false
            }

        fun fromJson(text: String): SnapshotManifest = JSON.decodeFromString(text)
    }

    fun toJson(): String = JSON.encodeToString(this)
}

/** One archive's decoded contents: its manifest + the `docs/` document tree (relPath → UTF-8). */
data class SnapshotContents(
    val manifest: SnapshotManifest,
    /** Keyed by path **relative to `docs/`** (e.g. `erp/db.ttrm`), sorted bytewise. */
    val docs: Map<String, String>,
)

/**
 * Content id of an archive: `sha256:<hex>` over the **compressed archive bytes** (contracts §2),
 * spelled exactly like a world fingerprint.
 */
object SnapshotId {
    fun of(compressed: ByteArray): String {
        val hex =
            MessageDigest
                .getInstance("SHA-256")
                .digest(compressed)
                .joinToString("") { "%02x".format(it) }
        return "sha256:$hex"
    }

    private val RE = Regex("^sha256:[0-9a-f]{64}$")

    fun isValid(id: String): Boolean = RE.matches(id)
}
