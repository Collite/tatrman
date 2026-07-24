// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

/**
 * review-071 R3 — the resolver sorts ambiguous alternatives deterministically (by canonical text), but
 * the goldens only asserted membership (`shouldContainExactlyInAnyOrder`). This pins the ORDER so a
 * regression to insertion/iteration order is caught.
 */
class AmbiguityOrderSpec :
    StringSpec({
        val resolver = DefaultMdPathResolver()

        "ambiguous alternatives come back in a stable, sorted canonical order" {
            // `Kaufland.net` names no cubelet and `net` lives on both `plan` and `sales` ⇒ two canonicals.
            val outcome =
                resolver.resolve(
                    PathText.parse("Kaufland.net"),
                    ResolverFixtures.model,
                    ResolverFixtures.snapshot(),
                    Instant.parse("2026-07-08T00:00:00Z"),
                )
            val ambiguous = outcome.shouldBeInstanceOf<ResolutionOutcome.Ambiguous>()
            // sorted by rendered canonical text: `plan[...]` < `sales[...]` — deterministic, not iteration-order.
            ambiguous.alternatives.map { it.path.cubelet } shouldBe listOf("plan", "sales")
        }
    })
