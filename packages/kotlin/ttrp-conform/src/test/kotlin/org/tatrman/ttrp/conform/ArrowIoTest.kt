// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.conform

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.nio.file.Paths

/** T3.4.1 — Arrow IPC reading against committed pyarrow-generated fixtures. */
class ArrowIoTest :
    FunSpec({
        fun fixture(name: String) = ArrowIo.readTable(Paths.get("src/test/resources/arrow", name))

        test("reads schema + rows of a 2-column file, with nulls") {
            val t = fixture("twocol.arrow")
            t.columns.map { it.name } shouldBe listOf("id", "name")
            t.columns.map { it.type } shouldBe listOf("int64", "utf8")
            t.rows.size shouldBe 4
            t.rows[0] shouldBe listOf(1L, "a")
            t.rows[2] shouldBe listOf(3L, null)
            t.rows[3] shouldBe listOf(null, "d")
        }

        test("reads decimal + timestamp[us, UTC] columns") {
            val t = fixture("typed.arrow")
            t.columns.map { it.type } shouldBe listOf("utf8", "decimal(19,2)", "timestamp(microsecond,UTC)")
            (t.rows[0][1] as BigDecimal).compareTo(BigDecimal("10.50")) shouldBe 0
            t.rows[2][1] shouldBe null
            // epoch micros for 2026-01-01T00:00:00Z
            t.rows[1][2] shouldBe 1767225600000000L
        }
    })
