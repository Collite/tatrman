// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.md.resolve.fixtures.InMemoryMemberSnapshot
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.semantics.md.MdModel
import java.time.Instant

/**
 * S2-B3 — the pathological-input guard (R8 / TTRP-MD-014). A synthetic model where one member name
 * belongs to five attributes makes each token 5-way ambiguous; a long path then has an exponential
 * assignment space (5^k). The resolver must hit its exhaustive-search bound and report MD-014 rather
 * than hang. Bound value: `DEFAULT_SEARCH_BOUND` = 50_000 states (documented in the diag text).
 */
class SearchBoundSpec :
    StringSpec({
        val model = fanOutModel()
        // "X" is a member of all five domains ⇒ classify("X") yields five Member candidates.
        val snapshot = InMemoryMemberSnapshot((0..4).associate { "D$it" to listOf("X") })
        val resolver = DefaultMdPathResolver()

        "a long, maximally-ambiguous path hits the bound as MD-014, not a hang" {
            val input = List(8) { "X" }.joinToString(".") // 5^8 ≈ 390k assignments ≫ the 50k bound
            val outcome = resolver.resolve(PathText.parse(input), model, snapshot, Instant.EPOCH)
            check(outcome is ResolutionOutcome.Failed) { "expected Failed, got $outcome" }
            outcome.diagnostics.single().code shouldBe "TTRP-MD-014"
        }

        "a short path under the same model still resolves or reports a normal diagnostic" {
            // Two tokens ⇒ 25 assignments ≪ bound: the guard never trips for ordinary inputs.
            val outcome = resolver.resolve(PathText.parse("X.X"), model, snapshot, Instant.EPOCH)
            check(outcome !is ResolutionOutcome.Failed || outcome.diagnostics.none { it.code == "TTRP-MD-014" }) {
                "short path must not trip MD-014: $outcome"
            }
        }
    })

/** Five domains + five dimensions (one attribute each) + one cubelet over all five; one measure. */
private fun fanOutModel(): MdModel {
    val text =
        buildString {
            appendLine("model md")
            for (i in 0..4) appendLine("def domain D$i { type: string, publish: members }")
            appendLine("def domain M { type: decimal }")
            for (i in 0..4) {
                appendLine(
                    "def dimension Dim$i { key: a, attributes: [ def attribute a { domain: md.D$i, isKey: true } ] }",
                )
            }
            appendLine("def measure m { domain: md.M, class: additive, aggregation: sum }")
            appendLine("def cubelet c { grain: [Dim0.a, Dim1.a, Dim2.a, Dim3.a, Dim4.a], measures: [m] }")
        }
    val r = TtrLoader.parseString(text, "fanout.ttrm")
    require(r.ok) { "fan-out model parse errors: ${r.errors}" }
    return MdModel.from(r.definitions)
}
