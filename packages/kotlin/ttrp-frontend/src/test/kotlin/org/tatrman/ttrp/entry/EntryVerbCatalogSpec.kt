// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * EN-P2.2 T1 — the `entry` verb catalogue (contracts §4): the six verbs, their catalogue ids (T5-c
 * dotted discipline), typed signatures, and the change-semantics each demands of a target.
 */
class EntryVerbCatalogSpec :
    StringSpec({
        "the six §4 verbs ship with dotted `entry.*` ids in roster order" {
            EntryVerbCatalog.ids shouldContainExactly
                listOf(
                    "entry.insert-rows",
                    "entry.update-rows",
                    "entry.effective-date-change",
                    "entry.reverse-and-replace",
                    "entry.delete-rows",
                    "entry.reject-row",
                )
        }

        "byId and byName resolve the same signature" {
            val v = EntryVerbCatalog.byId("entry.effective-date-change")!!
            v.name shouldBe "effective-date-change"
            EntryVerbCatalog.byName("effective-date-change") shouldBe v
            EntryVerbCatalog.byId("entry.nope").shouldBeNull()
        }

        "signatures match the §4 verb table" {
            EntryVerbCatalog.byName("insert-rows")!!.params shouldContainExactly
                listOf(EntryVerbCatalog.ParamKind.TARGET, EntryVerbCatalog.ParamKind.ROWS)
            EntryVerbCatalog.byName("effective-date-change")!!.params shouldContainExactly
                listOf(
                    EntryVerbCatalog.ParamKind.TARGET,
                    EntryVerbCatalog.ParamKind.KEYED_ROWS,
                    EntryVerbCatalog.ParamKind.EFFECTIVE_DATE,
                )
            EntryVerbCatalog.byName("reject-row")!!.params shouldContainExactly
                listOf(
                    EntryVerbCatalog.ParamKind.KEY,
                    EntryVerbCatalog.ParamKind.CODE,
                    EntryVerbCatalog.ParamKind.DETAIL,
                )
        }

        "only effective-date-change and reverse-and-replace pin a required semantics" {
            EntryVerbCatalog.byName("effective-date-change")!!.requiresSemantics shouldBe setOf("scd2")
            EntryVerbCatalog.byName("reverse-and-replace")!!.requiresSemantics shouldBe setOf("ledger")
            EntryVerbCatalog.byName("insert-rows")!!.requiresSemantics.shouldBeNull()
            EntryVerbCatalog.byName("update-rows")!!.requiresSemantics.shouldBeNull()
            EntryVerbCatalog.byName("delete-rows")!!.requiresSemantics.shouldBeNull()
        }
    })
