// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P2.1 T2 — an apply program is recognized by filename and resolves to a typed [EntryApplyResolver
 * .EntryApplyUnit]: target table via ttr-metadata, the §5 batch bound, the verb from the catalogue. A
 * program targeting an unmodeled table raises `TTRP-EN-007`.
 */
class EntryProgramResolutionSpec :
    StringSpec({
        "the fixture model loads and surfaces the entry declarations" {
            EntryFixtures.table("dim_customer").changeSemantics?.mode shouldBe "scd2"
            EntryFixtures.table("txn_book").changeSemantics?.mode shouldBe "ledger"
            EntryFixtures.table("catalog_ref").managementMode shouldBe "canon"
            EntryFixtures.table("plain_notes").changeSemantics shouldBe null
        }

        "isEntryApply recognizes `<table>-entry-apply.ttrp` and extracts the target" {
            EntryApplyResolver.isEntryApply("dim_customer-entry-apply.ttrp").shouldBeTrue()
            EntryApplyResolver.targetName("/models/dim_customer-entry-apply.ttrp") shouldBe "dim_customer"
            EntryApplyResolver.isEntryApply("plain_summary.ttrp") shouldBe false
        }

        "a valid scd2 apply program resolves to a typed unit that typechecks" {
            val unit =
                EntryApplyResolver.resolve(
                    fileName = EntryFixtures.fileName("dim_customer"),
                    source = EntryFixtures.programSource(),
                    verbId = "entry.effective-date-change",
                    batch = EntryFixtures.batchResource("dim_customer-valid"),
                    modelIndex = EntryFixtures.modelIndex(),
                )
            unit.target.shouldNotBeNull()
            unit.target!!.qname.name shouldBe "dim_customer"
            unit.verb!!.id shouldBe "entry.effective-date-change"
            unit.ok.shouldBeTrue()
        }

        "an apply program targeting an unmodeled table raises TTRP-EN-007" {
            val unit =
                EntryApplyResolver.resolve(
                    fileName = EntryFixtures.fileName("no_such_table"),
                    source = EntryFixtures.programSource(),
                    verbId = "entry.insert-rows",
                    batch = null,
                    modelIndex = EntryFixtures.modelIndex(),
                )
            unit.target shouldBe null
            unit.diagnostics.map { it.id } shouldContain TtrpDiagnosticId.EN_007
        }
    })
