// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.codec.sql

import org.apache.calcite.config.Lex
import org.apache.calcite.sql.SqlNode
import org.apache.calcite.sql.parser.SqlParseException
import org.apache.calcite.sql.parser.SqlParser as CalciteSqlParser
import org.tatrman.translator.parser.impl.CalciteExtParserImpl

/**
 * Thin wrapper around Calcite's [CalciteSqlParser] tuned for the v1 pipeline.
 *
 * The parser is configured with [Lex.MYSQL_ANSI]:
 *   - identifiers are case-insensitive (matches LLM-generated SQL conventions)
 *   - allows both backtick and double-quoted identifiers
 *   - lenient enough to accept common SQL Server / MS SQL inputs without
 *     pre-processing, while still being strict on grammar
 *
 * It also uses the same custom parser factory as `TranslatorFramework`
 * ([CalciteExtParserImpl.FACTORY]) so it understands the T-SQL extension productions the validator
 * accepts — `COLLATE`, `DATEADD`, `(TRY_)CONVERT`. Keeping the two parsers aligned is what lets
 * schema detection ([org.tatrman.translator.detect.SchemaDetector]) run on the exact SQL the
 * validator can compile; with the stock factory a query using any of those extensions failed to
 * parse here, detection returned `NOT_APPLICABLE`, and the caller silently fell back to the wrong
 * catalog (the "Fix B" gap — CalciteExtParser port, decision D7).
 *
 * Calcite engagement rule #5: this wrapper sticks to standard SQL parsing —
 * the planner is for custom rules, not classical optimisation. Parse errors
 * are surfaced as structured [SqlParseError] payloads with a code, location,
 * and human message; never as raw exceptions to callers.
 */
object SqlParser {
    fun parseQuery(text: String): ParseResult {
        val config =
            CalciteSqlParser
                .config()
                .withLex(Lex.MYSQL_ANSI)
                .withParserFactory(CalciteExtParserImpl.FACTORY)
        val parser = CalciteSqlParser.create(text, config)
        return try {
            ParseResult.Success(parser.parseQuery())
        } catch (ex: SqlParseException) {
            val pos = ex.pos
            ParseResult.Failure(
                SqlParseError(
                    code = "parse_failed",
                    message = ex.message ?: "SQL parse failed",
                    line = pos?.lineNum ?: -1,
                    column = pos?.columnNum ?: -1,
                ),
            )
        }
    }
}

sealed interface ParseResult {
    data class Success(
        val sqlNode: SqlNode,
    ) : ParseResult

    data class Failure(
        val error: SqlParseError,
    ) : ParseResult
}

/** Structured parse error. Mirrors the shape of `metadata.proto`'s ResponseMessage. */
data class SqlParseError(
    val code: String,
    val message: String,
    val line: Int,
    val column: Int,
)
