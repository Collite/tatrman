// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.b

import org.antlr.v4.runtime.Token
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.dialect.RejectTable
import org.tatrman.ttrp.dialect.sql.TtrSqlLoc
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.parser.generated.TTRBLexer

/**
 * Names the curated TTR-B rejects (T7.1.2 table) from the token stream BEFORE the parser
 * can emit a bare syntax error (C2-g: rejects are named grammar rejects). One primary
 * diagnostic per fragment, first match in a deterministic priority order (P2, no NLP).
 * Messages/suggestions come from the reject table (single source), never inlined.
 */
class TtrbRejectScanner(
    private val table: RejectTable,
    private val loc: TtrSqlLoc,
) {
    /** Default-channel tokens (`#` comments already off-channel). */
    fun scan(tokens: List<Token>): TtrpDiagnostic? {
        // 1. C-style comment lexis `//` / `/*` (S19) — highest priority (it hides the real sentence).
        adjacent(
            tokens,
            TTRBLexer.SLASH,
            TTRBLexer.SLASH,
        )?.let { return diag("TTRP-B-005", TtrpDiagnosticId.B_005, it) }
        adjacent(tokens, TTRBLexer.SLASH, TTRBLexer.STAR)?.let { return diag("TTRP-B-005", TtrpDiagnosticId.B_005, it) }
        // 2. Non-ASCII in the sentence (English-only, S20): the lexer's UNMATCHED catch-all.
        tokens.firstOrNull { it.type == TTRBLexer.UNMATCHED }?.let {
            return diag(
                "TTRP-B-006",
                TtrpDiagnosticId.B_006,
                it,
            )
        }
        // 3. `==` used as equality (S9) — the shared canonical diagnostic, not a new B id.
        tokens.firstOrNull { it.type == TTRBLexer.EQEQ }?.let {
            return diag(
                "TTRP-EQ-001",
                TtrpDiagnosticId.EQ_001,
                it,
            )
        }
        // 4. Sentence-initial non-roster verbs (verbs are keywords; a sentence-initial IDENT is off-roster).
        for (tok in sentenceInitial(tokens)) {
            when (tok.text.lowercase()) {
                "update" -> return diag("TTRP-B-001", TtrpDiagnosticId.B_001, tok)
                "insert" -> return diag("TTRP-B-002", TtrpDiagnosticId.B_002, tok)
                "drop", "truncate", "alter" -> return diag("TTRP-B-003", TtrpDiagnosticId.B_003, tok)
                "pivot" -> return diag("TTRP-B-008", TtrpDiagnosticId.B_008, tok)
            }
        }
        // 5. Unknown verbose comparison: `is <word> <operand>` where <word> is not a table phrase
        //    (a closed 3-token window — deterministic, never fuzzy; C4-c).
        unknownVerbose(tokens)?.let { return diag("TTRP-B-007", TtrpDiagnosticId.B_007, it) }
        // 6. Catch-all: any sentence-initial off-roster word.
        sentenceInitial(tokens).firstOrNull()?.let { return diag("TTRP-B-004", TtrpDiagnosticId.B_004, it) }
        return null
    }

    /**
     * Tokens in sentence-verb position: the first token, or the first token on a line after a
     * sentence-terminating `.` (a `.` at line end — distinct from a dotted-ref `.` like
     * `sales.account_id`, which sits mid-line). Deterministic (P2): one sentence per line.
     */
    private fun sentenceInitial(tokens: List<Token>): List<Token> {
        val out = mutableListOf<Token>()
        var prev: Token? = null
        for (t in tokens) {
            if (t.type == Token.EOF) break
            val initial = prev.let { it == null || (it.type == TTRBLexer.DOT && t.line > it.line) }
            if (initial && t.type == TTRBLexer.IDENT) out += t
            prev = t
        }
        return out
    }

    private fun adjacent(
        tokens: List<Token>,
        a: Int,
        b: Int,
    ): Token? {
        for (i in 0 until tokens.size - 1) {
            if (tokens[i].type == a && tokens[i + 1].type == b) return tokens[i]
        }
        return null
    }

    private val operandStart = setOf(TTRBLexer.IDENT, TTRBLexer.NUMBER, TTRBLexer.STRING, TTRBLexer.CHAR_STRING)

    private fun unknownVerbose(tokens: List<Token>): Token? {
        for (i in 0 until tokens.size - 2) {
            if (tokens[i].type == TTRBLexer.IS &&
                tokens[i + 1].type == TTRBLexer.IDENT &&
                tokens[i + 2].type in operandStart
            ) {
                return tokens[i + 1]
            }
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

    companion object {
        /** A generic (uncurated) TTR-B syntax reject when the parser fails and no rule matched. */
        fun generic(location: SourceLocation): TtrpDiagnostic =
            TtrpDiagnostic(
                id = TtrpDiagnosticId.B_004,
                severity = Severity.ERROR,
                message = "Not a TTR-B sentence (C4-b roster).",
                location = location,
                suggestedAlternative = TtrpDiagnosticId.B_004.suggestedAlternative,
            )
    }
}
