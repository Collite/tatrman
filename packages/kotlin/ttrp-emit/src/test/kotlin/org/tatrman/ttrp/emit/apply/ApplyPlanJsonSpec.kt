// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * EN-P4b — the cross-repo wire codec ([ApplyPlanJson]): an emitted plan round-trips encode→decode
 * byte-identically (the platform interpreter reads exactly this). The `kind`-discriminated [Bind]
 * polymorphism and the nested F3 [Bind.DerivedIdRef] survive the round trip.
 */
class ApplyPlanJsonSpec :
    StringSpec({
        val f = EntryEmitFixtures

        fun batch(body: String) = """{ "target": { "table": "x" }, "proposals": [ $body ] }"""

        val ledger =
            f
                .emit(
                    f.txnBook,
                    "entry.reverse-and-replace",
                    batch("""{ "op": "update", "key": { "entry_id": "e1" }, "values": { "amount": 42 } }"""),
                ).plan!!

        "an emitted plan round-trips encode → decode → encode identically" {
            val once = ApplyPlanJson.encode(ledger)
            val twice = ApplyPlanJson.encode(ApplyPlanJson.decode(once))
            twice shouldBe once
        }

        "decode reconstructs the typed binds incl. the nested F3 derived id" {
            val back = ApplyPlanJson.decode(ApplyPlanJson.encode(ledger))
            back shouldBe ledger
            val revStep =
                back.proposals
                    .single()
                    .steps
                    .first { it.effect == Effect.REVERSED }
            revStep.binds.any { it is Bind.DerivedIdRef && it.role == "rev" } shouldBe true
        }

        "the wire uses the stable `kind` discriminator, not the class name" {
            val wire = ApplyPlanJson.encode(ledger)
            wire shouldContain "\"kind\": \"derivedId\""
            wire shouldContain "\"kind\": \"value\""
        }
    })
