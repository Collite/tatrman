// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.resolve.ResolutionFixtures

/**
 * EN-P2.1 T7 — the fail-fast pin (RJ house pattern, R-P3): the EN surface is additive and NOT wired
 * into `TtrpChecker` (recognition is ready but routing lands with the deferred surface, contracts §1),
 * so a normal (non-apply) program must compile with **zero** `TTRP-EN-*` diagnostics — the sealed v1
 * surface is untouched. This runs green now and stays in CI as every later EN stage wires more in.
 */
class EntryFailFastPinSpec :
    StringSpec({
        "a normal v1 program emits no TTRP-EN-* diagnostics" {
            val src =
                """
                uses world "acme.worlds.dev"
                acc = load(erp.accounts)
                acc -> display(main_result)
                """.trimIndent() + "\n"
            val diags = ResolutionFixtures.checker().check(src, "hero.ttrp").diagnostics
            diags.count { it.id.id.startsWith("TTRP-EN-") } shouldBe 0
        }
    })
