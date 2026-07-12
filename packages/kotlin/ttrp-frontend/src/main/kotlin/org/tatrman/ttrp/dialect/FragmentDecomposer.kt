// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect

import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.ast.PortKind
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.dialect.b.TtrB
import org.tatrman.ttrp.dialect.pandas.TtrPandas
import org.tatrman.ttrp.dialect.sql.TtrSql

/**
 * Runs after the canonical walk (C2-a-β): for every `"""sql`/`"""pandas` container it
 * lowers the fragment interior to canonical AST and attaches it to the [FragmentBody]
 * ([FragmentBody.decomposition]), so `TtrpChecker` and `GraphBuilder` reuse their
 * FlowBody paths. `ttrb` stays opaque (P7). The interior `sourceText` is untouched (C2-f).
 */
object FragmentDecomposer {
    data class Result(
        val document: TtrpDocument,
        val diagnostics: List<TtrpDiagnostic>,
    )

    fun run(doc: TtrpDocument): Result {
        val diags = mutableListOf<TtrpDiagnostic>()
        val statements =
            doc.statements.map { stmt ->
                if (stmt is ContainerDecl && stmt.body is FragmentBody && stmt.body.decomposition == null) {
                    val body = stmt.body as FragmentBody
                    val decomp = decompose(stmt, body)
                    if (decomp != null) {
                        diags += decomp.diagnostics
                        stmt.copy(body = body.copy(decomposition = decomp))
                    } else {
                        stmt
                    }
                } else {
                    stmt
                }
            }
        return Result(doc.copy(statements = statements), diags)
    }

    private fun decompose(
        decl: ContainerDecl,
        body: FragmentBody,
    ): org.tatrman.ttrp.ast.FragmentDecomposition? {
        val outPort = decl.ports.firstOrNull { it.kind == PortKind.OUT }?.name
        return when (body.tag) {
            "sql" -> TtrSql.decompose(body.sourceText, body.interiorLocation, outPort)
            "pandas" -> TtrPandas.decompose(body.sourceText, body.interiorLocation, outPort)
            "ttrb" -> TtrB.decompose(body.sourceText, body.interiorLocation, outPort)
            else -> null // unknown tag (FRG-001 handled upstream)
        }
    }
}
