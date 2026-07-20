// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.eval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.conform.eval.EvalComparator
import org.tatrman.ttrp.conform.eval.EvalCorpus
import org.tatrman.ttrp.conform.eval.EvalRunner
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Path

/**
 * T7.2.7 — the committed assist-quality baseline (`baselines/001`). Re-scores the committed model
 * candidates through the **real front-half** against the eval corpus and pins the verdicts. **No
 * model / no LLM runs here**: the gateway produced these candidates once, at capture time; CI only
 * compiles + shape-compares them (the same deterministic half `ttrp eval` runs). This guards against
 * front-half drift silently changing assist quality — if a candidate that compiled + shape-matched
 * now fails (or an invalid one starts compiling), this breaks and the baseline must be re-blessed.
 *
 * Baseline captured 2026-07-20 via an OpenAI-compatible chat gateway (gpt-4.1, temperature 0): **9
 * pass, 1 invalid** — eval-003 emitted a `;` statement separator (→ `TTRP-PRS-001`), an honest
 * model-quality data point kept as-is.
 */
class EvalBaselineSpec :
    StringSpec({
        // Locate the committed corpus + baseline from any cwd under the repo (Gradle runs tests from
        // the module dir; walk up to the repo root where the path resolves).
        fun locate(rel: String): Path {
            var dir: Path? = Path.of("").toAbsolutePath()
            while (dir != null) {
                val p = dir.resolve(rel)
                if (Files.exists(p)) return p
                dir = dir.parent
            }
            error("could not locate `$rel` from any parent of ${Path.of("").toAbsolutePath()}")
        }

        val corpusDir = locate("packages/kotlin/ttrp-conform/src/test/eval")
        val candDir = corpusDir.resolve("baselines/001/candidates")
        val corpus = EvalCorpus.load(corpusDir)

        // Real front-half over the shared erp-project world — exactly the CLI `EvalCommand` wiring.
        val pipeline =
            TtrpPipeline(
                TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                MetadataFixtures.erpModelsRoot(),
            )
        val runner =
            EvalRunner { src, fn ->
                val plan = pipeline.plan(src, fn)
                EvalRunner.CompileOutcome(
                    plan.graph,
                    plan.diagnostics.filter { it.severity == Severity.ERROR }.map { it.id.id },
                )
            }
        val candidates =
            corpus.entries
                .mapNotNull { e ->
                    val f = candDir.resolve("${e.id}.ttrp")
                    if (Files.isRegularFile(f)) e.id to Files.readString(f) else null
                }.toMap()
        val report = runner.score(corpus, candidates)

        "every corpus entry has a committed candidate" {
            candidates.keys shouldBe corpus.entries.map { it.id }.toSet()
        }

        "baselines/001 re-scores to the pinned verdicts: 9 pass, 0 shape-mismatch, 1 invalid" {
            report.pass shouldBe 9
            report.shapeMismatch shouldBe 0
            report.invalid shouldBe 1
        }

        "eval-003 is the pinned invalid (a `;` statement separator → TTRP-PRS-001)" {
            val verdict = report.entries.single { it.id == "eval-003" }.verdict
            (verdict is EvalComparator.Verdict.Invalid) shouldBe true
        }
    })
