// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * EN-P3.1 T2 — the F3 ledger id scheme (contracts §5, ⚑EN-2): `-rev<n>`/`-rep<n>` with n = 1 + the
 * committed-state reversal count. A pure function of (original id, count) — deterministic, unique under
 * repeated corrections, replay-stable.
 */
class LedgerIdSchemeSpec :
    StringSpec({
        "the first correction of an original yields -rev1 / -rep1" {
            LedgerIds.reversalId("e1", existingReversals = 0) shouldBe "e1-rev1"
            LedgerIds.replacementId("e1", existingReversals = 0) shouldBe "e1-rep1"
        }

        "a second correction (one prior reversal in state) yields -rev2 / -rep2" {
            LedgerIds.reversalId("e1", existingReversals = 1) shouldBe "e1-rev2"
            LedgerIds.replacementId("e1", existingReversals = 1) shouldBe "e1-rep2"
        }

        "the scheme is pure — same (id, count) always yields the same ids" {
            (0..5).forEach { n ->
                LedgerIds.reversalId("x", n) shouldBe LedgerIds.reversalId("x", n)
                LedgerIds.reversalId("x", n) shouldBe "x-rev${n + 1}"
                LedgerIds.replacementId("x", n) shouldBe "x-rep${n + 1}"
            }
        }
    })
