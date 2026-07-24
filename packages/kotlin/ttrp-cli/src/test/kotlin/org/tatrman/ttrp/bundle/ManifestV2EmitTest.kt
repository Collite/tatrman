// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as stringShouldContain
import io.kotest.matchers.string.shouldNotContain
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Paths

/**
 * PL-P1.S3.T3 — the hero emits a **v2** manifest: schemaVersion 2, per-island `connections`, static
 * `lineage` (the `sum(amount) → total` aggregate), **no provenance block, params absent** (params
 * emission arrives with the PL-P2 grammar), and it validates against the PL-P0 JSON Schema.
 */
class ManifestV2EmitTest :
    FunSpec({
        val heroSource = Files.readString(Paths.get("src/test/resources/fixtures/hero.ttrp"))

        fun buildHero() =
            BundleAssembler("1.0.0").build(
                source = heroSource,
                fileName = "hero.ttrp",
                pipelineManifest =
                    TtrpManifest(
                        world = "acme.worlds.dev",
                        manifestDir = MetadataFixtures.erpProjectRoot(),
                    ),
                modelsRoot = MetadataFixtures.erpModelsRoot(),
                outDir = Files.createTempDirectory("ttrp-v2"),
            )

        test("hero manifest is schemaVersion 2 with per-island connections and no provenance/params") {
            val m = buildHero().manifest
            m.schemaVersion shouldBe 2
            // the pg island carries its own connection; the polars island carries none.
            m.islands.first { it.name == "acc_prep" }.connections shouldContain "TTR_CONN_ERP_PG"
            m.islands.first { it.name == "crunch" }.connections shouldBe emptyList()
            val json = m.toJson()
            json shouldNotContain "\"provenance\""
            // A param-FREE program still omits `params` (byte-identity); emission is exercised by the
            // params-hero below. `retries`/`onFailureOf` stay omitted here too (explicitNulls=false).
            json shouldNotContain "\"params\""
            m.params shouldBe null
        }

        // ---- PL-P2.S1 (F-4): the params-hero against the `tatrman`-executor platform world ----
        val paramsProject = Paths.get("src/test/resources/fixtures/params-project")

        fun buildParamsHero() =
            BundleAssembler("1.0.0").build(
                source = Files.readString(Paths.get("src/test/resources/fixtures/params-hero.ttrp")),
                fileName = "params-hero.ttrp",
                pipelineManifest = TtrpManifest(world = "acme.worlds.platform", manifestDir = paramsProject),
                modelsRoot = paramsProject.resolve("models"),
                outDir = Files.createTempDirectory("ttrp-params"),
            )

        test("params-hero emits top-level params[] (name/type/required/default, source order)") {
            val m = buildParamsHero().manifest
            val params = m.params.orEmpty()
            params.map { it.name } shouldContainExactly listOf("run_date", "min_amount")
            val runDate = params.first { it.name == "run_date" }
            runDate.type shouldBe "date"
            runDate.required shouldBe false
            runDate.default shouldBe "@run-date"
            val minAmount = params.first { it.name == "min_amount" }
            minAmount.type shouldBe "int"
            minAmount.default shouldBe "0"
        }

        test("params-hero emits per-island retries, onFailureOf, and consumed params") {
            val m = buildParamsHero().manifest
            val accPrep = m.islands.first { it.name == "acc_prep" }
            accPrep.retries shouldBe 2
            accPrep.params.orEmpty() shouldContain "run_date"

            val salvage = m.islands.first { it.name == "salvage" }
            salvage.onFailureOf shouldBe "crunch"
            salvage.params.orEmpty() shouldContainExactly listOf("run_date", "min_amount")

            // The on-failure island is excluded from the happy-path waves.
            m.waves.flatten() shouldNotContain "salvage"
            m.waves.flatten() shouldContain "acc_prep"
        }

        test("params-hero manifest validates against the PL-P0 v2 JSON Schema") {
            val json = buildParamsHero().manifest.toJson()
            val schemaStream = javaClass.getResourceAsStream("/schemas/bundle-manifest-v2.schema.json")!!
            val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaStream)
            schema.validate(ObjectMapper().readTree(json)) shouldBe emptySet()
        }

        test("the same params program against the bash `dev` world is a T6 capability error (gate)") {
            // The source `uses world` is authoritative — retarget it to the bash `dev` world.
            val devSource =
                Files
                    .readString(Paths.get("src/test/resources/fixtures/params-hero.ttrp"))
                    .replace("acme.worlds.platform", "acme.worlds.dev")
            val ex =
                shouldThrow<IllegalArgumentException> {
                    BundleAssembler("1.0.0").build(
                        source = devSource,
                        fileName = "params-hero.ttrp",
                        pipelineManifest = TtrpManifest(world = "acme.worlds.dev", manifestDir = paramsProject),
                        modelsRoot = paramsProject.resolve("models"),
                        outDir = Files.createTempDirectory("ttrp-params-neg"),
                    )
                }
            // The bash executor supports no F-4 vocabulary → params/on-failure/retries all reject.
            ex.message!! stringShouldContain "TTRP-CAP-201"
        }

        test("lineage carries the aggregate:SUM hero column (total ← amount)") {
            val lineage = buildHero().manifest.lineage
            lineage?.columns.orEmpty().shouldNotBeEmpty()
            val total = lineage!!.columns.first { it.output.column == "total" }
            total.transform shouldBe "aggregate:SUM"
            total.inputs.map { it.column } shouldContain "amount"
        }

        test("lineage output.materialized carries the island's program-level store target (v2.2)") {
            // crunch's aggregate output flows to `crunch.low -> store(files.low_regions)`. Before the R1-2
            // fix this was silently null (the store is program-level, not a container member).
            val total =
                buildHero()
                    .manifest.lineage!!
                    .columns
                    .first { it.output.column == "total" }
            total.output.materialized shouldBe "files.low_regions"
        }

        test("the generated manifest validates against the PL-P0 v2 JSON Schema") {
            val json = buildHero().manifest.toJson()
            val schemaStream =
                javaClass.getResourceAsStream("/schemas/bundle-manifest-v2.schema.json")
                    ?: error("schema not on classpath (ttrp-emit main resource)")
            val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaStream)
            val errors = schema.validate(ObjectMapper().readTree(json))
            errors shouldBe emptySet()
        }
    })
