// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.semantics.md.AggKind
import java.time.Instant

/**
 * S2-C4 — `PathContext` overlay (R20, §5, as a library function; statement wiring is S5). RHS tokens
 * win per slot; unmentioned dimensions/cubelet/measure/agg inherit from the LHS context; a RHS
 * `dim.*` un-pins an inherited coordinate (D-"* escape", the share-of-total case).
 */
class ContextOverlaySpec :
    StringSpec({
        val model = ResolverFixtures.model
        val resolver = DefaultMdPathResolver()

        // Context (a resolved LHS): sales[customer.name: "Kaufland", time.year: 2026].net @ sum
        val context =
            PathContext(
                CanonicalPath(
                    cubelet = "sales",
                    coordinates =
                        listOf(
                            Coordinate("Customer", "Customer.name", Selector.Pinned(MemberRef("Kaufland"))),
                            Coordinate("Time", "Time.year", Selector.Pinned(MemberRef("2026"))),
                        ),
                    measure = "net",
                    agg = AggKind.SUM,
                ),
            )

        fun resolve(input: String) =
            resolver.resolve(PathText.parse(input), model, ResolverFixtures.snapshot(), Instant.EPOCH, context)
                as ResolutionOutcome.Resolved

        "RHS token overrides the same dimension; other slots inherit from context" {
            // input `sales.2025`: time overridden to 2025, customer + measure + agg inherited.
            CanonicalRenderer.render(resolve("sales.2025").path) shouldBe
                "sales[customer.name: \"Kaufland\", time.year: 2025].net @ sum"
        }

        "an omitted RHS inherits the whole context" {
            CanonicalRenderer.render(resolve("2025").path) shouldBe
                "sales[customer.name: \"Kaufland\", time.year: 2025].net @ sum"
        }

        "a RHS dim.* un-pins an inherited coordinate (share-of-total)" {
            // input `sales.2024.customer.*`: customer freed (not inherited Kaufland), year overridden.
            val resolved = resolve("sales.2024.customer.*")
            CanonicalRenderer.render(resolved.path) shouldBe
                "sales[customer.name: *, time.year: 2024].net @ sum"
            resolved.shape.freeDims shouldBe listOf("Customer.name")
        }

        "context inheritance is recorded in the explanation" {
            resolve("sales.2025").explanation.steps.any { it.via == "context" } shouldBe true
        }
    })
