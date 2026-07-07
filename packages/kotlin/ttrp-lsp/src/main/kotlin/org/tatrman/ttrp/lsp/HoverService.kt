package org.tatrman.ttrp.lsp

import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.tatrman.ttrp.expr.Column
import org.tatrman.ttrp.lsp.nav.SourceNav
import org.tatrman.ttrp.lsp.nav.VarRefs
import org.tatrman.ttrp.resolve.ErRewrite
import org.tatrman.ttrp.resolve.TtrpChecker

/**
 * Hover content (T4.1.5): expression/port types + er provenance (E-d). Rendering order:
 *  1. an er-rewritten reference renders its **provenance** first (E-d: diagnostics, view,
 *     and lineage all render through the recorded origin, never re-derived);
 *  2. a variable / port ref renders its resolved output-column schema;
 *  3. otherwise no hover.
 */
class HoverService {
    fun hover(
        report: TtrpChecker.Report,
        pos: Position,
    ): Hover? {
        // 1. er provenance (mandatory, E-d) — match the rewrite whose span covers the cursor.
        report.rewrites.firstOrNull { SourceNav.contains(it.location, pos) }?.let {
            return markdown(renderProvenance(it), it)
        }

        // 2. variable / port reference — resolve its schema from the dataflow pass.
        val doc = report.document
        val scope = SourceNav.scopeStatements(doc, pos)
        val inPorts = SourceNav.containerAt(doc, pos)?.ports?.map { it.name } ?: emptyList()
        val varNames = VarRefs.variableNames(scope, inPorts)
        val occurrence = VarRefs.occurrences(scope, varNames).firstOrNull { SourceNav.contains(it.location, pos) }
        if (occurrence != null) {
            // Look the schema up within the cursor's own scope so same-named vars in other
            // containers can't shadow it (schemas is keyed by scope label then name).
            val cols = report.schemas[SourceNav.scopeLabel(doc, pos)]?.get(occurrence.name)
            return markdown(renderVariable(occurrence.name, cols), locationHover = occurrence.location)
        }
        return null
    }

    private fun renderProvenance(rw: ErRewrite): String {
        val origin = rw.provenance.originQname
        return buildString {
            append("**`${rw.dbSpelling}`** ← `${rw.erSpelling}`  ")
            append("_(er→db rewrite)_\n\n")
            append("origin: `$origin`")
        }
    }

    private fun renderVariable(
        name: String,
        cols: List<Column>?,
    ): String =
        buildString {
            append("**`$name`**")
            if (cols == null) {
                append("\n\n_schema not resolved here_")
            } else {
                append(" — ${cols.size} column${if (cols.size == 1) "" else "s"}\n\n")
                append(cols.joinToString("\n") { "- `${it.name}`: ${it.type}" })
            }
        }

    private fun markdown(
        value: String,
        rw: ErRewrite,
    ): Hover = Hover(MarkupContent(MarkupKind.MARKDOWN, value), SourceNav.rangeOf(rw.location))

    private fun markdown(
        value: String,
        locationHover: org.tatrman.ttrp.ast.SourceLocation,
    ): Hover = Hover(MarkupContent(MarkupKind.MARKDOWN, value), SourceNav.rangeOf(locationHover))
}
