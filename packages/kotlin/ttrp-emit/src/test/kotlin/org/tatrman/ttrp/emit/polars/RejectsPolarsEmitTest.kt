// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.polars

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.graph.capability.RejectDomain
import org.tatrman.ttrp.graph.capability.RejectsEntry
import org.tatrman.ttrp.graph.capability.RejectsSupport
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Paths

/**
 * RJ-P4 4.1 — RH-1 as a Polars island, rejects emitted (mask-and-split). Asserts the contracts-§6
 * Polars shape: a guard `with_columns` computing the canonical validity mask, the two branch
 * `filter`s (split), the cast over the valid frame, and the `rejects` frame (`_ttrp_v*` dropped +
 * code/expr literals) — all sunk in one script. Its fail-fast twin (no wire) carries none of it.
 */
class RejectsPolarsEmitTest :
    FunSpec({
        fun emit(fixture: String): Pair<Boolean, String> {
            val src = Files.readString(Paths.get("src/test/resources/fixtures/$fixture"))
            val plan =
                TtrpPipeline(
                    TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                    MetadataFixtures.erpModelsRoot(),
                ).plan(src, fixture)
            if (!plan.ok || plan.graph == null) return false to ""
            val g = plan.graph!!
            val island = plan.exec!!.islands.single { it.engine == "polars" }
            val container = g.containers.getValue(island.id)
            val steps = PolarsGraphEmitter(g, plan.bound!!).steps(container)
            val rejects =
                plan.bound!!
                    .engines[island.engine]
                    ?.manifest
                    ?.rejectsSupport() ?: RejectsSupport.NONE
            return true to PolarsIslandEmitter().emit(island.name, steps, rejects).text
        }

        val (wiredOk, wired) = emit("rejects-polars.ttrp")

        test("the rejects-wired island plans and emits") {
            wiredOk.shouldBeTrue()
        }

        test("the guard computes the canonical int64 validity mask (contracts §6, narrower ⇒ enforcing)") {
            // canonical int64 domain from the RJ-P0 YAML: ASCII-ws strip, regex, int64 bounds, NULL-safe.
            wired shouldContain ".str.strip_chars(\" \\t\\n\\r\\x0c\\x0b\")"
            wired shouldContain ".str.contains(r\"^[+-]?[0-9]+\$\")"
            wired shouldContain ".cast(pl.Int64, strict=False).is_not_null()"
            wired shouldContain ".alias(\"_ttrp_v1\")"
            wired shouldContain "pl.col(\"customer\").is_null() |"
        }

        test("the branch splits the guarded frame into valid + invalid via two filters") {
            wired shouldContain ".filter(pl.col(\"_ttrp_v1\"))"
            wired shouldContain "pl.coalesce([pl.col(\"_ttrp_v1\")" // NOT COALESCE(v, False) on the false side
        }

        test("the rejects frame drops the validity flag and carries the code/expr ladder (R-B4)") {
            wired shouldContain "pl.all().exclude(\"^_ttrp_v[0-9]+\$\")"
            wired shouldContain "pl.lit(\"TTRP-RJ-001\")"
            wired shouldContain ".alias(\"_ttrp_reject_code\")"
            wired shouldContain "pl.lit(\"returned_qty\")"
            wired shouldContain ".alias(\"_ttrp_reject_expr\")"
        }

        test("all three ports are sunk") {
            wired shouldContain "out/clean_result.arrow"
            wired shouldContain "staging/bad.arrow"
            wired shouldContain "staging/rejects.arrow"
        }

        test("golden: rejects-polars island") {
            GoldenSupport.assertMatchesGolden(wired, "polars/rejects_polars.py")
        }

        // ---- 4.1.2 native-form / canonical variant: a test-only manifest claiming domain: canonical
        //      emits the BARE non-strict mask instead of the enforcing regex guard. ----

        test("a canonical manifest entry emits the bare non-strict cast mask, not the enforcing guard") {
            val src = Files.readString(Paths.get("src/test/resources/fixtures/rejects-polars.ttrp"))
            val plan =
                TtrpPipeline(
                    TtrpManifest(world = "acme.worlds.dev", manifestDir = MetadataFixtures.erpProjectRoot()),
                    MetadataFixtures.erpModelsRoot(),
                ).plan(src, "rejects-polars.ttrp")
            val g = plan.graph!!
            val island = plan.exec!!.islands.single { it.engine == "polars" }
            val steps = PolarsGraphEmitter(g, plan.bound!!).steps(g.containers.getValue(island.id))
            val canonical =
                RejectsSupport(
                    produces = true,
                    entries =
                        listOf(
                            RejectsEntry(function = "cast", typePair = "text->int64", domain = RejectDomain.CANONICAL),
                        ),
                )
            val sql = PolarsIslandEmitter().emit(island.name, steps, canonical).text
            sql shouldContain "pl.col(\"customer\").cast(pl.Int64, strict=False).is_null()"
            sql shouldNotContain ".str.contains(" // the canonical enforcing regex guard is NOT emitted
        }

        // ---- fail-fast twin (R-P3): no wire ⇒ no elaboration, no `_ttrp_`, no mask ----

        val (ffOk, ff) = emit("rejects-polars-failfast.ttrp")

        test("the fail-fast twin carries none of the rejects machinery") {
            ffOk.shouldBeTrue()
            ff shouldNotContain "_ttrp_"
            ff shouldNotContain "strip_chars"
            ff shouldNotContain "exclude("
            GoldenSupport.assertMatchesGolden(ff, "polars/rejects_polars_failfast.py")
        }
    })
