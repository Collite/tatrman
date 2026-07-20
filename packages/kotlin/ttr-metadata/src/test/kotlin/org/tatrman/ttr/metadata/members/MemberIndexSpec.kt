// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/** S6-A2 — [InternedMemberIndex] contract: exact case-sensitive membership, sorted+truncated lookup, interning. */
class MemberIndexSpec :
    StringSpec({
        "contains is exact and case-sensitive — members are data" {
            val idx = InternedMemberIndex.of(listOf("Kaufland", "Lidl", "Aldi"))
            idx.contains("Kaufland") shouldBe true
            idx.contains("kaufland") shouldBe false // case-sensitive: not folded
            idx.contains("Kauf") shouldBe false // exact, not a prefix match
            idx.contains("Metro") shouldBe false
        }

        "lookup returns prefix matches, sorted and truncated to the limit" {
            val idx = InternedMemberIndex.of(listOf("Alpha", "Alps", "Alpine", "Beta", "Al"))
            idx.lookup("Al", 10) shouldBe listOf("Al", "Alpha", "Alpine", "Alps") // sorted
            idx.lookup("Al", 2) shouldBe listOf("Al", "Alpha") // truncated to limit
            idx.lookup("Be", 10) shouldBe listOf("Beta")
            idx.lookup("Zz", 10) shouldBe emptyList() // no matches
            idx.lookup("Al", 0) shouldBe emptyList() // non-positive limit
        }

        "count is the de-duplicated member cardinality" {
            InternedMemberIndex.of(listOf("A", "B", "B", "C", "A")).count shouldBe 3L
        }

        "members are interned — indexes built through one interner share string identity" {
            // Force distinct-but-equal instances (string literals are already JVM-pooled, which would
            // make this pass trivially): build "Kaufland" at runtime twice.
            val k1 =
                buildString {
                    append("Kauf")
                    append("land")
                }
            val k2 =
                buildString {
                    append("Kauf")
                    append("land")
                }
            k1 shouldNotBeSameInstanceAs k2 // sanity: the inputs really are two objects

            val interner = MemberInterner()
            val a = InternedMemberIndex.of(listOf(k1, "Lidl"), interner)
            val b = InternedMemberIndex.of(listOf(k2, "Metro"), interner)
            a.lookup("Kaufland", 1).single() shouldBeSameInstanceAs b.lookup("Kaufland", 1).single()
        }
    })
