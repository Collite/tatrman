// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Paths

/**
 * RJ-P3 3.1.6/3.2.4/3.2.6 — a rejects-wired PG island bundles with its `rejects` port exported and
 * a `rejectSites` entry in `manifest.json` (the site record RJ-P5's eighth conform check reads).
 */
class RejectsBundleTest :
    FunSpec({
        val src = Files.readString(Paths.get("src/test/resources/fixtures/rejects-pg.ttrp"))

        fun build() =
            BundleAssembler("1.0.0").build(
                source = src,
                fileName = "rejects-pg.ttrp",
                pipelineManifest =
                    TtrpManifest(
                        world = "acme.worlds.dev",
                        manifestDir = MetadataFixtures.erpProjectRoot(),
                    ),
                modelsRoot = MetadataFixtures.erpModelsRoot(),
                outDir = Files.createTempDirectory("ttrp-rejects-bundle"),
            )

        test("manifest.rejectSites records the elaborated site (contracts §7)") {
            val sites = build().manifest.rejectSites
            sites.size shouldBe 1
            val s = sites.single()
            s.site shouldBe "checked"
            s.container shouldBe "returns_ingest"
            s.rejectsPort shouldBe "rejects"
            s.processedPorts shouldContainAll listOf("clean", "bad")
        }

        test("the PG island exports the rejects port to its own Arrow sink (3.2.4)") {
            val result = build()
            val script = Files.readString(result.dir.resolve("islands/returns_ingest.py"))
            // one execute + fetch per port, incl. the rejects terminal → staging/rejects.arrow.
            script shouldContain "staging/rejects.arrow"
            script shouldContain "_ttrp_reject_code"
            script shouldContain "out/clean_result.arrow"
        }

        test("a Polars rejects island records its site and exports the rejects frame (4.1.6/4.1.7)") {
            val polarsSrc = Files.readString(Paths.get("src/test/resources/fixtures/rejects-polars.ttrp"))
            val result =
                BundleAssembler("1.0.0").build(
                    source = polarsSrc,
                    fileName = "rejects-polars.ttrp",
                    pipelineManifest =
                        TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                    modelsRoot = MetadataFixtures.erpModelsRoot(),
                    outDir = Files.createTempDirectory("ttrp-rejects-polars-bundle"),
                )
            // rejectSites are derived engine-agnostically (from synthProvenance) — same shape as PG.
            val s = result.manifest.rejectSites.single()
            s.site shouldBe "checked"
            s.container shouldBe "returns_ingest"
            s.processedPorts shouldContainAll listOf("clean", "bad")
            // the single Polars island script sinks all three ports, rejects included.
            val script = Files.readString(result.dir.resolve("islands/returns_ingest.py"))
            script shouldContain "staging/rejects.arrow"
            script shouldContain "_ttrp_reject_code"
            script shouldContain "pl.all().exclude("
        }

        test("a rejects-free program still emits an empty rejectSites (fail-fast, backward compat)") {
            val heroSrc = Files.readString(Paths.get("src/test/resources/fixtures/hero.ttrp"))
            val m =
                BundleAssembler("1.0.0")
                    .build(
                        source = heroSrc,
                        fileName = "hero.ttrp",
                        pipelineManifest =
                            TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                        modelsRoot = MetadataFixtures.erpModelsRoot(),
                        outDir = Files.createTempDirectory("ttrp-hero-bundle"),
                    ).manifest
            m.rejectSites shouldBe emptyList()
        }
    })
