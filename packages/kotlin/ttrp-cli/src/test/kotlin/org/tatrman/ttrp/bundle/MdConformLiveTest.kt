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
 * Covers both binding shapes, both journaling modes, and both viaCalc drill forms — all as scalar
 * reads (full grain pinned, or a coarser-grain coordinate collapsed by `.sum`):
 *  - `plan.name.Kaufland.month.6.net` — LONG shape + INVALIDATE (is_current read view) + NET code → 150.00
 *  - `sales.name.Kaufland.day."2025-06-20".net` — WIDE shape + OVERWRITE → 85.00
 *  - `sales.name.Kaufland.year.2025.net.sum` — coarser-grain Time.year via a **derived inline** calc
 *    (`date_to_year` → `EXTRACT(YEAR FROM sale_date)`) → 885.00
 *  - `sales.name.Kaufland.month.6.net.sum` — coarser-grain Time.month via a **derived case-table** calc
 *    (`date_to_month` → JOIN d_calendar ON cal_month) → 585.00
 *
 * The last two exercise the S4-A5 viaCalc lowerings driven from an *authored* coarser-than-grain path
 * (the lowering derives the calc + the emitter is threaded the MdModel — both fixed alongside).
 *
 * **PG↔Polars parity (S4-B4).** The same program is built on **two placements** and both must compute
 * the same goldens:
 *  - **`authored`** — `q`@erp_pg: the MD reads lower inline as scalar subqueries in the PG island.
 *  - **`q`@polars** — the [org.tatrman.ttrp.graph.movement.MdReadHoist] stratum moves each MD read into
 *    its own db island (`mdsrc~…`), stages the 1-row `::float8` result, and the Polars island reads it
 *    as `pl.lit(mdstage.item(...))`. Since a Polars island cannot touch Postgres (F-c), this hoist is
 *    the only way MD reads run under a Polars placement.
 * Identical values across placements is the parity claim; the Polars variant still needs
 * `TTR_CONN_ERP_PG` (its md-source island + transfer run on erp_pg).
 *
 * Gated by `TTRP_CONFORM_PG=1` (needs PG seeded from `md_seed.sql` via `TTR_CONN_ERP_PG`, plus
 * `polars` + `adbc-driver-postgresql` + `pyarrow` on PATH). Skips visibly otherwise — the offline
 * `MdBundleTest` is the standing regression gate. Vector/hop read conformance is a follow-up.
 */
class MdConformLiveTest :
    FunSpec({
        val enabled = System.getenv("TTRP_CONFORM_PG") == "1"

        // Golden values hand-computed in md_seed.sql (the "excluded" rows prove the read view discriminates).
        val goldens =
            listOf(
                Triple("plan_jun_net", "150.00", "plan.name.Kaufland.month.6.net (long/invalidate)"),
                Triple("sales_day_net", "85.00", "sales.name.Kaufland.day.2025-06-20.net (wide/overwrite)"),
                Triple("sales_2025_net", "885.00", "sales.name.Kaufland.year.2025.net.sum (inline EXTRACT viaCalc)"),
                Triple(
                    "sales_jun_net",
                    "585.00",
                    "sales.name.Kaufland.month.6.net.sum (d_calendar case-table viaCalc)",
                ),
            )

        // authored PG placement + the hoisted Polars placement — same goldens is the parity claim.
        for ((label, overrides) in listOf("authored" to emptyMap(), "q@polars" to mapOf("q" to "polars"))) {
            test("MD dot-path reads compute their goldens on live Postgres [$label]") {
                if (!enabled) {
                    System.err.println("SKIP: TTRP_CONFORM_PG != 1 — live MD read conform not run.")
                    return@test
                }
                val conn =
                    System.getenv("TTR_CONN_ERP_PG")
                        ?: error("TTRP_CONFORM_PG=1 but TTR_CONN_ERP_PG is unset")
                // T-P1: seed the fixture tables to their golden state FIRST — the read conform must not
                // depend on a prior write suite (e.g. MdWriteRoundTripTest) having left them untouched.
                seedMdFixture(conn)
                val projectRoot = Paths.get("src/test/resources/fixtures/md-project")
                val source = Files.readString(projectRoot.resolve("md-conform.ttrp"))
                val outDir = Files.createTempDirectory("ttrp-md-conform-$label")

                // BundleAssembler.provisionLocalFiles copies files/probe.csv (the 1-row calc input) into the
                // bundle, so no manual CSV staging is needed here (unlike the hero's out-of-band copy).
                val bundle =
                    BundleAssembler("1.0.0").build(
                        source = source,
                        fileName = "md-conform.ttrp",
                        pipelineManifest = TtrpManifest(world = "acme.worlds.dev", manifestDir = projectRoot),
                        modelsRoot = projectRoot.resolve("models"),
                        outDir = outDir,
                        targetOverrides = overrides,
                    )

                val run = BundleInvoker(mapOf("TTR_CONN_ERP_PG" to conn)).invoke(bundle.dir)
                withClue("run.sh exit=${run.exitCode}\n${run.output}") { run.exitCode shouldBe 0 }

                val arrow = run.displays["md_val"] ?: error("no md_val display in ${run.displays.keys}")
                val table = ArrowIo.readTable(arrow)
                table.rows.size shouldBe 1

                fun value(col: String): BigDecimal {
                    val idx = table.columns.indexOfFirst { it.name == col }
                    require(idx >= 0) { "no column '$col' in ${table.columns.map { it.name }}" }
                    val cell = table.rows[0][idx]
                    // T-P1: a NULL cell (e.g. a mis-seeded/invalidated fixture) surfaces as a readable value
                    // mismatch, not an opaque NumberFormatException thrown outside the clue.
                    return cell?.toString()?.toBigDecimalOrNull()
                        ?: throw AssertionError("column '$col' is NULL/non-numeric ($cell) — fixture not seeded?")
                }

                for ((col, golden, clue) in goldens) {
                    withClue("[$label] $clue") { value(col).compareTo(BigDecimal(golden)) shouldBe 0 }
                }
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

/** T-P1: reset the MD fixture tables (f_sales / f_plan / d_calendar) to their golden state via JDBC. */
private fun seedMdFixture(conn: String) {
    val uri = java.net.URI(conn) // postgresql://user:pass@host:port/db
    val userInfo = uri.userInfo?.split(":") ?: emptyList()
    val jdbcUrl = "jdbc:postgresql://${uri.host}:${if (uri.port > 0) uri.port else 5432}${uri.path}"
    java.sql.DriverManager
        .getConnection(jdbcUrl, userInfo.getOrElse(0) { "postgres" }, userInfo.getOrElse(1) { "" })
        .use { c ->
            c.autoCommit = true
            c.createStatement().use { it.execute(mdSeedSql()) }
        }
}

/** Locate `ttrp-conform`'s `md_seed.sql` by walking up from the working dir (the canonical fixture seed). */
private fun mdSeedSql(): String {
    var dir: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        val f = java.io.File(dir, "packages/kotlin/ttrp-conform/src/test/resources/seed/md_seed.sql")
        if (f.isFile) return f.readText()
        dir = dir.parentFile
    }
    error("md_seed.sql not found from ${System.getProperty("user.dir")}")
}
