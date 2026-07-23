// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * EN-P2.1 T6 (extended EN-P5) — the diagnostics-table completeness check (RJ house pattern, contracts
 * §7). Every `TTRP-EN-0xx` delivered so far has an enum seat with a well-formed id + a fix hint, each
 * exercised by a fixture in this package (BatchShapeMismatch/VerbDeclarationMatrix/Purity/Resolution +
 * the CallFn* specs for 006). Code 008 (Q-8 beyond reservation) is seated by its phase with its
 * fixtures — asserted absent here so the phase boundary stays explicit.
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
                TtrpDiagnosticId.EN_006,
                TtrpDiagnosticId.EN_007,
            )

        "the EN seats delivered so far are exactly 001..007" {
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

        "008 (Q-8) is not yet seated — its phase adds it" {
            TtrpDiagnosticId.entries.none { it.id == "TTRP-EN-008" } shouldBe true
        }
    })
