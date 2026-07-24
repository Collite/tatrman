// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.semantics.md.AggKind
import org.tatrman.ttr.semantics.md.MdCubelet
import org.tatrman.ttr.semantics.md.MdMeasure
import java.time.Instant

/**
 * Regression coverage for the resolver-side findings of arc review-071 (T-S1…T-S5). Each block pins
 * the corrected behaviour so the specific footgun the review found cannot come back.
 */
class ReviewFixes071Spec :
    StringSpec({
        val model = ResolverFixtures.model
        val resolver = DefaultMdPathResolver()
        val asof = Instant.parse("2026-07-08T00:00:00Z")

        fun resolve(
            input: String,
            m: org.tatrman.ttr.semantics.md.MdModel = model,
            members: MemberSnapshot? = ResolverFixtures.snapshot(),
            strict: Boolean = false,
        ) = resolver.resolve(PathText.parse(input), m, members, asof, strict = strict)

        // ---- T-S1: a nonAdditive measure must not be blind-summed (R10a) -----------------------

        // The shared fixture has no nonAdditive measure (which is why the review found this uncaught),
        // so add one: a `balance` measure (no declared aggregation) on a `stock` cubelet.
        val nonAdditive =
            model.copy(
                measures = model.measures + ("balance" to MdMeasure("balance", "md.Money", "nonAdditive", null, null)),
                cubelets =
                    model.cubelets +
                        ("stock" to MdCubelet("stock", listOf("Customer.name", "Time.day"), listOf("balance"))),
            )

        "an agg-less path on a nonAdditive measure fails, not silently sums (T-S1)" {
            val outcome = resolve("stock.Kaufland.balance", nonAdditive)
            val failed = outcome.shouldBeInstanceOf<ResolutionOutcome.Failed>()
            failed.diagnostics.map { it.code } shouldContain "TTRP-MD-002"
            failed.diagnostics.first { it.code == "TTRP-MD-002" }.detail shouldContain "non-additive"
        }

        "an explicit aggregation on a nonAdditive measure resolves (T-S1)" {
            val resolved =
                resolve(
                    "stock.Kaufland.balance.max",
                    nonAdditive,
                ).shouldBeInstanceOf<ResolutionOutcome.Resolved>()
            resolved.path.agg shouldBe AggKind.MAX
        }

        "an additive measure still defaults to sum (T-S1 regression)" {
            resolve("sales.Kaufland.net").shouldBeInstanceOf<ResolutionOutcome.Resolved>().path.agg shouldBe AggKind.SUM
        }

        // ---- T-S2: a bare attribute token no longer silently vanishes --------------------------

        "a bare attribute with no selector fails rather than resolving at a wrong grain (T-S2)" {
            // `sales.month.net` — `month` carries no member; it must NOT drop and resolve at day grain.
            resolve("sales.month.net").shouldBeInstanceOf<ResolutionOutcome.Failed>()
        }

        "the same path with a member on the attribute resolves (T-S2 contrast)" {
            resolve("sales.month.6.net").shouldBeInstanceOf<ResolutionOutcome.Resolved>()
        }

        // ---- T-S3: repetition covers every selector kind, not just Pinned×Pinned ---------------

        "a pin and a `*` on the same attribute is a repetition (T-S3)" {
            val failed = resolve("sales.name.Kaufland.name.*.net").shouldBeInstanceOf<ResolutionOutcome.Failed>()
            failed.diagnostics.map { it.code } shouldContain "TTRP-MD-006"
        }

        "two different Time attributes remain a legal drill (T-S3 regression)" {
            resolve("sales.2025.6.net").shouldBeInstanceOf<ResolutionOutcome.Resolved>()
        }

        // ---- T-S4: a disconnected strict LHS no longer bypasses R19 via the deferred member -----

        "a strict LHS on a non-grain (hop) attribute is rejected even disconnected (T-S4)" {
            // plan grain is Customer.name; `region` is a hop off it — R19 forbids it on a strict LHS.
            resolve("plan.region.North.month.3.net", members = null, strict = true)
                .shouldBeInstanceOf<ResolutionOutcome.Failed>()
        }

        "a strict LHS on the grain attribute still resolves disconnected (T-S4 regression)" {
            resolve("plan.name.Kaufland.month.3.net", members = null, strict = true)
                .shouldBeInstanceOf<ResolutionOutcome.Resolved>()
        }

        // ---- T-S5: a 1:1 co-leaf hop is a legal coordinate (R8 "N:1 or 1:1") -------------------

        "a coordinate on a 1:1 co-leaf of the grain attribute resolves (T-S5)" {
            // sales grain is Customer.name; Customer.code is a 1:1 co-leaf (code↔name). `C042` binds to
            // Customer.code via the connected snapshot.
            val members = ResolverFixtures.snapshot(mapOf("Code" to listOf("C042")))
            val resolved =
                resolver
                    .resolve(PathText.parse("sales.C042.net"), model, members, asof)
                    .shouldBeInstanceOf<ResolutionOutcome.Resolved>()
            resolved.path.coordinates
                .firstOrNull { it.attribute == "Customer.code" }
                .shouldNotBeNull()
        }
    })
