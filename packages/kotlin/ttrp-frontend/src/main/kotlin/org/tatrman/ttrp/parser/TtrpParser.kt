package org.tatrman.ttrp.parser

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.parser.generated.TTRPLexer
import org.tatrman.ttrp.parser.generated.TTRPParser
import java.nio.file.Files
import java.nio.file.Path

/** Parse output: the AST, all diagnostics (syntax + Stage-1.1 named rejects), and the verbatim source. */
data class TtrpParseResult(
    val document: TtrpDocument,
    val diagnostics: List<TtrpDiagnostic>,
    val source: String,
)

/**
 * The TTR-P parser front door. Mirrors the TTR-M `parseString`/`parseFile` shape.
 * ANTLR syntax errors surface as `TTRP-PRS-001` with an accurate [SourceLocation]
 * (collected via a [BaseErrorListener] — never printed to stderr). The default
 * error strategy stays on so a bad statement does not swallow the rest of the
 * document (Stage 1.1 error-recovery baseline).
 */
object TtrpParser {
    fun parseString(
        source: String,
        fileName: String = "<memory>",
    ): TtrpParseResult {
        val syntax = mutableListOf<TtrpDiagnostic>()
        val listener = SyntaxErrorListener(fileName, syntax)

        val lexer = TTRPLexer(CharStreams.fromString(source))
        lexer.removeErrorListeners()
        lexer.addErrorListener(listener)
        val tokens = CommonTokenStream(lexer)
        val parser = TTRPParser(tokens)
        parser.removeErrorListeners()
        parser.addErrorListener(listener)

        val tree = parser.document()
        val document = TtrpWalker(fileName, source, tokens).walk(tree)
        val diagnostics = syntax + TtrpChecks.run(document)
        return TtrpParseResult(document = document, diagnostics = diagnostics, source = source)
    }

    fun parseFile(path: Path): TtrpParseResult = parseString(Files.readString(path), path.toString())

    private class SyntaxErrorListener(
        private val fileName: String,
        private val out: MutableList<TtrpDiagnostic>,
    ) : BaseErrorListener() {
        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String,
            e: RecognitionException?,
        ) {
            val token = offendingSymbol as? Token
            val location =
                if (token != null) {
                    SourceLocation(
                        file = fileName,
                        line = line,
                        column = charPositionInLine,
                        endLine = line,
                        endColumn = charPositionInLine + (token.text?.length ?: 1),
                        offsetStart = token.startIndex,
                        offsetEnd = token.stopIndex + 1,
                    )
                } else {
                    SourceLocation(fileName, line, charPositionInLine, line, charPositionInLine + 1, -1, -1)
                }
            out +=
                TtrpDiagnostic(
                    id = TtrpDiagnosticId.PRS_001,
                    severity = Severity.ERROR,
                    message = msg,
                    location = location,
                )
        }
    }
}
