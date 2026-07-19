// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * S2-A4 — qualified pairs (R6) and star/set/range binding (R7). Pairs bind atomically and are
 * order-free within the path; a selector must reach an attribute, else `TTRP-MD-004`.
 */
class QualifiedPairSpec :
    StringSpec({
        val model = ResolverFixtures.model
        val snap = ResolverFixtures.snapshot()

        fun bind(vararg comps: PathComponent) = PairBinder.bind(comps.toList(), model, snap)

        // ---- R6: qualified pairs -------------------------------------------------------------

        "dim.member binds the member to the dimension's attribute" {
            bind(PathComponent.Ident("customer"), PathComponent.Ident("Kaufland")).coordinates shouldContain
                Coordinate("Customer", "Customer.name", Selector.Pinned(MemberRef("Kaufland")))
        }

        "attr.star binds a free coordinate on that attribute" {
            bind(PathComponent.Ident("month"), PathComponent.Star).coordinates shouldContain
                Coordinate("Time", "Time.month", Selector.Star)
        }

        "attr.set binds the set to that attribute" {
            val set = PathComponent.SetLit(listOf(PathComponent.Ident("Kaufland"), PathComponent.Ident("Lidl")))
            bind(PathComponent.Ident("name"), set).coordinates shouldContain
                Coordinate(
                    "Customer",
                    "Customer.name",
                    Selector.MemberSet(listOf(MemberRef("Kaufland"), MemberRef("Lidl"))),
                )
        }

        "a pair is order-free within the path — the leftover token stays free" {
            val r = bind(PathComponent.Ident("net"), PathComponent.Ident("customer"), PathComponent.Ident("Kaufland"))
            r.coordinates shouldContain Coordinate("Customer", "Customer.name", Selector.Pinned(MemberRef("Kaufland")))
            r.free shouldContain PathComponent.Ident("net")
        }

        "a pair whose first part is neither attribute nor dimension falls back to plain components" {
            // `net` is a measure, not a qualifier ⇒ no pair; both components go to the free search.
            val r = bind(PathComponent.Ident("net"), PathComponent.Ident("Kaufland"))
            r.coordinates shouldBe emptyList()
            r.free shouldContainExactlyInAnyOrder listOf(PathComponent.Ident("net"), PathComponent.Ident("Kaufland"))
        }

        // ---- R7: star / set / range binding --------------------------------------------------

        "an unqualified set binds via its members' unique common attribute" {
            val set = PathComponent.SetLit(listOf(PathComponent.Ident("Kaufland"), PathComponent.Ident("Lidl")))
            bind(set).coordinates shouldContain
                Coordinate(
                    "Customer",
                    "Customer.name",
                    Selector.MemberSet(listOf(MemberRef("Kaufland"), MemberRef("Lidl"))),
                )
        }

        "a mixed-attribute set is unbindable (MD-004)" {
            val set = PathComponent.SetLit(listOf(PathComponent.Ident("Kaufland"), PathComponent.IntLit("2025")))
            bind(set).diagnostics.map { it.id } shouldContain MdDiagId.UNBINDABLE_SELECTOR
        }

        "a bare star is unbindable (MD-004)" {
            bind(PathComponent.Star).diagnostics.map { it.id } shouldContain MdDiagId.UNBINDABLE_SELECTOR
        }

        "a range over an ordered numeric domain binds" {
            bind(
                PathComponent.RangeLit(PathComponent.IntLit("2024"), PathComponent.IntLit("2026")),
            ).coordinates shouldContain
                Coordinate("Time", "Time.year", Selector.Range(MemberRef("2024"), MemberRef("2026")))
        }

        "a range over an unordered (string) domain is unbindable (MD-004)" {
            val range = PathComponent.RangeLit(PathComponent.Ident("Kaufland"), PathComponent.Ident("Lidl"))
            bind(range).diagnostics.map { it.id } shouldContain MdDiagId.UNBINDABLE_SELECTOR
        }
    })
