// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.walker

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Lifted from CPython's `Lib/test/test_textwrap.py` — DedentTestCase.
 * Verifies that our dedent matches Python's behaviour.
 */
class DedentSpec :
    StringSpec({

        "no-op when there is no common prefix" {
            val text = "Hello there.\nHow are you?\nOh good, I'm glad."
            Dedent.applyTextwrapDedent(text) shouldBe text
        }

        "removes common leading whitespace" {
            val text = "  Hello there.\n  How are you?\n  Oh good, I'm glad."
            val expected = "Hello there.\nHow are you?\nOh good, I'm glad."
            Dedent.applyTextwrapDedent(text) shouldBe expected
        }

        "uses the smallest common prefix when lines have different indentation" {
            val text = "  Hello there.\n    How are you?"
            val expected = "Hello there.\n  How are you?"
            Dedent.applyTextwrapDedent(text) shouldBe expected
        }

        "blank lines do not contribute to the common prefix" {
            val text = "  line one\n\n  line two"
            // CPython's textwrap.dedent normalises blank lines to empty.
            val expected = "line one\n\nline two"
            Dedent.applyTextwrapDedent(text) shouldBe expected
        }

        "drops a leading newline so triple-quoted authoring is natural" {
            val text = "\n  hello\n  world"
            Dedent.applyTextwrapDedent(text) shouldBe "hello\nworld"
        }

        "tabs and spaces participate in the prefix as-is (no expansion)" {
            // Python's textwrap.dedent compares tabs and spaces literally. We do
            // the same: the prefix is the longest whitespace string common to all
            // non-blank lines.
            val text = "\tone\n\ttwo"
            Dedent.applyTextwrapDedent(text) shouldBe "one\ntwo"
        }

        "no leading whitespace produces no change" {
            val text = "no\nindent"
            Dedent.applyTextwrapDedent(text) shouldBe text
        }
    })
