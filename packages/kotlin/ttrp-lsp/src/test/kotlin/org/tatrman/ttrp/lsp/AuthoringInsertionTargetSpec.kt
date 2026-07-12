// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.eclipse.lsp4j.Position
import org.tatrman.ttrp.lsp.protocol.AuthoringContextParams
import org.tatrman.ttrp.lsp.test.TtrpLspHarness

/**
 * T7.2.3: cursor-scoped dialect insertion (C4-d-i γ) — the assist inserts in the dialect of the
 * container the cursor is pointed at (host-declared via the position, no heuristics, P2), or `ttrp`
 * at program scope. Plus T7.2.2: the diagnostics catalogue carries the `area` grouping, and the
 * TTR-B roster (Stage 7.1) is surfaced in the grammar summary.
 */
class AuthoringInsertionTargetSpec :
    StringSpec({
        "insertionTarget inside the acc_prep \"\"\"sql container → dialect sql" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                val text = Fixtures.text("hero.ttrp")
                h.open(uri, text)
                val cursor = Fixtures.positionOf(text, "branch_code, region")
                val bundle =
                    h.remote
                        .authoringContext(AuthoringContextParams(uri, Position(cursor.line, cursor.character)))
                        .get()
                        .bundle
                val target = bundle.getAsJsonObject("scope").getAsJsonObject("insertionTarget")
                target.get("dialect").asString shouldBe "sql"
                target.get("containerName").asString shouldBe "acc_prep"
                target.get("targetEngine").asString shouldBe "erp_pg"
            }
        }

        "insertionTarget inside the canonical crunch container → dialect ttrp" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                val text = Fixtures.text("hero.ttrp")
                h.open(uri, text)
                val cursor = Fixtures.positionOf(text, "sums = j")
                val bundle =
                    h.remote
                        .authoringContext(AuthoringContextParams(uri, Position(cursor.line, cursor.character)))
                        .get()
                        .bundle
                val target = bundle.getAsJsonObject("scope").getAsJsonObject("insertionTarget")
                target.get("dialect").asString shouldBe "ttrp"
                target.get("containerName").asString shouldBe "crunch"
                target.get("targetEngine").asString shouldBe "polars"
            }
        }

        "program scope (cursor outside any container) → dialect ttrp, no containerName" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                val text = Fixtures.text("hero.ttrp")
                h.open(uri, text)
                val bundle =
                    h.remote
                        .authoringContext(AuthoringContextParams(uri, Position(0, 0)))
                        .get()
                        .bundle
                val target = bundle.getAsJsonObject("scope").getAsJsonObject("insertionTarget")
                target.get("dialect").asString shouldBe "ttrp"
                target.has("containerName") shouldBe false
            }
        }

        "the diagnostics catalogue carries the area segment (T7.2.2)" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val bundle =
                    h.remote
                        .authoringContext(AuthoringContextParams(null, null))
                        .get()
                        .bundle
                val b004 =
                    bundle
                        .getAsJsonArray("diagnostics")
                        .map { it.asJsonObject }
                        .single { it.get("id").asString == "TTRP-B-004" }
                b004.get("area").asString shouldBe "B"
            }
        }

        "the TTR-B dialect roster is surfaced in the grammar summary (T7.2.1)" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val bundle =
                    h.remote
                        .authoringContext(AuthoringContextParams(null, null))
                        .get()
                        .bundle
                bundle
                    .getAsJsonObject("grammar")
                    .getAsJsonObject("dialectRosters")
                    .getAsJsonArray("ttrb")
                    .size() shouldBeGreaterThan 0
            }
        }
    })
