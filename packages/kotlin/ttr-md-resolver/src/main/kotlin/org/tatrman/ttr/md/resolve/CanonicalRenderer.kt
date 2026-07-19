// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

/**
 * Render a [CanonicalPath] to its canonical text (§3):
 * `sales[customer.name: "Kaufland", time.year: 2025].net @ sum`. Dimension names are lowercased,
 * coordinates appear in the caller-supplied order (the cubelet's declared dimension order — the
 * resolver sorts before calling), string members are quoted and numeric members bare, `Star` is `*`.
 */
object CanonicalRenderer {
    fun render(path: CanonicalPath): String {
        val coords = path.coordinates.joinToString(", ") { renderCoordinate(it) }
        return "${path.cubelet}[$coords].${path.measure} @ ${path.agg.name.lowercase()}"
    }

    private fun renderCoordinate(c: Coordinate): String {
        val dim = c.dimension.lowercase()
        val attr = c.attribute.substringAfterLast('.')
        return "$dim.$attr: ${renderSelector(c.selector)}"
    }

    private fun renderSelector(s: Selector): String =
        when (s) {
            is Selector.Pinned -> renderMember(s.member)
            is Selector.MemberSet -> s.members.joinToString(", ", "{", "}") { renderMember(it) }
            is Selector.Range -> "${renderMember(s.lo)}..${renderMember(s.hi)}"
            Selector.Star -> "*"
        }

    /** Numeric members render bare; everything else is quoted (§6 quoting rule). */
    private fun renderMember(m: MemberRef): String =
        if (m.text.isNotEmpty() && m.text.all { it.isDigit() }) m.text else "\"${m.text}\""
}
