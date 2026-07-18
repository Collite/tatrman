// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.parser.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.withClue
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.parser.TtrpParser

/**
 * MD dot-path float/path disambiguation — contracts §1.3 (D14/D15), normative table.
 *
 * Loads `resources/md/float-path.cases` (`<source> :: <kind>`) and asserts each row parses
 * without syntax errors AND yields the expected top-level expression kind:
 *   literal → a numeric literal (float or int)   path → an mdPath node   binop → a binary op
 *
 * TDD: **red** until S0-B lands the grammar (`INT`/`floatLiteral`/`mdPath` + `DOTDOT`) and the
 * walker/AST `MdPath` node. Kind is read via the runtime class name so this spec compiles
 * before the `MdPath` type exists (green side turns it real).
 */
class MdFloatPathParseSpec :
    StringSpec({
        // simpleName → the normative kind label used in the cases file.
        fun kindOf(node: Any): String =
            when (node::class.simpleName) {
                "Literal" -> "literal"
                "MdPath" -> "path"
                "FunctionCall" -> "binop" // binary/unary operators fold to FunctionCall
                else -> node::class.simpleName ?: "?"
            }

        for ((source, expected) in MdCases.load("/md/float-path.cases")) {
            "'$source' parses as $expected" {
                val parsed = TtrpParser.parseExpression(source)
                withClue("syntax errors for `$source`: ${parsed.diagnostics.filter { it.severity == Severity.ERROR }}") {
                    parsed.diagnostics.filter { it.severity == Severity.ERROR } shouldBe emptyList()
                }
                withClue("`$source` classified wrong") {
                    kindOf(parsed.expression) shouldBe expected
                }
            }
        }
    })

/** Loads `<source> :: <kind>` lines from a classpath resource; `#`-comments and blanks skipped. */
internal object MdCases {
    fun load(path: String): List<Pair<String, String>> =
        (MdCases::class.java.getResourceAsStream(path)
            ?: error("fixture not found: $path"))
            .readBytes()
            .decodeToString()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val idx = line.lastIndexOf("::")
                require(idx >= 0) { "malformed cases line: $line" }
                line.substring(0, idx).trim() to line.substring(idx + 2).trim()
            }
            .toList()
}
