// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.conform

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.emit.bash.BashEmitPlugin
import org.tatrman.ttrp.emit.kestra.KestraEmitPlugin
import org.tatrman.ttrp.emit.spi.EmitIsland
import org.tatrman.ttrp.emit.spi.EmitRequest
import org.tatrman.ttrp.emit.spi.EmitResult
import org.tatrman.ttrp.emit.spi.OrchestrationGraph
import org.tatrman.ttrp.emit.spi.ProgramMeta
import org.tatrman.ttrp.emit.spi.ResolvedManifest
import org.tatrman.ttrp.emit.spi.TtrEmitPlugin
import java.util.SortedMap
import java.util.TreeMap

/**
 * PL-P5.S2.T1 — the H-6 emit-determinism kit. Double-emits each request and byte-compares: the real bash plugin
 * PASSES; a deliberately timestamping fixture FAILS with a report naming the file + the first differing offset.
 */
class EmitDeterminismKitTest :
    StringSpec({

        fun request() =
            EmitRequest(
                program = ProgramMeta("hero.ttrp", "acme.worlds.dev", "org.tatrman:ttrp:0.0.0"),
                graph =
                    OrchestrationGraph(
                        waves = listOf(listOf("crunch")),
                        islands = listOf(EmitIsland("crunch", "erp_pg", "psql", "islands/crunch.sql")),
                        transfers = emptyList(),
                        connections = listOf("TTR_CONN_ERP_PG"),
                        displays = emptyList(),
                        connectionByIsland = mapOf("crunch" to "TTR_CONN_ERP_PG"),
                    ),
                islandPayloads = emptyList(),
                transferPayloads = emptyList(),
                executorType = ResolvedManifest(""),
                executorInstance = ResolvedManifest(""),
                manifestJson = "{}",
            )

        "the bash plugin is byte-deterministic (PASS)" {
            val report = EmitDeterminismKit.check(BashEmitPlugin(), listOf(request(), request()))
            report.deterministic.shouldBeTrue()
            report.cases shouldBe 2
            report.render() shouldContain "PASS"
        }

        "the kestra plugin is byte-deterministic (PASS — the S3 Q-6 certification guard)" {
            val report = EmitDeterminismKit.check(KestraEmitPlugin(), listOf(request(), request()))
            report.deterministic.shouldBeTrue()
            report.cases shouldBe 2
            report.render() shouldContain "PASS"
        }

        "a non-deterministic (clock/counter-stamping) plugin FAILS, naming the file + first differing offset" {
            val report = EmitDeterminismKit.check(StampingPlugin(), listOf(request()))
            report.deterministic.shouldBeFalse()
            val d = report.divergences.single()
            d.file shouldBe "run.sh"
            // "# run " is a 6-byte fixed prefix; the stamped counter first differs at byte 6.
            d.offset shouldBe "# run ".length
            report.render() shouldContain "FAIL"
            report.render() shouldContain "run.sh"
        }

        "a plugin that emits a different FILE SET across runs is caught (offset -1, file-set divergence)" {
            val report = EmitDeterminismKit.check(FlakyFileSetPlugin(), listOf(request()))
            report.deterministic.shouldBeFalse()
            report.divergences.single().offset shouldBe -1
            report.render() shouldContain "file set"
        }
    })

/**
 * A plugin that stamps a monotonic counter into its output (a stand-in for a clock/`nanoTime` read) — so two
 * emits of the same request differ. A deterministic counter (not the real clock) keeps the TEST itself stable:
 * the divergence is always at the same known offset.
 */
private class StampingPlugin : TtrEmitPlugin {
    private var n = 0
    override val targetId = "stamping"
    override val spiVersion = TtrEmitPlugin.SPI_VERSION

    override fun executorTypeManifest() = ""

    override fun emit(request: EmitRequest): EmitResult {
        val files: SortedMap<String, ByteArray> = TreeMap()
        files["run.sh"] = "# run ${n++}\n".toByteArray()
        return EmitResult(files)
    }
}

/** A plugin whose emitted FILE SET flips between runs (first emit lacks the extra file, second has it). */
private class FlakyFileSetPlugin : TtrEmitPlugin {
    private var n = 0
    override val targetId = "flaky-fileset"
    override val spiVersion = TtrEmitPlugin.SPI_VERSION

    override fun executorTypeManifest() = ""

    override fun emit(request: EmitRequest): EmitResult {
        val files: SortedMap<String, ByteArray> = TreeMap()
        files["run.sh"] = "exit 0\n".toByteArray()
        if (n++ % 2 == 1) files["extra.txt"] = ByteArray(0)
        return EmitResult(files)
    }
}
