// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.expr.catalog.ValidityCatalog

/**
 * RJ-P5 5.1.4 — the corpus→fixture generator. The canonical `cast text->int64` validity corpus
 * turns into a deterministic conform-fixture CSV with a known accept/reject split; CSV-unsafe corpus
 * cases are excluded and reported (no silent caps). The committed CSV is the traceable, corpus-
 * derived fixture the seal's known-count reject path rides on.
 */
class CorpusFixturesTest :
    FunSpec({
        val gen = CorpusFixtures.forCast("text->int64")
        val spec = ValidityCatalog.rejectCapability("cast", "text->int64")!!

        test("the split matches the corpus partition minus the CSV-unsafe cases") {
            // accept rows (minus the one CR/LF value) + the explicit null row = processed.
            val usableAccept = spec.corpus.accept.count { v -> v.none { it == '\n' || it == '\r' } }
            val usableReject = spec.corpus.reject.count { v -> v.isNotEmpty() && v.none { it == '\n' || it == '\r' } }
            gen.expectedProcessed shouldBe usableAccept + 1
            gen.expectedRejects shouldBe usableReject
        }

        test("CSV-unsafe corpus values are excluded and reported, not silently miscounted") {
            // the empty string (a reject that CSV would read as null ⇒ a success) + the one \n value.
            gen.excluded shouldHaveSize 2
            gen.excluded.any { it.contains("empty") } shouldBe true
            gen.excluded.any { it.contains("CR/LF") } shouldBe true
        }

        test("generation is deterministic (no timestamps / ordering churn)") {
            CorpusFixtures.forCast("text->int64").csv shouldBe gen.csv
        }

        test("golden: the generated text->int64 conform fixture CSV") {
            gen.csv shouldContain "customer,region,amount"
            GoldenSupport.assertMatchesGolden(gen.csv, "fixtures/corpus_text_int64.csv")
        }
    })
