package org.tatrman.ttrp.dialect.pandas

import org.tatrman.ttrp.dialect.sql.TtrSqlLoc
import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.CaseWhen
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.InList
import org.tatrman.ttrp.expr.IsNull
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.expr.catalog.FunctionCatalog
import org.tatrman.ttrp.expr.catalog.FunctionKind
import org.tatrman.ttrp.parser.generated.TTRPandasParser as P

/**
 * Folds TTR-pandas expression trees into the ONE PL expression IR (S16 / T5-e) — the
 * SAME [CatalogId]s canonical TTR-P produces. `==` is the ONE closed dialect synonym for
 * `=` (S9): both fold to `op.eq`, same tree, pinned by TtrPandasKeywordDriftSpec. Columns
 * are port-qualified (`left`/`right`) inside a join `on:`, unqualified elsewhere.
 */
class TtrPandasExpr(
    private val loc: TtrSqlLoc,
    private val catalog: FunctionCatalog,
) {
    fun fold(
        ctx: P.ExprContext,
        aliasPort: Map<String, String> = emptyMap(),
    ): Expression = orExpr(ctx.orExpr(), aliasPort)

    private fun orExpr(
        ctx: P.OrExprContext,
        ap: Map<String, String>,
    ): Expression = foldChain(ctx.andExpr().map { andExpr(it, ap) }, CatalogId.OR, loc.of(ctx))

    private fun andExpr(
        ctx: P.AndExprContext,
        ap: Map<String, String>,
    ): Expression = foldChain(ctx.notExpr().map { notExpr(it, ap) }, CatalogId.AND, loc.of(ctx))

    private fun notExpr(
        ctx: P.NotExprContext,
        ap: Map<String, String>,
    ): Expression =
        if (ctx.NOT() != null) {
            FunctionCall(CatalogId.NOT, listOf(notExpr(ctx.notExpr(), ap)), loc.of(ctx))
        } else {
            predicate(ctx.predicate(), ap)
        }

    private fun predicate(
        ctx: P.PredicateContext,
        ap: Map<String, String>,
    ): Expression =
        when (ctx) {
            is P.ComparePredicateContext ->
                FunctionCall(
                    compareOp(ctx),
                    listOf(addExpr(ctx.addExpr(0), ap), addExpr(ctx.addExpr(1), ap)),
                    loc.of(ctx),
                )
            is P.IsNullPredicateContext ->
                IsNull(addExpr(ctx.addExpr(), ap), negated = ctx.NOT() != null, location = loc.of(ctx))
            is P.InPredicateContext ->
                InList(
                    addExpr(ctx.addExpr(), ap),
                    ctx.expr().map { fold(it, ap) },
                    negated = ctx.NOT() != null,
                    location = loc.of(ctx),
                )
            is P.BetweenPredicateContext ->
                FunctionCall(
                    CatalogId.AND,
                    listOf(
                        FunctionCall(
                            CatalogId.GTE,
                            listOf(addExpr(ctx.addExpr(0), ap), addExpr(ctx.addExpr(1), ap)),
                            loc.of(ctx),
                        ),
                        FunctionCall(
                            CatalogId.LTE,
                            listOf(addExpr(ctx.addExpr(0), ap), addExpr(ctx.addExpr(2), ap)),
                            loc.of(ctx),
                        ),
                    ),
                    loc.of(ctx),
                )
            is P.BarePredicateContext -> addExpr(ctx.addExpr(), ap)
            else -> error("unhandled predicate: ${ctx::class.simpleName}")
        }

    /** `=` and `==` both → op.eq (S9 synonym). */
    private fun compareOp(ctx: P.ComparePredicateContext): CatalogId =
        when {
            ctx.ASSIGN() != null || ctx.EQEQ() != null -> CatalogId.EQ
            ctx.NEQ() != null || ctx.NEQ2() != null -> CatalogId.NEQ
            ctx.LT() != null -> CatalogId.LT
            ctx.LTE() != null -> CatalogId.LTE
            ctx.GT() != null -> CatalogId.GT
            else -> CatalogId.GTE
        }

    private fun addExpr(
        ctx: P.AddExprContext,
        ap: Map<String, String>,
    ): Expression {
        var acc = mulExpr(ctx.mulExpr(0), ap)
        val ops = symbolOps(ctx, setOf("+", "-"))
        for (i in 1 until ctx.mulExpr().size) {
            acc =
                FunctionCall(
                    if (ops[i - 1] ==
                        "+"
                    ) {
                        CatalogId.ADD
                    } else {
                        CatalogId.SUB
                    },
                    listOf(acc, mulExpr(ctx.mulExpr(i), ap)),
                    loc.of(ctx),
                )
        }
        return acc
    }

    private fun mulExpr(
        ctx: P.MulExprContext,
        ap: Map<String, String>,
    ): Expression {
        var acc = unaryExpr(ctx.unaryExpr(0), ap)
        val ops = symbolOps(ctx, setOf("*", "/"))
        for (i in 1 until ctx.unaryExpr().size) {
            acc =
                FunctionCall(
                    if (ops[i - 1] ==
                        "*"
                    ) {
                        CatalogId.MUL
                    } else {
                        CatalogId.DIV
                    },
                    listOf(acc, unaryExpr(ctx.unaryExpr(i), ap)),
                    loc.of(ctx),
                )
        }
        return acc
    }

    private fun unaryExpr(
        ctx: P.UnaryExprContext,
        ap: Map<String, String>,
    ): Expression =
        if (ctx.MINUS() != null) {
            FunctionCall(CatalogId.NEG, listOf(unaryExpr(ctx.unaryExpr(), ap)), loc.of(ctx))
        } else {
            primary(ctx.primary(), ap)
        }

    private fun primary(
        ctx: P.PrimaryContext,
        ap: Map<String, String>,
    ): Expression =
        when (ctx) {
            is P.LitPrimaryContext -> literal(ctx.literal())
            is P.CastPrimaryContext -> Cast(fold(ctx.expr(), ap), typeName(ctx.typeName()), loc.of(ctx))
            is P.CasePrimaryContext -> caseExpr(ctx, ap)
            is P.CallPrimaryContext -> functionCall(ctx.funcCall(), ap)
            is P.ColPrimaryContext -> columnRef(ctx.columnRef(), ap)
            is P.ParenPrimaryContext -> fold(ctx.expr(), ap)
            is P.ChainPrimaryContext ->
                // A chain-source (`right: load(...).filter(...)`) is structural — the decomposer
                // intercepts it before folding; reaching here means it was used as a scalar.
                error("chain source used in expression position")
            else -> error("unhandled primary: ${ctx::class.simpleName}")
        }

    private fun caseExpr(
        ctx: P.CasePrimaryContext,
        ap: Map<String, String>,
    ): CaseWhen {
        val whenCount = ctx.WHEN().size
        val exprs = ctx.expr()
        val branches = (0 until whenCount).map { fold(exprs[it * 2], ap) to fold(exprs[it * 2 + 1], ap) }
        val elseExpr = if (exprs.size > whenCount * 2) fold(exprs[whenCount * 2], ap) else null
        return CaseWhen(branches, elseExpr, loc.of(ctx))
    }

    private fun functionCall(
        ctx: P.FuncCallContext,
        ap: Map<String, String>,
    ): Expression =
        when (ctx) {
            is P.CountStarContext -> {
                val name = ctx.name.text.lowercase()
                val agg = catalog.resolve(name).firstOrNull { it.kind == FunctionKind.AGGREGATE }
                AggregateCall(agg?.id ?: CatalogId(name), emptyList(), distinct = false, location = loc.of(ctx))
            }
            is P.NamedCallContext -> {
                val name = ctx.name.text.lowercase()
                val args = ctx.expr().map { fold(it, ap) }
                val distinct = ctx.DISTINCT() != null
                val entries = catalog.resolve(name)
                val aggEntry = entries.firstOrNull { it.kind == FunctionKind.AGGREGATE }
                val scalarEntry = entries.firstOrNull { it.kind == FunctionKind.SCALAR }
                when {
                    aggEntry != null -> AggregateCall(aggEntry.id, args, distinct, loc.of(ctx))
                    distinct -> AggregateCall(CatalogId(name), args, distinct = true, location = loc.of(ctx))
                    scalarEntry != null -> FunctionCall(scalarEntry.id, args, loc.of(ctx))
                    else -> FunctionCall(CatalogId(name), args, loc.of(ctx))
                }
            }
            else -> error("unhandled funcCall: ${ctx::class.simpleName}")
        }

    private fun columnRef(
        ctx: P.ColumnRefContext,
        ap: Map<String, String>,
    ): ColumnRef {
        // Pandas authors write the port qualifier explicitly (`left.x`/`right.y`, C3-a-iv-4),
        // so the qualifier IS the port — no table-alias translation (unlike SQL). [ap] is unused.
        val parts = ctx.IDENT().map { it.text }
        return if (parts.size >= 2) {
            ColumnRef(port = parts[parts.size - 2], column = parts.last(), location = loc.of(ctx))
        } else {
            ColumnRef(port = null, column = parts.last(), location = loc.of(ctx))
        }
    }

    private fun literal(ctx: P.LiteralContext): Literal {
        val value: LiteralValue =
            when {
                ctx.STRING() != null -> LiteralValue.Str(unquote(ctx.STRING().text))
                ctx.NUMBER() != null -> LiteralValue.Num(ctx.NUMBER().text)
                ctx.TRUE() != null -> LiteralValue.Bool(true)
                ctx.FALSE() != null -> LiteralValue.Bool(false)
                else -> LiteralValue.Null
            }
        return Literal(value, loc.of(ctx))
    }

    private fun typeName(ctx: P.TypeNameContext): TtrpType {
        val nums = ctx.NUMBER()
        return TtrpType.parse(
            ctx.IDENT().text,
            nums.getOrNull(0)?.text?.toIntOrNull(),
            nums.getOrNull(1)?.text?.toIntOrNull(),
        )
    }

    private fun foldChain(
        operands: List<Expression>,
        op: CatalogId,
        at: org.tatrman.ttrp.ast.SourceLocation,
    ): Expression {
        var acc = operands.first()
        for (i in 1 until operands.size) acc = FunctionCall(op, listOf(acc, operands[i]), at)
        return acc
    }

    private fun symbolOps(
        ctx: org.antlr.v4.runtime.ParserRuleContext,
        wanted: Set<String>,
    ): List<String> =
        ctx.children.orEmpty().mapNotNull { (it as? org.antlr.v4.runtime.tree.TerminalNode)?.text }.filter {
            it in
                wanted
        }

    private fun unquote(raw: String): String = raw.substring(1, raw.length - 1)
}
