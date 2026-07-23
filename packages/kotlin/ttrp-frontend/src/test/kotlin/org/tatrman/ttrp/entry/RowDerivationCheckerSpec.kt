// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * ED-P4 — the derived-row typecheck ([RowDerivationChecker], ED `contracts.md` §7). A derived row (the
 * cash counter-leg) is a column→source map; every column must exist on the target md shape (EN-001) and
 * each `call-fn` source must resolve pure + type-fit (EN-005/006). Drives the checker directly against
 * the `txn_book` fixture (`amount` → NUMBER, `reversal_of`/`entry_id` → TEXT) + the fixture registry.
 */
class RowDerivationCheckerSpec :
    StringSpec({
        val target = EntryFixtures.table("txn_book")
        val registry = CanonFunctionFixtureRegistry

        fun twr(id: String = "twr") =
            CallFnDemand(
                functionId = id,
                idIsLiteral = true,
                versionConstraint = "1.0.0",
                args =
                    listOf(
                        CallFnArg("number[]", SourceLocation.UNKNOWN),
                        CallFnArg("number[]", SourceLocation.UNKNOWN),
                    ),
                location = SourceLocation.UNKNOWN,
            )

        fun row(columns: Map<String, RowColumnSource>) = RowDerivationDemand(columns, SourceLocation.UNKNOWN)

        "a well-formed derived row (const + batch-copy + call-fn) resolves clean" {
            val diags =
                RowDerivationChecker.check(
                    target,
                    listOf(
                        row(
                            mapOf(
                                "entry_id" to RowColumnSource.Batch("entry_id"),
                                "reversal_of" to RowColumnSource.Const("cash"),
                                "amount" to RowColumnSource.Call(twr()), // BIGINT ← number: fits
                            ),
                        ),
                    ),
                    registry,
                )
            diags.shouldBeEmpty()
        }

        "a derived-row column not on the target is TTRP-EN-001" {
            val diags = RowDerivationChecker.check(target, listOf(row(mapOf("nonesuch" to RowColumnSource.Const("x")))), registry)
            diags.map { it.id } shouldContain TtrpDiagnosticId.EN_001
            diags.single().message shouldBe "unknown derived-row column `nonesuch` — not on the target `txn_book`"
        }

        "a call-fn source whose return type does not fit the column is TTRP-EN-006" {
            // reversal_of is TEXT; twr returns number.
            val diags = RowDerivationChecker.check(target, listOf(row(mapOf("reversal_of" to RowColumnSource.Call(twr())))), registry)
            diags.map { it.id } shouldContain TtrpDiagnosticId.EN_006
        }

        "a non-pure call-fn source is TTRP-EN-005" {
            val wallClock =
                CallFnDemand("wall-clock", idIsLiteral = true, versionConstraint = "1.0.0", args = emptyList(), location = SourceLocation.UNKNOWN)
            val diags = RowDerivationChecker.check(target, listOf(row(mapOf("reversal_of" to RowColumnSource.Call(wallClock)))), registry)
            diags.map { it.id } shouldContain TtrpDiagnosticId.EN_005
        }
    })
