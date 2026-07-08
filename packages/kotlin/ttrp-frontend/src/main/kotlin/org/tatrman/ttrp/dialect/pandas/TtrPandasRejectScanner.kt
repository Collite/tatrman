package org.tatrman.ttrp.dialect.pandas

import org.antlr.v4.runtime.Token
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.dialect.RejectTable
import org.tatrman.ttrp.dialect.sql.TtrSqlLoc
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.parser.generated.TTRPandasLexer as L

/**
 * Names the curated TTR-pandas rejects (T6.2.1 table) from the token stream before the
 * parser emits a bare syntax error (C2-g). One primary diagnostic per fragment,
 * deterministic priority order (P2, no fuzzy matching — S17's closed roster). Messages
 * come from the reject table (single source).
 */
class TtrPandasRejectScanner(
    private val table: RejectTable,
    private val loc: TtrSqlLoc,
) {
    private val ioNames = setOf("to_sql", "read_csv", "to_csv", "to_parquet", "read_parquet", "read_sql")
    private val indexNames = setOf("set_index", "reset_index", "loc", "iloc")

    fun scan(tokens: List<Token>): TtrpDiagnostic? {
        // Token-level rejects first.
        firstOf(tokens, L.LAMBDA)?.let { return diag("TTRP-PD-002", TtrpDiagnosticId.PD_002, it) }
        firstOf(
            tokens,
            L.FOR,
            L.WHILE,
            L.IF,
            L.DEF,
            L.IMPORT,
        )?.let { return diag("TTRP-PD-005", TtrpDiagnosticId.PD_005, it) }

        // `pl.` / `pd.` engine-API ceremony: IDENT(pl|pd) DOT.
        for (i in 0 until tokens.size - 1) {
            if (tokens[i].type == L.IDENT && tokens[i].text in setOf("pl", "pd") && tokens[i + 1].type == L.DOT) {
                return diag("TTRP-PD-007", TtrpDiagnosticId.PD_007, tokens[i])
            }
        }

        // Method-name rejects: a `. name (` or `. name [` call (name is a NON-roster IDENT).
        for (i in 1 until tokens.size - 1) {
            if (tokens[i - 1].type == L.DOT && tokens[i].type == L.IDENT) {
                val name = tokens[i].text
                val next = tokens[i + 1].type
                if (next == L.LPAREN || next == L.LBRACKET) {
                    when {
                        name == "apply" -> return diag("TTRP-PD-002", TtrpDiagnosticId.PD_002, tokens[i])
                        name in ioNames -> return diag("TTRP-PD-003", TtrpDiagnosticId.PD_003, tokens[i])
                        name in indexNames -> return diag("TTRP-PD-006", TtrpDiagnosticId.PD_006, tokens[i])
                        name == "branch" -> return diag("TTRP-PD-008", TtrpDiagnosticId.PD_008, tokens[i])
                        else -> return diag("TTRP-PD-001", TtrpDiagnosticId.PD_001, tokens[i])
                    }
                }
            }
        }

        // Boolean-mask indexing: any remaining `[` (subscript is never valid TTR-pandas).
        firstOf(tokens, L.LBRACKET)?.let { return diag("TTRP-PD-004", TtrpDiagnosticId.PD_004, it) }
        return null
    }

    private fun firstOf(
        tokens: List<Token>,
        vararg types: Int,
    ): Token? = tokens.firstOrNull { it.type in types.toSet() }

    private fun diag(
        id: String,
        diagId: TtrpDiagnosticId,
        at: Token,
    ): TtrpDiagnostic {
        val entry = table.entry(id)
        return TtrpDiagnostic(diagId, Severity.ERROR, entry.message, loc.of(at), entry.suggest)
    }

    companion object {
        fun generic(
            table: RejectTable,
            location: SourceLocation,
        ): TtrpDiagnostic {
            val entry = table.entry("TTRP-PD-010")
            return TtrpDiagnostic(TtrpDiagnosticId.PD_010, Severity.ERROR, entry.message, location, entry.suggest)
        }
    }
}
