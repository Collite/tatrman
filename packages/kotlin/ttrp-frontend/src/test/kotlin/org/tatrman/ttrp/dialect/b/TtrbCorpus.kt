package org.tatrman.ttrp.dialect.b

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.tatrman.ttrp.ast.FragmentDecomposition
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.parser.generated.TTRBLexer
import org.tatrman.ttrp.parser.generated.TTRBParser

/**
 * Test-only helper: loads a `.ttrb` corpus fixture and runs the generated TTR-B parser
 * over its verbatim bytes (the bare-program interior IS the fixture — `#` header comments
 * ride the hidden channel). Returns the parse tree + collected syntax errors so the parse
 * specs can assert clean acceptance without the full decompose pipeline.
 */
object TtrbCorpus {
    data class Parsed(
        val tree: TTRBParser.FragmentProgramContext,
        val syntaxErrors: List<String>,
    )

    fun read(rel: String): String =
        TtrbCorpus::class.java
            .getResourceAsStream("/ttrb/$rel")
            ?.readBytes()
            ?.decodeToString()
            ?: error("corpus fixture not found: /ttrb/$rel")

    /** Parse arbitrary TTR-B source (a fixture body or a single sentence). */
    fun parse(source: String): Parsed {
        val lexer = TTRBLexer(CharStreams.fromString(source))
        lexer.removeErrorListeners()
        val tokens = CommonTokenStream(lexer)
        val parser = TTRBParser(tokens)
        parser.removeErrorListeners()
        val errors = mutableListOf<String>()
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
                    errors += "$line:$col $msg"
                }
            },
        )
        val tree = parser.fragmentProgram()
        return Parsed(tree, errors)
    }

    fun parseFixture(rel: String): Parsed = parse(read(rel))

    /** Decompose a bare `.ttrb` fixture directly (no host container) — for the decomposition specs. */
    fun decompose(
        rel: String,
        outPort: String? = null,
    ): FragmentDecomposition {
        val src = read(rel)
        val interior =
            SourceLocation(
                file = "/ttrb/$rel",
                line = 1,
                column = 0,
                endLine = 1,
                endColumn = 0,
                offsetStart = 0,
                offsetEnd = src.length,
            )
        return TtrB.decompose(src, interior, outPort)
    }
}
