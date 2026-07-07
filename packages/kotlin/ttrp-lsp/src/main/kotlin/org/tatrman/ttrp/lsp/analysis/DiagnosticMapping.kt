package org.tatrman.ttrp.lsp.analysis

import com.google.gson.JsonObject
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic

/**
 * Front-half diagnostic → LSP `Diagnostic`. The `code` is the stable `TTRP-<AREA>-<NNN>`
 * id; `source = "ttrp"`; the suggested alternative is appended to the message (contracts
 * §8) and carried structurally in `data` for the Stage-4.2 quick-fix path.
 */
object DiagnosticMapping {
    /**
     * ANTLR-style [SourceLocation] (1-indexed line, 0-indexed column) → LSP [Range]
     * (0-indexed line + character). An UNKNOWN location clamps to the document start.
     */
    fun rangeOf(loc: SourceLocation): Range {
        if (loc.line < 1) return Range(Position(0, 0), Position(0, 0))
        val start = Position(loc.line - 1, maxOf(0, loc.column))
        val endLine = if (loc.endLine >= 1) loc.endLine - 1 else loc.line - 1
        val endColumn = if (loc.endColumn >= 0) loc.endColumn else loc.column
        return Range(start, Position(endLine, maxOf(0, endColumn)))
    }

    fun toLsp(d: TtrpDiagnostic): Diagnostic {
        val diag = Diagnostic()
        diag.range = rangeOf(d.location)
        diag.code =
            org.eclipse.lsp4j.jsonrpc.messages.Either
                .forLeft<String, Int>(d.id.id)
        diag.source = "ttrp"
        diag.severity = severityOf(d.severity)
        diag.message =
            d.suggestedAlternative?.let { "${d.message}\n↳ suggested: $it" } ?: d.message
        diag.data =
            JsonObject().apply {
                addProperty("id", d.id.id)
                d.suggestedAlternative?.let { addProperty("suggestedAlternative", it) }
            }
        return diag
    }

    private fun severityOf(s: Severity): DiagnosticSeverity =
        when (s) {
            Severity.ERROR -> DiagnosticSeverity.Error
            Severity.WARNING -> DiagnosticSeverity.Warning
            Severity.INFO -> DiagnosticSeverity.Information
        }
}
