// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `counts.json` — the per-engine partition tally an island writes **at run time**, next to its Arrow
 * exports (contracts §7 / RJ-P5). One [SiteCounts] per elaborated reject site: the rows entering the
 * guard (`in`), the rows leaving on the processed OUT ports, and the rows on the `rejects` port.
 *
 * This is the honest, engine-sourced input to the eighth conform point (`PartitionCheck`): the
 * comparator cannot recover `in` from the exported streams alone (only the *split* is exported), so
 * each engine reports its own guard-input count here. A rejects-free program writes no `counts.json`
 * (or an empty one), so the artifact is absent exactly when [RunManifest.rejectSites] is empty.
 */
@Serializable
data class SiteCounts(
    val site: String,
    @SerialName("in") val inCount: Long,
    val processed: Long,
    val rejects: Long,
)

@Serializable
data class CountsFile(
    val sites: List<SiteCounts> = emptyList(),
) {
    fun toJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
                encodeDefaults = true
                ignoreUnknownKeys = false
            }

        fun fromJson(text: String): CountsFile = JSON.decodeFromString(text)
    }
}
