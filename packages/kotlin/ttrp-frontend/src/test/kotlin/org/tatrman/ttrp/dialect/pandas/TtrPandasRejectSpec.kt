// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.pandas

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.parser.TtrpParser

/**
 * Every fixture under `reject/` produces its header-declared `TTRP-PD-NNN` with the
 * reject table's suggestion (single source). Named grammar rejects (C2-g), abbreviations
 * rejected with the closed-roster message (S17), no fuzzy matching (P2).
 */
class TtrPandasRejectSpec :
    StringSpec({
        val table = TtrPandas.rejectTable
        val ids = (1..10).map { "TTRP-PD-%03d".format(it) }

        for (id in ids) {
            "reject fixture $id → $id with the table suggestion" {
                val src = PandasCorpus.read("reject/$id.ttrp")
                val expect =
                    src
                        .lineSequence()
                        .first()
                        .substringAfter("expect:")
                        .trim()
                expect shouldBe id
                val diags = TtrpParser.parseString(src, "$id.ttrp").diagnostics
                val hit = diags.firstOrNull { it.id.id == id }
                (hit != null) shouldBe true
                hit!!.suggestedAlternative shouldBe table.entry(id).suggest
            }
        }
    })
