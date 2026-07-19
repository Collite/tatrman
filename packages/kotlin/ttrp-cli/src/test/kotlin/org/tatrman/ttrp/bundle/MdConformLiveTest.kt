// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.conform.ArrowIo
import org.tatrman.ttrp.conform.BundleInvoker
import org.tatrman.ttrp.project.TtrpManifest
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths

/**
 * S4-B — MD dot-path **read** end-to-end conformance on a live Postgres. `md-conform.ttrp` surfaces
 * resolved MD reads as displayed columns; the compiled bundle runs on PG (adbc island) and the
 * emitted values must equal the hand-computed goldens seeded by `ttrp-conform`'s `md_seed.sql`. This
 * is the value-golden counterpart of `MdBundleTest` (which asserts the lowered SQL text): it proves
 * the whole production path — MdModel/MdBindings loaded from the models root, the read resolved
 * disconnected (R13) and lowered — computes the right answer on a real engine.
 *
 * Covers both binding shapes and journaling modes with the full grain pinned (scalar reads):
 *  - `plan.name.Kaufland.month.6.net` — LONG shape + INVALIDATE (is_current read view) + NET code → 150.00
 *  - `sales.name.Kaufland.day."2025-06-20".net` — WIDE shape + OVERWRITE → 85.00
 *
 * Gated by `TTRP_CONFORM_PG=1` (needs PG seeded from `md_seed.sql` via `TTR_CONN_ERP_PG`, plus
 * `polars` + `adbc-driver-postgresql` + `pyarrow` on PATH). Skips visibly otherwise — the offline
 * `MdBundleTest` is the standing regression gate. Vector/hop/viaCalc read conformance and PG↔Polars
 * parity are follow-ups (see S4-B coder notes).
 */
class MdConformLiveTest :
    FunSpec({
        val enabled = System.getenv("TTRP_CONFORM_PG") == "1"

        test("MD dot-path reads compute their goldens on live Postgres") {
            if (!enabled) {
                System.err.println("SKIP: TTRP_CONFORM_PG != 1 — live MD read conform not run.")
                return@test
            }
            val conn =
                System.getenv("TTR_CONN_ERP_PG")
                    ?: error("TTRP_CONFORM_PG=1 but TTR_CONN_ERP_PG is unset")
            val projectRoot = Paths.get("src/test/resources/fixtures/md-project")
            val source = Files.readString(projectRoot.resolve("md-conform.ttrp"))
            val outDir = Files.createTempDirectory("ttrp-md-conform")

            // BundleAssembler.provisionLocalFiles copies files/probe.csv (the 1-row calc input) into the
            // bundle, so no manual CSV staging is needed here (unlike the hero's out-of-band copy).
            val bundle =
                BundleAssembler("1.0.0").build(
                    source = source,
                    fileName = "md-conform.ttrp",
                    pipelineManifest = TtrpManifest(world = "acme.worlds.dev", manifestDir = projectRoot),
                    modelsRoot = projectRoot.resolve("models"),
                    outDir = outDir,
                )

            val run = BundleInvoker(mapOf("TTR_CONN_ERP_PG" to conn)).invoke(bundle.dir)
            withClue("run.sh exit=${run.exitCode}\n${run.output}") { run.exitCode shouldBe 0 }

            val arrow = run.displays["md_val"] ?: error("no md_val display in ${run.displays.keys}")
            val table = ArrowIo.readTable(arrow)
            table.rows.size shouldBe 1

            fun value(col: String): BigDecimal {
                val idx = table.columns.indexOfFirst { it.name == col }
                require(idx >= 0) { "no column '$col' in ${table.columns.map { it.name }}" }
                return BigDecimal(table.rows[0][idx].toString())
            }

            // Values hand-computed in md_seed.sql (the "excluded" rows prove the read view discriminates).
            withClue("plan.name.Kaufland.month.6.net") {
                value("plan_jun_net").compareTo(BigDecimal("150.00")) shouldBe
                    0
            }
            withClue("sales.name.Kaufland.day.2025-06-20.net") {
                value("sales_day_net").compareTo(BigDecimal("85.00")) shouldBe 0
            }
        }
    })

private fun withClue(
    clue: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("$clue\n---\n${e.message}", e)
    }
}
