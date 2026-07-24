// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.model.TableDef

/**
 * EN-P1 (grammar 0.10) — TTR-M entry declarations parse onto the `table` AST: `management`,
 * `changeSemantics: <mode> { <role>: <column> }`, and the Q-8 `writeback { … }` reservation. The
 * parser stays mechanical — vocabulary (data|canon; scd1|scd2|ledger; role names) and role-column
 * checks are ttr-semantics' job (see the metadata/classifier specs). Mirrors the TS parser suite.
 */
class EntryDeclarationsParseSpec :
    StringSpec({

        fun table(
            src: String,
            name: String,
        ): TableDef {
            val r = TtrLoader.parseString("model db\n$src")
            r.errors shouldBe emptyList()
            return r.definitions.filterIsInstance<TableDef>().first { it.name == name }
        }

        "management: canon lands on the table" {
            table("def table ref_region { management: canon }", "ref_region").management shouldBe "canon"
        }

        "changeSemantics: scd2 with a valid-from/valid-to role map" {
            val cs =
                table(
                    "def table dim_customer { changeSemantics: scd2 { validFrom: valid_from, validTo: valid_to }, " +
                        "columns: [ def column valid_from { type: date }, def column valid_to { type: date } ] }",
                    "dim_customer",
                ).changeSemantics!!
            cs.mode shouldBe "scd2"
            cs.roles shouldBe linkedMapOf("validFrom" to "valid_from", "validTo" to "valid_to")
        }

        "changeSemantics: ledger with a reversal-link role" {
            val cs =
                table(
                    "def table txn_book { changeSemantics: ledger { reversalLink: reversal_of } }",
                    "txn_book",
                ).changeSemantics!!
            cs.mode shouldBe "ledger"
            cs.roles shouldBe linkedMapOf("reversalLink" to "reversal_of")
        }

        "changeSemantics: scd1 with no role map" {
            val cs = table("def table ref { changeSemantics: scd1 }", "ref").changeSemantics!!
            cs.mode shouldBe "scd1"
            cs.roles shouldBe emptyMap()
        }

        "a table with no entry declarations carries nulls (default posture)" {
            val t = table("def table plain { description: \"x\" }", "plain")
            t.management.shouldBeNull()
            t.changeSemantics.shouldBeNull()
            t.writeback.shouldBeNull()
        }

        "the Q-8 writeback reservation parses as a structured no-op" {
            val r = TtrLoader.parseString("model db\ndef table q8 { writeback { mapping: valid_from } }")
            r.errors shouldBe emptyList()
            val t = r.definitions.filterIsInstance<TableDef>().first { it.name == "q8" }
            t.writeback!!.entries.keys shouldBe setOf("mapping")
        }
    })
