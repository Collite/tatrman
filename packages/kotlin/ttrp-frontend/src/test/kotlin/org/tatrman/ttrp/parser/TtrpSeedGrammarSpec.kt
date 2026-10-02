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

        // AG-P0: identifiers are the client's model names and are never translated (AG C-23) — the
        // TTR.g4 Latin-extended range (\u00C0-\u024F) lexes as IDENT in TTR-P too.
        "Czech identifiers (diacritics) lex as identifiers" {
            parse(
                """
                param zakázka_id: int
                container zakázka(out hlavička) target erp {
                    hlavička = load(zakázka) -> filter(id_zakázky = zakázka_id and stav_zakázky = 1)
                }
                zakázka.hlavička -> display(výsledek)
                """.trimIndent(),
            ) shouldBe 0
        }
    })
