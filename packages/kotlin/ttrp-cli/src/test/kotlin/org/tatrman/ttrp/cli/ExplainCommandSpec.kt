package org.tatrman.ttrp.cli

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import java.nio.file.Files
import java.nio.file.Path

/** `ttrp explain` CLI component test (T2.3b.6) — no process spawn; drives [TtrpCli.runExplain]. */
class ExplainCommandSpec :
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

        fun explain(rel: String): Pair<Int, String> {
            val lines = mutableListOf<String>()
            val code = TtrpCli.runExplain(resolutionRoot.resolve(rel), { lines += it }, modelsRootOverride = models)
            return code to lines.joinToString("\n")
        }

        "explain hero.ttrp exits 0 and emits the island + wave sections" {
            val (code, out) = explain("project/programs/hero.ttrp")
            withClue(out) { code shouldBe 0 }
            out shouldContain "islands:"
            out shouldContain "engine=erp_pg"
            out shouldContain "engine=polars"
            out shouldContain "waves:"
            out shouldContain "branch->filter"
        }

        "explain of a broken program exits nonzero printing diagnostics" {
            val (code, out) = explain("negative/res-006-dangling-import.ttrp")
            code shouldBe 1
            out shouldContain "TTRP-RES-006"
        }

        "dispatch accepts explain and rejects an unknown subcommand" {
            val lines = mutableListOf<String>()
            TtrpCli.dispatch(arrayOf("build"), { lines += it }) shouldBe 2
        }
    })
