// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.graph.entry.EntryLowering
import org.tatrman.ttrp.graph.entry.PlanValue

/**
 * ED-P2 — the computed-bind emit (ED `contracts.md` §4). A derived column emits as a `?` bound to a
 * [Bind.FuncRef], fed by an [EmittedFuncEval] `{name, pin, argBinds}` in the proposal's `funcs` prefix
 * (the deploy pin comes from `pluginPins`; arg binds are typed from their source columns). The SQL text
 * golden + the JSON wire artifact are the ED-P3 platform round-trip's input; direct assertions pin the
 * shape. Regenerate goldens with `-DupdateGolden=true`.
 */
class EntryDerivationEmitSpec :
    StringSpec({
        val f = EntryEmitFixtures

        // The FO-8 cash leg: cash_amount = call-fn("cash-of", security_amount), pinned to cash-of@1.0.0.
        val cashLeg =
            EntryLowering.PlanDerivation(
                column = "cash_amount",
                functionId = "cash-of",
                versionConstraint = "1.0.0",
                args = listOf(PlanValue.BatchValue("security_amount")),
            )

        fun cashLegEmit(): ApplyEmitResult =
            f.emit(
                f.derivBook,
                "entry.insert-rows",
                """{ "target": { "table": "x" }, "proposals": [ """ +
                    """{ "op": "insert", "values": { "entry_id": "s1", "security_amount": 100 } } ] }""",
                pluginPins = listOf(PluginPin("cash-of", "1.0.0")),
                derivations = listOf(cashLeg),
            )

        "a derived column emits a FuncRef bind fed by a resolved-pin EmittedFuncEval" {
            val plan = cashLegEmit().plan!!
            val proposal = plan.proposals.single()

            // The func-eval prefix carries the deploy pin + the typed arg bind (security_amount is BIGINT).
            val funcEval = proposal.funcs.single()
            funcEval.name shouldBe "fn_cash_amount"
            funcEval.pin shouldBe PluginPin("cash-of", "1.0.0")
            funcEval.argBinds.single() shouldBe Bind.Value("100", SqlType.BIGINT)

            // The INSERT binds columns in sorted order (cash_amount, entry_id, security_amount): the derived
            // column is a FuncRef typed as its md column (BIGINT); the proposed columns are typed Values.
            val insert = proposal.steps.single()
            insert.binds shouldBe
                listOf(
                    Bind.FuncRef("fn_cash_amount", SqlType.BIGINT),
                    Bind.Value("s1", SqlType.TEXT),
                    Bind.Value("100", SqlType.BIGINT),
                )
        }

        "the derivation SQL text golden is byte-stable" {
            GoldenSupport.assertMatchesGolden(
                EmittedApplyPlanRender.write(cashLegEmit().plan!!),
                "apply/insert-derivation.txt",
            )
        }

        "the derivation JSON wire artifact is byte-stable (ED-P3 round-trip input)" {
            GoldenSupport.assertMatchesGolden(
                ApplyPlanJson.encode(cashLegEmit().plan!!),
                "apply-json/deriv_book-insert-derivation.json",
            )
        }
    })
