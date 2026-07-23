// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

/**
 * The **row-batch input kind** (FO contracts §5 / demand `ttr-p-row-entry.md` §1) — the compile-time
 * internal model of the table-valued batch that an `<table>-entry-apply` program binds as its one
 * parameter. Field-for-field the FROZEN §5 shape ratified 2026-07-19; the platform's JSON/wire form
 * lives in `tatrman-platform` `cz.tatrman.entry.journal.RowBatch` — **this is the compiler's
 * shape-checking model, not a second source of truth and not a parser surface yet.**
 *
 * EN-P0.2 draft (types only). EN-P2 binds a program's batch parameter against these rosters and raises
 * `TTRP-EN-001` (batch shape mismatch vs the target's md shape) / `TTRP-EN-002…004` from them.
 *
 * `// EN: input-kind decl pins on PLA-2` — the concrete surface *declaration syntax* for the batch
 * parameter arrives with PL's PLA-2 table-valued-param ratification (deferral A2). The shape below is
 * ratified and fixed; only the spelling of its declaration is provisional. Same shape, one owner, no
 * fork (architecture §5, contracts §3).
 */
object RowBatchInputKind {
    /** One field of the input kind: its name and whether §5 requires it to be *present* (may be null). */
    data class Field(
        val name: String,
        val required: Boolean,
    )

    /** §5 batch envelope. All six fields are required-present. */
    val batchFields =
        listOf(
            Field("batchId", required = true),
            Field("kind", required = true),
            Field("target", required = true),
            Field("modelVersion", required = true),
            Field("proposals", required = true),
            Field("source", required = true),
        )

    /**
     * §5 proposal. All five fields are required-*present*; `key`/`baseRowVersion`/`effectiveDate` are
     * nullable in value but must appear (the schema lists them in `required`). Per-op presence of `key`
     * (null for insert, object for update/delete) is a value rule EN-P2 enforces, not a field-roster
     * fact.
     */
    val proposalFields =
        listOf(
            Field("op", required = true),
            Field("key", required = true),
            Field("values", required = true),
            Field("baseRowVersion", required = true),
            Field("effectiveDate", required = true),
        )

    /** §5 source (proposal provenance; `pluginId`/`pluginVersion` present-but-nullable). */
    val sourceFields =
        listOf(
            Field("type", required = true),
            Field("ref", required = true),
            Field("pluginId", required = true),
            Field("pluginVersion", required = true),
        )

    /** §5 target is a `oneOf`: a base table, or a table-backed entity lowered v1 (FO-32). */
    val targetTableFields = listOf(Field("table", required = true))
    val targetEntityFields = listOf(Field("entity", required = true), Field("lowering", required = true))

    /** The `kind` discriminator constant (§5). */
    const val KIND = "row-proposal"

    /** The `lowering` constant an entity target must carry (§5, FO-32). */
    const val ENTITY_LOWERING_V1 = "v1"
}

/** §5 proposal op vocabulary. `DELETE` is ratification amendment A1 (`delete-rows`), §12-guarded. */
enum class EntryOp(
    val wire: String,
) {
    INSERT("insert"),
    UPDATE("update"),
    DELETE("delete"),
}

/** §5 proposal-source type vocabulary. */
enum class EntrySourceType(
    val wire: String,
) {
    FORM("form"),
    IMPORT("import"),
    AGENT("agent"),
    RECONCILIATION("reconciliation"),
}
