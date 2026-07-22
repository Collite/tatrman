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
 * → EN-P3 [EntryLowering] → [ApplyEmitter]). The `audit_log` table carries a MixedCase column/pk to
 * prove F4 exact-case quoting; the other four mirror the §9 postures.
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
            primaryKey = listOf("customer_id"),
            columns =
                listOf(
                    col("dim_customer", "customer_id", "integer"),
                    col("dim_customer", "customer_name", "string"),
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
                    col("txn_book", "amount", "bigint"),
                    col("txn_book", "reversal_of", "string"),
                ),
            changeSemantics = TableChangeSemantics("ledger", mapOf("reversalLink" to "reversal_of")),
        )

    val plainNotes =
        DbTable(
            internalId = "db.dbo.plain_notes",
            qname = QualifiedName(SchemaCode.DB, "dbo", "plain_notes"),
            primaryKey = listOf("note_id"),
            columns = listOf(col("plain_notes", "note_id", "integer"), col("plain_notes", "body", "string")),
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
