// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Paths

/**
 * RJ-P3 3.1/3.2 — RH-1 as a PG island, rejects emitted (Option E). Asserts the contracts-§6 shape:
 * a `<ssa>_guard` CTE computing the canonical validity flag as raw SQL, `clean`/`bad` over the
 * valid rows, and the `rejects` terminal (first-error CASE ladder + expr-id). Its fail-fast twin
 * (no wire) carries none of that machinery (R-P3).
 */
class RejectsPgSqlTest :
    FunSpec({
        fun emit(fixture: String): Pair<Boolean, Map<String, String>> {
            val src = Files.readString(Paths.get("src/test/resources/fixtures/$fixture"))
            val plan =
                TtrpPipeline(
                    TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                    MetadataFixtures.erpModelsRoot(),
                ).plan(src, fixture)
            if (!plan.ok || plan.graph == null) return false to emptyMap()
            val container: Container =
                plan.graph!!
                    .containers.values
                    .single { it.label == "returns_ingest" }
            val sqls =
                SqlGraphEmitter(plan.graph!!, plan.bound!!).plansByOutput(container).mapValues { (_, chain) ->
                    EmitFixtures.pgPlanner().emit(chain, "returns_ingest")
                }
            return true to sqls
        }

        val (wiredOk, wired) = emit("rejects-pg.ttrp")

        test("the rejects-wired island plans and emits all three ports") {
            wiredOk.shouldBeTrue()
            wired.keys shouldContainExactlyInAnyOrder listOf("clean", "bad", "rejects")
        }

        test("the guard CTE computes the canonical int64 validity flag as raw SQL (contracts §6)") {
            val rejects = wired.getValue("rejects")
            rejects shouldContain "\"checked_guard\" AS ("
            // canonical int64 domain from the RJ-P0 YAML: ASCII-ws trim, regex, int64 bounds, NULL-safe.
            rejects shouldContain "btrim(\"customer\", E' \\t\\n\\r\\f\\x0B') ~ '^[+-]?[0-9]+\$'"
            rejects shouldContain "::numeric BETWEEN -9223372036854775808 AND 9223372036854775807"
            rejects shouldContain "\"customer\" IS NULL OR"
        }

        test("the rejects terminal is a first-error CASE ladder over the failing rows (R-B4)") {
            val rejects = wired.getValue("rejects")
            rejects shouldContain "WHERE NOT COALESCE(\"_ttrp_v1\", FALSE)"
            rejects shouldContain "CASE WHEN NOT \"_ttrp_v1\" THEN 'TTRP-RJ-001'"
            rejects shouldContain "AS \"_ttrp_reject_code\""
            rejects shouldContain "'returned_qty'" // the failing expression's stable id
            rejects shouldContain "AS \"_ttrp_reject_expr\""
            // rejects schema = inSchema ⊕ {code, expr}: the internal validity flag is NOT exported.
            rejects.substringAfter("FROM \"checked_branch_f\"").shouldNotContain("_ttrp_v1")
        }

        test("clean/bad run over the guard-valid rows with the cast applied, no validity flag leak") {
            val clean = wired.getValue("clean")
            clean shouldContain "WHERE \"_ttrp_v1\"" // branch-true keeps valid rows (internal use)
            clean shouldContain "CAST(\"customer\" AS integer)) AS \"returned_qty\""
            // the cast CTE drops the internal validity flag ⇒ it is not in the output row.
            clean.substringAfter("\"checked_1\" AS (").substringBefore("\n)").shouldNotContain("_ttrp_v1")
        }

        // One golden per test so `-DupdateGolden=true` regenerates all of them in one run
        // (GoldenSupport.fail aborts the test after each update).
        test("golden: clean terminal") {
            GoldenSupport.assertMatchesGolden(wired.getValue("clean"), "sql/postgres/rejects_pg_clean.sql")
        }
        test("golden: bad terminal") {
            GoldenSupport.assertMatchesGolden(wired.getValue("bad"), "sql/postgres/rejects_pg_bad.sql")
        }
        test("golden: rejects terminal") {
            GoldenSupport.assertMatchesGolden(wired.getValue("rejects"), "sql/postgres/rejects_pg_rejects.sql")
        }

        // ---- fail-fast twin (R-P3): no wire ⇒ no elaboration, no `_ttrp_` anywhere ----

        val (ffOk, ff) = emit("rejects-pg-failfast.ttrp")

        test("the fail-fast twin carries none of the rejects machinery") {
            ffOk.shouldBeTrue()
            ff.keys shouldContainExactlyInAnyOrder listOf("clean", "bad")
            ff.values.forEach { it shouldNotContain "_ttrp_" }
            ff.values.forEach { it shouldNotContain "_guard" }
            GoldenSupport.assertMatchesGolden(ff.getValue("clean"), "sql/postgres/rejects_pg_failfast_clean.sql")
        }
    })
