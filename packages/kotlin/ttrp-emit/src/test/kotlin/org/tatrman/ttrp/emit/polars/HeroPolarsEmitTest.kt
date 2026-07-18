// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.polars

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Paths

/**
 * T3.2.5 — the hero's Polars island (`crunch`) emitted from the real front-half → graph → emit
 * pipeline via [PolarsGraphEmitter]. Byte-pinned as `hero_crunch.py`.
 *
 * The hero's `rejects` wire (`j.rejects` → store) is a **dead wire** (RJ-101): the join is not
 * reject-capable once ON-decomposition has run, so RJ-P1 never elaborates it and it stays literally
 * mapped to `.rejects` — [PolarsGraphEmitter] skips it (no empty stream, matching SQL). This test
 * asserts that skip. // re-asserted as a live rejects sink in RJ-P5 after the hero is re-authored
 * onto a reject-capable site (task 12).
 */
class HeroPolarsEmitTest :
    FunSpec({
        val heroSource = Files.readString(Paths.get("src/test/resources/fixtures/hero.ttrp"))

        fun plan() =
            TtrpPipeline(
                TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                MetadataFixtures.erpModelsRoot(),
            ).plan(heroSource, "hero.ttrp")

        test("hero crunch Polars island emits the join/aggregate/branch mainline") {
            val plan = plan()
            plan.ok.shouldBeTrue()
            val g = plan.graph!!
            val island = plan.exec!!.islands.single { it.engine == "polars" }
            val container = g.containers.getValue(island.id)
            val steps = PolarsGraphEmitter(g, plan.bound!!).steps(container)
            val script = PolarsIslandEmitter().emit(island.name, steps).text

            // Mainline shape (the A4 crunch): staged accounts read, CSV sales, filter, equi-join,
            // group-by aggregate, and the two branch-lowered filters + display/store sinks.
            script shouldContain "accounts = pl.read_ipc(\"staging/accounts.arrow\")"
            script shouldContain "pl.read_csv"
            script shouldContain ".filter("
            script shouldContain ".join("
            script shouldContain "left_on="
            script shouldContain ".group_by("
            script shouldContain ".sum().alias(\"total\")"
            script shouldContain "out/main_result.arrow"

            // The hero rejects wire is a dead wire (RJ-101, join not reject-capable) — it stays
            // mapped to `.rejects` and is skipped, so no rejects sink is emitted.
            // re-asserted as a live sink in RJ-P5 (hero re-author, task 12).
            val rejectsPort =
                container.portMapping.entries
                    .first { it.value.port == "rejects" }
                    .key
            PolarsGraphEmitter(g, plan.bound!!).isRejectsPort(container, rejectsPort).shouldBeTrue()

            GoldenSupport.assertMatchesGolden(script, "polars/hero_crunch.py")
        }
    })
