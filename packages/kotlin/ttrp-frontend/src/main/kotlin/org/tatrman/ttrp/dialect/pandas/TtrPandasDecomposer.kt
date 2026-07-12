// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.pandas

import org.tatrman.ttrp.ast.Arg
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.AssignEntry
import org.tatrman.ttrp.ast.Chain
import org.tatrman.ttrp.ast.ChainElem
import org.tatrman.ttrp.ast.ChainStmt
import org.tatrman.ttrp.ast.ConfigBlock
import org.tatrman.ttrp.ast.ConfigEntry
import org.tatrman.ttrp.ast.DottedRef
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.GroupByEntry
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.dialect.sql.TtrSqlLoc
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.catalog.FunctionCatalog
import org.tatrman.ttrp.parser.generated.TTRPandasParser as P

/**
 * Statement/SSA decomposition (C2-a-β): lowers a TTR-pandas method chain into canonical
 * TTR-P statements — the SAME AST canonical authoring emits, so `bare ≡ embedded ≡
 * canonical` graphs hold (6.3 KEY GATE). Assignment = SSA rebind (Q7-γ); the chain
 * receiver carries the left port of a `.join` (the C2-b-ii analogue for method chains);
 * the last value = the container default out (C2-c-ii). Roster 1:1: select→project,
 * calc→calc, filter→filter, join→join, aggregate→aggregate, sort→sort, union→union,
 * limit→limit, load→load, store→store, display→display.
 */
class TtrPandasDecomposer(
    private val loc: TtrSqlLoc,
    catalog: FunctionCatalog,
) {
    private val exprFolder = TtrPandasExpr(loc, catalog)
    private val pre = mutableListOf<Statement>()
    private val cteNames = mutableSetOf<String>()
    private val derivedSet = LinkedHashSet<String>()
    private var synth = 0

    data class Result(
        val statements: List<Statement>,
        val derivedInPorts: List<String>,
    )

    fun decompose(
        program: P.FragmentProgramContext,
        outPort: String?,
    ): Result {
        val out = mutableListOf<Statement>()
        for (stmt in program.statement()) {
            val name = stmt.target.text
            val chain = decomposeChain(stmt.chain())
            out += drainPre()
            out += Assignment(name, loc.of(stmt), chain, loc.of(stmt))
            cteNames += name
        }
        val finalChain = decomposeChain(program.finalValue().chain())
        out += drainPre()
        out +=
            if (outPort !=
                null
            ) {
                Assignment(outPort, loc.of(program.finalValue()), finalChain, loc.of(program.finalValue()))
            } else {
                ChainStmt(finalChain, loc.of(program.finalValue()))
            }
        return Result(out, derived())
    }

    private fun decomposeChain(chain: P.ChainContext): Chain {
        val elems = mutableListOf<ChainElem>()
        elems += head(chain.head())
        for (mc in chain.methodCall()) elems += methodCall(mc)
        return Chain(elems, loc.of(chain))
    }

    private fun head(h: P.HeadContext): ChainElem {
        h.loadCall()?.let { return loadCall(it) }
        val ref = h.ref()!!
        val name = ref.IDENT().joinToString(".") { it.text }
        return if (name.contains(".") && !isCte(name)) {
            loadOf(name, loc.of(ref))
        } else {
            noteExternal(name) // a bare receiver is an in-port ref (the bare wrapper synthesizes it, T6.3.3)
            dref(name, loc.of(ref))
        }
    }

    /** A bare name used as a node reference (chain head, join `right:`, union arm) is an external in-port. */
    private fun noteExternal(name: String) {
        if (name !in cteNames && !name.contains(".")) derivedSet += name
    }

    private fun methodCall(mc: P.MethodCallContext): ChainElem {
        val name = methodName(mc)
        val args = mc.argList()?.arg().orEmpty()
        val at = loc.of(mc)
        return when (name) {
            "select" -> opCall("project", positionalExprs(args).map { arg(null, it, at) }, null, at)
            "calc" -> opCall("calc", emptyList(), ConfigBlock(namedAssignEntries(args), at), at)
            "filter" -> opCall("filter", listOf(arg(null, firstExpr(args), at)), null, at)
            "join" -> joinCall(args, at)
            "aggregate" -> opCall("aggregate", emptyList(), ConfigBlock(aggregateEntries(args), at), at)
            "sort" -> opCall("sort", byColumns(args).map { arg(null, ColumnRef(null, it, at), at) }, null, at)
            "union" -> opCall("union", unionRefs(args, at), null, at)
            "limit" -> opCall("limit", listOf(arg(null, firstExpr(args), at)), null, at)
            "load" -> loadArgs(args, at)
            "store" -> opCall("store", listOf(arg(null, firstExpr(args), at)), null, at)
            "display" -> opCall("display", listOf(arg(null, firstExpr(args), at)), null, at)
            else ->
                opCall(
                    name,
                    positionalExprs(args).map {
                        arg(null, it, at)
                    },
                    null,
                    at,
                ) // out-of-roster (scanner already named it)
        }
    }

    private fun joinCall(
        args: List<P.ArgContext>,
        at: SourceLocation,
    ): ChainElem {
        val out = mutableListOf<Arg>()
        for (a in args) {
            val n = a.name?.text ?: continue
            when (n) {
                "right" -> out += arg("right", refOrChain(a, at), at)
                "on" -> out += arg("on", exprFolder.fold(a.expr()), at)
                "type" -> out += arg("type", exprFolder.fold(a.expr()), at)
            }
        }
        // left is the chain receiver (prev) — GraphBuilder's join uses prev when no `left:` arg.
        return opCall("join", out, null, at)
    }

    private fun loadArgs(
        args: List<P.ArgContext>,
        at: SourceLocation,
    ): ChainElem {
        val out = mutableListOf<Arg>()
        for (a in args) {
            if (a.name?.text == "schema") {
                out += arg("schema", exprFolder.fold(a.expr()), at)
            } else if (a.name == null) {
                out += arg(null, exprFolder.fold(a.expr()), at)
            }
        }
        return opCall("load", out, null, at)
    }

    private fun loadCall(lc: P.LoadCallContext): ChainElem {
        val args = lc.argList()?.arg().orEmpty()
        return loadArgs(args, loc.of(lc))
    }

    // ---- arg helpers ----

    private fun positionalExprs(args: List<P.ArgContext>): List<Expression> =
        args.filter { it.name == null }.map { exprFolder.fold(it.expr()) }

    private fun firstExpr(args: List<P.ArgContext>): Expression = exprFolder.fold(args.first { it.name == null }.expr())

    private fun namedAssignEntries(args: List<P.ArgContext>): List<ConfigEntry> =
        args.filter { it.name != null }.map { AssignEntry(it.name.text, exprFolder.fold(it.expr()), loc.of(it)) }

    private fun aggregateEntries(args: List<P.ArgContext>): List<ConfigEntry> {
        val entries = mutableListOf<ConfigEntry>()
        val byKeys = mutableListOf<String>()
        for (a in args) {
            when (a.name?.text) {
                "by" -> (exprFolder.fold(a.expr()) as? ColumnRef)?.let { byKeys += it.column }
                null -> Unit
                else -> entries += AssignEntry(a.name.text, exprFolder.fold(a.expr()), loc.of(a))
            }
        }
        val out = mutableListOf<ConfigEntry>()
        if (byKeys.isNotEmpty()) out += GroupByEntry(byKeys, loc.of(args.first()))
        out += entries
        return out
    }

    private fun byColumns(args: List<P.ArgContext>): List<String> =
        args
            .filter {
                it.name?.text == "by" || it.name == null
            }.mapNotNull { (exprFolder.fold(it.expr()) as? ColumnRef)?.column }

    private fun unionRefs(
        args: List<P.ArgContext>,
        at: SourceLocation,
    ): List<Arg> = args.map { arg(null, refOrChain(it, at), at) }

    /** A ref arg (or a nested chain source materialised to a synth name). */
    private fun refOrChain(
        a: P.ArgContext,
        at: SourceLocation,
    ): Expression {
        val chainArg = unwrapChainArg(a.expr())
        if (chainArg != null) {
            val name = synthName()
            pre += Assignment(name, at, decomposeChainArg(chainArg), at)
            return ColumnRef(null, name, at)
        }
        val folded = exprFolder.fold(a.expr())
        if (folded is ColumnRef && folded.port == null) noteExternal(folded.column) // join right / union arm
        return folded
    }

    private fun decomposeChainArg(ca: P.ChainArgContext): Chain {
        val elems = mutableListOf<ChainElem>()
        ca.loadCall()?.let { elems += loadCall(it) }
        ca.ref()?.let { elems += dref(it.IDENT().joinToString(".") { p -> p.text }, loc.of(it)) }
        for (mc in ca.methodCall()) elems += methodCall(mc)
        return Chain(elems, loc.of(ca))
    }

    private fun unwrapChainArg(expr: P.ExprContext): P.ChainArgContext? {
        val or = expr.orExpr()
        if (or.andExpr().size != 1) return null
        val and = or.andExpr(0)
        if (and.notExpr().size != 1) return null
        val not = and.notExpr(0)
        if (not.NOT() != null) return null
        val pred = not.predicate() as? P.BarePredicateContext ?: return null
        val add = pred.addExpr()
        if (add.mulExpr().size != 1) return null
        val mul = add.mulExpr(0)
        if (mul.unaryExpr().size != 1) return null
        val un = mul.unaryExpr(0)
        if (un.MINUS() != null) return null
        return (un.primary() as? P.ChainPrimaryContext)?.chainArg()
    }

    // ---- canonical AST builders ----

    private fun methodName(mc: P.MethodCallContext): String {
        val m = mc.method()
        return m.bad?.text ?: m.text.lowercase()
    }

    private fun isCte(name: String): Boolean = name in cteNames

    private fun synthName(): String = "_p${synth++}"

    private fun derived(): List<String> = derivedSet.toList() // bare receiver/right/union in-ports (T6.3.3)

    private fun drainPre(): List<Statement> {
        val p = pre.toList()
        pre.clear()
        return p
    }

    private fun opCall(
        name: String,
        args: List<Arg>,
        config: ConfigBlock?,
        at: SourceLocation,
    ): OpCall = OpCall(name, args, config, at)

    private fun dref(
        name: String,
        at: SourceLocation,
    ): DottedRef = DottedRef(name.split("."), at)

    private fun loadOf(
        qname: String,
        at: SourceLocation,
    ): OpCall {
        val parts = qname.split(".")
        val ref = ColumnRef(parts.dropLast(1).joinToString(".").ifEmpty { null }, parts.last(), at)
        return opCall("load", listOf(arg(null, ref, at)), null, at)
    }

    private fun arg(
        name: String?,
        expr: Expression,
        at: SourceLocation,
    ): Arg = Arg(name, ExprArg(expr, at), at)
}
