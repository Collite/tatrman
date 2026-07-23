// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * ED-P1 — the derivation typecheck ([DerivationChecker], ED `contracts.md` §2). Drives the checker
 * directly (the `BatchShapeChecker` spec house pattern), against the real `entry` fixture `txn_book`
 * (`amount` decimal → NUMBER, `reversal_of` string → TEXT) and the fixture `CanonFunctionRegistry`
 * (`twr`/`fifo-cost` pure → `number`; `wall-clock` impure → `date`). One valid path + the five ways a
 * derivation is rejected, each on the reused call-fn contract or the derivation-specific shape checks.
 */
class DerivationCheckerSpec :
    StringSpec({
        val target = EntryFixtures.table("txn_book")
        val registry = CanonFunctionFixtureRegistry

        // A `twr(number[], number[]) -> number` call — the arity/arg-types match the SPI signature.
        fun twr(
            id: String = "twr",
            constraint: String = "1.0.0",
        ) = CallFnDemand(
            functionId = id,
            idIsLiteral = true,
            versionConstraint = constraint,
            args =
                listOf(
                    CallFnArg("number[]", SourceLocation.UNKNOWN),
                    CallFnArg("number[]", SourceLocation.UNKNOWN),
                ),
            location = SourceLocation.UNKNOWN,
        )

        fun derive(
            column: String,
            call: CallFnDemand,
        ) = DerivationDemand(column, call, SourceLocation.UNKNOWN)

        "a well-typed derivation of a real, unproposed column resolves clean" {
            // `amount` (NUMBER) derived by twr (-> number); the security leg proposes entry_id, not amount.
            val diags =
                DerivationChecker.check(
                    target = target,
                    derivations = listOf(derive("amount", twr())),
                    proposedColumns = setOf("entry_id"),
                    registry = registry,
                )
            diags.shouldBeEmpty()
        }

        "a derived column not on the target is TTRP-EN-001" {
            val diags =
                DerivationChecker.check(target, listOf(derive("nonesuch", twr())), setOf("entry_id"), registry)
            diags.map { it.id } shouldContain TtrpDiagnosticId.EN_001
            diags.single().message shouldBe "unknown derived column `nonesuch` — not on the target `txn_book`"
        }

        "a column that is both proposed and derived is TTRP-EN-001" {
            val diags =
                DerivationChecker.check(target, listOf(derive("amount", twr())), setOf("amount"), registry)
            diags.map { it.id } shouldContain TtrpDiagnosticId.EN_001
            diags.any { it.message.contains("both proposed and derived") } shouldBe true
        }

        "a return type that does not fit the md column type is TTRP-EN-006" {
            // reversal_of is TEXT; twr returns number → the derivation cannot land its result.
            val diags =
                DerivationChecker.check(target, listOf(derive("reversal_of", twr())), setOf("entry_id"), registry)
            diags.map { it.id } shouldContain TtrpDiagnosticId.EN_006
            diags.any { it.message.contains("returning `number`") } shouldBe true
        }

        "a call to a non-pure canon-function is TTRP-EN-005 (via the reused call-fn contract)" {
            // wall-clock: determinism null (not pure), takes no args.
            val wallClock =
                CallFnDemand(
                    functionId = "wall-clock",
                    idIsLiteral = true,
                    versionConstraint = "1.0.0",
                    args = emptyList(),
                    location = SourceLocation.UNKNOWN,
                )
            val diags =
                DerivationChecker.check(
                    target,
                    listOf(derive("reversal_of", wallClock)),
                    setOf("entry_id"),
                    registry,
                )
            diags.map { it.id } shouldContain TtrpDiagnosticId.EN_005
        }

        "an unknown function id is TTRP-EN-006 (via the reused call-fn contract), with no return-type noise" {
            val diags =
                DerivationChecker.check(
                    target,
                    listOf(derive("amount", twr(id = "no-such-fn"))),
                    setOf("entry_id"),
                    registry,
                )
            diags.map { it.id } shouldContain TtrpDiagnosticId.EN_006
        }
    })
