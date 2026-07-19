// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
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
            json shouldNotContain "\"params\"" // emission lands PL-P2
        }

        test("lineage carries the aggregate:SUM hero column (total ← amount)") {
            val lineage = buildHero().manifest.lineage
            lineage?.columns.orEmpty().shouldNotBeEmpty()
            val total = lineage!!.columns.first { it.output.column == "total" }
            total.transform shouldBe "aggregate:SUM"
            total.inputs.map { it.column } shouldContain "amount"
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
