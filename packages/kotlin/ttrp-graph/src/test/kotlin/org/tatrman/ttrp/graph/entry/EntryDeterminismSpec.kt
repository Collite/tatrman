// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P3.1 T3/T6 — determinism + the EN-004 structural guard. Lowering the same (program, md model)
 * twice is byte-identical (no clock/random/map-order leakage); and the lowering never produces a
 * physical delete on a ledger/scd2 target (deletes there soft-close / reverse) — the invariant behind
 * `TTRP-EN-004`, enforced at lowering independent of the server §12 gate.
 */
class EntryDeterminismSpec :
    StringSpec({
        val f = EntryLowerFixtures

        fun batch(body: String) = """{ "target": { "table": "entry.db.dbo.x" }, "proposals": [ $body ] }"""

        "the same unit lowers to a byte-identical render across two fresh runs" {
            val json =
                batch("""{ "op": "update", "key": { "entry_id": "e1" }, "values": { "amount": 9 } }""")
            val a =
                EntryLoweringRender.write(
                    EntryLowering.lower(f.unit(f.txnBook, "entry.reverse-and-replace", json)).plan!!,
                )
            val b =
                EntryLoweringRender.write(
                    EntryLowering.lower(f.unit(f.txnBook, "entry.reverse-and-replace", json)).plan!!,
                )
            a shouldBe b
        }

        "delete-rows on a ledger target produces a reversal, never a physical delete" {
            val json = batch("""{ "op": "delete", "key": { "entry_id": "e1" } }""")
            val plan = EntryLowering.lower(f.unit(f.txnBook, "entry.delete-rows", json)).plan!!
            val steps = plan.proposals.flatMap { it.steps }
            steps.any { it is PlanStep.ReverseRow } shouldBe true
            steps.any { it is PlanStep.PhysicalDelete } shouldBe false
        }

        "delete-rows on an scd2 target soft-closes, never a physical delete" {
            val json = batch("""{ "op": "delete", "key": { "customer_id": "C1" }, "effectiveDate": "2026-02-01" }""")
            val plan = EntryLowering.lower(f.unit(f.dimCustomer, "entry.delete-rows", json)).plan!!
            val steps = plan.proposals.flatMap { it.steps }
            steps.any { it is PlanStep.CloseValidity } shouldBe true
            steps.any { it is PlanStep.PhysicalDelete } shouldBe false
        }

        "a unit carrying a surface error does not lower (plan is null, diagnostics preserved)" {
            val errored =
                f
                    .unit(f.rawNotes, "entry.insert-rows", batch("""{ "op": "insert", "values": {} }"""))
                    .copy(
                        diagnostics =
                            listOf(
                                org.tatrman.ttrp.diagnostics.TtrpDiagnostic(
                                    TtrpDiagnosticId.EN_007,
                                    org.tatrman.ttrp.diagnostics.Severity.ERROR,
                                    "unresolved",
                                    org.tatrman.ttrp.ast.SourceLocation.UNKNOWN,
                                ),
                            ),
                    )
            val result = EntryLowering.lower(errored)
            result.plan shouldBe null
            result.diagnostics.map { it.id } shouldContain TtrpDiagnosticId.EN_007
        }
    })
