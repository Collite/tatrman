// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.conform

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.bundle.SiteCounts

/**
 * RJ-P5 5.1.1/5.1.5 — the eighth conform point ([PartitionCheck]). `in == processed + rejects` per
 * engine, and the triple agrees across engines; the dropped-reject / double-count failure classes
 * (the broken-producer canary) turn it red and name the site.
 */
class PartitionCheckTest :
    FunSpec({
        fun s(
            site: String,
            inC: Long,
            processed: Long,
            rejects: Long,
        ) = SiteCounts(site, inC, processed, rejects)

        test("no reject sites ⇒ n/a pass (rejects-free ecosystem unaffected)") {
            val r = PartitionCheck.check(mapOf("pg" to emptyList(), "polars" to emptyList()))
            r.point shouldBe 8
            r.pass.shouldBeTrue()
            r.detail shouldContain "n/a"
        }

        test("one engine, in == processed + rejects ⇒ pass") {
            PartitionCheck.check(mapOf("pg" to listOf(s("checked", 8, 4, 4)))).pass.shouldBeTrue()
        }

        test("two engines with identical balanced triples ⇒ pass") {
            val r =
                PartitionCheck.check(
                    mapOf(
                        "pg" to listOf(s("checked", 8, 4, 4)),
                        "polars" to listOf(s("checked", 8, 4, 4)),
                    ),
                )
            r.pass.shouldBeTrue()
            r.detail shouldContain "1 site(s) balance"
        }

        test("canary — a dropped reject (WHERE FALSE ⇒ rejects=0) breaks the per-engine balance") {
            val r = PartitionCheck.check(mapOf("pg" to listOf(s("checked", 8, 4, 0))))
            r.pass.shouldBeFalse()
            r.detail shouldContain "checked"
            r.detail shouldContain "in=8 != processed=4 + rejects=0"
        }

        test("a double-counted row (processed+rejects > in) also fails") {
            PartitionCheck.check(mapOf("pg" to listOf(s("checked", 8, 4, 5)))).pass.shouldBeFalse()
        }

        test("cross-engine mismatch — engines partition the same input differently ⇒ fail") {
            val r =
                PartitionCheck.check(
                    mapOf(
                        "pg" to listOf(s("checked", 8, 4, 4)),
                        "polars" to listOf(s("checked", 8, 5, 3)), // each balances, but the split differs
                    ),
                )
            r.pass.shouldBeFalse()
            r.detail shouldContain "differs across engines"
        }

        test("a site present in one engine but missing from another ⇒ fail") {
            val r =
                PartitionCheck.check(
                    mapOf(
                        "pg" to listOf(s("checked", 8, 4, 4)),
                        "polars" to emptyList(),
                    ),
                )
            r.pass.shouldBeFalse()
            r.detail shouldContain "missing"
        }

        // B3 (RJ-P5 review): the de-vacuum. A site the manifest DECLARES but that produced no counts on
        // ANY engine (RejectSites.of failed to resolve it symmetrically) must fail — not pass "n/a".
        test("declared reject site absent from every engine's counts ⇒ fail, never vacuous n/a") {
            val r =
                PartitionCheck.check(
                    byEngine = mapOf("pg" to emptyList(), "polars" to emptyList()),
                    declaredSites = setOf("checked"),
                )
            r.pass.shouldBeFalse()
            r.detail shouldContain "declared reject site 'checked' produced no counts"
        }

        test("declared site present and balanced on every engine ⇒ pass (reconciliation satisfied)") {
            val r =
                PartitionCheck.check(
                    byEngine =
                        mapOf(
                            "pg" to listOf(s("checked", 8, 4, 4)),
                            "polars" to listOf(s("checked", 8, 4, 4)),
                        ),
                    declaredSites = setOf("checked"),
                )
            r.pass.shouldBeTrue()
        }
    })
