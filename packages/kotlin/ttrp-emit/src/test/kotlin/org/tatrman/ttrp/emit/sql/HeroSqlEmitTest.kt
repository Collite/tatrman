package org.tatrman.ttrp.emit.sql

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Paths

/**
 * T3.1.7 — the hero's Postgres island emitted from the real front-half → graph → emit pipeline.
 * The hero's `acc_prep` is an authored `"""sql` fragment container, so SQL emit is the verbatim
 * interior (C2-f; fragment decomposition is P6). This proves the fragment-island emit path and
 * pins the hero SQL golden.
 */
class HeroSqlEmitTest :
    FunSpec({
        val heroSource = Files.readString(Paths.get("src/test/resources/fixtures/hero.ttrp"))

        fun pipeline() =
            TtrpPipeline(
                TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                MetadataFixtures.erpModelsRoot(),
            )

        test("hero acc_prep SQL island emits its fragment interior verbatim") {
            val plan = pipeline().plan(heroSource, "hero.ttrp")
            plan.ok.shouldBeTrue()
            val exec = plan.exec!!
            val sqlIsland = exec.islands.single { it.engine == "erp_pg" }
            val emitter = SqlIslandEmitter(plan.bound!!)
            emitter.dialect(sqlIsland) shouldBe org.tatrman.proteus.v1.SqlDialect.POSTGRESQL
            val result = emitter.emit(sqlIsland, plan.graph!!)
            GoldenSupport.assertMatchesGolden(result.text, "sql/postgres/hero_accounts_prep.sql")
        }
    })
