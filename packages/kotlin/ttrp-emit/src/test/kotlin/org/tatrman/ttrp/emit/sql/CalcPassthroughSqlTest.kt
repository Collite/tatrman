// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

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
 * RJ-P3 foundation — a named-computed `calc` (add-semantics) emits `SELECT src.*, <expr> AS <name>`
 * to PG SQL. Before RJ-P3 `CalcToProject` dropped both the assignment name and the passthrough
 * columns, so this shape was un-emittable; the guard/reject/cast calcs all depend on it.
 */
class CalcPassthroughSqlTest :
    FunSpec({
        val src = Files.readString(Paths.get("src/test/resources/fixtures/calc-passthrough.ttrp"))
        val plan =
            TtrpPipeline(
                TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                MetadataFixtures.erpModelsRoot(),
            ).plan(src, "calc-passthrough.ttrp")

        test("the calc container plans cleanly") {
            plan.ok.shouldBeTrue()
        }

        test("calc adds `doubled` over the passed-through input columns") {
            val container =
                plan.graph!!
                    .containers.values
                    .single { it.label == "calc_demo" }
            val sql =
                EmitFixtures.pgPlanner().emit(
                    SqlGraphEmitter(plan.graph!!, plan.bound!!).plansByOutput(container).getValue("result"),
                    "calc_demo",
                )
            // passthrough (input columns) + the aliased computed column.
            sql shouldContain "\"amount\" * 2 AS \"doubled\""
            sql shouldContain "\"customer\""
            GoldenSupport.assertMatchesGolden(sql, "sql/postgres/calc_passthrough.sql")
        }
    })
