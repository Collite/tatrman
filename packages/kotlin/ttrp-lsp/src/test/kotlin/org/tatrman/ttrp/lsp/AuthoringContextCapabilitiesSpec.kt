// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.lsp.protocol.AuthoringContextParams
import org.tatrman.ttrp.lsp.test.TtrpLspHarness

/**
 * T7.2 tail (3f): the two `authoringContext` sections that used to be present-but-empty —
 * per-engine capability rosters (T6 manifests) and `modelObjects` (db+er enumeration) — are
 * now populated against the shared erp-project world.
 */
class AuthoringContextCapabilitiesSpec :
    StringSpec({
        "capabilities.engines carries T6 manifest node/function rosters, not empty arrays" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val bundle =
                    h.remote
                        .authoringContext(AuthoringContextParams(null, null))
                        .get()
                        .bundle
                val engines = bundle.getAsJsonObject("capabilities").getAsJsonArray("engines").map { it.asJsonObject }
                val polars = engines.single { it.get("engine").asString == "polars" }
                val nodeNames = polars.getAsJsonArray("nodes").map { it.asString }
                nodeNames shouldContain "Join"
                nodeNames shouldContain "Aggregate"
                polars.getAsJsonArray("functions").size() shouldBeGreaterThan 0
                // RJ-P6 6.1.4: each engine carries its rejects capability so assist won't offer a
                // rejects tap on an engine that can't produce one. Polars ships rejects support.
                polars.get("rejects").asBoolean shouldBe true
            }
        }

        "grammar.entryVerbs carries the `entry` stdlib verb roster with signatures (EN-P2 T6)" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val bundle =
                    h.remote
                        .authoringContext(AuthoringContextParams(null, null))
                        .get()
                        .bundle
                val verbs = bundle.getAsJsonObject("grammar").getAsJsonArray("entryVerbs").map { it.asJsonObject }
                verbs.map { it.get("id").asString } shouldContain "entry.effective-date-change"
                val edc = verbs.single { it.get("id").asString == "entry.effective-date-change" }
                edc.getAsJsonArray("params").map { it.asString } shouldContain "EFFECTIVE_DATE"
                edc.getAsJsonArray("requiresSemantics").map { it.asString } shouldContain "scd2"
            }
        }

        "modelObjects enumerates the shared world's db tables with inline schema" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val bundle =
                    h.remote
                        .authoringContext(AuthoringContextParams(null, null))
                        .get()
                        .bundle
                val objects = bundle.getAsJsonArray("modelObjects").map { it.asJsonObject }
                objects.size shouldBeGreaterThan 0
                val accounts = objects.single { it.get("name").asString.endsWith("accounts") }
                accounts.get("kind").asString shouldBe "table"
                val columnNames = accounts.getAsJsonArray("schema").map { it.asJsonObject.get("name").asString }
                columnNames shouldContain "account_id"
            }
        }
    })
