package org.tatrman.ttrp.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.Assignment

/**
 * Trivia attach (C2-f / P4 groundwork): comments ride the HIDDEN channel and attach
 * to statements — nearest preceding hidden tokens are leading; same-line following
 * comments are trailing; a same-line trailing comment is not also counted as the
 * next statement's leading.
 */
class TtrpTriviaSpec :
    StringSpec({
        "a leading // comment attaches to its statement" {
            val doc = TtrpParser.parseString("// header note\na = load(files.x)\n").document
            val a = doc.statements.filterIsInstance<Assignment>().single()
            a.leadingTrivia.map { it.text } shouldBe listOf("// header note")
        }

        "a comment between two statements attaches to the second" {
            val src = "a = load(files.x)\n// between\nb = load(files.y)\n"
            val doc = TtrpParser.parseString(src).document
            val stmts = doc.statements.filterIsInstance<Assignment>()
            stmts[0].trailingTrivia shouldBe emptyList()
            stmts[1].leadingTrivia.map { it.text } shouldBe listOf("// between")
        }

        "a trailing same-line comment attaches to its statement and not the next" {
            val src = "a = load(files.x) // trailing\nb = load(files.y)\n"
            val doc = TtrpParser.parseString(src).document
            val stmts = doc.statements.filterIsInstance<Assignment>()
            stmts[0].trailingTrivia.map { it.text } shouldBe listOf("// trailing")
            stmts[1].leadingTrivia shouldBe emptyList()
        }
    })
