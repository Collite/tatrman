// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P5.1 T3 — deploy-time resolution against the registry seam. A well-formed call resolves to a
 * `{id, version}` pin; an unknown id or an unsatisfiable version constraint is **TTRP-EN-006** at
 * deploy (never a runtime fallback). Resolution is deterministic and consults the registry exactly at
 * deploy — the pin, once recorded, is what replay reads (proven registry-free at the emit layer).
 */
class CallFnResolutionSpec :
    StringSpec({
        val registry = CanonFunctionFixtureRegistry

        fun twrCall(constraint: String) =
            CallFnDemand(
                functionId = "twr",
                idIsLiteral = true,
                versionConstraint = constraint,
                args =
                    listOf(
                        CallFnArg("number[]", SourceLocation.UNKNOWN),
                        CallFnArg("number[]", SourceLocation.UNKNOWN),
                    ),
                location = SourceLocation.UNKNOWN,
            )

        "an exact-version call resolves to that pin" {
            val r = CallFnResolver.resolve(listOf(twrCall("1.0.0")), registry)
            r.ok shouldBe true
            r.pins shouldContainExactly listOf(CallFnPin("twr", "1.0.0"))
        }

        "a caret constraint pins the highest satisfying version in the major line" {
            val r = CallFnResolver.resolve(listOf(twrCall("^1.0.0")), registry)
            r.ok shouldBe true
            r.pins shouldContainExactly listOf(CallFnPin("twr", "1.2.0"))
        }

        "an unknown id is TTRP-EN-006 at deploy, with no pin" {
            val call = twrCall("*").copy(functionId = "no-such-fn")
            val r = CallFnResolver.resolve(listOf(call), registry)
            r.pins.shouldBeEmpty()
            r.diagnostics.map { it.id } shouldContainExactly listOf(TtrpDiagnosticId.EN_006)
        }

        "a known id with no satisfying version is TTRP-EN-006 at deploy, with no pin" {
            val r = CallFnResolver.resolve(listOf(twrCall("^9.0.0")), registry)
            r.pins.shouldBeEmpty()
            r.diagnostics.single().id shouldBe TtrpDiagnosticId.EN_006
        }

        "a dynamic (non-literal) id never resolves — TTRP-EN-006, no registry pin" {
            val call = twrCall("1.0.0").copy(idIsLiteral = false)
            val r = CallFnResolver.resolve(listOf(call), registry)
            r.pins.shouldBeEmpty()
            r.diagnostics.single().id shouldBe TtrpDiagnosticId.EN_006
        }

        "resolution is deterministic and pins are de-duplicated across repeated calls" {
            val r = CallFnResolver.resolve(listOf(twrCall("1.0.0"), twrCall("1.0.0")), registry)
            r.pins shouldContainExactly listOf(CallFnPin("twr", "1.0.0"))
        }
    })
