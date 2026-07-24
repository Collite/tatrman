// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.entry.CallFnArg
import org.tatrman.ttrp.entry.CallFnDemand
import org.tatrman.ttrp.entry.CallFnResolver
import org.tatrman.ttrp.entry.CanonFunctionFixtureRegistry

/**
 * EN-P5.1 T6 — the deploy-resolve-then-pin path proves out end-to-end through the EN-P4 emit/wire: a
 * `call-fn` demand is resolved ONCE against the registry at deploy, its `{id, version}` pin is stamped
 * into the [EmittedApplyPlan], and the plan round-trips through the JSON wire byte-identically — so
 * replay reads the baked pin and never re-consults the registry (P-3). The decode path touches no
 * registry at all: the pin is data on the plan.
 */
class CallFnPinReplaySpec :
    StringSpec({
        val f = EntryEmitFixtures

        fun batch(body: String) = """{ "target": { "table": "x" }, "proposals": [ $body ] }"""

        // Deploy-time resolution (the ONLY registry consultation) → the pins to stamp.
        fun arg(type: String) = CallFnArg(type, SourceLocation.UNKNOWN)

        val demand =
            CallFnDemand(
                functionId = "twr",
                idIsLiteral = true,
                versionConstraint = "^1.0.0",
                args = listOf(arg("number[]"), arg("number[]")),
                location = SourceLocation.UNKNOWN,
            )
        val pins =
            CallFnResolver
                .resolve(listOf(demand), CanonFunctionFixtureRegistry)
                .pins
                .map { PluginPin(it.id, it.version) }

        val emitted =
            f
                .emit(
                    f.refRegion,
                    "entry.update-rows",
                    batch(
                        """{ "op": "update", "key": { "region_code": "EU" }, """ +
                            """"values": { "region_name": "EMEA" } }""",
                    ),
                    pluginPins = pins,
                ).plan!!

        "the deploy-resolved pin is stamped onto the emitted plan (caret resolved to 1.2.0)" {
            emitted.pluginPins shouldContainExactly listOf(PluginPin("twr", "1.2.0"))
        }

        "the plan (pins included) round-trips through the JSON wire byte-identically" {
            val once = ApplyPlanJson.encode(emitted)
            val back = ApplyPlanJson.decode(once) // no registry is consulted here — the pin is baked data
            back shouldBe emitted
            ApplyPlanJson.encode(back) shouldBe once
        }

        "the wire carries the pin id + version verbatim (replay reads this, never re-resolves)" {
            val wire = ApplyPlanJson.encode(emitted)
            wire.contains("\"twr\"") shouldBe true
            wire.contains("\"1.2.0\"") shouldBe true
        }
    })
