// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * MH T1 — the COLLISION fold, held to the same table as its two twins.
 *
 * `TermNormalizer.fold` here, `foldForCollision` in `@tatrman/semantics` and
 * `org.tatrman.text.Normalization.fold` in tatrman-server are one function written three
 * times, and the third one is the anchor index the matcher actually queries: two refs meet
 * at runtime iff their FOLDED forms are equal. A comment saying so cannot fail a build, so
 * the table is read off disk from the TS package (both live in this repo) and asserted row
 * by row — the same shape `VocabularyParitySpec` uses for the vocabulary twins.
 *
 * `normalize` is untouched by all of this: it keeps diacritics and stays the archive's
 * merge key (`LexiconCompiler.merge`). Two different keys, two different jobs.
 */
class FoldParitySpec :
    StringSpec({

        val table =
            Json
                .parseToJsonElement(
                    Files.readString(repoRoot().resolve("packages/semantics/src/lexicon/fold-parity.json")),
                ).jsonObject["cases"]!!
                .jsonArray
                .map { row ->
                    val pair = row.jsonArray
                    pair[0].jsonPrimitive.content to pair[1].jsonPrimitive.content
                }

        "the parity table is the 13 rows of contracts §1" {
            table.size shouldBe 13
        }

        "a NON-BREAKING space survives the fold — the runtime index does not collapse it" {
            // review-087 F3. Java's `\s` is `[ \t\n\x0B\f\r]`, so `normalize` leaves U+00A0
            // alone; the TS twin had to stop using JS's `\s`, which matches it. The runtime is
            // the tie-breaker: `Normalization.fold` does not collapse whitespace at all.
            TermNormalizer.fold("Tržby\u00A0z prodejen") shouldBe "trzby\u00A0z prodejen"
        }

        table.forEachIndexed { i, (input, expected) ->
            "row $i — fold(${'"'}$input${'"'}) == ${'"'}$expected${'"'}" {
                TermNormalizer.fold(input) shouldBe expected
            }
        }

        "fold is idempotent" {
            for ((input, _) in table) {
                TermNormalizer.fold(TermNormalizer.fold(input)) shouldBe TermNormalizer.fold(input)
            }
        }

        "fold collapses what normalize keeps apart — the diacritic pair is ONE collision key" {
            TermNormalizer.fold("výroba") shouldBe TermNormalizer.fold("vyroba")
            // …while the merge key keeps them apart, which is why fold is a SECOND function.
            (TermNormalizer.normalize("výroba") == TermNormalizer.normalize("vyroba")) shouldBe false
        }
    })

/** The repo root — the directory holding `pnpm-workspace.yaml`, walking up from the test's cwd. */
private fun repoRoot(): Path {
    var dir: Path? = Paths.get("").toAbsolutePath()
    while (dir != null) {
        if (Files.isRegularFile(dir.resolve("pnpm-workspace.yaml"))) return dir
        dir = dir.parent
    }
    error("could not locate the repo root (no pnpm-workspace.yaml above ${Paths.get("").toAbsolutePath()})")
}
