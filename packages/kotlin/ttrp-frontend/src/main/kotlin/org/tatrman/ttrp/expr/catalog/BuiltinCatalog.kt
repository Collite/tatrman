package org.tatrman.ttrp.expr.catalog

import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.TtrpType

/**
 * The v1 general function library (T5-c-β). Deliberately minimal (P1); grow only
 * when a later stage needs a function. Operators live here too — one uniform
 * catalogue — but their typing is handled directly by [org.tatrman.ttrp.expr.ExpressionTypechecker]
 * (comparison -> bool, arithmetic -> promoted, Kleene `and/or`); the entries below
 * mainly pin arity + return-type rules for the roster/manifest.
 *
 * Closed alias reject table ([ALIASES]) is deterministic (P2, never fuzzy): a hit
 * yields `TTRP-FN-001` with a "use <canonical>" alternative.
 */
object BuiltinCatalog : FunctionCatalog {
    override val catalogId: String = "ttrp.builtin"

    private val bool = TtrpType.Bool
    private val str = TtrpType.Str
    private val int = TtrpType.Integer
    private val dec = TtrpType.Decimal()
    private val num = TtrpType.Number

    private val entries: List<CatalogEntry> =
        listOf(
            // Logical operators — Kleene 3VL (CUSTOM null handling).
            op("op.and", "and", listOf(bool, bool), ReturnTypeRule.Fixed(bool), NullRule.CUSTOM),
            op("op.or", "or", listOf(bool, bool), ReturnTypeRule.Fixed(bool), NullRule.CUSTOM),
            op("op.not", "not", listOf(bool), ReturnTypeRule.Fixed(bool), NullRule.CUSTOM),
            // Comparison operators — STRICT -> bool.
            op("op.eq", "eq", listOf(num, num), ReturnTypeRule.Fixed(bool), NullRule.STRICT),
            op("op.neq", "neq", listOf(num, num), ReturnTypeRule.Fixed(bool), NullRule.STRICT),
            op("op.lt", "lt", listOf(num, num), ReturnTypeRule.Fixed(bool), NullRule.STRICT),
            op("op.lte", "lte", listOf(num, num), ReturnTypeRule.Fixed(bool), NullRule.STRICT),
            op("op.gt", "gt", listOf(num, num), ReturnTypeRule.Fixed(bool), NullRule.STRICT),
            op("op.gte", "gte", listOf(num, num), ReturnTypeRule.Fixed(bool), NullRule.STRICT),
            // Arithmetic operators — STRICT, promoted numeric.
            op("op.add", "add", listOf(num, num), ReturnTypeRule.Promoted, NullRule.STRICT),
            op("op.sub", "sub", listOf(num, num), ReturnTypeRule.Promoted, NullRule.STRICT),
            op("op.mul", "mul", listOf(num, num), ReturnTypeRule.Promoted, NullRule.STRICT),
            op("op.div", "div", listOf(num, num), ReturnTypeRule.Promoted, NullRule.STRICT),
            op("op.neg", "neg", listOf(num), ReturnTypeRule.Promoted, NullRule.STRICT),
            // Scalar functions.
            scalar("fn.coalesce", "coalesce", listOf(str, str), ReturnTypeRule.SameAsArg(0), NullRule.CUSTOM),
            scalar("fn.substring", "substring", listOf(str, int, int), ReturnTypeRule.Fixed(str), NullRule.STRICT),
            scalar("fn.upper", "upper", listOf(str), ReturnTypeRule.Fixed(str), NullRule.STRICT),
            scalar("fn.lower", "lower", listOf(str), ReturnTypeRule.Fixed(str), NullRule.STRICT),
            scalar("fn.length", "length", listOf(str), ReturnTypeRule.Fixed(int), NullRule.STRICT),
            scalar("fn.abs", "abs", listOf(num), ReturnTypeRule.SameAsArg(0), NullRule.STRICT),
            scalar("fn.round", "round", listOf(num), ReturnTypeRule.SameAsArg(0), NullRule.STRICT),
            // Aggregates — the distinct [AggregateCall] arm.
            agg("agg.sum", "sum", listOf(num), ReturnTypeRule.Fixed(dec)),
            agg("agg.avg", "avg", listOf(num), ReturnTypeRule.Fixed(dec)),
            agg("agg.count", "count", listOf(num), ReturnTypeRule.Fixed(int)),
            agg("agg.min", "min", listOf(num), ReturnTypeRule.SameAsArg(0)),
            agg("agg.max", "max", listOf(num), ReturnTypeRule.SameAsArg(0)),
        )

    private val byName: Map<String, List<CatalogEntry>> = entries.groupBy { it.name }

    /** The aggregate surface names — the walker uses this to fold [AggregateCall] vs [FunctionCall]. */
    val aggregateNames: Set<String> = entries.filter { it.kind == FunctionKind.AGGREGATE }.map { it.name }.toSet()

    /**
     * Closed, deterministic alias reject table (P2): a known misspelling maps to its
     * canonical function. A hit is `TTRP-FN-001` with "use <canonical>".
     */
    val aliases: Map<String, String> =
        mapOf(
            "mean" to "avg",
            "substr" to "substring",
            "nvl" to "coalesce",
            "ifnull" to "coalesce",
            "ucase" to "upper",
            "lcase" to "lower",
        )

    override fun resolve(name: String): List<CatalogEntry> = byName[name].orEmpty()

    private fun op(
        id: String,
        name: String,
        params: List<TtrpType>,
        ret: ReturnTypeRule,
        nulls: NullRule,
    ) = CatalogEntry(CatalogId(id), name, FunctionKind.SCALAR, params, ret, nulls)

    private fun scalar(
        id: String,
        name: String,
        params: List<TtrpType>,
        ret: ReturnTypeRule,
        nulls: NullRule,
    ) = CatalogEntry(CatalogId(id), name, FunctionKind.SCALAR, params, ret, nulls)

    private fun agg(
        id: String,
        name: String,
        params: List<TtrpType>,
        ret: ReturnTypeRule,
    ) = CatalogEntry(CatalogId(id), name, FunctionKind.AGGREGATE, params, ret, NullRule.STRICT)
}
