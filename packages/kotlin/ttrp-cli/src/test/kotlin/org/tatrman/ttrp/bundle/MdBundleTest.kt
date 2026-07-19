// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainIgnoringCase
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * S4-A tail — the MD dot-path graph→bundle integration. An authored `.ttrp` with an MD dot-path
 * predicate assembles to a bundle whose PG island SQL carries the lowered read. This closes the arc's
 * last gap end-to-end through the *production* path (no injected fixtures): [BundleAssembler] →
 * [org.tatrman.ttrp.graph.TtrpPipeline] loads the models root's MdModel (front-half resolution, R13
 * disconnected) + MdBindings ([org.tatrman.ttrp.bundle.PgIslandScript]'s emitter lowers the resolved
 * path). The fixture project's models/ tree carries the world, the erp.accounts db table (filter
 * source), and the sales-model MD tier together.
 */
class MdBundleTest :
    FunSpec({
        val projectRoot = Paths.get("src/test/resources/fixtures/md-project")

        fun build(
            program: String,
            outDir: Path,
        ): BundleAssembler.BundleResult =
            BundleAssembler("1.0.0").build(
                source = Files.readString(projectRoot.resolve(program)),
                fileName = program,
                pipelineManifest =
                    TtrpManifest(
                        world = "acme.worlds.dev",
                        manifestDir = projectRoot,
                    ),
                modelsRoot = projectRoot.resolve("models"),
                outDir = outDir,
            )

        fun islandText(result: BundleAssembler.BundleResult): String =
            Files
                .walk(result.dir.resolve("islands"))
                .use { s -> s.filter { Files.isRegularFile(it) }.map { Files.readString(it) }.toList() }
                .joinToString("\n")

        test("an MD dot-path predicate lowers to SQL in the assembled bundle") {
            val result = build("md.ttrp", Files.createTempDirectory("ttrp-md-bundle"))
            val islandText = islandText(result)

            // The `plan` cubelet lowers to its long-shape, invalidate-journaled read over f_plan: both
            // pinned grain coordinates (customer_name = 'Kaufland', month_num = 6), the NET measure code
            // selected from the value column, the valid-flag read view, and the outer measure aggregate.
            islandText shouldContain "f_plan"
            islandText shouldContain "customer_name"
            islandText shouldContain "Kaufland"
            islandText shouldContain "month_num"
            islandText shouldContain "6"
            islandText shouldContain "measure_code"
            islandText shouldContain "NET"
            islandText shouldContain "amount"
            islandText shouldContain "is_current" // invalidate read view (R31)
            islandText shouldContainIgnoringCase "sum"
        }

        test("viaCalc reads lower in the bundle — proves the MdModel is threaded to the emitter") {
            // `md-conform.ttrp` carries coarser-than-grain reads (Time.year / Time.month over the
            // Time.day-grained `sales`). These need the MdModel at emit — to derive the calc and to
            // resolve the case table / domains. Before the model was threaded through
            // BundleAssembler → SqlIslandEmitter, these threw `md/…` in the bundle path though the
            // unit lowering (constructed with a model directly) passed. This is that regression.
            val result = build("md-conform.ttrp", Files.createTempDirectory("ttrp-md-conform-bundle"))
            val islandText = islandText(result)

            // Inline viaCalc (Time.year → date_to_year): EXTRACT over the base date column.
            islandText shouldContainIgnoringCase "extract"
            islandText shouldContain "sale_date"
            // Case-table viaCalc (Time.month → date_to_month): a join to d_calendar on cal_month.
            islandText shouldContain "d_calendar"
            islandText shouldContain "cal_month"
        }
    })
