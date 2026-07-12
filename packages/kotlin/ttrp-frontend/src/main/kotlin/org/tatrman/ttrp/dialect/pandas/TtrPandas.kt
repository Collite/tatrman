// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.pandas

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.tatrman.ttrp.ast.Chain
import org.tatrman.ttrp.ast.FragmentDecomposition
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.dialect.RejectTable
import org.tatrman.ttrp.dialect.sql.TtrSqlLoc
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.parser.TtrpParser
import org.tatrman.ttrp.parser.generated.TTRPandasLexer
import org.tatrman.ttrp.parser.generated.TTRPandasParser

/**
 * The TTR-pandas fragment front door (T6.2.7). Peeled `"""pandas` interior + host offset
 * → canonical AST + host-mapped diagnostics. A curated reject (T6.2.1) is named before
 * any bare syntax error (C2-g); a clean parse decomposes statement-wise (C2-a-β) and runs
 * the S15 ordered-`.limit()` check (`TTRP-PD-009`). Interior never rewritten (C2-f).
 */
object TtrPandas {
    val rejectTable: RejectTable = RejectTable.load("/rejects/ttr-pandas.rejects.toml")

    fun decompose(
        sourceText: String,
        interior: SourceLocation,
        outPort: String?,
    ): FragmentDecomposition {
        val loc = TtrSqlLoc(interior)
        val lexer = TTRPandasLexer(CharStreams.fromString(sourceText))
        lexer.removeErrorListeners()
        val tokenStream = CommonTokenStream(lexer)
        tokenStream.fill()
        val defaultTokens = tokenStream.tokens.filter { it.channel == Token.DEFAULT_CHANNEL }

        val reject = TtrPandasRejectScanner(rejectTable, loc).scan(defaultTokens)
        if (reject != null) return FragmentDecomposition(emptyList(), listOf(reject), emptyList())

        val parser = TTRPandasParser(tokenStream)
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
            return FragmentDecomposition(
                emptyList(),
                listOf(TtrPandasRejectScanner.generic(rejectTable, syntaxError!!)),
                emptyList(),
            )
        }

        val result = TtrPandasDecomposer(loc, TtrpParser.catalog).decompose(tree, outPort)
        val diags = orderedLimitCheck(result.statements)
        return FragmentDecomposition(result.statements, diags, result.derivedInPorts)
    }

    /** S15 mirror → `TTRP-PD-009`: a `.limit()` with no governing `.sort()` in its chain. */
    private fun orderedLimitCheck(statements: List<Statement>): List<TtrpDiagnostic> {
        val out = mutableListOf<TtrpDiagnostic>()

        fun scan(chain: Chain) {
            var sawSort = false
            for (elem in chain.elements) {
                if (elem is OpCall && elem.name == "sort") sawSort = true
                if (elem is OpCall && elem.name == "limit" && !sawSort) {
                    val e = rejectTable.entry("TTRP-PD-009")
                    out += TtrpDiagnostic(TtrpDiagnosticId.PD_009, Severity.ERROR, e.message, elem.location, e.suggest)
                }
            }
        }
        for (s in statements) {
            when (s) {
                is org.tatrman.ttrp.ast.Assignment -> scan(s.chain)
                is org.tatrman.ttrp.ast.ChainStmt -> scan(s.chain)
                else -> Unit
            }
        }
        return out
    }
}
