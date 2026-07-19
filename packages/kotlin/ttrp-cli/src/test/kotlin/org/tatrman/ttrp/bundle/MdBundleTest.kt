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
        val source = Files.readString(projectRoot.resolve("md.ttrp"))

        fun build(outDir: Path): BundleAssembler.BundleResult =
            BundleAssembler("1.0.0").build(
                source = source,
                fileName = "md.ttrp",
                pipelineManifest =
                    TtrpManifest(
                        world = "acme.worlds.dev",
                        manifestDir = projectRoot,
                    ),
                modelsRoot = projectRoot.resolve("models"),
                outDir = outDir,
            )

        test("an MD dot-path predicate lowers to SQL in the assembled bundle") {
            val result = build(Files.createTempDirectory("ttrp-md-bundle"))

            // The relational PG island carrying the filter (a `python3` + adbc script).
            val islandDir = result.dir.resolve("islands")
            val islandText =
                Files
                    .walk(islandDir)
                    .use { s -> s.filter { Files.isRegularFile(it) }.map { Files.readString(it) }.toList() }
                    .joinToString("\n")

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
    })
