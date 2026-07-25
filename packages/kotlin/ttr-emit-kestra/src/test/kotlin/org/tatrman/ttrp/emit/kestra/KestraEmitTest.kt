// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.kestra

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.tatrman.ttrp.emit.spi.EmitDisplay
import org.tatrman.ttrp.emit.spi.EmitIsland
import org.tatrman.ttrp.emit.spi.EmitRequest
import org.tatrman.ttrp.emit.spi.EmitTransfer
import org.tatrman.ttrp.emit.spi.OrchestrationGraph
import org.tatrman.ttrp.emit.spi.ProgramMeta
import org.tatrman.ttrp.emit.spi.ResolvedManifest
import org.tatrman.ttrp.emit.spi.TtrEmitPlugin

/**
 * PL-P5.S3.T1/T2 — the Kestra plugin's `flow.yaml`, pinned. Same island/wave fixture as the bash plugin, plus a
 * transfer edge in its own wave: one `Parallel` per wave (wave order via the sequential task list), psql → a
 * `shell.Script` with the F-lite `ON_ERROR_STOP=1` invocation, polars → a `python.Commands` task, transfer → a
 * python task between waves, and connections surfaced only as `TTR_CONN_*` secret placeholders. T2: the native
 * flow (E-3-β) has NO program-door URL anywhere — the platform is not on the runtime path.
 */
class KestraEmitTest :
    FunSpec({
        // waves [[a,b],[move],[c]] — a/b psql, c polars, `move` a transfer between waves. Connection TTR_CONN_ERP_PG.
        val graph =
            OrchestrationGraph(
                waves = listOf(listOf("a", "b"), listOf("move"), listOf("c")),
                islands =
                    listOf(
                        EmitIsland("a", "erp_pg", "psql", "islands/a.sql"),
                        EmitIsland("b", "erp_pg", "psql", "islands/b.sql"),
                        EmitIsland("c", "polars", "python3", "islands/c.py"),
                    ),
                transfers =
                    listOf(
                        EmitTransfer(from = "b", to = "c", via = "staging", file = "transfers/move.py"),
                    ),
                connections = listOf("TTR_CONN_ERP_PG"),
                displays = listOf(EmitDisplay("main_result", "out/main_result.arrow")),
                connectionByIsland = mapOf("a" to "TTR_CONN_ERP_PG", "b" to "TTR_CONN_ERP_PG"),
            )

        fun request(g: OrchestrationGraph = graph) =
            EmitRequest(
                program = ProgramMeta("hero.ttrp", "acme.worlds.dev", "org.tatrman:ttrp:1.0.0"),
                graph = g,
                islandPayloads = emptyList(),
                transferPayloads = emptyList(),
                executorType = ResolvedManifest(""),
                executorInstance = ResolvedManifest(""),
                manifestJson = "{}",
            )

        val plugin = KestraEmitPlugin()
        val yaml = String(plugin.emit(request()).files.getValue("flow.yaml"))

        @Suppress("UNCHECKED_CAST")
        fun parsed(): Map<String, Any> = Load(LoadSettings.builder().build()).loadFromString(yaml) as Map<String, Any>

        test("emits exactly one file: flow.yaml") {
            plugin
                .emit(request())
                .files.keys
                .toList() shouldBe listOf("flow.yaml")
        }

        test("targetId + spiVersion") {
            plugin.targetId shouldBe "kestra"
            plugin.spiVersion shouldBe TtrEmitPlugin.SPI_VERSION
        }

        test("flow id (program, .ttrp stripped) + configured namespace (the world qname)") {
            val flow = parsed()
            flow["id"] shouldBe "hero"
            flow["namespace"] shouldBe "acme.worlds.dev"
        }

        test("one Parallel per wave, wave order preserved by the sequential task list") {
            @Suppress("UNCHECKED_CAST")
            val tasks = parsed()["tasks"] as List<Map<String, Any>>
            tasks shouldHaveSize 3
            tasks.map { it["id"] } shouldBe listOf("wave_0", "wave_1", "wave_2")
            tasks.forEach { it["type"] shouldBe "io.kestra.plugin.core.flow.Parallel" }
        }

        test("psql island → shell.Script with the verbatim F-lite ON_ERROR_STOP invocation") {
            yaml shouldContain "io.kestra.plugin.scripts.shell.Script"
            yaml shouldContain "psql \"\$TTR_CONN_ERP_PG\" -v ON_ERROR_STOP=1 --no-psqlrc -f islands/a.sql"
        }

        test("polars island → python Commands task") {
            yaml shouldContain "io.kestra.plugin.scripts.python.Commands"
            yaml shouldContain "python3 islands/c.py"
        }

        test("transfer edge renders as a task in its own wave (between waves)") {
            @Suppress("UNCHECKED_CAST")
            val wave1 = (parsed()["tasks"] as List<Map<String, Any>>)[1]

            @Suppress("UNCHECKED_CAST")
            val inner = wave1["tasks"] as List<Map<String, Any>>
            inner shouldHaveSize 1
            inner.single()["id"] shouldBe "move"
            yaml shouldContain "python3 transfers/move.py"
        }

        test("connections surface only as TTR_CONN_* secret placeholders — never material") {
            yaml shouldContain "TTR_CONN_ERP_PG"
            yaml shouldContain "secret(\"TTR_CONN_ERP_PG\")"
        }

        test("T2 (E-3-β native/standalone): no program-door URL anywhere in the flow") {
            yaml shouldNotContain "http://"
            yaml shouldNotContain "https://"
            yaml shouldNotContain "://"
            yaml.lowercase() shouldNotContain "door"
        }

        test("determinism: same request ⇒ byte-identical flow.yaml (H-6 obligation)") {
            String(plugin.emit(request()).files.getValue("flow.yaml")) shouldBe yaml
        }

        test("output is well-formed YAML (parses back to a mapping)") {
            parsed()["tasks"].shouldBeInstanceOfList()
        }

        test("ships the kestra executor-type manifest (§7: fs/ss, psql/python3, cron)") {
            val m = plugin.executorTypeManifest()
            m shouldContain "def executor kestra"
            m shouldContain "control: [fs, ss]"
            m shouldContain "invocation: [psql, python3]"
            m shouldContain "events: [cron]"
        }
    })

private fun Any?.shouldBeInstanceOfList() {
    check(this is List<*>) { "expected the flow's `tasks` to be a YAML sequence, got ${this?.let { it::class }}" }
}
