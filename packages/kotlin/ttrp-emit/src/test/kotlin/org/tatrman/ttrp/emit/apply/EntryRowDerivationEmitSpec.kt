// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.graph.entry.EntryLowering
import org.tatrman.ttrp.graph.entry.PlanValue

/**
 * ED-P4 — the derived-row emit (ED `contracts.md` §7). NO new wire: the derived cash-leg row is just a
 * second `EmittedStep` INSERT, and its `call-fn` rides the existing `funcs` prefix — so this spec is a
 * VERIFICATION that the ED-P2 emit machinery covers the counter-row. The SQL text golden + the JSON
 * wire artifact (the ED-P4 round-trip input) prove two typed INSERTs come out; a direct assertion pins
 * the derived row's FuncRef `amount` bind. Regenerate goldens with `-DupdateGolden=true`.
 */
class EntryRowDerivationEmitSpec :
    StringSpec({
        val f = EntryEmitFixtures

        // The cash counter-leg: const leg + copied portfolio + call-fn amount, distinct derived pk.
        val cashLegRow =
            EntryLowering.PlanRowDerivation(
                columns =
                    linkedMapOf(
                        "txn_id" to EntryLowering.PlanRowSource.Const("s1-cash"),
                        "portfolio_ref" to EntryLowering.PlanRowSource.Batch("portfolio_ref"),
                        "leg" to EntryLowering.PlanRowSource.Const("cash"),
                        "amount" to
                            EntryLowering.PlanRowSource.Call(
                                "cash-of",
                                "1.0.0",
                                listOf(PlanValue.BatchValue("amount")),
                            ),
                    ),
            )

        fun bookingEmit(): ApplyEmitResult =
            f.emit(
                f.booking,
                "entry.insert-rows",
                """{ "target": { "table": "x" }, "proposals": [ """ +
                    """{ "op": "insert", "values": """ +
                    """{ "txn_id": "s1", "portfolio_ref": "p1", "leg": "security", "amount": 100 } } ] }""",
                pluginPins = listOf(PluginPin("cash-of", "1.0.0")),
                rowDerivations = listOf(cashLegRow),
            )

        "a derived row emits as a second INSERT, its amount a FuncRef fed by the funcs prefix" {
            val proposal = bookingEmit().plan!!.proposals.single()

            proposal.steps.size shouldBe 2 // the security INSERT, then the derived cash INSERT
            proposal.funcs.single().name shouldBe "fnrow_amount"
            proposal.funcs.single().pin shouldBe PluginPin("cash-of", "1.0.0")

            // The cash INSERT binds amount (sorted first of amount/leg/portfolio_ref/txn_id) to the func result.
            val cash = proposal.steps[1]
            cash.binds shouldBe
                listOf(
                    Bind.FuncRef("fnrow_amount", SqlType.BIGINT), // amount
                    Bind.Value("cash", SqlType.TEXT), // leg
                    Bind.Value("p1", SqlType.TEXT), // portfolio_ref (copied from the batch)
                    Bind.Value("s1-cash", SqlType.TEXT), // txn_id (const)
                )
        }

        "the derived-row SQL text golden is byte-stable" {
            GoldenSupport.assertMatchesGolden(
                EmittedApplyPlanRender.write(bookingEmit().plan!!),
                "apply/insert-derived-row.txt",
            )
        }

        "the derived-row JSON wire artifact is byte-stable (ED-P4 round-trip input)" {
            GoldenSupport.assertMatchesGolden(
                ApplyPlanJson.encode(bookingEmit().plan!!),
                "apply-json/booking-insert-derived-row.json",
            )
        }
    })
