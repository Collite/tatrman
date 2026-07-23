// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import org.tatrman.ttr.metadata.model.DbColumn
import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.model.TableChangeSemantics
import org.tatrman.ttrp.entry.EntryApplyResolver.EntryApplyUnit
import org.tatrman.ttrp.entry.EntryVerbCatalog
import org.tatrman.ttrp.entry.RowBatch
import org.tatrman.ttrp.graph.entry.EntryLowering

/**
 * EN-P4 emit fixtures: build an [EmittedApplyPlan] end-to-end (constructed [DbTable] + parsed §5 batch
 * → EN-P3 [EntryLowering] → [ApplyEmitter]). The `AuditLog` table carries a MixedCase column/pk to
 * prove F4 exact-case quoting; the other four mirror the §9 postures and are aligned to the FO-P2
 * platform `SemanticsFixtures` live DDL (EN-P4b: composite-PK `dim_customer` with a `region` column,
 * `txn_book` with `txn_ref`, undeclared `raw_notes` with the convention `row_version` column) so the
 * emitted plans run against the seeded tables in the live door round-trip.
 */
object EntryEmitFixtures {
    private fun col(
        table: String,
        name: String,
        type: String,
    ) = DbColumn(
        internalId = "db.dbo.$table.$name",
        qname = QualifiedName(SchemaCode.DB, "dbo", name),
        table = QualifiedName(SchemaCode.DB, "dbo", table),
        dataType = type,
    )

    val refRegion =
        DbTable(
            internalId = "db.dbo.ref_region",
            qname = QualifiedName(SchemaCode.DB, "dbo", "ref_region"),
            primaryKey = listOf("region_code"),
            columns = listOf(col("ref_region", "region_code", "string"), col("ref_region", "region_name", "string")),
            changeSemantics = TableChangeSemantics("scd1"),
        )

    val dimCustomer =
        DbTable(
            internalId = "db.dbo.dim_customer",
            qname = QualifiedName(SchemaCode.DB, "dbo", "dim_customer"),
            primaryKey = listOf("customer_id", "valid_from"),
            columns =
                listOf(
                    col("dim_customer", "customer_id", "string"),
                    col("dim_customer", "region", "string"),
                    col("dim_customer", "valid_from", "date"),
                    col("dim_customer", "valid_to", "date"),
                ),
            changeSemantics = TableChangeSemantics("scd2", mapOf("validFrom" to "valid_from", "validTo" to "valid_to")),
        )

    val txnBook =
        DbTable(
            internalId = "db.dbo.txn_book",
            qname = QualifiedName(SchemaCode.DB, "dbo", "txn_book"),
            primaryKey = listOf("entry_id"),
            columns =
                listOf(
                    col("txn_book", "entry_id", "string"),
                    col("txn_book", "txn_ref", "string"),
                    col("txn_book", "amount", "bigint"),
                    col("txn_book", "reversal_of", "string"),
                ),
            changeSemantics = TableChangeSemantics("ledger", mapOf("reversalLink" to "reversal_of")),
        )

    /** Undeclared (no change-semantics) → optimistic; the `row_version` convention column drives §10. */
    val rawNotes =
        DbTable(
            internalId = "db.dbo.raw_notes",
            qname = QualifiedName(SchemaCode.DB, "dbo", "raw_notes"),
            primaryKey = listOf("k"),
            columns =
                listOf(
                    col("raw_notes", "k", "string"),
                    col("raw_notes", "v", "string"),
                    col("raw_notes", "row_version", "string"),
                ),
        )

    /** F4 proof table — MixedCase pk + column; the emitter must quote them in exact case. */
    val auditLog =
        DbTable(
            internalId = "db.dbo.AuditLog",
            qname = QualifiedName(SchemaCode.DB, "dbo", "AuditLog"),
            primaryKey = listOf("Id"),
            columns = listOf(col("AuditLog", "Id", "string"), col("AuditLog", "MixedCol", "string")),
            changeSemantics = TableChangeSemantics("scd1"),
        )

    fun emit(
        table: DbTable,
        verbId: String,
        batchJson: String,
    ): ApplyEmitResult {
        val batch = RowBatch.parse(batchJson)
        val unit =
            EntryApplyUnit(
                fileName = "${table.qname.name}-entry-apply.ttrp",
                targetName = table.qname.name,
                target = table,
                verb = EntryVerbCatalog.byId(verbId),
                batch = batch,
                diagnostics = emptyList(),
            )
        val lowered = EntryLowering.lower(unit)
        return ApplyEmitter.emit(lowered.plan!!, batch, table)
    }
}
