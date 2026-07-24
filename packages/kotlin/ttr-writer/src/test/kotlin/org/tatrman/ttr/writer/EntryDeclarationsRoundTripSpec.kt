// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.writer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.TableDef

/**
 * EN-P1 (grammar 0.10) — the entry declarations (`management` / `changeSemantics { roles }` / the
 * Q-8 `writeback` reservation) survive a write→reparse round-trip byte-stable (task 03 T1). Stability
 * is render-idempotence (`render∘parse` is a fixed point) plus a structural check that the
 * declarations survive the trip.
 */
class EntryDeclarationsRoundTripSpec :
    StringSpec({

        val src =
            """
            model db
            def table dim_customer {
                changeSemantics: scd2 { validFrom: valid_from, validTo: valid_to },
                columns: [
                    def column customer_id { type: text },
                    def column valid_from { type: date },
                    def column valid_to { type: date }
                ],
                primaryKey: [customer_id, valid_from]
            }
            def table txn_book { changeSemantics: ledger { reversalLink: reversal_of } }
            def table ref_region { management: canon }
            def table q8 { writeback { mapping: valid_from } }
            def table plain { description: "x" }
            """.trimIndent()

        "entry declarations round-trip byte-stable" {
            val r1 = TtrLoader.parseString(src)
            r1.ok shouldBe true
            val text1 = TtrRenderer.render(r1)

            val r2 = TtrLoader.parseString(text1)
            r2.ok shouldBe true
            val text2 = TtrRenderer.render(r2)

            text2 shouldBe text1 // render-idempotence fixed point
        }

        "the declarations survive the trip structurally" {
            val r1 = TtrLoader.parseString(src)
            val r2 = TtrLoader.parseString(TtrRenderer.render(r1))

            fun t(
                r: org.tatrman.ttr.parser.loader.ParseResult,
                n: String,
            ): TableDef = r.definitions.filterIsInstance<TableDef>().first { it.name == n }

            t(r2, "dim_customer").changeSemantics.let {
                it!!.mode shouldBe "scd2"
                it.roles shouldBe linkedMapOf("validFrom" to "valid_from", "validTo" to "valid_to")
            }
            t(r2, "txn_book").changeSemantics!!.roles shouldBe linkedMapOf("reversalLink" to "reversal_of")
            t(r2, "ref_region").management shouldBe "canon"
            t(r2, "q8").writeback!!.entries.keys shouldBe setOf("mapping")
            t(r2, "plain").management shouldBe null
        }
    })
