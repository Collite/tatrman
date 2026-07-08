package org.tatrman.ttrp.dialect.b

import org.tatrman.ttrp.ast.Arg
import org.tatrman.ttrp.ast.AssignEntry
import org.tatrman.ttrp.ast.Assignment
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
import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.expr.catalog.FunctionCatalog
import org.tatrman.ttrp.expr.catalog.FunctionKind
import org.tatrman.ttrp.parser.generated.TTRBParser as P

/**
 * Sentence→node decomposition (C2-a-β): lowers a TTR-B sentence program into canonical
 * TTR-P statements — the SAME AST canonical authoring emits, so `bare ≡ embedded ≡
 * canonical` graphs hold (the P6 KEY-GATE pattern). Each sentence maps to node(s) of the
 * standard set (C4-b "Maps to" column); anaphora (`that`/`this`/`it` + the implicit
 * subject) = the previous sentence's out (C4-b-i, deterministic, P2); `as <name>` binds
 * an SSA label (Q7-γ) → CTE names (E-b) / ζ keys. Statement order = pipeline order.
 */
class TtrbDecomposer(
    private val loc: TtrSqlLoc,
    private val catalog: FunctionCatalog,
) {
    private val exprFolder = TtrbExpr(loc, catalog)
    private val out = mutableListOf<Statement>()
    private val elems = mutableListOf<ChainElem>()
    private var curName: String? = null
    private val bound = mutableSetOf<String>()
    private val derived = LinkedHashSet<String>()
    private var synth = 0

    data class Result(
        val statements: List<Statement>,
        val derivedInPorts: List<String>,
    )

    fun decompose(
        program: P.FragmentProgramContext,
        outPort: String?,
    ): Result {
        for (s in program.sentence()) statement(s.statement())
        // A pipeline not terminated by Show/Store is the container's default out (C4-b-iv).
        if (elems.isNotEmpty()) {
            val at = elems.first().location
            out +=
                if (outPort != null) {
                    assign(outPort, Chain(elems.toList(), at), at)
                } else {
                    ChainStmt(Chain(elems.toList(), at), at)
                }
            elems.clear()
        }
        return Result(out, derived.toList())
    }

    private fun statement(s: P.StatementContext) {
        when (s) {
            is P.LoadSentenceContext -> bindLoad(s.loadStmt())
            is P.JoinSentenceContext -> bindJoin(s.joinStmt())
            is P.KeepColumnsSentenceContext -> append(projectOf(s.keepColumnsStmt()))
            is P.KeepExceptSentenceContext -> append(exceptOf(s.keepExceptStmt()))
            is P.FilterSentenceContext -> append(filterOf(s.filterStmt()))
            is P.RenameSentenceContext -> append(renameOf(s.renameStmt()))
            is P.ConvertSentenceContext -> append(convertOf(s.convertStmt()))
            is P.ComputeSentenceContext -> append(computeOf(s.computeStmt()))
            is P.SummarizeSentenceContext -> append(summarizeOf(s.summarizeStmt()))
            is P.SortSentenceContext -> append(sortOf(s.sortStmt()))
            is P.LimitSentenceContext -> append(limitOf(s.limitStmt()))
            is P.CombineSentenceContext -> append(combineOf(s.combineStmt()))
            is P.StoreSentenceContext -> sinkStore(s.storeStmt())
            is P.ShowSentenceContext -> sinkShow(s.showStmt())
            else -> error("unhandled sentence: ${s::class.simpleName}")
        }
    }

    // ---- bindings (introduce a named source) ---------------------------------------

    private fun bindLoad(ctx: P.LoadStmtContext) {
        flushDangling()
        val at = loc.of(ctx)
        val load: OpCall
        val name: String
        when (ctx) {
            is P.LoadFileContext -> {
                val path = unquote(ctx.fileSource().str().text)
                val args = mutableListOf(arg(null, ColumnRef(null, path, at), at))
                ctx.schema?.let { args += arg("schema", qref(it.text, at), at) }
                load = OpCall("load", args, null, at)
                name = ctx.name?.text ?: synthName()
            }
            is P.LoadModelContext -> {
                val src = ctx.source.text
                noteExternal(src)
                load = OpCall("load", listOf(arg(null, qref(src, at), at)), null, at)
                name = ctx.name?.text ?: src.substringAfterLast('.')
            }
            else -> error("unhandled load: ${ctx::class.simpleName}")
        }
        out += assign(name, Chain(listOf(load), at), at)
        curName = name
        bound += name
    }

    private fun bindJoin(ctx: P.JoinStmtContext) {
        flushDangling()
        val at = loc.of(ctx)
        val leftName = joinLeftName(ctx.joinLeft())
        val rightName = ctx.right.text
        val ap = mapOf(leftName to "left", rightName to "right")
        val on = exprFolder.foldBool(ctx.boolExpr(), ap)
        val join =
            OpCall(
                "join",
                listOf(
                    refArg("left", leftName, at),
                    refArg("right", rightName, at),
                    namedArg("on", on, at),
                    refArg("type", "inner", at),
                ),
                null,
                at,
            )
        noteExternal(rightName)
        val name = ctx.name?.text ?: synthName()
        out += assign(name, Chain(listOf(join), at), at)
        curName = name
        bound += name
    }

    private fun joinLeftName(ctx: P.JoinLeftContext): String =
        if (ctx.refWord() != null) currentRef(loc.of(ctx)) else ctx.qname().text.also { noteExternal(it) }

    // ---- transforms (extend the anaphoric chain) -----------------------------------

    private fun projectOf(ctx: P.KeepColumnsStmtContext): ChainElem {
        val at = loc.of(ctx)
        val cols = ctx.colRenameList().colRename().map { ColumnRef(null, it.IDENT(0).text, at) as Expression }
        return OpCall("project", cols.map { namedArg(null, it, at) }, null, at)
    }

    /** `Keep all columns except a` — negative Select; schema expansion is deferred (C2-b-iii β). */
    private fun exceptOf(ctx: P.KeepExceptStmtContext): ChainElem {
        val at = loc.of(ctx)
        val cols = ctx.colList().IDENT().map { ColumnRef(null, it.text, at) as Expression }
        return OpCall("project", cols.map { namedArg(null, it, at) }, null, at)
    }

    private fun filterOf(ctx: P.FilterStmtContext): ChainElem {
        val at = loc.of(ctx)
        val (boolCtx, negate) =
            when (ctx) {
                is P.KeepFilterContext -> ctx.boolExpr() to false
                is P.RemoveFilterContext -> ctx.boolExpr() to true
                else -> error("unhandled filter: ${ctx::class.simpleName}")
            }
        val pred = exprFolder.foldBool(boolCtx)
        val eff = if (negate) FunctionCall(CatalogId.NOT, listOf(pred), at) else pred
        return OpCall("filter", listOf(namedArg(null, eff, at)), null, at)
    }

    private fun renameOf(ctx: P.RenameStmtContext): ChainElem {
        val at = loc.of(ctx)
        val entries: List<ConfigEntry> =
            ctx.renamePair().map { AssignEntry(it.to.text, ColumnRef(null, it.from.text, at), loc.of(it)) }
        return OpCall("calc", emptyList(), ConfigBlock(entries, at), at)
    }

    private fun convertOf(ctx: P.ConvertStmtContext): ChainElem {
        val at = loc.of(ctx)
        val col = ctx.col.text
        val type = TtrpType.parse(ctx.typeName().IDENT().text)
        val entry: ConfigEntry = AssignEntry(col, Cast(ColumnRef(null, col, at), type, at), at)
        return OpCall("calc", emptyList(), ConfigBlock(listOf(entry), at), at)
    }

    private fun computeOf(ctx: P.ComputeStmtContext): ChainElem {
        val at = loc.of(ctx)
        val entry: ConfigEntry = AssignEntry(ctx.name.text, exprFolder.foldExpr(ctx.expr()), at)
        return OpCall("calc", emptyList(), ConfigBlock(listOf(entry), at), at)
    }

    private fun summarizeOf(ctx: P.SummarizeStmtContext): ChainElem {
        val at = loc.of(ctx)
        val entries = mutableListOf<ConfigEntry>()
        val keys = ctx.groupKey().map { it.text }
        if (keys.isNotEmpty()) entries += GroupByEntry(keys, at)
        for (item in ctx.aggItem()) {
            val agg = aggCall(item.func.text, item.arg.text, loc.of(item))
            val name = item.name?.text ?: item.func.text
            entries += AssignEntry(name, agg, loc.of(item))
        }
        return OpCall("aggregate", emptyList(), ConfigBlock(entries, at), at)
    }

    /** `sum of amount` → `AggregateCall(agg.sum, [col(amount)])` — same id canonical `sum(amount)` folds to. */
    private fun aggCall(
        func: String,
        col: String,
        at: SourceLocation,
    ): Expression {
        val name = func.lowercase()
        val id = catalog.resolve(name).firstOrNull { it.kind == FunctionKind.AGGREGATE }?.id ?: CatalogId(name)
        return AggregateCall(id, listOf(ColumnRef(null, col, at)), distinct = false, location = at)
    }

    private fun sortOf(ctx: P.SortStmtContext): ChainElem {
        val at = loc.of(ctx)
        val keys = ctx.sortKey().map { ColumnRef(null, it.col.text, at) as Expression }
        return OpCall("sort", keys.map { namedArg(null, it, at) }, null, at)
    }

    private fun limitOf(ctx: P.LimitStmtContext): ChainElem {
        val at = loc.of(ctx)
        return OpCall("limit", listOf(namedArg(null, Literal(LiteralValue.Num(ctx.count.text), at), at)), null, at)
    }

    private fun combineOf(ctx: P.CombineStmtContext): ChainElem {
        val at = loc.of(ctx)
        // Left is the chain receiver (the current pipeline value); right is the other input.
        if (ctx.joinLeft().refWord() == null) noteExternal(ctx.joinLeft().qname().text)
        val right = ctx.right.text
        noteExternal(right)
        return OpCall("union", listOf(refArg(null, right, at)), null, at)
    }

    // ---- sinks ---------------------------------------------------------------------

    private fun sinkShow(ctx: P.ShowStmtContext) {
        ensureHead(loc.of(ctx))
        val at = loc.of(ctx)
        val name = ctx.name?.text ?: "result"
        elems += OpCall("display", listOf(refArg(null, name, at)), null, at)
        out += assign(name, Chain(elems.toList(), at), at)
        curName = name
        bound += name
        elems.clear()
    }

    private fun sinkStore(ctx: P.StoreStmtContext) {
        ensureHead(loc.of(ctx))
        val at = loc.of(ctx)
        val target =
            if (ctx.fileSource() != null) {
                ColumnRef(null, unquote(ctx.fileSource().str().text), at)
            } else {
                qref(ctx.dest.text, at)
            }
        elems += OpCall("store", listOf(arg(null, target, at)), null, at)
        out += ChainStmt(Chain(elems.toList(), at), at)
        curName = null
        elems.clear()
    }

    // ---- anaphora + chain plumbing -------------------------------------------------

    private fun append(elem: ChainElem) {
        ensureHead(elem.location)
        elems += elem
    }

    private fun ensureHead(at: SourceLocation) {
        if (elems.isEmpty()) elems += DottedRef(listOf(currentRef(at)), at)
    }

    /** The anaphoric antecedent = the previous sentence's out (C4-b-i). */
    private fun currentRef(at: SourceLocation): String = curName ?: synthName()

    private fun flushDangling() {
        if (elems.isNotEmpty()) {
            val at = elems.first().location
            out += ChainStmt(Chain(elems.toList(), at), at)
            elems.clear()
        }
    }

    private fun noteExternal(name: String) {
        if (name !in bound) derived += name
    }

    // ---- builders ------------------------------------------------------------------

    private fun assign(
        target: String,
        chain: Chain,
        at: SourceLocation,
    ): Assignment = Assignment(target = target, targetLocation = at, chain = chain, location = at)

    private fun qref(
        qname: String,
        at: SourceLocation,
    ): ColumnRef {
        val parts = qname.split(".")
        return ColumnRef(
            port = parts.dropLast(1).joinToString(".").ifEmpty { null },
            column = parts.last(),
            location = at,
        )
    }

    private fun namedArg(
        name: String?,
        expr: Expression,
        at: SourceLocation,
    ): Arg = Arg(name, ExprArg(expr, at), at)

    private fun arg(
        name: String?,
        expr: Expression,
        at: SourceLocation,
    ): Arg = Arg(name, ExprArg(expr, at), at)

    private fun refArg(
        name: String?,
        ref: String,
        at: SourceLocation,
    ): Arg = Arg(name, ExprArg(ColumnRef(port = null, column = ref, location = at), at), at)

    private fun synthName(): String = "_b${synth++}"

    private fun unquote(raw: String): String = raw.substring(1, raw.length - 1)
}
