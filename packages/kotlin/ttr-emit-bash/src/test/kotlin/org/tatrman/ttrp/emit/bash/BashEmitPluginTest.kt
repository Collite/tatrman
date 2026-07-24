// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.bash

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.emit.spi.EmitDisplay
import org.tatrman.ttrp.emit.spi.EmitIsland
import org.tatrman.ttrp.emit.spi.EmitRequest
import org.tatrman.ttrp.emit.spi.OrchestrationGraph
import org.tatrman.ttrp.emit.spi.ProgramMeta
import org.tatrman.ttrp.emit.spi.ResolvedManifest
import org.tatrman.ttrp.emit.spi.TtrEmitPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * PL-P5.S1.T4 — the bash plugin's `run.sh`, pinned. These are the pre-SPI `RunShGeneratorTest` assertions
 * verbatim, now driving [BashEmitPlugin] through the SPI: the extraction is byte-identical (the "proven by
 * extraction" gate, at the launcher level; whole-`.bundle/` parity is the ttrp-cli HeroBundleTest, unchanged).
 */
class BashEmitPluginTest :
    FunSpec({
        // 3-island / 2-wave fixture: waves [[a,b],[c]], connection TTR_CONN_ERP_PG.
        val graph =
            OrchestrationGraph(
                waves = listOf(listOf("a", "b"), listOf("c")),
                islands =
                    listOf(
                        EmitIsland("a", "erp_pg", "psql", "islands/a.sql"),
                        EmitIsland("b", "erp_pg", "psql", "islands/b.sql"),
                        EmitIsland("c", "polars", "python3", "islands/c.py"),
                    ),
                transfers = emptyList(),
                connections = listOf("TTR_CONN_ERP_PG"),
                displays = listOf(EmitDisplay("main_result", "out/main_result.arrow")),
                connectionByIsland = mapOf("a" to "TTR_CONN_ERP_PG", "b" to "TTR_CONN_ERP_PG"),
            )

        fun request(g: OrchestrationGraph = graph) =
            EmitRequest(
                program = ProgramMeta("p.ttrp", "w", "org.tatrman:ttrp:1.0.0"),
                graph = g,
                islandPayloads = emptyList(),
                transferPayloads = emptyList(),
                executorType = ResolvedManifest(""),
                executorInstance = ResolvedManifest(""),
                manifestJson = "{}",
            )

        val plugin = BashEmitPlugin()
        val script = String(plugin.emit(request()).files.getValue("run.sh"))

        test("emits exactly one file: run.sh") {
            plugin
                .emit(request())
                .files.keys
                .toList() shouldBe listOf("run.sh")
        }

        test("targetId + spiVersion") {
            plugin.targetId shouldBe "bash"
            plugin.spiVersion shouldBe TtrEmitPlugin.SPI_VERSION
        }

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

        test("determinism: same request ⇒ byte-identical run.sh (H-6 obligation)") {
            String(plugin.emit(request()).files.getValue("run.sh")) shouldBe script
        }

        test("ships the bash executor-type manifest (§7 F-lite subset)") {
            val m = plugin.executorTypeManifest()
            m shouldContain "def executor bash"
            m shouldContain "control: [fs, ss]"
            m shouldContain "invocation: [psql, python3]"
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
