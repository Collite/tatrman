// SPDX-License-Identifier: Apache-2.0
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
    /**
     * One entry per elaborated reject site (RJ-P3, contracts §7). Empty for every program with no
     * wired rejects — the field defaults empty, so a rejects-free `manifest.json` is byte-identical
     * to pre-feature. Feeds RJ-P5's eighth conform check: per site,
     * `count(in) == count(processed) + count(rejects)`.
     */
    val rejectSites: List<RejectSiteEntry> = emptyList(),
    /**
     * The MD compile parameters recorded for bind-time staleness (S4-B5, decision 13): the resolved
     * `asof` + the member-snapshot fingerprint. **Null (and omitted) for a non-MD program**, so every
     * existing manifest is byte-identical — the point of `explicitNulls = false`. Emitted only when the
     * program carries an MD model AND there is something to record (an `asof` or a fingerprint).
     */
    val md: MdManifest? = null,
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
                explicitNulls = false // omit null optionals (the `md` block) → non-MD manifests unchanged
                ignoreUnknownKeys = false // strict: reject unknown keys
            }

        fun fromJson(text: String): RunManifest = JSON.decodeFromString(text)
    }
}

/**
 * The MD compile parameters recorded in `manifest.json` for bind-time staleness (S4-B5, decision 13):
 * the resolved [asof] (the `[ttrp] md-asof` value, else the compile-pass clock) and the
 * [memberFingerprint] of the [org.tatrman.ttr.md.resolve.MemberSnapshot] the resolution used. Both are
 * optional and omitted when absent (`explicitNulls = false`): a disconnected compile (S4, no snapshot)
 * records only `asof`; production snapshot loading (S6-B) fills the fingerprint.
 */
@Serializable
data class MdManifest(
    /** Resolved `asof`, ISO-8601 (e.g. `2026-07-08T00:00:00Z`). Omitted only if never resolved. */
    val asof: String? = null,
    /** `sha256:…`-style member-snapshot fingerprint; omitted in disconnected mode. */
    val memberFingerprint: String? = null,
)

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

/**
 * An elaborated reject site (RJ-P3, contracts §7): the reject-producing node, its island/container,
 * the container OUT port carrying the `rejects` stream, and the sibling OUT ports carrying the
 * accepted rows. RJ-P5's eighth check does NOT count these OUT ports (that model was abandoned in the
 * seal — a downstream join/aggregate makes OUT-port counts diverge from the guard's clean output);
 * instead each engine writes `counts.json` counted at the guard's clean branch-child and the partition
 * check asserts `in == processed + rejects` there, cross-engine. The [site] id is the reconciliation
 * key the partition check verifies every engine's `counts.json` reports (RJ-P5 review, B3). The
 * [rejectsPort]/[processedPorts] here drive the reject/bad **stream** compares, not the count balance.
 */
@Serializable
data class RejectSiteEntry(
    val site: String,
    val container: String,
    val rejectsPort: String,
    val processedPorts: List<String>,
)
