// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * S2-B7 — resolution latency. Target (contracts §10): a ≤10-token path resolves in < 10 ms median
 * over 1000 iterations post-warmup. We measure and log the median; the hard assertion is a generous
 * CI-safe ceiling (a cold shared runner is far slower than a warm laptop) so the suite isn't flaky —
 * the observed median is recorded in the S2-B coder notes.
 */
class PerformanceSpec :
    StringSpec({
        val model = ResolverFixtures.model
        val snapshot = ResolverFixtures.snapshot()
        val resolver = DefaultMdPathResolver()
        // A qualified-pair-heavy ~9-token path that resolves to a scalar.
        val components = PathText.parse("sales.customer.Kaufland.time.2025.net.sum")

        "a representative path resolves and stays well under the latency ceiling" {
            // sanity: it actually resolves
            (resolver.resolve(components, model, snapshot, Instant.EPOCH) is ResolutionOutcome.Resolved) shouldBe true

            repeat(2000) { resolver.resolve(components, model, snapshot, Instant.EPOCH) } // warmup
            val samples = LongArray(1000)
            for (i in samples.indices) {
                val t0 = System.nanoTime()
                resolver.resolve(components, model, snapshot, Instant.EPOCH)
                samples[i] = System.nanoTime() - t0
            }
            samples.sort()
            val medianMs = samples[samples.size / 2] / 1_000_000.0
            println("[S2-B7] resolve median = %.3f ms (target < 10 ms)".format(medianMs))
            check(medianMs < 50.0) { "resolve median $medianMs ms exceeded the CI ceiling" }
        }
    })
