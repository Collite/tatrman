// SPDX-License-Identifier: Apache-2.0
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
 *
 * [joinCondition] is populated ONLY for an `on: relation X` join rewrite (T2.1.0):
 * the port-qualified equality [org.tatrman.ttrp.expr.Expression] synthesized from
 * the relation's bound join pairs (`op.eq`/`op.and` catalogue ids). It is null for
 * every ordinary entity/attribute rewrite. Stage 2.1's GraphBuilder reads it onto
 * the `Join` node's `on` condition (the first consumer, per review-001 1.3-A).
 */
data class ErRewrite(
    val erSpelling: String,
    val dbSpelling: String,
    val provenance: Provenance,
    val location: SourceLocation,
    val joinCondition: org.tatrman.ttrp.expr.Expression? = null,
) {
    fun renderErFirst(): String = "`$erSpelling` (bound to `$dbSpelling`)"
}
