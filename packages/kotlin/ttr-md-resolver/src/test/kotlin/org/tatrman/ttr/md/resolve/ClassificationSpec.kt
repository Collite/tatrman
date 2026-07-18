// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.semantics.md.AggKind

/**
 * S2-A3 — token classification (R5). Order defines *candidacy, not priority*: a component yields the
 * set of every slot it could fill, and a name that is both a measure and a member yields both. INT
 * components are member-candidates of numeric/temporal domains only (R5f).
 */
class ClassificationSpec :
    StringSpec({
        val model = ResolverFixtures.model
        val snap = ResolverFixtures.snapshot()

        fun classify(c: PathComponent) = TokenClassifier.classify(c, model, snap)

        "an agg name classifies as an aggregation slot" {
            classify(PathComponent.Ident("sum")) shouldContain SlotCandidate.Agg(AggKind.SUM)
        }

        "a cubelet name classifies as a cubelet slot" {
            classify(PathComponent.Ident("sales")) shouldContain SlotCandidate.Cubelet("sales")
        }

        "a measure name classifies as a measure slot" {
            classify(PathComponent.Ident("net")) shouldContain SlotCandidate.Measure("net")
        }

        "an attribute name classifies as an attribute slot" {
            classify(PathComponent.Ident("region")) shouldContain SlotCandidate.Attribute("Customer", "region")
        }

        "a relative-time token classifies as a calc slot" {
            classify(PathComponent.Ident("lastMonth")) shouldContain SlotCandidate.Calc("lastMonth")
        }

        "a string member classifies as a member of its domain's attribute" {
            classify(PathComponent.Ident("Kaufland")) shouldContain
                SlotCandidate.Member("Kaufland", "Name", "Customer", "name")
        }

        "a quoted member classifies as a member only" {
            classify(PathComponent.Quoted("Kaufland K123")) shouldContainExactlyInAnyOrder
                setOf(SlotCandidate.Member("Kaufland K123", "Name", "Customer", "name"))
        }

        "an INT member binds to a numeric/temporal domain, never a string one" {
            // '2025' is seeded in both the string Name domain and the int Year domain; only Year is legal (R5f).
            val snapWith2025InName = ResolverFixtures.snapshot(mapOf("Name" to listOf("Kaufland", "2025")))
            val cands = TokenClassifier.classify(PathComponent.IntLit("2025"), model, snapWith2025InName)
            cands shouldContain SlotCandidate.Member("2025", "Year", "Time", "year")
            cands.none { it is SlotCandidate.Member && it.domain == "Name" } shouldBe true
        }

        "a name that is both a measure and a member yields both candidates" {
            val snapWithNetMember = ResolverFixtures.snapshot(mapOf("Name" to listOf("Kaufland", "net")))
            val cands = TokenClassifier.classify(PathComponent.Ident("net"), model, snapWithNetMember)
            cands shouldContain SlotCandidate.Measure("net")
            cands shouldContain SlotCandidate.Member("net", "Name", "Customer", "name")
        }

        "an unknown token has zero candidates (the resolver maps this to MD-001)" {
            classify(PathComponent.Ident("zzzznotathing")) shouldBe emptySet()
        }
    })
