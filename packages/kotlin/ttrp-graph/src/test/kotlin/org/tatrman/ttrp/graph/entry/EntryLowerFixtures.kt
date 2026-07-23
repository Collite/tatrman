// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.entry

import org.tatrman.ttr.metadata.model.DbColumn
import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.model.TableChangeSemantics
import org.tatrman.ttrp.entry.EntryApplyResolver.EntryApplyUnit
import org.tatrman.ttrp.entry.EntryVerbCatalog
import org.tatrman.ttrp.entry.RowBatch

/**
 * EN-P3 lowering fixtures. The lowering takes an already-resolved [EntryApplyUnit], so tests build one
 * directly (constructed [DbTable] with entry declarations + a parsed §5 [RowBatch]) rather than routing
 * through the graph-build path — `GraphFixtures` is TtrpChecker/GraphBuilder-based and does not apply.
 * The five change-semantics postures mirror the frontend EN-P2 fixture model. Table/column shapes are
 * aligned to the FO-P2 platform `SemanticsFixtures` live DDL (EN-P4b) so the emitted plans hit the real
 * tables: `dim_customer` has a composite PK + a `region` column, `txn_book` carries `txn_ref`, and the
 * undeclared `raw_notes` uses the convention `row_version` column for the §10 optimistic protocol.
 */
object EntryLowerFixtures {
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

    /**
     * ED — the FO-8 cash-leg book: the author proposes the security leg (`entry_id`, `security_amount`)
     * and `cash_amount` is DERIVED by `call-fn("cash-of", security_amount)`. Undeclared → optimistic;
     * `insert-rows` needs no change-semantics, so the derivation shape shows on a plain insert.
     */
    val derivBook =
        DbTable(
            internalId = "db.dbo.deriv_book",
            qname = QualifiedName(SchemaCode.DB, "dbo", "deriv_book"),
            primaryKey = listOf("entry_id"),
            columns =
                listOf(
                    col("deriv_book", "entry_id", "string"),
                    col("deriv_book", "security_amount", "bigint"),
                    col("deriv_book", "cash_amount", "bigint"),
                ),
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

    /** Build a resolved unit for [table] under [verbId] with the §5 batch [batchJson]. */
    fun unit(
        table: DbTable,
        verbId: String,
        batchJson: String,
    ): EntryApplyUnit =
        EntryApplyUnit(
            fileName = "${table.qname.name}-entry-apply.ttrp",
            targetName = table.qname.name,
            target = table,
            verb = EntryVerbCatalog.byId(verbId),
            batch = RowBatch.parse(batchJson),
            diagnostics = emptyList(),
        )
}
