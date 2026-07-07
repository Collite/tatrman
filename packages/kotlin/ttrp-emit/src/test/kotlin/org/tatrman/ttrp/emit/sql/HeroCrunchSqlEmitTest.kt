package org.tatrman.ttrp.emit.sql

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Paths

/**
 * S3.5 T3.5.2/3 — the hero `crunch` container, **retargeted to `erp_pg`** via the build-API target
 * override, emitted through the decomposed-relational SQL path ([SqlGraphEmitter] → [CtePlanner]).
 * Exercises, end-to-end on the real hero, the whole variant-B emit stack:
 *  - Branch→Filter lowering (T8, since Postgres has no native Branch) → two outputs `result`/`low`;
 *  - the `coalesce` FALSE-port complement through the translator;
 *  - equi-join `right_on` dedup (matching `hero_crunch.py`: surviving `region` is `accounts.region`);
 *  - the world-declared `accounts` staging-boundary schema typing the container IN port;
 *  - Aggregate schema propagation feeding the terminal Filters.
 */
class HeroCrunchSqlEmitTest :
    FunSpec({
        val heroSource = Files.readString(Paths.get("src/test/resources/fixtures/hero.ttrp"))

        fun pipeline() =
            TtrpPipeline(
                TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                MetadataFixtures.erpModelsRoot(),
            )

        // Plan + walk once (the retarget re-runs the whole Phase-2 pipeline); each output asserts
        // in its own test so `-DupdateGolden=true` regenerates every golden in a single run (the
        // update path fails per-call).
        val plan = pipeline().plan(heroSource, "hero.ttrp", targetOverrides = mapOf("crunch" to "erp_pg"))
        val crunch =
            plan.graph!!
                .containers.values
                .single { it.label == "crunch" }
        val plans = SqlGraphEmitter(plan.graph!!, plan.bound!!).plansByOutput(crunch)

        test("crunch@erp_pg is a relational container with exactly the result/low outputs") {
            plan.ok.shouldBeTrue()
            crunch.fragment.shouldBeNull() // a relational container, not a """sql fragment
            plans.keys shouldContainExactlyInAnyOrder listOf("result", "low") // rejects skipped
        }

        test("result output — branch-true filter over the deduped join + aggregate") {
            GoldenSupport.assertMatchesGolden(
                EmitFixtures.pgPlanner().emit(plans.getValue("result"), "crunch"),
                "sql/postgres/hero_crunch_result.sql",
            )
        }

        test("low output — branch-false filter uses the 3VL coalesce complement") {
            GoldenSupport.assertMatchesGolden(
                EmitFixtures.pgPlanner().emit(plans.getValue("low"), "crunch"),
                "sql/postgres/hero_crunch_low.sql",
            )
        }

        test("SqlIslandEmitter.emitOutputs routes the decomposed island (the bundle-facing seam)") {
            val island = plan.exec!!.islands.single { it.name == "crunch" }
            val outputs = SqlIslandEmitter(plan.bound!!).emitOutputs(island, plan.graph!!)
            outputs.keys shouldContainExactlyInAnyOrder listOf("result", "low")
            GoldenSupport.assertMatchesGolden(outputs.getValue("result").text, "sql/postgres/hero_crunch_result.sql")
        }
    })
