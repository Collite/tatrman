// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * D4 — the published Kotlin SourceLocation is the modeler superset:
 * file, line, column, endLine, endColumn, offsetStart, offsetEnd.
 *
 * The load-bearing case is the multi-token / multi-line span invariant from
 * CLAUDE.md: `endColumn = stopToken.column + stopToken.length`, NOT
 * `startColumn + spanLength`. On a single line those two formulae coincide, so
 * only a multi-line def distinguishes a correct walker from the historical bug.
 */
class SourceLocationSpec :
    StringSpec({

        "single-token / single-line span: line == endLine, offsets bracket the text" {
            val src = "def project M {}"
            val r = TtrLoader.parseString(src)
            r.ok shouldBe true
            val loc = r.definitions[0].source

            loc.line shouldBe loc.endLine
            loc.column shouldBe 0
            // offsets are 0-indexed, offsetEnd exclusive — they bracket the def text.
            src.substring(loc.offsetStart, loc.offsetEnd) shouldBe src
            // single line: endColumn is start column + the rendered length.
            loc.endColumn shouldBe loc.column + src.length
        }

        "multi-line span: endLine > line and offsets cover the whole slice" {
            val src =
                """
                def entity X {
                    labelPlural: "xs"
                }
                """.trimIndent()
            val r = TtrLoader.parseString(src)
            r.ok shouldBe true
            val loc = r.definitions[0].source

            loc.line shouldBe 1
            (loc.endLine > loc.line) shouldBe true
            (loc.offsetEnd - loc.offsetStart) shouldBe src.length
            src.substring(loc.offsetStart, loc.offsetEnd) shouldBe src
        }

        "multi-token-span invariant: endColumn = stopToken.column + stopToken.length" {
            // The def spans 3 lines; the stop token is the closing `}` sitting at
            // column 0 of line 3 with length 1, so endColumn MUST be 1.
            // The wrong `startColumn + spanLength` formula would yield a large value.
            val src =
                """
                def entity X {
                    labelPlural: "xs"
                }
                """.trimIndent()
            val r = TtrLoader.parseString(src)
            val loc = r.definitions[0].source

            loc.endLine shouldBe 3
            loc.endColumn shouldBe 1
        }
    })
