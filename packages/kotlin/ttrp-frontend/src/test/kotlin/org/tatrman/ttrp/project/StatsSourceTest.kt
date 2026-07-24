// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.project

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** PL-P1.S3.T1 — the StatisticsSource serve/discard/absent behaviour (contracts §4). */
class StatsSourceTest :
    FunSpec({
        val entry =
            StatsEntry(
                qname = "shop.sales.db.dbo.ORDER_LINE",
                objectSchemaHash = "sha256:${"aa".repeat(32)}",
                observedAt = "2026-07-09T02:00:00Z",
                values = mapOf("rowCount" to 182000344.0, "byteSize" to 21474836480.0),
            )

        test("entry with matching objectSchemaHash is served to the optimizer") {
            val src = StatisticsSource(listOf(entry))
            val lookup = src.forObject("shop.sales.db.dbo.ORDER_LINE", entry.objectSchemaHash)
            lookup.served shouldBe true
            lookup.entry.shouldNotBeNull().values["rowCount"] shouldBe 182000344.0
            lookup.diagnostic.shouldBeNull()
            src.statsUsed() shouldBe listOf(entry)
        }

        test("mismatched hash → entry discarded, TTRP-STA-001, object degrades to static cost model") {
            val src = StatisticsSource(listOf(entry))
            val lookup = src.forObject("shop.sales.db.dbo.ORDER_LINE", "sha256:${"bb".repeat(32)}")
            lookup.served shouldBe false
            lookup.entry.shouldBeNull()
            lookup.diagnostic
                .shouldNotBeNull()
                .id.id shouldBe "TTRP-STA-001"
            src.statsUsed() shouldBe emptyList() // a discarded entry is not "used"
        }

        test("absent stats == stats-less compile (no error, no diagnostic)") {
            val src = StatisticsSource(emptyList())
            val lookup = src.forObject("shop.sales.db.dbo.NOPE", "sha256:${"cc".repeat(32)}")
            lookup.served shouldBe false
            lookup.entry.shouldBeNull()
            lookup.diagnostic.shouldBeNull()
        }
    })
