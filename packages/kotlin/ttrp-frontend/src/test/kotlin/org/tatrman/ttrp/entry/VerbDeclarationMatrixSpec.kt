// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import org.tatrman.ttr.metadata.model.DbColumn
import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.model.TableChangeSemantics
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P2.1 T4 — the verb/declaration matrix (contracts §4/§9): `TTRP-EN-002` (verb needs a semantics
 * the target lacks), `TTRP-EN-003` (declared mode missing a role column), `TTRP-EN-004` (hard delete
 * demanded of a ledger/scd2 target).
 */
class VerbDeclarationMatrixSpec :
    StringSpec({
        val loc = SourceLocation("x-entry-apply.ttrp", 1, 0, 1, 0, 0, 0)

        fun verb(name: String) = EntryVerbCatalog.byName(name)!!

        fun check(
            v: String,
            table: DbTable,
            physicalDelete: Boolean = false,
        ) = VerbDeclarationChecker.check(verb(v), table, physicalDelete, loc).map { it.id }

        "effective-date-change on a non-scd2 target raises EN-002" {
            // ref_region is scd1, plain_notes is undeclared — neither is scd2.
            check("effective-date-change", EntryFixtures.table("ref_region")) shouldContain TtrpDiagnosticId.EN_002
            check("effective-date-change", EntryFixtures.table("plain_notes")) shouldContain TtrpDiagnosticId.EN_002
        }

        "reverse-and-replace on a non-ledger target raises EN-002" {
            // dim_customer is scd2, not ledger.
            check("reverse-and-replace", EntryFixtures.table("dim_customer")) shouldContain TtrpDiagnosticId.EN_002
        }

        "the matching verb on the right target does not raise EN-002" {
            check("effective-date-change", EntryFixtures.table("dim_customer")) shouldNotContain TtrpDiagnosticId.EN_002
            check("reverse-and-replace", EntryFixtures.table("txn_book")) shouldNotContain TtrpDiagnosticId.EN_002
        }

        "an scd2 target whose role column is unresolved raises EN-003" {
            // validTo points at a column the table does not carry.
            val broken =
                DbTable(
                    internalId = "db.dbo.broken_scd2",
                    qname = QualifiedName(SchemaCode.DB, "dbo", "broken_scd2"),
                    columns =
                        listOf(
                            col("broken_scd2", "id"),
                            col("broken_scd2", "valid_from"),
                        ),
                    changeSemantics =
                        TableChangeSemantics("scd2", mapOf("validFrom" to "valid_from", "validTo" to "missing_col")),
                )
            check("update-rows", broken) shouldContain TtrpDiagnosticId.EN_003
        }

        "a hard physical delete on a ledger/scd2 target raises EN-004; delete-rows does not" {
            check("delete-rows", EntryFixtures.table("txn_book"), physicalDelete = true) shouldContain
                TtrpDiagnosticId.EN_004
            check("delete-rows", EntryFixtures.table("dim_customer"), physicalDelete = true) shouldContain
                TtrpDiagnosticId.EN_004
            check("delete-rows", EntryFixtures.table("txn_book"), physicalDelete = false) shouldNotContain
                TtrpDiagnosticId.EN_004
            check("delete-rows", EntryFixtures.table("plain_notes"), physicalDelete = true) shouldNotContain
                TtrpDiagnosticId.EN_004
        }
    })

private fun col(
    table: String,
    name: String,
) = DbColumn(
    internalId = "db.dbo.$table.$name",
    qname = QualifiedName(SchemaCode.DB, "dbo", name),
    table = QualifiedName(SchemaCode.DB, "dbo", table),
    dataType = "string",
)
