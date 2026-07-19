// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.diagnostics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.expr.catalog.ValidityCatalog
import java.nio.file.Files
import java.nio.file.Path

/**
 * RJ-P6 6.1.3 — the rejects diagnostics table in `contracts.md §8.1` is complete both ways: every
 * `TTRP-RJ-*` code defined in code (the `TtrpDiagnosticId` authoring/rewrite enum + the validity-YAML
 * row reject-codes) appears in the doc, and every `TTRP-RJ-NNN` token in the doc is a real code
 * (no stale rows). Guards the "documented, stable ids" contract.
 */
class RejectDiagnosticsTableSpec :
    StringSpec({
        val contracts =
            Files.readString(Path.of("../../../docs/features/ttr-p/architecture/contracts.md"))

        // Every RJ code the codebase actually defines: the RJ_1xx authoring/rewrite enum ids …
        val enumCodes = TtrpDiagnosticId.entries.map { it.id }.filter { it.startsWith("TTRP-RJ-") }
        // … and the RJ-00x row reject-codes from the validity catalogue.
        val corpusCodes = ValidityCatalog.all.map { it.code }.filter { it.startsWith("TTRP-RJ-") }
        val definedCodes = (enumCodes + corpusCodes).toSortedSet()

        "every RJ code defined in code is documented in contracts §8" {
            val missing = definedCodes.filterNot { contracts.contains(it) }
            missing shouldBe emptyList()
        }

        "every RJ code cited in contracts is a real defined code (no stale rows)" {
            val cited = Regex("TTRP-RJ-\\d{3}").findAll(contracts).map { it.value }.toSortedSet()
            val stale = cited - definedCodes
            stale shouldBe emptySet()
        }
    })
