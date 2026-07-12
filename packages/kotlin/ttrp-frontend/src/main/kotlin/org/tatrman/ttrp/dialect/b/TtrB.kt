// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.b

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.tatrman.ttrp.ast.FragmentDecomposition
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.dialect.RejectTable
import org.tatrman.ttrp.dialect.sql.TtrSqlLoc
import org.tatrman.ttrp.parser.TtrpParser
import org.tatrman.ttrp.parser.generated.TTRBLexer
import org.tatrman.ttrp.parser.generated.TTRBParser

/**
 * The TTR-B fragment front door (T7.1.3/T7.1.7). Peeled `"""ttrb` interior + host offset →
 * canonical AST + host-mapped diagnostics. A curated reject (T7.1.2 table) is named BEFORE
 * any bare syntax error (C2-g); a clean parse decomposes sentence-wise (C2-a-β). The
 * interior is never rewritten (C2-f) — this parse is derived from the verbatim `sourceText`.
 */
object TtrB {
    val rejectTable: RejectTable = RejectTable.load("/rejects/ttr-b.rejects.toml")

    fun decompose(
        sourceText: String,
        interior: SourceLocation,
        outPort: String?,
    ): FragmentDecomposition {
        val loc = TtrSqlLoc(interior)
        val diags = mutableListOf<TtrpDiagnostic>()

        val lexer = TTRBLexer(CharStreams.fromString(sourceText))
        lexer.removeErrorListeners()
        val tokenStream = CommonTokenStream(lexer)
        tokenStream.fill()
        val defaultTokens = tokenStream.tokens.filter { it.channel == Token.DEFAULT_CHANNEL }

        // 1) Curated reject scan — one primary diagnostic, wins over syntax noise.
        val reject = TtrbRejectScanner(rejectTable, loc).scan(defaultTokens)
        if (reject != null) return FragmentDecomposition(emptyList(), listOf(reject), emptyList())

        // 2) Parse.
        val parser = TTRBParser(tokenStream)
        parser.removeErrorListeners()
        var syntaxError: SourceLocation? = null
        parser.addErrorListener(
            object : BaseErrorListener() {
                override fun syntaxError(
                    r: Recognizer<*, *>?,
                    sym: Any?,
                    line: Int,
                    col: Int,
                    msg: String,
                    e: RecognitionException?,
                ) {
                    if (syntaxError == null) syntaxError = (sym as? Token)?.let { loc.of(it) } ?: interior
                }
            },
        )
        val tree = parser.fragmentProgram()
        if (syntaxError != null) {
            return FragmentDecomposition(emptyList(), listOf(TtrbRejectScanner.generic(syntaxError!!)), emptyList())
        }

        // 3) Decompose.
        val result = TtrbDecomposer(loc, TtrpParser.catalog).decompose(tree, outPort)
        return FragmentDecomposition(result.statements, diags, result.derivedInPorts)
    }
}
