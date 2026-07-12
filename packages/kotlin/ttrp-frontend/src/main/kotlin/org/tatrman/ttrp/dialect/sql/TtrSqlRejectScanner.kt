// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.sql

import org.antlr.v4.runtime.Token
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.dialect.RejectTable
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.parser.generated.TTRSqlLexer

/**
 * Names the curated TTR-SQL rejects (T6.1.1 table) from the token stream BEFORE the
 * parser can emit a bare syntax error (C2-g: rejects are named grammar rejects). One
 * primary diagnostic per fragment — the first match in a deterministic priority order
 * (P2). Messages/suggestions come from the reject table (single source), never inlined.
 */
class TtrSqlRejectScanner(
    private val table: RejectTable,
    private val loc: TtrSqlLoc,
) {
    private data class Rule(
        val id: String,
        val diagId: TtrpDiagnosticId,
        val at: (List<Token>) -> Token?,
    )

    /** The default-channel tokens (comments/WS already off-channel). */
    fun scan(tokens: List<Token>): TtrpDiagnostic? {
        for (rule in rules) {
            val tok = rule.at(tokens)
            if (tok != null) return diag(rule.id, rule.diagId, tok)
        }
        return null
    }

    private fun diag(
        id: String,
        diagId: TtrpDiagnosticId,
        at: Token,
    ): TtrpDiagnostic {
        val entry = table.entry(id)
        return TtrpDiagnostic(
            id = diagId,
            severity = Severity.ERROR,
            message = entry.message,
            location = loc.of(at),
            suggestedAlternative = entry.suggest,
        )
    }

    private fun firstOf(
        tokens: List<Token>,
        vararg types: Int,
    ): Token? = tokens.firstOrNull { it.type in types.toSet() }

    /** A token of [type] immediately followed by a token of [next]. */
    private fun adjacentPair(
        tokens: List<Token>,
        type: Int,
        next: Int,
    ): Token? {
        for (i in 0 until tokens.size - 1) {
            if (tokens[i].type == type && tokens[i + 1].type == next) return tokens[i]
        }
        return null
    }

    private val rules: List<Rule> =
        listOf(
            // 001 — DML/DDL head token anywhere.
            Rule("TTRP-SQL-001", TtrpDiagnosticId.SQL_001) { t ->
                firstOf(
                    t,
                    TTRSqlLexer.INSERT,
                    TTRSqlLexer.UPDATE,
                    TTRSqlLexer.DELETE,
                    TTRSqlLexer.CREATE,
                    TTRSqlLexer.DROP,
                    TTRSqlLexer.ALTER,
                    TTRSqlLexer.TRUNCATE,
                    TTRSqlLexer.MERGE,
                )
            },
            // 008 — procedural: DECLARE or @var.
            Rule("TTRP-SQL-008", TtrpDiagnosticId.SQL_008) { t ->
                firstOf(t, TTRSqlLexer.DECLARE, TTRSqlLexer.AT)
            },
            // 009 — a `;` that is not the sole trailing token (statement chain).
            Rule("TTRP-SQL-009", TtrpDiagnosticId.SQL_009) { t ->
                val semis = t.filter { it.type == TTRSqlLexer.SEMI }
                val nonEof = t.filter { it.type != Token.EOF }
                semis.firstOrNull { it != nonEof.lastOrNull() }
                    ?: if (semis.isNotEmpty() && nonEof.size > 1) semis.first() else null
            },
            // 010 — SELECT … INTO t.
            Rule("TTRP-SQL-010", TtrpDiagnosticId.SQL_010) { t -> firstOf(t, TTRSqlLexer.INTO) },
            // 002 — vendor TOP.
            Rule("TTRP-SQL-002", TtrpDiagnosticId.SQL_002) { t -> firstOf(t, TTRSqlLexer.TOP) },
            // 003 — hints: NOLOCK, or `WITH (` (WITH followed by a paren = table hint, not a CTE).
            Rule("TTRP-SQL-003", TtrpDiagnosticId.SQL_003) { t ->
                firstOf(t, TTRSqlLexer.NOLOCK) ?: adjacentPair(t, TTRSqlLexer.WITH, TTRSqlLexer.LPAREN)
            },
            // 005 — `::` cast.
            Rule("TTRP-SQL-005", TtrpDiagnosticId.SQL_005) { t -> firstOf(t, TTRSqlLexer.DCOLON) },
            // 004 — backtick / bracket quoting.
            Rule("TTRP-SQL-004", TtrpDiagnosticId.SQL_004) { t ->
                firstOf(t, TTRSqlLexer.BACKTICK, TTRSqlLexer.LBRACKET)
            },
            // 007 — window function OVER.
            Rule("TTRP-SQL-007", TtrpDiagnosticId.SQL_007) { t -> firstOf(t, TTRSqlLexer.OVER) },
            // 011 — out-of-cut relational syntax.
            Rule("TTRP-SQL-011", TtrpDiagnosticId.SQL_011) { t ->
                firstOf(t, TTRSqlLexer.PIVOT, TTRSqlLexer.UNPIVOT, TTRSqlLexer.GROUPING, TTRSqlLexer.LATERAL)
            },
            // 013 — NATURAL JOIN / USING.
            Rule("TTRP-SQL-013", TtrpDiagnosticId.SQL_013) { t ->
                firstOf(t, TTRSqlLexer.NATURAL, TTRSqlLexer.USING)
            },
            // 012 — derived table in FROM: `FROM (`.
            Rule("TTRP-SQL-012", TtrpDiagnosticId.SQL_012) { t ->
                adjacentPair(t, TTRSqlLexer.FROM, TTRSqlLexer.LPAREN)
            },
            // 006 — scalar/correlated subquery in an expression: `( SELECT` NOT preceded by IN/EXISTS/AS.
            Rule("TTRP-SQL-006", TtrpDiagnosticId.SQL_006) { t ->
                for (i in 0 until t.size - 1) {
                    if (t[i].type == TTRSqlLexer.LPAREN && t[i + 1].type == TTRSqlLexer.SELECT) {
                        val prev = if (i > 0) t[i - 1].type else -1
                        if (prev != TTRSqlLexer.IN && prev != TTRSqlLexer.EXISTS && prev != TTRSqlLexer.AS) {
                            return@Rule t[i]
                        }
                    }
                }
                null
            },
        )

    companion object {
        /** A generic (uncurated) syntax reject when the parser fails and no rule matched. */
        fun generic(
            table: RejectTable,
            location: SourceLocation,
        ): TtrpDiagnostic {
            val entry = table.entry("TTRP-SQL-015")
            return TtrpDiagnostic(
                id = TtrpDiagnosticId.SQL_015,
                severity = Severity.ERROR,
                message = entry.message,
                location = location,
                suggestedAlternative = entry.suggest,
            )
        }
    }
}
