// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.model.TableDef

/**
 * RV-P1.5 (grammar 0.12, RV-31/RV-32) — Kotlin parity for the match-method
 * attribute. Mirrors the TS `search-method.test.ts`: the walker is mechanical
 * (name as authored, raw argument), `searchable`'s boolean is optional, and
 * `fuzzy` still parses so 0.11 models keep loading.
 */
class SearchMethodSpec :
    StringSpec({

        fun table(body: String): TableDef {
            val r = TtrLoader.parseString("def table T { $body }")
            r.ok shouldBe true
            return r.definitions[0] as TableDef
        }

        "`searchable method: TYPOS(2)` captures name and argument" {
            val s = table("search { searchable method: TYPOS(2) }").search
            s.searchable shouldBe true
            s.method?.name shouldBe "TYPOS"
            s.method?.argument shouldBe 2.0
        }

        "`searchable method: EXACT` and `TOKENS` carry no argument" {
            table("search { searchable method: EXACT }").search.method?.name shouldBe "EXACT"
            val tokens = table("search { searchable method: TOKENS }").search.method
            tokens?.name shouldBe "TOKENS"
            tokens?.argument shouldBe null
        }

        "bare `searchable` is the inclusion marker" {
            val s = table("search { searchable }").search
            s.searchable shouldBe true
            s.method shouldBe null
        }

        "`searchable: true method: TYPOS(1)` combines the boolean and the method" {
            val s = table("search { searchable: true method: TYPOS(1) }").search
            s.searchable shouldBe true
            s.method?.argument shouldBe 1.0
        }

        "`searchable: false` still parses and is not included" {
            table("search { searchable: false }").search.searchable shouldBe false
        }

        "legacy `fuzzy` still parses, and its authorship is recorded" {
            val s = table("search { searchable: true, fuzzy: true }").search
            s.fuzzy shouldBe true
            s.fuzzyAuthored shouldBe true
            s.method shouldBe null
        }

        "an absent `fuzzy` is distinguishable from an authored `fuzzy: false`" {
            table("search { searchable }").search.fuzzyAuthored shouldBe false
            val authored = table("search { searchable: true, fuzzy: false }").search
            authored.fuzzy shouldBe false
            authored.fuzzyAuthored shouldBe true
        }

        "the method rides a nested column's search block" {
            val t = table("columns: [def column C { type: varchar, search { searchable method: TOKENS } }]")
            t.columns[0]
                .search.method
                ?.name shouldBe "TOKENS"
        }

        "`method` stays usable as an ordinary identifier" {
            val r = TtrLoader.parseString("def table method { columns: [def column c { type: int }] }")
            r.ok shouldBe true
        }
    })
