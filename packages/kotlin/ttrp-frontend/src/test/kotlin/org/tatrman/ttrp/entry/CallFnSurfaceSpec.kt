// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P5.1 T1 — `call-fn` typechecks against the registry's declared SPI signature: the id is a
 * compile-time string literal (P2, no dynamic ids), and the args' count + types match the resolved
 * signature. Arity and per-arg type mismatches are **TTRP-EN-006**, named at the call/arg site.
 */
class CallFnSurfaceSpec :
    StringSpec({
        val registry = CanonFunctionFixtureRegistry

        fun call(
            args: List<CallFnArg>,
            id: String = "twr",
            literal: Boolean = true,
        ) = CallFnDemand(id, literal, "1.0.0", args, SourceLocation.UNKNOWN)

        fun arg(type: String) = CallFnArg(type, SourceLocation.UNKNOWN)

        "a well-formed call with matching arity + arg types typechecks with no diagnostics" {
            val r = CallFnResolver.resolve(listOf(call(listOf(arg("number[]"), arg("number[]")))), registry)
            r.ok shouldBe true
            r.diagnostics.shouldBeEmpty()
        }

        "too few args is TTRP-EN-006 naming the expected arity" {
            val r = CallFnResolver.resolve(listOf(call(listOf(arg("number[]")))), registry)
            r.ok shouldBe false
            val d = r.diagnostics.single()
            d.id shouldBe TtrpDiagnosticId.EN_006
            d.message.contains("2 arg") shouldBe true
        }

        "a wrong arg type is TTRP-EN-006 naming the parameter and expected type" {
            val r = CallFnResolver.resolve(listOf(call(listOf(arg("number[]"), arg("text")))), registry)
            r.ok shouldBe false
            val d = r.diagnostics.single()
            d.id shouldBe TtrpDiagnosticId.EN_006
            d.message.contains("values") shouldBe true // the second param's name
        }

        "a non-literal id is TTRP-EN-006 (no dynamic ids) even when the args are well-formed" {
            val r =
                CallFnResolver.resolve(
                    listOf(call(listOf(arg("number[]"), arg("number[]")), literal = false)),
                    registry,
                )
            r.diagnostics.single().id shouldBe TtrpDiagnosticId.EN_006
        }
    })
