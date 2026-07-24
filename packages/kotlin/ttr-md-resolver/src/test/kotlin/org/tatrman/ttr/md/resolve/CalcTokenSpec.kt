// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * S2-C2 — evaluation-relative calc tokens (R12, subset). `lastMonth` anchors on `asof` (D17), lowers
 * to a computed coordinate over `Time.month` with `viaCalc` set, and works with or without a member
 * snapshot (the value comes from `asof`, not a time-dim table). Changing `asof` changes the result.
 */
class CalcTokenSpec :
    StringSpec({
        val model = ResolverFixtures.model
        val resolver = DefaultMdPathResolver()

        fun resolve(
            input: String,
            asof: String,
            members: MemberSnapshot? = ResolverFixtures.snapshot(),
        ) = resolver.resolve(PathText.parse(input), model, members, Instant.parse(asof))

        fun coordFor(
            outcome: ResolutionOutcome,
            attr: String,
        ): Coordinate? =
            (outcome as? ResolutionOutcome.Resolved)?.path?.coordinates?.firstOrNull { it.attribute == attr }

        fun monthCoord(outcome: ResolutionOutcome): Coordinate? = coordFor(outcome, "Time.month")

        "lastMonth anchors on asof and lowers to a viaCalc month coordinate" {
            val coord = monthCoord(resolve("sales.lastMonth.net", "2026-07-08T00:00:00Z"))
            coord.shouldNotBeNull()
            coord.selector shouldBe Selector.Pinned(MemberRef("6")) // July − 1 = June
            coord.viaCalc shouldBe "monthOfDate"
        }

        "lastMonth also pins the anchor year (T-R1-1) — one year's June, not June of every year" {
            val year = coordFor(resolve("sales.lastMonth.net", "2026-07-08T00:00:00Z"), "Time.year")
            year.shouldNotBeNull()
            year.selector shouldBe Selector.Pinned(MemberRef("2026"))
            year.viaCalc shouldBe "yearOfDate"
        }

        "lastMonth at a January asof rolls the anchor year back (T-R1-1)" {
            val outcome = resolve("sales.lastMonth.net", "2026-01-15T00:00:00Z")
            monthCoord(outcome)?.selector shouldBe Selector.Pinned(MemberRef("12")) // Jan − 1 = Dec
            coordFor(outcome, "Time.year")?.selector shouldBe Selector.Pinned(MemberRef("2025")) // …of the prior year
        }

        "lastQuarter pins both the quarter and its anchor year (T-R1-1)" {
            val outcome = resolve("sales.lastQuarter.net", "2026-01-15T00:00:00Z")
            coordFor(outcome, "Time.quarter")?.selector shouldBe Selector.Pinned(MemberRef("4")) // Oct 2025 ⇒ Q4
            coordFor(outcome, "Time.year")?.selector shouldBe Selector.Pinned(MemberRef("2025"))
        }

        "changing asof changes the resolution (D17)" {
            monthCoord(resolve("sales.lastMonth.net", "2026-06-15T00:00:00Z"))?.selector shouldBe
                Selector.Pinned(MemberRef("5")) // June − 1 = May
        }

        "a calc token resolves identically with no member snapshot (no time-dim table needed)" {
            monthCoord(resolve("sales.lastMonth.net", "2026-07-08T00:00:00Z", members = null))?.selector shouldBe
                Selector.Pinned(MemberRef("6"))
        }

        "thisYear lowers onto the year attribute" {
            val outcome = resolve("sales.thisYear.net", "2026-07-08T00:00:00Z")
            val coord =
                (outcome as? ResolutionOutcome.Resolved)?.path?.coordinates?.firstOrNull {
                    it.attribute ==
                        "Time.year"
                }
            coord.shouldNotBeNull()
            coord.selector shouldBe Selector.Pinned(MemberRef("2026"))
        }
    })
