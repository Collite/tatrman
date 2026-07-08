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

        // Each expected fixture carries a `// shape: …` marker the stub compiles to a graph.
        "a passing candidate set → all Pass" {
            val candidates =
                mapOf(
                    "eval-001" to "// shape: filter,aggregate",
                    "eval-002" to "// shape: sort,limit",
                )
            val report = runner.score(corpus, candidates)
            report.pass shouldBe 2
            report.shapeMismatch shouldBe 0
            report.invalid shouldBe 0
        }

        "a shape-mismatch candidate (extra node) → ShapeMismatch" {
            val candidates =
                mapOf(
                    "eval-001" to "// shape: filter,aggregate,distinct",
                    "eval-002" to "// shape: sort,limit",
                )
            val report = runner.score(corpus, candidates)
            report.shapeMismatch shouldBe 1
            report.pass shouldBe 1
        }

        "an invalid candidate → Invalid carrying the named diagnostic" {
            val candidates =
                mapOf(
                    "eval-001" to "// invalid: TTRP-B-001|Store <name> to <model-ref>.",
                    "eval-002" to "// shape: sort,limit",
                )
            val report = runner.score(corpus, candidates)
            report.invalid shouldBe 1
            val verdict = report.entries.single { it.id == "eval-001" }.verdict
            verdict.shouldBeInstanceOf<EvalComparator.Verdict.Invalid>()
            verdict.diagnostics.first() shouldBe "TTRP-B-001"
        }
    })
