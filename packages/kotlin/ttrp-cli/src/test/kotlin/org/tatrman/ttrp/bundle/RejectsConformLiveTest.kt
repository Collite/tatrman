// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.conform.BundleInvoker
import org.tatrman.ttrp.conform.ConformRunner
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * RJ-P5 5.2.4/5.2.6 — the **live two-engine reject seal**. The RH-1 `returns_ingest` program built in
 * two placement variants — `authored` (Polars island) and `pg` (whole container retargeted to
 * `erp_pg`, an adbc island) — must conform on a rejects-exercising CSV: the seven per-stream points
 * green for every out/staging stream (incl. the `rejects` and `bad` staging streams as multisets),
 * **and** the eighth (partition) point balancing `in == processed + rejects` identically on both
 * engines. A companion canary proves the eighth point turns red for a deliberately-broken producer.
 *
 * Gated by `TTRP_CONFORM_PG=1` + `TTR_CONN_ERP_PG` (needs `polars`, `adbc-driver-postgresql`,
 * `pyarrow` on PATH). Skips visibly otherwise; the offline emit/conform suites are the standing gate.
 */
class RejectsConformLiveTest :
    FunSpec({
        val enabled = System.getenv("TTRP_CONFORM_PG") == "1"
        val src = Files.readString(Paths.get("src/test/resources/fixtures/rejects-polars.ttrp"))
        val csv = Paths.get("src/test/resources/fixtures/sales_2026_rejects.csv")

        fun buildVariants(outDir: Path): Map<String, Path> {
            val pm = TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot())
            val models = MetadataFixtures.erpModelsRoot()
            val asm = BundleAssembler("1.0.0")
            val variants =
                linkedMapOf(
                    "authored" to asm.build(src, "returns_ingest.ttrp", pm, models, outDir.resolve("authored")).dir,
                    "pg" to
                        asm
                            .build(
                                src,
                                "returns_ingest.ttrp",
                                pm,
                                models,
                                outDir.resolve("pg"),
                                mapOf("returns_ingest" to "erp_pg"),
                            ).dir,
                )
            variants.values.forEach { b ->
                val files = Files.createDirectories(b.resolve("files"))
                Files.copy(csv, files.resolve("sales_2026.csv"), StandardCopyOption.REPLACE_EXISTING)
            }
            return variants
        }

        test("returns_ingest conforms PG↔Polars incl. rejects streams + eighth point (live)") {
            if (!enabled) {
                System.err.println("SKIP: TTRP_CONFORM_PG != 1 — live reject seal not run.")
                return@test
            }
            val conn = System.getenv("TTR_CONN_ERP_PG") ?: error("TTRP_CONFORM_PG=1 but TTR_CONN_ERP_PG unset")
            val variants = buildVariants(Files.createTempDirectory("ttrp-reject-seal"))
            val outcome = ConformRunner(BundleInvoker(mapOf("TTR_CONN_ERP_PG" to conn))).run(variants)
            withClue(outcome.summary()) {
                outcome.exitCode shouldBe 0
                outcome.partition?.pass shouldBe true
            }
        }

        test("canary — a broken PG rejects producer (rejects=0) turns the eighth point red (live)") {
            if (!enabled) {
                System.err.println("SKIP: TTRP_CONFORM_PG != 1 — live canary not run.")
                return@test
            }
            val conn = System.getenv("TTR_CONN_ERP_PG") ?: error("TTRP_CONFORM_PG=1 but TTR_CONN_ERP_PG unset")
            val variants = buildVariants(Files.createTempDirectory("ttrp-reject-canary"))
            // Break the PG producer: the reject false-branch WHERE now selects nothing, so both the
            // rejects stream and its count go to zero while `in`/`processed` stand — imbalance (5.1.5).
            val island = variants.getValue("pg").resolve("islands/returns_ingest.py")
            val patched =
                Files.readString(island).replace("WHERE NOT COALESCE(\"_ttrp_v1\", FALSE)", "WHERE FALSE")
            Files.writeString(island, patched)
            val outcome = ConformRunner(BundleInvoker(mapOf("TTR_CONN_ERP_PG" to conn))).run(variants)
            withClue(outcome.summary()) { outcome.exitCode shouldNotBe 0 }
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
