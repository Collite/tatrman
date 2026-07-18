// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * S2-C1 — canonical rendering (§3) and inferred shape (R15/D10): 0 free = scalar, 1 = vector,
 * n = sub-cubelet.
 *
 * Coder note: the task's example `sales.2025.month.*.net → vector[time.month]` is, under R10
 * (unmentioned grain dimension → free), actually a **sub-cubelet** — Customer is unmentioned and so
 * also free. The vector case is shown here with Customer pinned; the sub-cubelet case frees both.
 */
class CanonicalFormSpec :
    StringSpec({
        val model = ResolverFixtures.model
        val resolver = DefaultMdPathResolver()

        fun resolve(input: String) =
            resolver.resolve(PathText.parse(input), model, ResolverFixtures.snapshot(), Instant.EPOCH)
                as ResolutionOutcome.Resolved

        "canonical text renders cubelet, grain-ordered coords, measure, agg" {
            CanonicalRenderer.render(resolve("sales.Kaufland.2025.net.sum").path) shouldBe
                "sales[customer.name: \"Kaufland\", time.year: 2025].net @ sum"
        }

        "all coordinates pinned ⇒ scalar (0 free)" {
            resolve("sales.Kaufland.2025.net").shape.freeDims shouldBe emptyList()
        }

        "one free dimension ⇒ vector" {
            resolve("sales.Kaufland.month.*.net").shape.freeDims shouldBe listOf("Time.month")
        }

        "two free dimensions ⇒ sub-cubelet" {
            resolve("sales.name.*.month.*.net").shape.freeDims shouldContainExactlyInAnyOrder
                listOf("Customer.name", "Time.month")
        }

        "a quoted member is always quoted in the canonical output" {
            CanonicalRenderer.render(resolve("""sales."Kaufland K123".2025.net""").path) shouldBe
                "sales[customer.name: \"Kaufland K123\", time.year: 2025].net @ sum"
        }

        "a star renders as *" {
            CanonicalRenderer.render(resolve("plan.Kaufland").path) shouldBe
                "plan[customer.name: \"Kaufland\", time.month: *].net @ sum"
        }
    })
