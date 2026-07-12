// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.diagnostics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The whole-catalogue id-uniqueness gate (Phase-1 DONE bar) + the contracts-§8
 * convention that every rejected form carries a non-blank suggested alternative.
 */
class TtrpDiagnosticIdSpec :
    StringSpec({

        "the diagnostics catalogue has no id-string collisions" {
            TtrpDiagnosticId.assertNoDuplicateIds()
        }

        "every resolution-tier id carries a non-blank suggested alternative" {
            val tiers = setOf("WLD", "RES", "SCH", "CFG", "MOV")
            val missing =
                TtrpDiagnosticId.entries
                    .filter { it.id.substringAfter("TTRP-").substringBefore('-') in tiers }
                    .filter { it.suggestedAlternative.isNullOrBlank() }
                    .map { it.id }
            missing shouldBe emptyList()
        }
    })
