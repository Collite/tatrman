// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.conform.eval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * T7.2.4: the eval corpus loads against its schema — ids unique, every `expected` fixture and
 * every `insertionTarget` fixture exists, tolerance parsed.
 */
class EvalCorpusSpec :
    StringSpec({
        val corpus = EvalCorpus.load(EvalTestGraphs.corpusDir())

        "the corpus loads with at least one entry" {
            corpus.entries.isNotEmpty() shouldBe true
        }

        "entry ids are unique" {
            corpus.entries
                .map { it.id }
                .toSet()
                .size shouldBe corpus.entries.size
        }

        "every expected fixture exists on disk" {
            corpus.entries.forEach { corpus.fixtureExists(it.expected) shouldBe true }
        }

        "every insertionTarget fixture exists and names a container" {
            corpus.entries.mapNotNull { it.insertionTarget }.forEach {
                corpus.fixtureExists(it.fixture) shouldBe true
                it.container shouldNotBe ""
            }
        }

        "tolerance knobs parse (eval-002 allows extra Calc nodes)" {
            corpus.entries
                .single { it.id == "eval-002" }
                .tolerance.extraCalcNodes shouldBe true
        }
    })
