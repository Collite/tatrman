// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P2.1 T3 — the `TTRP-EN-001` matrix (contracts §5): a §5 batch shape-checked against the target's
 * md shape. Each case asserts the code AND that the message names the offending column/row. The scd2
 * target `dim_customer` has customer_id(int), customer_name(str), valid_from/valid_to(date).
 */
class BatchShapeMismatchSpec :
    StringSpec({
        val loc = SourceLocation("dim_customer-entry-apply.ttrp", 1, 0, 1, 0, 0, 0)
        val scd2 = EntryFixtures.table("dim_customer")

        // Wraps a proposal body in the §5 envelope, then shape-checks it against the scd2 target.
        fun check(
            body: String,
            effectiveDateRequired: Boolean = false,
        ) = BatchShapeChecker.check(
            RowBatch.parse("""{ "target": { "table": "entry.db.dbo.dim_customer" }, "proposals": [ $body ] }"""),
            scd2,
            effectiveDateRequired,
            loc,
        )

        "an unknown column in `values` names the column (EN-001)" {
            val d = check("""{ "op": "update", "key": { "customer_id": 1 }, "values": { "nope": "x" } }""")
            d.map { it.id } shouldContain TtrpDiagnosticId.EN_001
            d.single { it.id == TtrpDiagnosticId.EN_001 }.message shouldContain "nope"
        }

        "a value incompatible with its md type is rejected (EN-001)" {
            // customer_id is integer (NUMBER) — a text value is incompatible.
            val d = check("""{ "op": "update", "key": { "customer_id": 1 }, "values": { "customer_id": "x" } }""")
            d.map { it.id } shouldContain TtrpDiagnosticId.EN_001
            d.any { it.message.contains("customer_id") } shouldBe true
        }

        "insert must not carry a key; update/delete must (EN-001)" {
            check("""{ "op": "insert", "key": { "customer_id": 1 }, "values": { "customer_name": "a" } }""")
                .map { it.id } shouldContain TtrpDiagnosticId.EN_001
            check("""{ "op": "update", "values": { "customer_name": "a" } }""")
                .map { it.id } shouldContain TtrpDiagnosticId.EN_001
        }

        "an op outside the §5 enum is rejected and named (EN-001)" {
            val d = check("""{ "op": "frobnicate", "values": { "customer_name": "a" } }""")
            d.single { it.id == TtrpDiagnosticId.EN_001 && it.message.contains("frobnicate") }
        }

        "an scd2 dated change missing effectiveDate is rejected and names the row (EN-001)" {
            val d =
                check(
                    """{ "op": "update", "key": { "customer_id": 1 }, "values": { "customer_name": "a" } }""",
                    effectiveDateRequired = true,
                )
            d
                .single { it.id == TtrpDiagnosticId.EN_001 && it.message.contains("effectiveDate") }
                .message shouldContain "row 1"
        }

        "a well-shaped batch produces no diagnostics" {
            check(
                """{ "op": "update", "key": { "customer_id": 1 }, "values": { "customer_name": "a" }, """ +
                    """"effectiveDate": "2026-01-01" }""",
                effectiveDateRequired = true,
            ) shouldBe emptyList()
        }
    })
