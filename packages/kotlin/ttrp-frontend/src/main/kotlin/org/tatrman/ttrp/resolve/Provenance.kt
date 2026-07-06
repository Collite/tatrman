package org.tatrman.ttrp.resolve

import org.tatrman.ttrp.ast.SourceLocation

/**
 * The origin of an er-rewritten node/expression (E-d, mandatory provenance). Every
 * er→db rewrite carries one so diagnostics, lineage and the graphical surface can
 * render the er spelling the analyst wrote — never the bare db spelling.
 */
data class Provenance(
    val originQname: String,
    val originName: String,
    val originLocation: SourceLocation?,
)

/**
 * One recorded er→db rewrite: the er spelling (what the analyst wrote), the db
 * spelling it rewrote to, and the provenance. `renderErFirst()` is the E-d render
 * path ("`amount` (bound to `AMOUNT`)").
 */
data class ErRewrite(
    val erSpelling: String,
    val dbSpelling: String,
    val provenance: Provenance,
    val location: SourceLocation,
) {
    fun renderErFirst(): String = "`$erSpelling` (bound to `$dbSpelling`)"
}
