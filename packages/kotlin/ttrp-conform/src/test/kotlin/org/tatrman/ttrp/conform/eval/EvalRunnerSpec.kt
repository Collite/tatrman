// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.conform.eval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * T7.2.5 (runner): three hand-written candidate sets → the three verdicts. `invalid` carries
 * the named diagnostics (the repair vocabulary round-trips). The runner never compiles by
 * itself — the front-half is injected (here a deterministic stub; the CLI wires the real one).
 */
class EvalRunnerSpec :
    StringSpec({
        val corpus = EvalCorpus.load(EvalTestGraphs.corpusDir())
        val runner = EvalRunner(EvalTestGraphs::stubCompile)

        // Each expected fixture carries a `// shape: …` marker the stub compiles to a graph —
        // one passing candidate per corpus entry (3c: 10 entries, one per canonical op family).
        val allPassingCandidates =
            mapOf(
                "eval-001" to "// shape: filter,aggregate",
                "eval-002" to "// shape: sort,limit",
                "eval-003" to "// shape: filter,distinct",
                "eval-004" to "// shape: join",
                "eval-005" to "// shape: branch",
                "eval-006" to "// shape: calc",
                "eval-007" to "// shape: select",
                "eval-008" to "// shape: union",
                "eval-009" to "// shape: distinct",
                "eval-010" to "// shape: calc",
            )

        "a passing candidate set → all Pass" {
            val report = runner.score(corpus, allPassingCandidates)
            report.pass shouldBe 10
            report.shapeMismatch shouldBe 0
            report.invalid shouldBe 0
        }

        "a shape-mismatch candidate (extra node) → ShapeMismatch" {
            val candidates = allPassingCandidates + ("eval-001" to "// shape: filter,aggregate,distinct")
            val report = runner.score(corpus, candidates)
            report.shapeMismatch shouldBe 1
            report.pass shouldBe 9
        }

        "an invalid candidate → Invalid carrying the named diagnostic" {
            val candidates =
                allPassingCandidates + ("eval-001" to "// invalid: TTRP-B-001|Store <name> to <model-ref>.")
            val report = runner.score(corpus, candidates)
            report.invalid shouldBe 1
            val verdict = report.entries.single { it.id == "eval-001" }.verdict
            verdict.shouldBeInstanceOf<EvalComparator.Verdict.Invalid>()
            verdict.diagnostics.first() shouldBe "TTRP-B-001"
        }
    })
