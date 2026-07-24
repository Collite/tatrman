// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.graph.GraphFixtures
import java.nio.file.Files

/**
 * ED-P2 — lowering a program-level derivation (ED `contracts.md` §3). A `PlanDerivation` on a
 * proposal-producing verb (`insert-rows`/`update-rows`) lowers to a `FunctionEval` in the proposal
 * prefix (after any state-reads) plus a `PlanValue.CallFnValue`-bound column in the row. The golden
 * proves the shape + the deterministic byte-stable render; two direct assertions pin the mechanics.
 * Regenerate the golden with `-DupdateGolden=true`.
 */
class EntryDerivationLoweringSpec :
    StringSpec({
        val f = EntryLowerFixtures

        // The FO-8 cash leg: propose the security leg, derive cash_amount = call-fn("cash-of", security_amount).
        val cashLeg =
            EntryLowering.PlanDerivation(
                column = "cash_amount",
                functionId = "cash-of",
                versionConstraint = "1.0.0",
                args = listOf(PlanValue.BatchValue("security_amount")),
            )

        fun cashLegPlan(): EntryApplyPlan {
            val unit =
                f.unit(
                    f.derivBook,
                    "entry.insert-rows",
                    """{ "target": { "table": "entry.db.dbo.deriv_book" }, "proposals": [ """ +
                        """{ "op": "insert", "values": { "entry_id": "s1", "security_amount": 100 } } ] }""",
                )
            return EntryLowering.lower(unit, listOf(cashLeg)).plan!!
        }

        "insert-rows with a derived column lowers to a func-eval prefix + a CallFnValue-bound column" {
            val plan = cashLegPlan()
            val pp = plan.proposals.single()

            // The func-eval prefix carries the derivation; the row binds the column to its result.
            pp.evals.single() shouldBe
                FunctionEval("fn_cash_amount", "cash-of", "1.0.0", listOf(PlanValue.BatchValue("security_amount")))
            val insert = pp.steps.single() as PlanStep.InsertRow
            insert.columns["cash_amount"] shouldBe PlanValue.CallFnValue("fn_cash_amount")
            insert.columns["security_amount"] shouldBe PlanValue.BatchValue("security_amount")
        }

        "the derivation render is deterministic + byte-stable (golden)" {
            val render = EntryLoweringRender.write(cashLegPlan())
            render shouldContain "evals:"
            render shouldContain "fn_cash_amount = call-fn(cash-of@1.0.0, batch.security_amount)"
            render shouldContain "cash_amount=fn.fn_cash_amount"

            val path = GraphFixtures.root.resolve("entry/insert-derivation.entry.txt")
            val update = System.getProperty("updateGolden") == "true"
            if (!Files.exists(path) || update) {
                Files.createDirectories(path.parent)
                Files.writeString(path, render)
                throw AssertionError("golden `insert-derivation` written — review the diff, then re-run")
            }
            render shouldBe Files.readString(path)
        }
    })
