// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P4.1 T3 — typed binds (F4 inverted): a batch value that cannot bind as its md column's type fails
 * at manifest construction (`TTRP-EN-001`) with the row/column named — never deferred to execution.
 */
class TypedBindSpec :
    StringSpec({
        val f = EntryEmitFixtures

        fun batch(body: String) = """{ "target": { "table": "x" }, "proposals": [ $body ] }"""

        "a text value for a BIGINT column fails at emit, naming the column and row" {
            // txn_book.amount is bigint; a text value cannot bind.
            val r =
                f.emit(
                    f.txnBook,
                    "entry.insert-rows",
                    batch("""{ "op": "insert", "values": { "entry_id": "e9", "amount": "not-a-number" } }"""),
                )
            r.plan shouldBe null
            r.diagnostics.map { it.id } shouldContain TtrpDiagnosticId.EN_001
            r.diagnostics.single { it.id == TtrpDiagnosticId.EN_001 }.message shouldContain "amount"
        }

        "a non-ISO value for a DATE column fails at emit" {
            // dim_customer.valid_from is date; effective-date-change binds effectiveDate there.
            val r =
                f.emit(
                    f.dimCustomer,
                    "entry.effective-date-change",
                    batch(
                        """{ "op": "update", "key": { "customer_id": "C1" }, "values": { "region": "A" }, "effectiveDate": "nope" }""",
                    ),
                )
            r.plan shouldBe null
            r.diagnostics.map { it.id } shouldContain TtrpDiagnosticId.EN_001
        }

        "well-typed values emit cleanly" {
            val r =
                f.emit(
                    f.txnBook,
                    "entry.insert-rows",
                    batch("""{ "op": "insert", "values": { "entry_id": "e9", "amount": 100 } }"""),
                )
            r.ok shouldBe true
            r.plan!!
                .proposals
                .single()
                .steps
                .single()
                .binds
                .any { it is Bind.Value && it.type == SqlType.BIGINT } shouldBe
                true
        }
    })
