// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P5.1 T2 — the purity contract (contracts §6, P-3). `call-fn` is the ONLY foreign call an apply
 * program may make, and only to a `pure`-certified canon-function; calling a function whose plugin
 * does not certify determinism is **TTRP-EN-005**, refused at compile. (The flow-construct half of
 * purity — no load/store/display — is `EntryPuritySurfaceCheck`, EN-P2.)
 */
class CallFnPuritySpec :
    StringSpec({
        val registry = CanonFunctionFixtureRegistry

        fun arg(type: String) = CallFnArg(type, SourceLocation.UNKNOWN)

        "a call to a `pure`-certified function is allowed" {
            val call =
                CallFnDemand(
                    "twr",
                    idIsLiteral = true,
                    versionConstraint = "1.0.0",
                    args = listOf(arg("number[]"), arg("number[]")),
                    location = SourceLocation.UNKNOWN,
                )
            val r = CallFnResolver.resolve(listOf(call), registry)
            r.ok shouldBe true
            r.diagnostics.shouldBeEmpty()
        }

        "a call to a function that does not certify determinism is TTRP-EN-005 and is not pinned" {
            val call =
                CallFnDemand(
                    "wall-clock",
                    idIsLiteral = true,
                    versionConstraint = "1.0.0",
                    args = emptyList(),
                    location = SourceLocation.UNKNOWN,
                )
            val r = CallFnResolver.resolve(listOf(call), registry)
            r.pins.shouldBeEmpty() // an impure call is never pinned
            r.diagnostics.map { it.id }.contains(TtrpDiagnosticId.EN_005) shouldBe true
        }

        "the registry can hold an impure function — the refusal is at the call site, not registration" {
            // `wall-clock` is legally registered (a plugin may declare it); only *calling* it is illegal.
            registry.resolve("wall-clock", "1.0.0").let { it is Resolution.Pinned } shouldBe true
        }
    })
