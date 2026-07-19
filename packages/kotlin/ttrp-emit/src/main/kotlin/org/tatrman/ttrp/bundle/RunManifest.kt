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
    /**
     * The E-5 version key (contracts §6 / §5.1). Door execution keys on this. PL-P1.S3 emits **v2**:
     * schemaVersion 2 + per-island/transfer `connections` + static `lineage`. `params`/`retries`/
     * `onFailureOf` stay ABSENT until PL-P2 supplies the F-4 grammar (the schema permits them).
     */
    val schemaVersion: Int = 2,
    val ttrpVersion: Int = 1,
    val toolchain: String,
    val program: String,
    val world: WorldRef,
    val islands: List<IslandEntry>,
    val transfers: List<TransferEntry>,
    val waves: List<List<String>>,
    val connections: List<String>,
    val displays: List<DisplayEntry>,
    /** CQ-5 static, compile-derived column lineage (contracts §6). Null ⇒ omitted (no columns derived). */
    val lineage: Lineage? = null,
    /**
     * One entry per elaborated reject site (RJ-P3, contracts §7). Empty for every program with no
     * wired rejects — the field defaults empty, so a rejects-free `manifest.json` is byte-identical
     * to pre-feature. Feeds RJ-P5's eighth conform check: per site,
     * `count(in) == count(processed) + count(rejects)`.
     */
    val rejectSites: List<RejectSiteEntry> = emptyList(),
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
                // Omit null lineage (a v2 manifest without derived columns carries no `lineage` key —
                // the schema forbids `lineage: null`). Non-null defaults still encode (encodeDefaults).
                explicitNulls = false
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
    /** v2 (§6): per-island connection refs (H-5 least exposure — was bundle-global only). */
    val connections: List<String> = emptyList(),
)

@Serializable
data class TransferEntry(
    val from: String,
    val to: String,
    val via: String,
    val file: String,
    val sha256: String,
    /** v2 (§6): the connection refs this transfer touches (source × target). */
    val connections: List<String> = emptyList(),
)

/**
 * CQ-5 static column lineage (contracts §6), compile-derived. Maps 1:1 onto the OpenLineage
 * `columnLineage` facet (export lives in Veles connectors, PL-P1.S9). `transform` draws the ⚑
 * vocabulary `identity | expression | aggregate:<fn> | join-key | filter-only`.
 */
@Serializable
data class Lineage(
    val version: Int = 1,
    val columns: List<LineageColumn> = emptyList(),
)

@Serializable
data class LineageColumn(
    val output: LineageOutput,
    val inputs: List<LineageInput>,
    val transform: String,
)

@Serializable
data class LineageOutput(
    val island: String,
    val relation: String,
    val column: String,
)

@Serializable
data class LineageInput(
    val qname: String,
    val column: String,
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
