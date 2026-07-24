// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.graph.entry.EntryLowering
import org.tatrman.ttrp.graph.entry.PlanValue

/**
 * FO-P4 S3.T3 — the investment cash-leg canon, emitted against the REAL `transaction` model. This is
 * `<transaction>-entry-apply`'s derivation half expressed as the frozen structured form (the surface
 * spelling is PLA-2-deferred; the `.ttrp` artifact in `kantheon/packages/investment/canon/` carries the
 * provisional syntax). The author proposes the security leg; the program derives the `leg='cash'`
 * counter-row: `portfolio_ref`/`trade_date`/`amount`/`currency` copied, `operation` mapped by
 * `call-fn("cash-operation", operation)` (buy→debit / sell→credit), `external_id` by
 * `call-fn("cash-ref", external_id)` (append `-cash`); `asset_ref`/`quantity`/`sk` omitted → null/serial
 * (Bora's S3.T3 ruling: no cash-account modeling in v0). The JSON artifact is the round-trip's input.
 */
class EntryCashLegEmitSpec :
    StringSpec({
        val f = EntryEmitFixtures

        val cashLeg =
            EntryLowering.PlanRowDerivation(
                columns =
                    linkedMapOf(
                        "external_id" to
                            EntryLowering.PlanRowSource.Call(
                                "cash-ref",
                                "0.1.0",
                                listOf(PlanValue.BatchValue("external_id")),
                            ),
                        "portfolio_ref" to EntryLowering.PlanRowSource.Batch("portfolio_ref"),
                        "leg" to EntryLowering.PlanRowSource.Const("cash"),
                        "operation" to
                            EntryLowering.PlanRowSource.Call(
                                "cash-operation",
                                "0.1.0",
                                listOf(PlanValue.BatchValue("operation")),
                            ),
                        "trade_date" to EntryLowering.PlanRowSource.Batch("trade_date"),
                        "amount" to EntryLowering.PlanRowSource.Batch("amount"),
                        "currency" to EntryLowering.PlanRowSource.Batch("currency"),
                    ),
            )

        // A buy of an asset: the security leg the user proposes (sk omitted — the ledger auto-assigns it).
        fun buyEmit(): ApplyEmitResult =
            f.emit(
                f.transaction,
                "entry.insert-rows",
                """{ "target": { "table": "x" }, "proposals": [ { "op": "insert", "values": { """ +
                    """"external_id": "T1", "portfolio_ref": 10, "asset_ref": 42, "leg": "security", """ +
                    """"operation": "buy", "trade_date": "2026-06-01", "quantity": 100, "amount": 25000, """ +
                    """"currency": "CZK" } } ] }""",
                pluginPins = listOf(PluginPin("cash-ref", "0.1.0"), PluginPin("cash-operation", "0.1.0")),
                rowDerivations = listOf(cashLeg),
            )

        "the cash-leg canon emits the security INSERT then the derived cash INSERT (real transaction shape)" {
            val proposal = buyEmit().plan!!.proposals.single()
            proposal.steps.size shouldBe 2

            // The derived cash row: leg const, portfolio/trade_date/amount/currency copied, operation +
            // external_id via call-fn; asset_ref/quantity/sk absent (null/serial). Columns sorted.
            val cash = proposal.steps[1]
            cash.sql shouldBe
                """INSERT INTO "transaction" ("amount", "currency", "external_id", "leg", "operation", """ +
                """"portfolio_ref", "trade_date") VALUES (?, ?, ?, ?, ?, ?, ?)"""
            cash.binds shouldBe
                listOf(
                    Bind.Value("25000", SqlType.BIGINT), // amount (copied)
                    Bind.Value("CZK", SqlType.TEXT), // currency (copied)
                    Bind.FuncRef("fnrow_external_id", SqlType.TEXT), // external_id (call-fn)
                    Bind.Value("cash", SqlType.TEXT), // leg (const)
                    Bind.FuncRef("fnrow_operation", SqlType.TEXT), // operation (call-fn)
                    Bind.Value("10", SqlType.BIGINT), // portfolio_ref (copied)
                    Bind.Value("2026-06-01", SqlType.DATE), // trade_date (copied)
                )
            proposal.funcs.map { it.name } shouldBe listOf("fnrow_external_id", "fnrow_operation")
        }

        "the cash-leg JSON wire artifact is byte-stable (S3.T3 round-trip input)" {
            GoldenSupport.assertMatchesGolden(
                ApplyPlanJson.encode(buyEmit().plan!!),
                "apply-json/transaction-cash-leg.json",
            )
        }
    })
