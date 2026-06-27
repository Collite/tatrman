package org.tatrman.ttr.parser.loader

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.tatrman.ttr.parser.diagnostics.DiagnosticCode
import org.tatrman.ttr.parser.generated.TTRLexer
import org.tatrman.ttr.parser.generated.TTRParser
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.ImportStatement
import org.tatrman.ttr.parser.model.ModelDirective
import org.tatrman.ttr.parser.walker.TtrWalker
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Public TTR parser entry point. Boots ANTLR, walks the parse tree, and
 * surfaces a [ParseResult] with typed [Definition]s plus structured errors.
 *
 * Three call shapes:
 *   - [parseString]    in-memory content, optional file label for diagnostics
 *   - [parseFile]      single `.ttrm` file
 *   - [parseDirectory] every `.ttrm` file under a root, recursive by default
 *
 * Syntax errors never throw; they accumulate on `ParseResult.errors`. On any
 * parser error, `ParseResult.definitions` is empty — no partial trees.
 */
object TtrLoader {
    fun parseString(
        content: String,
        fileLabel: String = "<inline>",
    ): ParseResult {
        val errors = mutableListOf<ParseError>()
        val lines = content.lines()
        val errorListener =
            object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String?,
                    e: RecognitionException?,
                ) {
                    val lineContent = lines.getOrNull(line - 1)?.take(200) ?: "<unknown>"
                    errors +=
                        ParseError(
                            file = fileLabel,
                            line = line,
                            column = charPositionInLine + 1,
                            message = "$msg\n        at: $lineContent",
                        )
                }
            }
        val lexer = TTRLexer(CharStreams.fromString(content))
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        val parser = TTRParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        val doc = parser.document()
        if (errors.isNotEmpty()) {
            return ParseResult(
                definitions = emptyList(),
                schemaDirective = null,
                errors = errors,
                sourceFile = fileLabel,
            )
        }
        val walked = TtrWalker(fileLabel).visitDocument(doc)
        return if (walked.errors.isNotEmpty()) {
            ParseResult(
                definitions = emptyList(),
                schemaDirective = null,
                errors = walked.errors,
                sourceFile = fileLabel,
            )
        } else {
            ParseResult(
                definitions = walked.definitions,
                schemaDirective = walked.schemaDirective,
                errors = emptyList(),
                warnings = walked.warnings,
                sourceFile = fileLabel,
                packageName = walked.packageName,
                imports = walked.imports,
            )
        }
    }

    fun parseFile(path: Path): ParseResult {
        val content =
            try {
                Files.readString(path)
            } catch (ex: Exception) {
                return ParseResult(
                    definitions = emptyList(),
                    schemaDirective = null,
                    errors =
                        listOf(
                            ParseError(
                                file = path.toString(),
                                line = -1,
                                column = -1,
                                message = "could not read file: ${ex.message}",
                            ),
                        ),
                    sourceFile = path.toString(),
                )
            }
        return parseString(content, fileLabel = path.toString())
    }

    /**
     * Filters to `*.ttrm` (model files); `*.ttrg` graphical artefacts are not
     * picked up since they do not match `.ttrm`. Skips `.modeler`, `node_modules`,
     * and `.git` directory subtrees — matching the TS `parseDirectory` in
     * `packages/parser/src/index.ts`.
     */
    fun parseDirectory(
        rootPath: Path,
        recursive: Boolean = true,
    ): List<ParseResult> {
        if (!Files.isDirectory(rootPath)) return emptyList()
        val results = mutableListOf<ParseResult>()
        Files.walkFileTree(
            rootPath,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult =
                    when {
                        dir == rootPath -> FileVisitResult.CONTINUE
                        dir.fileName.toString() in EXCLUDED_DIRS -> FileVisitResult.SKIP_SUBTREE
                        !recursive -> FileVisitResult.SKIP_SUBTREE
                        else -> FileVisitResult.CONTINUE
                    }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    val name = file.fileName.toString()
                    if (name.endsWith(".ttrm")) {
                        results += parseFile(file)
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return results.sortedBy { it.sourceFile }
    }

    private val EXCLUDED_DIRS = setOf(".modeler", "node_modules", ".git")

    @Suppress("unused")
    fun parseFile(path: String): ParseResult = parseFile(Paths.get(path))
}

/**
 * Result of one parse attempt.
 *
 * On success: `errors` is empty, `definitions` fully populated. On a syntax
 * error: `errors` carries entries and `definitions` is empty (no partial
 * trees). `ok` is gated on errors only; warnings never block ingestion.
 */
data class ParseResult(
    val definitions: List<Definition>,
    val schemaDirective: ModelDirective?,
    val errors: List<ParseError>,
    val sourceFile: String,
    val warnings: List<ParseWarning> = emptyList(),
    /** The `package <qualifiedName>` declared at the top of the file, if any. */
    val packageName: String? = null,
    /** All `import <qualifiedName> [.*]` statements in file order. */
    val imports: List<ImportStatement> = emptyList(),
) {
    val ok: Boolean get() = errors.isEmpty()
}

data class ParseError(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val code: DiagnosticCode = DiagnosticCode.ParseError,
) {
    override fun toString(): String = "$file:$line:$column: $message"
}

// NOTE (1.4): contracts.md §2.3 also gives ParseWarning a `code`, but the walker
// emits several warnings (empty search block, unknown language code, duplicate
// pattern, keyword whitespace, graph-block-ignored) that have no dedicated
// DiagnosticCode — and in the canonical TS architecture some of these (e.g.
// fuzzy-without-searchable) are validator diagnostics, not parser warnings.
// Coding ParseWarning cleanly needs either enum additions or moving those
// warnings to the (Phase 2) validator; deferred until that alignment.
data class ParseWarning(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
) {
    override fun toString(): String = "$file:$line:$column: $message"
}
