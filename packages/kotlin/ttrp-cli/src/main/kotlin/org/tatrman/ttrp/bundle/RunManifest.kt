package org.tatrman.ttrp.bundle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `manifest.json` — the bundle's machine record (contracts §5, F-f-i). Field order and names are
 * a contract (the file is committed/diffed by users), so this is pretty-printed with a stable key
 * order and **strict** decoding (unknown keys rejected — the manifest is a contract, not a grab
 * bag). `manifest.json` never appears in [files] (it can't contain its own hash — flagged for a
 * contracts §5 clarification line).
 */
@Serializable
data class RunManifest(
    val ttrpVersion: Int = 1,
    val toolchain: String,
    val program: String,
    val world: WorldRef,
    val islands: List<IslandEntry>,
    val transfers: List<TransferEntry>,
    val waves: List<List<String>>,
    val connections: List<String>,
    val displays: List<DisplayEntry>,
    val files: Map<String, String>,
) {
    fun toJson(): String = JSON.encodeToString(this)

    companion object {
        /** Pretty, stable, strict — the manifest is a diffable contract. */
        val JSON =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
                encodeDefaults = true
                ignoreUnknownKeys = false // strict: reject unknown keys
            }

        fun fromJson(text: String): RunManifest = JSON.decodeFromString(text)
    }
}

@Serializable
data class WorldRef(
    val qname: String,
    /** `sha256:<64 hex>` semantic world fingerprint (F-f-ii). */
    val fingerprint: String,
)

@Serializable
data class IslandEntry(
    val name: String,
    val engine: String,
    val executor: String,
    /** `psql` | `python3`. */
    val invocation: String,
    val file: String,
    val sha256: String,
)

@Serializable
data class TransferEntry(
    val from: String,
    val to: String,
    val via: String,
    val file: String,
    val sha256: String,
)

@Serializable
data class DisplayEntry(
    val name: String,
    /** `out/<name>.<fmt>`. */
    val file: String,
)
