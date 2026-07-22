// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * EN-P4.1 T2 — emit hygiene: no statement interpolates a value inline (placeholders only), and each
 * statement's `?` count equals its bind count. Values live in the typed bind manifest, never the text
 * (the F4 discipline; the live parse-check is the door round-trip, EN-P4b).
 */
class ApplyEmitHygieneSpec :
    StringSpec({
        val f = EntryEmitFixtures

        fun batch(body: String) = """{ "target": { "table": "x" }, "proposals": [ $body ] }"""

        val plans =
            listOf(
                f.emit(
                    f.refRegion,
                    "entry.update-rows",
                    batch(
                        """{ "op": "update", "key": { "region_code": "NA" }, "values": { "region_name": "North" } }""",
                    ),
                ),
                f.emit(
                    f.dimCustomer,
                    "entry.effective-date-change",
                    batch(
                        """{ "op": "update", "key": { "customer_id": 1 }, "values": { "customer_name": "A" }, "effectiveDate": "2026-01-01" }""",
                    ),
                ),
                f.emit(
                    f.txnBook,
                    "entry.reverse-and-replace",
                    batch("""{ "op": "update", "key": { "entry_id": "e1" }, "values": { "amount": 42 } }"""),
                ),
                f.emit(
                    f.plainNotes,
                    "entry.update-rows",
                    batch(
                        """{ "op": "update", "key": { "note_id": 7 }, "values": { "body": "hi" }, "baseRowVersion": "v3" }""",
                    ),
                ),
                f.emit(
                    f.refRegion,
                    "entry.insert-rows",
                    batch("""{ "op": "insert", "values": { "region_code": "SA", "region_name": "South" } }"""),
                ),
            ).map { it.plan!! }

        data class Stmt(
            val sql: String,
            val bindCount: Int,
        )

        val statements =
            plans.flatMap { p ->
                p.proposals.flatMap { pp ->
                    pp.reads.map { Stmt(it.sql, it.binds.size) } + pp.steps.map { Stmt(it.sql, it.binds.size) }
                }
            }

        "no emitted statement contains a single-quoted (inline) literal" {
            statements.forEach { it.sql.contains('\'') shouldBe false }
        }

        "every statement's placeholder count equals its bind count" {
            statements.forEach { it.sql.count { c -> c == '?' } shouldBe it.bindCount }
        }

        "every DML statement quotes its identifiers (contains a double-quote)" {
            statements.forEach { it.sql.contains('"') shouldBe true }
        }
    })
