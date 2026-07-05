package org.tatrman.ttrp.cli

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import java.nio.file.Files
import java.nio.file.Path

/**
 * `ttrp check` CLI component test (T1.3.7) — no process spawn; the same entry
 * function [TtrpCli.runCheck] is called directly. The shared erp-project models are
 * injected via [MetadataFixtures]; the `.ttrp` programs live in ttrp-frontend's test
 * resources (located by walk-up).
 */
class TtrpCheckCliSpec :
    StringSpec({

        val resolutionRoot: Path =
            run {
                val rel = Path.of("packages/kotlin/ttrp-frontend/src/test/resources/resolution")
                var dir: Path? = Path.of("").toAbsolutePath()
                while (dir != null) {
                    if (Files.isDirectory(dir.resolve(rel))) return@run dir.resolve(rel)
                    dir = dir.parent
                }
                error("could not locate resolution fixtures")
            }
        val models = MetadataFixtures.erpModelsRoot()

        fun run(rel: String): Pair<Int, String> {
            val lines = mutableListOf<String>()
            val code = TtrpCli.runCheck(resolutionRoot.resolve(rel), { lines += it }, modelsRootOverride = models)
            return code to lines.joinToString("\n")
        }

        "check hero.ttrp exits 0 with zero ERROR diagnostics" {
            val (code, out) = run("project/programs/hero.ttrp")
            withClue(out) { code shouldBe 0 }
        }

        "check hero_er.ttrp exits 0" {
            val (code, out) = run("project/programs/hero_er.ttrp")
            withClue(out) { code shouldBe 0 }
        }

        "check a negative fixture exits 1 and prints the expected id" {
            val (code, out) = run("negative/res-006-dangling-import.ttrp")
            code shouldBe 1
            out shouldContain "TTRP-RES-006"
        }

        "check prints a suggested-alternative line" {
            val (_, out) = run("negative/res-001-bare-miss.ttrp")
            out shouldContain "↳ suggested:"
        }

        "dispatch rejects a non-check subcommand" {
            val lines = mutableListOf<String>()
            TtrpCli.dispatch(arrayOf("build"), { lines += it }) shouldBe 2
        }
    })
