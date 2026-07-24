// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.graph.GraphFixtures
import java.nio.file.Files

/**
 * ED-P4 — lowering a **derived row** (ED `contracts.md` §7): the FO-8 cash counter-leg. A
 * `PlanRowDerivation` on `insert-rows` lowers to the proposed (security) `InsertRow` FOLLOWED BY an
 * extra (cash) `InsertRow` whose columns are bound to `Const` (`leg='cash'`), `BatchValue` (copied
 * `portfolio_ref`), and `CallFnValue` (`amount = call-fn("cash-of", batch.amount)`), plus the
 * `fnrow_*` `FunctionEval` for the call. The golden pins the two-row shape + the deterministic render.
 */
class EntryRowDerivationLoweringSpec :
    StringSpec({
        val f = EntryLowerFixtures

        // The cash counter-leg: a full derived row (const leg + copied portfolio + call-fn amount).
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

        fun bookingPlan(): EntryApplyPlan {
            val unit =
                f.unit(
                    f.booking,
                    "entry.insert-rows",
                    """{ "target": { "table": "entry.db.dbo.booking" }, "proposals": [ """ +
                        """{ "op": "insert", "values": """ +
                        """{ "txn_id": "s1", "portfolio_ref": "p1", "leg": "security", "amount": 100 } } ] }""",
                )
            return EntryLowering.lower(unit, emptyList(), listOf(cashLegRow)).plan!!
        }

        "insert-rows with a derived row lowers to the security row then the derived cash row + its func-eval" {
            val pp = bookingPlan().proposals.single()

            // Two InsertRows: the proposed security leg, then the derived cash leg.
            pp.steps.size shouldBe 2
            val security = pp.steps[0] as PlanStep.InsertRow
            security.columns["leg"] shouldBe PlanValue.BatchValue("leg") // proposed as 'security'
            val cash = pp.steps[1] as PlanStep.InsertRow
            cash.columns["leg"] shouldBe PlanValue.Const("cash")
            cash.columns["portfolio_ref"] shouldBe PlanValue.BatchValue("portfolio_ref")
            cash.columns["amount"] shouldBe PlanValue.CallFnValue("fnrow_amount")

            // The call-fn for the derived amount rides the proposal's eval prefix, named fnrow_*.
            pp.evals.single() shouldBe
                FunctionEval("fnrow_amount", "cash-of", "1.0.0", listOf(PlanValue.BatchValue("amount")))
        }

        "the derived-row render is deterministic + byte-stable (golden)" {
            val render = EntryLoweringRender.write(bookingPlan())
            render shouldContain "fnrow_amount = call-fn(cash-of@1.0.0, batch.amount)"
            render shouldContain "leg=batch.leg" // the proposed security row
            render shouldContain "leg='cash'" // the derived cash row

            val path = GraphFixtures.root.resolve("entry/insert-derived-row.entry.txt")
            val update = System.getProperty("updateGolden") == "true"
            if (!Files.exists(path) || update) {
                Files.createDirectories(path.parent)
                Files.writeString(path, render)
                throw AssertionError("golden `insert-derived-row` written — review the diff, then re-run")
            }
            render shouldBe Files.readString(path)
        }
    })
