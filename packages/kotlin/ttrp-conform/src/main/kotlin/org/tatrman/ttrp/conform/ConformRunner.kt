// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.conform

import org.tatrman.ttrp.bundle.RunManifest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/** Strict `manifest.json` reader (reuses the Stage-3.3 [RunManifest] contract). */
object ManifestReader {
    fun read(bundleDir: Path): RunManifest = RunManifest.fromJson(Files.readString(bundleDir.resolve("manifest.json")))
}

/** The outcome of invoking one placement variant's bundle. */
data class VariantRun(
    val name: String,
    val exitCode: Int,
    /** display name → its `out/<name>.arrow` path (present only when the run succeeded). */
    val displays: Map<String, Path>,
    /** Captured stdout+stderr of `run.sh` — the diagnostic clue on a non-zero exit. */
    val output: String = "",
)

/**
 * Executes a bundle offline-or-live: provisions `TTR_CONN_*` env (from the conform run's own
 * config — never from the bundle, F-c-ii), runs `bash run.sh` in the bundle dir, captures the exit
 * code, and collects the `out/` Arrow files afterward (contracts §9).
 */
class BundleInvoker(
    private val env: Map<String, String> = emptyMap(),
) {
    fun invoke(bundleDir: Path): VariantRun {
        val pb =
            ProcessBuilder("bash", "run.sh")
                .directory(bundleDir.toFile())
                .redirectErrorStream(true)
        pb.environment().putAll(env)
        val proc = pb.start()
        val output = proc.inputStream.readBytes().decodeToString()
        val code = proc.waitFor()
        val displays =
            if (code == 0) collectArrow(bundleDir.resolve("out")) else emptyMap()
        return VariantRun(bundleDir.fileName.toString(), code, displays, output)
    }

    private fun collectArrow(dir: Path): Map<String, Path> {
        if (!Files.isDirectory(dir)) return emptyMap()
        return Files.list(dir).use { s ->
            s
                .toList()
                .filter { it.fileName.toString().endsWith(".arrow") }
                .associate { it.fileName.toString().removeSuffix(".arrow") to it }
        }
    }
}

/** Aggregate outcome: per-display per-stream reports + the run-wide partition point + exit code. */
data class ConformOutcome(
    val exitCode: Int,
    val reports: Map<String, ConformReport>,
    val message: String,
    /** The eighth (partition) point — a single run-wide verdict over every reject site (contracts §7). */
    val partition: PointResult? = null,
) {
    fun summary(): String =
        buildString {
            appendLine(message)
            reports.forEach { (display, report) ->
                appendLine("=== display: $display ===")
                appendLine(report.summary())
            }
            partition?.let {
                appendLine("=== partition (point 8) ===")
                appendLine("[${if (it.pass) "PASS" else "FAIL"}] Q9-8 ${it.name}: ${it.detail}")
            }
        }.trimEnd()
}

/**
 * Orchestrates N placement variants → invoke each → pair out-dir displays by name → per-stream
 * compare (variant 0 as the reference) → the run-wide partition point (8) over every reject site →
 * aggregate. Exit 0 all-pass · 1 comparison/partition failure · 2 invocation/pre-flight failure
 * (mirrors the bundle exit contract).
 */
class ConformRunner(
    private val invoker: BundleInvoker,
    private val terminalSortByDisplay: Map<String, Pair<Boolean, List<String>>> = emptyMap(),
    private val tolerances: Map<String, Double> = emptyMap(),
) {
    fun run(variants: Map<String, Path>): ConformOutcome {
        require(variants.size >= 2) { "conformance needs ≥2 placement variants" }
        val runs = variants.mapValues { invoker.invoke(it.value) }
        val failed = runs.filter { it.value.exitCode != 0 }
        if (failed.isNotEmpty()) {
            val f = failed.entries.first()
            val clue =
                f.value.output
                    .trim()
                    .takeLast(4000)
            return ConformOutcome(
                2,
                emptyMap(),
                "invocation failed: variant ${f.key} exited ${f.value.exitCode}" +
                    if (clue.isEmpty()) "" else "\n--- run.sh output ---\n$clue",
            )
        }
        val names = variants.keys.toList()
        val reference = runs.getValue(names.first())
        val reports = linkedMapOf<String, ConformReport>()
        for (display in reference.displays.keys.sorted()) {
            val refTable = ArrowIo.readTable(reference.displays.getValue(display))
            val (terminalSort, sortCols) = terminalSortByDisplay[display] ?: (false to emptyList())
            val comparator = SevenPointComparator(terminalSort, sortCols, tolerances)
            for (other in names.drop(1)) {
                val otherRun = runs.getValue(other)
                val otherPath =
                    otherRun.displays[display]
                        ?: return ConformOutcome(1, reports, "variant $other is missing display '$display'")
                val report = comparator.compare(refTable, ArrowIo.readTable(otherPath))
                reports[if (names.size > 2) "$display@$other" else display] = report
                if (!report.pass) {
                    return ConformOutcome(1, reports, "comparison failed for display '$display' ($other)")
                }
            }
        }
        // Eighth point (contracts §7): the partition tally across every variant's counts.json.
        val partition = PartitionCheck.check(variants.mapValues { PartitionCheck.readCounts(it.value).sites })
        if (!partition.pass) {
            return ConformOutcome(1, reports, "partition check (point 8) failed: ${partition.detail}", partition)
        }
        val sites = if (partition.detail.startsWith("n/a")) "" else "; ${partition.detail}"
        return ConformOutcome(0, reports, "all placement variants agree (${reports.size} comparisons)$sites", partition)
    }
}
