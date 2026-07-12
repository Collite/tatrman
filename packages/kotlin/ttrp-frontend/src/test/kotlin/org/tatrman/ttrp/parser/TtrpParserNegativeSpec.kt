// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.tatrman.ttrp.diagnostics.Severity

/**
 * Named-reject corpus: each fixtures under `negative/` fixture produces exactly one ERROR
 * with its expected `TTRP-…` id, and (except the generic PRS-001 syntax bucket) a
 * non-blank suggested alternative (contracts §8).
 */
class TtrpParserNegativeSpec :
    StringSpec({
        val cases =
            listOf(
                "eq-001.ttrp" to "TTRP-EQ-001",
                "ctl-001.ttrp" to "TTRP-CTL-001",
                "prs-002-program-header.ttrp" to "TTRP-PRS-002",
                "prs-003-positional-join.ttrp" to "TTRP-PRS-003",
                "prs-004-named-union.ttrp" to "TTRP-PRS-004",
                "prs-005-reserved-port.ttrp" to "TTRP-PRS-005",
                "frg-001-unknown-tag.ttrp" to "TTRP-FRG-001",
            )

        for ((fixture, expectedId) in cases) {
            "negative/$fixture → exactly one $expectedId with a suggested alternative" {
                val result = TtrpParser.parseString(Fixtures.negative(fixture), fixture)
                val errors = result.diagnostics.filter { it.severity == Severity.ERROR }
                errors shouldHaveSize 1
                errors.single().id.id shouldBe expectedId
                errors.single().suggestedAlternative.shouldNotBeBlank()
            }
        }

        // review-001 1.1-B: `err`/`rejects` reach the S10 walker check (PRS-005), but the
        // keyword/literal tokens `true`/`false`/`else` are not `identifier`-lexable, so
        // binding one is a grammar-level SYNTAX error (PRS-001) — a conscious split.
        "reserved literal `true` as an assignment target is a PRS-001 syntax error, not PRS-005" {
            val result = TtrpParser.parseString("true = load(files.x)", "reserved-literal.ttrp")
            val ids = result.diagnostics.filter { it.severity == Severity.ERROR }.map { it.id.id }
            ids shouldContain "TTRP-PRS-001"
            ids shouldNotContain "TTRP-PRS-005"
        }
    })
