// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * S2-B1 — the golden-fixture harness. Each `src/test/resources/golden/<case>.json` names a raw path
 * `input`, a members mode, an `asof`, and the `expected` outcome; the harness parses the input via
 * [PathText], resolves against the shared `sales-model`, and checks the outcome. The JSON format is
 * a **contract shared with the future TS port** (contracts §10) — changes need a changelog entry.
 *
 * A case may carry `"pending": "<reason>"` to be listed-but-skipped (keeps the coverage sweep
 * visible for diagnostics that arrive in S3/S5/S6 — S2-C6).
 */
class GoldenSpec :
    StringSpec({
        val json = Json { ignoreUnknownKeys = true }
        val resolver = DefaultMdPathResolver()

        for (file in goldenFiles()) {
            val case = json.decodeFromString<GoldenCase>(file.readText())
            val name = file.nameWithoutExtension
            if (case.pending != null) {
                "golden $name (pending: ${case.pending})".config(enabled = false) {}
                continue
            }
            "golden $name — ${case.input}" {
                val snapshot = if (case.members == "default") ResolverFixtures.snapshot() else null
                val components = PathText.parse(case.input)
                val outcome = resolver.resolve(components, ResolverFixtures.model, snapshot, Instant.parse(case.asof))
                checkOutcome(outcome, case.expected)
            }
        }
    })

private fun checkOutcome(
    outcome: ResolutionOutcome,
    expected: GoldenExpected,
) {
    when (expected.status) {
        "resolved" -> {
            check(outcome is ResolutionOutcome.Resolved) { "expected resolved, got $outcome" }
            expected.canonical?.let { CanonicalRenderer.render(outcome.path) shouldBe it }
            expected.shape?.let { outcome.shape.freeDims shouldContainExactlyInAnyOrder it }
        }
        "ambiguous" -> {
            check(outcome is ResolutionOutcome.Ambiguous) { "expected ambiguous, got $outcome" }
            val texts = outcome.alternatives.map { CanonicalRenderer.render(it.path) }
            expected.alternatives?.let { texts shouldContainExactlyInAnyOrder it }
        }
        "failed" -> {
            check(outcome is ResolutionOutcome.Failed) { "expected failed, got $outcome" }
            expected.diagnostics?.let { outcome.diagnostics.map { d -> d.code } shouldContainExactlyInAnyOrder it }
        }
        else -> error("unknown expected status: ${expected.status}")
    }
}

@Serializable
private data class GoldenCase(
    val model: String = "sales-model",
    val members: String? = "default",
    val asof: String = "2026-07-08T00:00:00Z",
    val input: String,
    val context: String? = null,
    val pending: String? = null,
    val expected: GoldenExpected = GoldenExpected(),
)

@Serializable
private data class GoldenExpected(
    val status: String = "resolved",
    val canonical: String? = null,
    val shape: List<String>? = null,
    val diagnostics: List<String>? = null,
    val alternatives: List<String>? = null,
)

/** Locate `src/test/resources/golden` by walking up from the working directory (jar-independent). */
private fun goldenFiles(): List<File> {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        val candidate = File(dir, "packages/kotlin/ttr-md-resolver/src/test/resources/golden")
        if (candidate.isDirectory) return candidate.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        dir = dir.parentFile
    }
    error("golden directory not found from ${System.getProperty("user.dir")}")
}
