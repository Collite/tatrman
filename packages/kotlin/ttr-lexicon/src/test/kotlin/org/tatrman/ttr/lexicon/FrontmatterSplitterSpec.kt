// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * RV-P1.1 T5 — the splitter's edge cases, which are all "a real editor wrote this file".
 */
class FrontmatterSplitterSpec :
    FunSpec({

        fun ok(markdown: String): Frontmatter =
            FrontmatterSplitter.split(markdown).shouldBeInstanceOf<LexiconLoad.Ok<Frontmatter>>().value

        test("plain frontmatter splits into yaml + body") {
            val fm = ok("---\nop: op:trend\n---\nRetrieval: group by grain.\n")

            fm.yaml shouldBe "op: op:trend"
            fm.body shouldBe "Retrieval: group by grain."
            fm.firstKeyLine shouldBe 2
        }

        test("CRLF line endings split the same as LF") {
            val fm = ok("---\r\nop: op:trend\r\n---\r\nRetrieval: group.\r\n")

            fm.yaml shouldBe "op: op:trend"
            fm.body shouldBe "Retrieval: group."
            // A stray \r would survive into the compiled artifact and change its hash.
            fm.yaml shouldNotContain "\r"
            fm.body shouldNotContain "\r"
        }

        test("a UTF-8 BOM does not hide the opening fence") {
            val fm = ok("\uFEFF---\nop: op:trend\n---\nBody.\n")

            fm.yaml shouldBe "op: op:trend"
            fm.body shouldBe "Body."
        }

        test("leading blank lines are tolerated and counted") {
            val fm = ok("\n\n---\nop: op:trend\n---\nBody.\n")

            fm.yaml shouldBe "op: op:trend"
            // Fence on line 3, so the first key is on line 4 — provenance must not drift.
            fm.firstKeyLine shouldBe 4
        }

        test("`---` inside the body is a horizontal rule, not a second fence") {
            val fm = ok("---\nop: op:trend\n---\nIntro.\n\n---\n\nMore prose.\n")

            fm.yaml shouldBe "op: op:trend"
            fm.body shouldContain "Intro."
            fm.body shouldContain "More prose."
            // The rule itself belongs to the body — the body is kept verbatim for the Golem.
            fm.body shouldContain "---"
        }

        test("no frontmatter at all is RG-LEX-009, not an empty skill") {
            val rejected = FrontmatterSplitter.split("Just prose.\n").shouldBeInstanceOf<LexiconLoad.Rejected>()

            rejected.codes shouldBe listOf(LexiconErrors.MALFORMED_FRONTMATTER)
        }

        test("an unterminated block is RG-LEX-009 too — it never becomes the whole file") {
            val rejected =
                FrontmatterSplitter
                    .split(
                        "---\nop: op:trend\nRetrieval: …\n",
                    ).shouldBeInstanceOf<LexiconLoad.Rejected>()

            rejected.codes shouldBe listOf(LexiconErrors.MALFORMED_FRONTMATTER)
            rejected.violations.single().message shouldContain "never closed"
        }

        test("an empty frontmatter block splits cleanly (the schema then rejects it)") {
            val fm = ok("---\n---\nBody.\n")

            fm.yaml shouldBe ""
            fm.body shouldBe "Body."
        }
    })
