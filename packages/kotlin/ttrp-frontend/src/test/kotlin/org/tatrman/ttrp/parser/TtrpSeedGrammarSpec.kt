// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.tatrman.ttrp.parser.generated.TTRPLexer
import org.tatrman.ttrp.parser.generated.TTRPParser

class TtrpSeedGrammarSpec :
    StringSpec({
        fun parse(src: String): Int {
            val lexer = TTRPLexer(CharStreams.fromString(src))
            val parser = TTRPParser(CommonTokenStream(lexer))
            parser.document()
            return parser.numberOfSyntaxErrors
        }
        "empty document parses" { parse("") shouldBe 0 }
        "comment-only document parses" { parse("// hello ttrp\n") shouldBe 0 }
    })
