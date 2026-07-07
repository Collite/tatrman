package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.conform.BundleInvoker
import org.tatrman.ttrp.conform.ConformRunner
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * S3.5 T3.5.6 — the A4 core: **one program, two engines, identical results.** The hero built in two
 * placement variants — `authored` (accounts@PG → Arrow → Polars crunch) and `crunch-pg` (whole
 * crunch retargeted to Postgres, an adbc island) — must produce identical `out/main_result.arrow`
 * under the Q9 seven-point comparison.
 *
 * Gated by `TTRP_CONFORM_PG=1` (needs Postgres seeded from `ttrp-conform`'s `hero_seed.sql` via
 * `TTR_CONN_ERP_PG`, plus `polars` + `adbc-driver-postgresql` + `pyarrow` on PATH — the executor
 * manifest's package list). Skips with a visible reason otherwise; the offline bundle/emit suites
 * are the standing regression gate.
 */
class HeroConformLiveTest :
    FunSpec({
        val enabled = System.getenv("TTRP_CONFORM_PG") == "1"

        test("hero placement variants produce identical results (live PG)") {
            if (!enabled) {
                System.err.println("SKIP: TTRP_CONFORM_PG != 1 — live hero conform not run.")
                return@test
            }
            val conn =
                System.getenv("TTR_CONN_ERP_PG")
                    ?: error("TTRP_CONFORM_PG=1 but TTR_CONN_ERP_PG is unset")
            val heroSource = Files.readString(Paths.get("src/test/resources/fixtures/hero.ttrp"))
            val csv = Paths.get("src/test/resources/fixtures/sales_2026.csv")
            val outDir = Files.createTempDirectory("ttrp-conform-live")

            val variants =
                PlacementVariants.build(
                    heroSource,
                    "hero.ttrp",
                    TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                    MetadataFixtures.erpModelsRoot(),
                    outDir,
                )
            // Provision the sales CSV into each bundle's files/ (the Stage-3.3 runtime CSV-path).
            variants.values.forEach { bundleDir ->
                val files = Files.createDirectories(bundleDir.resolve("files"))
                Files.copy(csv, files.resolve("sales_2026.csv"), StandardCopyOption.REPLACE_EXISTING)
            }

            val outcome = ConformRunner(BundleInvoker(mapOf("TTR_CONN_ERP_PG" to conn))).run(variants)
            withClue(outcome.summary()) { outcome.exitCode shouldBe 0 }
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
