// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P2.1 T6 — the diagnostics-table completeness check (RJ house pattern, contracts §7). Every
 * `TTRP-EN-0xx` this phase delivers has an enum seat with a well-formed id + a fix hint, and each is
 * exercised by a fixture elsewhere in this package (BatchShapeMismatch/VerbDeclarationMatrix/Purity/
 * Resolution). Codes 006 (call-fn, EN-P5) and 008 (Q-8 beyond reservation) are seated by their phases
 * with their fixtures — asserted absent here so the phase boundary is explicit.
 */
class EntryDiagnosticsTableSpec :
    StringSpec({
        val delivered =
            listOf(
                TtrpDiagnosticId.EN_001,
                TtrpDiagnosticId.EN_002,
                TtrpDiagnosticId.EN_003,
                TtrpDiagnosticId.EN_004,
                TtrpDiagnosticId.EN_005,
                TtrpDiagnosticId.EN_007,
            )

        "the EN seats this phase delivers are exactly 001..005 + 007" {
            TtrpDiagnosticId.entries.filter { it.id.startsWith("TTRP-EN-") } shouldContainExactly delivered
        }

        "every delivered EN code has a well-formed id and a non-empty fix hint" {
            delivered.forEach { code ->
                code.id.matches(Regex("TTRP-EN-00[1-7]")) shouldBe true
                (code.suggestedAlternative?.isNotBlank() ?: false) shouldBe true
            }
        }

        "the id catalogue has no duplicate id-strings after seating EN" {
            TtrpDiagnosticId.assertNoDuplicateIds() // throws on collision
        }

        "006 (call-fn) and 008 (Q-8) are not yet seated — their phases add them" {
            TtrpDiagnosticId.entries.none { it.id == "TTRP-EN-006" || it.id == "TTRP-EN-008" } shouldBe true
        }
    })
