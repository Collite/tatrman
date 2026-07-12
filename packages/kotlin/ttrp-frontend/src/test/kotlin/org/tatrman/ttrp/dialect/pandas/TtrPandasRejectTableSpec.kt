// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.pandas

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.parser.TtrpParser

/** The pandas reject table is unique/monotone, non-empty suggestions, table ↔ corpus ↔ parser (contracts §8). */
class TtrPandasRejectTableSpec :
    StringSpec({
        val table = TtrPandas.rejectTable

        "ids are unique, TTRP-PD-prefixed, and monotone" {
            val ids = table.entries.map { it.id }
            ids.distinct() shouldBe ids
            ids.all { it.startsWith("TTRP-PD-") } shouldBe true
            ids shouldBe ids.sorted()
        }

        "every entry has a non-empty suggestion" {
            table.entries.all { it.suggest.isNotBlank() } shouldBe true
        }

        "table ↔ corpus ↔ parser: every id has a fixture that produces it" {
            for (entry in table.entries) {
                val src = PandasCorpus.read("reject/${entry.id}.ttrp")
                val produced = TtrpParser.parseString(src, entry.id).diagnostics.map { it.id.id }
                (entry.id in produced) shouldBe true
            }
        }
    })
