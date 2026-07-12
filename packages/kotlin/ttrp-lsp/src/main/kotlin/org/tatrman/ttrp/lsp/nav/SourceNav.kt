// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.nav

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.ChainElem
import org.tatrman.ttrp.ast.ChainStmt
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.DottedRef
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.ast.TtrpDocument

/**
 * AST position/containment helpers shared by hover, definition and rename. Position
 * lookup over an AST the front-half produced is tree traversal, not language
 * understanding — it stays in the LSP; only *resolution* (schemas, provenance) is
 * front-half code.
 *
 * LSP positions are 0-indexed (line + UTF-16 character); [SourceLocation] is
 * ANTLR-style (1-indexed line, 0-indexed column, `endColumn` exclusive).
 */
object SourceNav {
    /** True if the 0-indexed LSP [pos] falls within [loc] (half-open on the end column). */
    fun contains(
        loc: SourceLocation,
        pos: Position,
    ): Boolean {
        if (loc.line < 1) return false
        val line = pos.line + 1
        val col = pos.character
        val afterStart = line > loc.line || (line == loc.line && col >= loc.column)
        val beforeEnd = line < loc.endLine || (line == loc.endLine && col < loc.endColumn)
        return afterStart && beforeEnd
    }

    fun rangeOf(loc: SourceLocation): Range {
        val startLine = maxOf(0, loc.line - 1)
        val endLine = if (loc.endLine >= 1) loc.endLine - 1 else startLine
        return Range(
            Position(startLine, maxOf(0, loc.column)),
            Position(endLine, maxOf(0, if (loc.endColumn >= 0) loc.endColumn else loc.column)),
        )
    }

    /** The container whose body encloses [pos], or null when [pos] is at program level. */
    fun containerAt(
        doc: TtrpDocument,
        pos: Position,
    ): ContainerDecl? = doc.statements.filterIsInstance<ContainerDecl>().firstOrNull { contains(it.location, pos) }

    /** The statements in [pos]'s scope: the enclosing container's body, else the program. */
    fun scopeStatements(
        doc: TtrpDocument,
        pos: Position,
    ): List<Statement> {
        val container = containerAt(doc, pos)
        if (container != null) return (container.body as? FlowBody)?.statements ?: emptyList()
        return doc.statements
    }

    /** A container-qualified scope label (`"crunch"` or `""` for program level) — the ζ-key prefix. */
    fun scopeLabel(
        doc: TtrpDocument,
        pos: Position,
    ): String = containerAt(doc, pos)?.name ?: ""

    /** Every [DottedRef] chain element in a statement list (recursing into containers). */
    fun dottedRefs(statements: List<Statement>): List<DottedRef> {
        val out = mutableListOf<DottedRef>()

        fun chain(elems: List<ChainElem>) {
            for (e in elems) if (e is DottedRef) out += e
        }
        for (s in statements) {
            when (s) {
                is Assignment -> chain(s.chain.elements)
                is ChainStmt -> chain(s.chain.elements)
                is ContainerDecl -> (s.body as? FlowBody)?.let { out += dottedRefs(it.statements) }
                else -> Unit
            }
        }
        return out
    }

    /** Assignments in a flat statement list (one scope level — no container recursion). */
    fun assignmentsIn(statements: List<Statement>): List<Assignment> = statements.filterIsInstance<Assignment>()

    /** The op calls of a chain, in order. */
    fun opCalls(elems: List<ChainElem>): List<OpCall> = elems.filterIsInstance<OpCall>()
}
