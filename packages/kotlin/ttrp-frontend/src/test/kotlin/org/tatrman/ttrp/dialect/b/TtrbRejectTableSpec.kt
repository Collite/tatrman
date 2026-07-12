// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.b

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * T7.1.2: the reject table is a complete, versioned fixture — every TTRP-B-00N id (001..008)
 * plus the shared TTRP-EQ-001 has an entry with a non-blank message + suggestion (the assist
 * repair vocabulary guarantee, C4-b-iii; consumed untouched by Stage 7.2).
 */
class TtrbRejectTableSpec :
    StringSpec({
        val table = TtrB.rejectTable

        "every TTRP-B id 001..008 has a table entry with message + suggestion" {
            (1..8).forEach { n ->
                val id = "TTRP-B-%03d".format(n)
                val entry = table.entry(id)
                entry.message.shouldNotBeBlank()
                entry.suggest.shouldNotBeBlank()
            }
        }

        "the shared TTRP-EQ-001 entry is present (S9 repair vocabulary)" {
            val eq = table.entry("TTRP-EQ-001")
            eq.suggest shouldBe "use ="
        }

        "the table loads without error and is non-empty" {
            table.ids() shouldNotBe emptySet<String>()
        }
    })
