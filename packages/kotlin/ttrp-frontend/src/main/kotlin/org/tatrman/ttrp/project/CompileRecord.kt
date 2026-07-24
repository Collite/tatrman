// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * The compile record (contracts §5, B-3-α/F-7) — emitted by **every** compile as a bundle-**adjacent**
 * sidecar `<program>.compile-record.json`, **beside** the `.bundle/` directory: never inside the
 * bundle, never in the manifest's `files{}`. The bundle stays a pure function of resolved inputs
 * (B-3); the record *describes* those inputs and is legitimately binding-dependent (`mode`,
 * `staleness`), so it must not be hashed into the artifact. The envelope carries it (F-7, §13).
 */
@Serializable
data class CompileRecord(
    val recordVersion: Int = 1,
    val toolchain: String,
    val program: String,
    /** `connected` | `offline` | `standalone` — informative; lives in the sidecar ONLY, never the bundle. */
    val mode: String,
    val lock: LockRef? = null,
    val snapshot: SnapshotRef = SnapshotRef(),
    val worldFingerprint: String,
    val plugins: List<PluginRef> = emptyList(),
    /** The §4 entries the optimizer consumed, verbatim ([] in stats-less compiles). */
    val statsUsed: List<StatsEntry> = emptyList(),
    val staleness: RecordStaleness = RecordStaleness(),
    /** The F-7 program-scoped provenance slice: db objects the program read. */
    val objectsRead: List<String> = emptyList(),
) {
    fun toJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
                encodeDefaults = true
                explicitNulls = false
            }

        fun sha256Of(bytes: ByteArray): String =
            "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Build the connected-mode record from a resolved lock + the stats the optimizer used. */
        fun connected(
            toolchain: String,
            program: String,
            worldFingerprint: String,
            lockBytes: ByteArray,
            lock: TtrLock,
            statsUsed: List<StatsEntry>,
            staleness: RecordStaleness,
            objectsRead: List<String>,
        ): CompileRecord =
            CompileRecord(
                toolchain = toolchain,
                program = program,
                mode = if (staleness.offline) "offline" else "connected",
                lock = LockRef(sha256Of(lockBytes), "ttr.lock"),
                snapshot =
                    SnapshotRef(
                        world = lock.world.archive,
                        models = lock.models.toSortedMap(),
                        manifests = lock.manifests.toSortedMap(),
                    ),
                worldFingerprint = worldFingerprint,
                plugins = lock.plugins.toSortedMap().map { (id, p) -> PluginRef(id, p.version, p.sha256) },
                statsUsed = statsUsed,
                staleness = staleness,
                objectsRead = objectsRead.sorted(),
            )

        /** Build the standalone-mode record (LocalFs binding — no lock, no server canon). */
        fun standalone(
            toolchain: String,
            program: String,
            worldFingerprint: String,
            objectsRead: List<String>,
        ): CompileRecord =
            CompileRecord(
                toolchain = toolchain,
                program = program,
                mode = "standalone",
                worldFingerprint = worldFingerprint,
                objectsRead = objectsRead.sorted(),
            )
    }
}

@Serializable
data class LockRef(
    val hash: String,
    val path: String,
)

@Serializable
data class SnapshotRef(
    val world: String = "",
    val models: Map<String, String> = emptyMap(),
    val manifests: Map<String, String> = emptyMap(),
)

@Serializable
data class PluginRef(
    val id: String,
    val version: String,
    val sha256: String,
)

/** §5 staleness: was this offline, and which served stats were older than their max-age. */
@Serializable
data class RecordStaleness(
    val offline: Boolean = false,
    val statsOlderThanMaxAge: List<String> = emptyList(),
)
