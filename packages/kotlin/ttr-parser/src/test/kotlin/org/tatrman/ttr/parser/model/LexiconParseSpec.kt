// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * v4.4 — TTR-M lexicon surface parse coverage (RG-P4). Mirrors the TS
 * `lexicon.test.ts`: the `lexicon` model code, the `term`/`pattern`/`example`
 * def kinds, the `model lexicon locale <id>` header, and inline `lexicon { … }`
 * sugar on a carrier. Cross-target dump parity is asserted by ConformanceSpec
 * against fixtures 62/63.
 */
class LexiconParseSpec :
    StringSpec({

        "a `model lexicon` file with term/pattern/example parses with no errors" {
            val r =
                TtrLoader.parseString(
                    """
                    model lexicon
                    def term trzba { for: md.measure.net, forms: ["tržba", "obrat", "utržit"] }
                    def pattern nazev { for: db.query.by_name, match: "název .*" }
                    def example q1 { for: md.cubelet.sales, text: "Kolik jsme utržili" }
                    """.trimIndent(),
                )
            r.errors shouldBe emptyList()
            r.modelDirective?.modelCode shouldBe "lexicon"

            val entries = r.definitions.filterIsInstance<LexiconEntryDef>()
            entries shouldHaveSize 3
            val term = entries[0]
            term.entryKind shouldBe "term"
            term.name shouldBe "trzba"
            term.target?.path shouldBe "md.measure.net"
            term.forms shouldBe listOf("tržba", "obrat", "utržit")
            entries[1].entryKind shouldBe "pattern"
            entries[1].match shouldBe "název .*"
            entries[2].entryKind shouldBe "example"
            entries[2].text shouldBe "Kolik jsme utržili"
        }

        "the unit-level `locale` header parses and is captured" {
            val r =
                TtrLoader.parseString(
                    "model lexicon locale cs\ndef term t { for: md.measure.net, forms: [\"tržba\"] }",
                )
            r.errors shouldBe emptyList()
            r.modelDirective?.locale shouldBe "cs"
        }

        "inline `lexicon { terms }` sugar parses on an entity carrier" {
            val r =
                TtrLoader.parseString(
                    "model er\ndef entity customer { lexicon { terms: [\"zákazník\", \"odběratel\"] } }",
                )
            r.errors shouldBe emptyList()
            val entity = r.definitions.filterIsInstance<EntityDef>().single()
            entity.lexicon.shouldNotBeNull()
            entity.lexicon?.terms shouldBe listOf("zákazník", "odběratel")
        }

        "the new keywords stay usable as bare ids (idPart)" {
            val r =
                TtrLoader.parseString(
                    "model db schema dbo\ndef table term { columns: [def column pattern { type: string }] }\ndef column example { type: string }",
                )
            r.errors shouldBe emptyList()
            r.definitions shouldHaveSize 2
        }
    })
