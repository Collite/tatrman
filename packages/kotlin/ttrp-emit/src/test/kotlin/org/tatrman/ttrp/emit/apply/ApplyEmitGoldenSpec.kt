// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import io.kotest.core.spec.style.StringSpec
import org.tatrman.ttrp.emit.GoldenSupport

/**
 * EN-P4.1 T1 — emitted-plan goldens per §9 row: the SQL text + typed positional bind manifest. F4 is
 * baked in — the `audit_log` case has MixedCase identifiers (quoted in exact case) and every bind
 * carries its md-derived [SqlType]. Regenerate with `-DupdateGolden=true`.
 */
class ApplyEmitGoldenSpec :
    StringSpec({
        val f = EntryEmitFixtures

        fun golden(
            result: ApplyEmitResult,
            name: String,
        ) = GoldenSupport.assertMatchesGolden(EmittedApplyPlanRender.write(result.plan!!), "apply/$name.txt")

        fun batch(body: String) = """{ "target": { "table": "x" }, "proposals": [ $body ] }"""

        "scd1 update emits a parameterized UPDATE" {
            golden(
                f.emit(
                    f.refRegion,
                    "entry.update-rows",
                    batch(
                        """{ "op": "update", "key": { "region_code": "NA" }, "values": { "region_name": "North" } }""",
                    ),
                ),
                "scd1-update",
            )
        }

        "scd2 effective-date-change emits close + insert" {
            golden(
                f.emit(
                    f.dimCustomer,
                    "entry.effective-date-change",
                    batch(
                        """{ "op": "update", "key": { "customer_id": 1 }, "values": { "customer_name": "Acme" }, """ +
                            """"effectiveDate": "2026-01-01" }""",
                    ),
                ),
                "scd2-effective-date-change",
            )
        }

        "ledger reverse-and-replace emits the count read, an INSERT…SELECT reversal, and a replacement" {
            golden(
                f.emit(
                    f.txnBook,
                    "entry.reverse-and-replace",
                    batch("""{ "op": "update", "key": { "entry_id": "e1" }, "values": { "amount": 42 } }"""),
                ),
                "ledger-reverse-and-replace",
            )
        }

        "undeclared update emits a version read, a guard, and an UPDATE" {
            golden(
                f.emit(
                    f.plainNotes,
                    "entry.update-rows",
                    batch(
                        """{ "op": "update", "key": { "note_id": 7 }, "values": { "body": "hi" }, "baseRowVersion": "v3" }""",
                    ),
                ),
                "optimistic-update",
            )
        }

        "insert-rows emits a parameterized INSERT" {
            golden(
                f.emit(
                    f.refRegion,
                    "entry.insert-rows",
                    batch("""{ "op": "insert", "values": { "region_code": "SA", "region_name": "South" } }"""),
                ),
                "insert-rows",
            )
        }

        "F4: MixedCase identifiers are quoted in exact case" {
            golden(
                f.emit(
                    f.auditLog,
                    "entry.update-rows",
                    batch("""{ "op": "update", "key": { "Id": "a1" }, "values": { "MixedCol": "v" } }"""),
                ),
                "f4-mixedcase",
            )
        }
    })
