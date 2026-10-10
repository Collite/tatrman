// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.b

import org.tatrman.ttrp.ast.FragmentDecomposition
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.dialect.RejectTable
import org.tatrman.ttrp.dialect.sql.TtrSqlLoc
import org.tatrman.ttrp.parser.TtrpParser

/**
 * The TTR-B fragment front door (T7.1.3/T7.1.7). Peeled `"""ttrb` / `"""ttrb-cs` interior +
 * host offset → canonical AST + host-mapped diagnostics. The [TtrbSkin] picks the keyword table
 * (AG B2). A curated reject (T7.1.2 table) is named BEFORE any bare syntax error (C2-g); a clean
 * parse decomposes sentence-wise (C2-a-β). The interior is never rewritten (C2-f) — this parse
 * is derived from the verbatim `sourceText`.
 */
object TtrB {
    val rejectTable: RejectTable = RejectTable.load("/rejects/ttr-b.rejects.toml")

    fun decompose(
        sourceText: String,
        interior: SourceLocation,
        outPort: String?,
        skin: TtrbSkin = TtrbSkin.EN,
    ): FragmentDecomposition {
        val loc = TtrSqlLoc(interior)
        val parsed = TtrbSyntax.parse(sourceText, skin)

        // 1) Curated reject scan — one primary diagnostic, wins over syntax noise.
        val reject = TtrbRejectScanner(rejectTable, loc, skin).scan(parsed.tokens)
        if (reject != null) return FragmentDecomposition(emptyList(), listOf(reject), emptyList())

        // 2) Parse.
        val syntaxError = parsed.syntaxErrors.firstOrNull()
        if (syntaxError != null) {
            val at = syntaxError.token?.let { loc.of(it) } ?: interior
            return FragmentDecomposition(emptyList(), listOf(TtrbRejectScanner.generic(at)), emptyList())
        }

        // 3) Decompose.
        val result = TtrbDecomposer(loc, TtrpParser.catalog, skin).decompose(parsed.tree, outPort)
        return FragmentDecomposition(result.statements, result.diagnostics, result.derivedInPorts, result.actionOutputs)
    }
}
