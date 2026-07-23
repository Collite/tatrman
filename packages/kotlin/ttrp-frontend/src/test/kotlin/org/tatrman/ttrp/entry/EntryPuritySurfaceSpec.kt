// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.parser.TtrpParser

/**
 * EN-P2.1 T5 — the surface purity rejection (`TTRP-EN-005`, contracts §6): an apply program is pure,
 * so a flow construct (load/store/display) in it is rejected. The full purity walk deepens in EN-P3;
 * the surface rejection is defined now.
 */
class EntryPuritySurfaceSpec :
    StringSpec({
        fun purity(src: String) =
            EntryPuritySurfaceCheck.check(TtrpParser.parseString(src, "x-entry-apply.ttrp").document).map { it.id }

        "a `load` in an apply program raises EN-005" {
            purity("acc = load(erp.accounts)\n") shouldContain TtrpDiagnosticId.EN_005
        }

        "a `display` sink in an apply program raises EN-005" {
            purity("acc = load(erp.accounts)\nacc -> display(main)\n") shouldContain TtrpDiagnosticId.EN_005
        }

        "a non-deterministic builtin (clock/random) in an apply program raises EN-005" {
            purity("x = random(erp.accounts)\n") shouldContain TtrpDiagnosticId.EN_005
        }

        "a program with no flow construct is pure" {
            purity("import entry.*\n") shouldBe emptyList()
        }
    })
