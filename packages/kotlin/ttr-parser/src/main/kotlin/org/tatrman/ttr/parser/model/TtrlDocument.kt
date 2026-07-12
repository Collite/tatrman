// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.model

/**
 * The parsed `.ttrl` view-state sidecar (grammar v4.3, C1-c-iii) — the family-wide
 * per-document layout companion (`x.ttrp` ↔ `x.ttrl`, `report.ttr.sql` ↔ `report.ttrl`).
 * Hosted by the TTR-M grammar (a separate `ttrlDocument` entry rule), consumed by the
 * TTR-P LSP's `ttrp/getLayout`/`setLayout` and written back wholesale by `TtrlWriter`.
 *
 * The inventory is fixed (C1-c-iii): a `ttrl <version>` header, `canvas` blocks each
 * carrying `skin`, `mode` (auto|manual), ζ-keyed `nodes` (manual only) and a `collapsed`
 * list. No `viewport` (dropped). The `bendPoints` (`edges:`) slot is reserved, never
 * written in v1.
 */
data class TtrlDocument(
    val version: Int,
    val canvases: List<TtrlCanvas>,
    val sourceFile: String,
)

enum class TtrlMode {
    AUTO,
    MANUAL,
}

data class TtrlCanvas(
    /** Canvas key: TTR-P `program` / container path, or a TTR-M qname. */
    val key: String,
    /** Skin id (`alteryx-knime` / `enso`), or null when unset. */
    val skin: String?,
    val mode: TtrlMode,
    /** ζ-keyed manual positions; empty on an auto canvas (grammar-agnostic, validated on write). */
    val nodes: List<TtrlNodeEntry>,
    /** ζ keys of collapsed containers. */
    val collapsed: List<String>,
    /**
     * Recorded SSA chain length per name group at write time — the orphaning
     * discriminator (C1-c-i): if a name's current chain length differs from this, ALL its
     * entries orphan (never re-attach by guess, P2). Empty when the sidecar predates the
     * feature or the canvas is auto.
     */
    val chains: Map<String, Int>,
    val location: SourceLocation,
)

data class TtrlNodeEntry(
    /** The ζ identity key (`crunch/sales#2`, `big_customers~1`, …). */
    val zeta: String,
    val x: Double,
    val y: Double,
)
