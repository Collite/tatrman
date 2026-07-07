package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** T3.3.3 — run.sh content is asserted on the generated text; no live execution. */
class RunShGeneratorTest :
    FunSpec({
        // 3-island / 2-wave fixture: waves [[a,b],[c]], connection TTR_CONN_ERP_PG.
        val manifest =
            RunManifest(
                toolchain = "org.tatrman:ttrp:1.0.0",
                program = "p.ttrp",
                world = WorldRef("w", "sha256:" + "0".repeat(64)),
                islands =
                    listOf(
                        IslandEntry("a", "erp_pg", "bash", "psql", "islands/a.sql", "sha256:" + "1".repeat(64)),
                        IslandEntry("b", "erp_pg", "bash", "psql", "islands/b.sql", "sha256:" + "2".repeat(64)),
                        IslandEntry("c", "polars", "bash", "python3", "islands/c.py", "sha256:" + "3".repeat(64)),
                    ),
                transfers = emptyList(),
                waves = listOf(listOf("a", "b"), listOf("c")),
                connections = listOf("TTR_CONN_ERP_PG"),
                displays = listOf(DisplayEntry("main_result", "out/main_result.arrow")),
                files = emptyMap(),
            )
        val script = RunShGenerator.generate(manifest, mapOf("a" to "TTR_CONN_ERP_PG", "b" to "TTR_CONN_ERP_PG"))

        test("header + strict mode") {
            script.lineSequence().first() shouldBe "#!/usr/bin/env bash"
            script shouldContain "set -euo pipefail"
        }

        test("pre-flight: bash version guard + connection checks exit 2") {
            script shouldContain "BASH_VERSINFO[0] < 4"
            script shouldContain
                "[[ -z \"\${TTR_CONN_ERP_PG:-}\" ]] && { echo \"missing TTR_CONN_ERP_PG\" >&2; exit 2; }"
        }

        test("wipe-on-restart before waves") {
            script shouldContain "rm -rf logs staging out && mkdir -p logs staging out"
        }

        test("invocations per F-c and wave launch with & + pid capture") {
            script shouldContain "psql \"\$TTR_CONN_ERP_PG\" -v ON_ERROR_STOP=1 --no-psqlrc -f islands/a.sql"
            script shouldContain "python3 islands/c.py"
            script shouldContain "pids+=(\$!)"
            script shouldContain "wait -n"
            script shouldContain "FAILED island="
            script shouldContain "exit 1"
        }

        test("display notice + final exit 0") {
            script shouldContain "echo \"display main_result: out/main_result.arrow\""
            script.trimEnd().endsWith("exit 0") shouldBe true
        }

        test("bash -n accepts the generated script (offline syntax check)") {
            val bash = which("bash")
            if (bash == null) {
                System.err.println("SKIP: bash not on PATH")
                return@test
            }
            val tmp = Files.createTempFile("run", ".sh")
            Files.writeString(tmp, script)
            val proc = ProcessBuilder(bash.toString(), "-n", tmp.toString()).redirectErrorStream(true).start()
            val out = proc.inputStream.readBytes().decodeToString()
            val code = proc.waitFor()
            if (code != 0) throw AssertionError("bash -n failed:\n$out\n---\n$script")
            code shouldBe 0
            script.length shouldBeGreaterThan 0
        }
    })

private fun which(cmd: String): Path? =
    System
        .getenv("PATH")
        .orEmpty()
        .split(File.pathSeparatorChar)
        .map { Paths.get(it, cmd) }
        .firstOrNull { Files.isExecutable(it) }
