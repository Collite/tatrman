// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.expr

import org.tatrman.ttrp.ast.SourceLocation

/**
 * A stable catalogue id — the identity a function/operator carries across surfaces
 * and what capability manifests reference (T5-c / B-T6 β). Value class over a
 * `String` such as `"op.eq"`, `"fn.avg"`, `"agg.sum"`.
 */
@JvmInline
value class CatalogId(
    val value: String,
) {
    /** The surface spelling behind a prefixed id (`fn.avg` -> `avg`); for a bare id, itself. */
    val name: String get() = value.substringAfterLast('.')

    override fun toString(): String = value

    companion object {
        // Operators are ordinary catalogue ids — one uniform [FunctionCall] arm (plan.v1 shape).
        val OR = CatalogId("op.or")
        val AND = CatalogId("op.and")
        val NOT = CatalogId("op.not")
        val EQ = CatalogId("op.eq")
        val NEQ = CatalogId("op.neq")
        val LT = CatalogId("op.lt")
        val LTE = CatalogId("op.lte")
        val GT = CatalogId("op.gt")
        val GTE = CatalogId("op.gte")
        val ADD = CatalogId("op.add")
        val SUB = CatalogId("op.sub")
        val MUL = CatalogId("op.mul")
        val DIV = CatalogId("op.div")
        val NEG = CatalogId("op.neg")
    }
}

/** A literal payload — the four scalar literal shapes TTR-P expressions carry. */
sealed interface LiteralValue {
    data class Str(
        val value: String,
    ) : LiteralValue

    data class Num(
        val raw: String,
    ) : LiteralValue

    data class Bool(
        val value: Boolean,
    ) : LiteralValue

    data object Null : LiteralValue
}

/**
 * The ONE PL expression IR (T5-e) — a deliberate structural twin of kantheon's
 * `plan.v1.Expression` (B-T3 "own IR, adapt") so the P3 lowering in ttr-translator
 * is mechanical. No proto is imported here (S25 vendors it downstream, not in the
 * front-half). Binary/unary operators are [FunctionCall]s over catalogue ids
 * (`op.and`, `op.eq`, `op.add`, …) — one uniform arm, exactly like plan.v1; the
 * walker does the folding. There is no lambda arm and no subquery arm (B-T5 sweep:
 * not in the v1 IR).
 *
 * [type] is `null` until [ExpressionTypechecker] annotates it; every node carries a
 * [SourceLocation] for surgical diagnostics/edits.
 */
sealed interface Expression {
    val location: SourceLocation
    val type: TtrpType?
}

/** A column reference, port-qualified per C3-a-iv-4 (`left.x`); [port] null = unqualified. */
data class ColumnRef(
    val port: String?,
    val column: String,
    override val location: SourceLocation,
    override val type: TtrpType? = null,
) : Expression

data class Literal(
    val value: LiteralValue,
    override val location: SourceLocation,
    override val type: TtrpType? = null,
) : Expression

/** A scalar function or operator call (operators are catalogue ids — B-T5). */
data class FunctionCall(
    val function: CatalogId,
    val args: List<Expression>,
    override val location: SourceLocation,
    override val type: TtrpType? = null,
) : Expression

/**
 * An aggregate call — a DISTINCT IR arm, never a [FunctionCall] (B-T5 sweep). Only
 * legal inside `aggregate(…)` / `aggregate { … }`; anywhere else is `TTRP-AGG-001`.
 */
data class AggregateCall(
    val function: CatalogId,
    val args: List<Expression>,
    val distinct: Boolean,
    override val location: SourceLocation,
    override val type: TtrpType? = null,
) : Expression

/** An explicit cast — the ONLY coercion the IR carries; implicit widening is applied in-place, never materialized. */
data class Cast(
    val expr: Expression,
    val target: TtrpType,
    override val location: SourceLocation,
    override val type: TtrpType? = null,
) : Expression

data class CaseWhen(
    val branches: List<Pair<Expression, Expression>>,
    val elseExpr: Expression?,
    override val location: SourceLocation,
    override val type: TtrpType? = null,
) : Expression

data class InList(
    val expr: Expression,
    val items: List<Expression>,
    val negated: Boolean,
    override val location: SourceLocation,
    override val type: TtrpType? = null,
) : Expression

data class IsNull(
    val expr: Expression,
    val negated: Boolean,
    override val location: SourceLocation,
    override val type: TtrpType? = null,
) : Expression
