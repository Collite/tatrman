// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.expr

import org.tatrman.ttr.md.resolve.CanonicalRenderer
import org.tatrman.ttr.md.resolve.MdDiagId
import org.tatrman.ttr.md.resolve.ResolutionOutcome
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.catalog.BuiltinCatalog
import org.tatrman.ttrp.expr.catalog.CatalogEntry
import org.tatrman.ttrp.expr.catalog.CompositeCatalog
import org.tatrman.ttrp.expr.catalog.FunctionCatalog
import org.tatrman.ttrp.expr.catalog.FunctionKind
import org.tatrman.ttrp.expr.catalog.ReturnTypeRule

/** A named, typed input column (schema is `port -> columns`; the `""` port = the unqualified/default input). */
data class Column(
    val name: String,
    val type: TtrpType,
)

/**
 * The result of typechecking one expression: its result type (null if it could not be typed),
 * diagnostics, and any MD dot-paths that resolved within it ([mdResolutions], S3-A — the canonical
 * form + shape + explanation, carried for the frontend API / future hover).
 */
data class TypedResult(
    val type: TtrpType?,
    val diagnostics: List<TtrpDiagnostic>,
    val mdResolutions: List<MdResolution> = emptyList(),
)

/**
 * Static typing + coercion for the one PL expression IR under canonical SQL 3VL
 * (B-T5, forced by A4). Bottom-up: every node gets a statically known result type;
 * all types are nullable (there is no not-null type in v1 — 3VL is the semantics,
 * not a flag).
 *
 * Coercion (Q9-4 keeps decimal exact): implicit `integer -> decimal` only; `decimal
 * -> double` is NEVER implicit (precision loss ⇒ an explicit [Cast]); `char/varchar/
 * string/text` unify to `string`. Cross-kind arithmetic (`string + int`) is
 * `TTRP-TYP-001`. Comparison operators (`= <> < <= > >=`) return `bool` for any two
 * well-typed operands — they do NOT kind-check (a join key `int = string` is legal,
 * it is just a predicate); the `TTRP-TYP-001` guard is arithmetic + boolean/predicate
 * positions only.
 *
 * NULL: STRICT operators propagate NULL (all results already nullable); `and/or` are
 * Kleene; `IsNull` returns a non-null bool. `TTRP-AGG-001` fires when an
 * [AggregateCall] appears where aggregates are not allowed; `TTRP-EXP-001` when a
 * ColumnRef names a bound variable or (given a schema) an out-of-scope column;
 * `TTRP-FN-001`/`002` on unknown/alias functions and arity mismatch.
 */
class ExpressionTypechecker(
    private val catalog: FunctionCatalog = CompositeCatalog(listOf(BuiltinCatalog)),
) {
    /**
     * Typechecks [expr] against [inputSchema] (`port -> columns`; `""` = default
     * input). [aggregatesAllowed] gates [AggregateCall] (false in predicate/scalar
     * positions ⇒ AGG-001). [variableNames] are the SSA variables in scope — any
     * unqualified reference to one is EXP-001 (expression scope = input columns only,
     * C3-a-iv-3). [predicateExpected] requires the root type to be `bool`.
     *
     * A `null` schema means "resolution deferred" (Stage 1.3): column typing/EXP-001
     * for unknown columns is skipped, but the schema-independent checks (FN/AGG,
     * variable-scope EXP-001) still run.
     */
    fun check(
        expr: Expression,
        inputSchema: Map<String, List<Column>>?,
        aggregatesAllowed: Boolean = true,
        variableNames: Set<String> = emptySet(),
        predicateExpected: Boolean = false,
        md: MdContext? = null,
    ): TypedResult {
        val mdResolutions = mutableListOf<MdResolution>()
        val ctx = Ctx(inputSchema, variableNames, md, mdResolutions)
        val diags = mutableListOf<TtrpDiagnostic>()
        val rootType = type(expr, ctx, aggregatesAllowed, diags)
        if (predicateExpected && rootType != null && rootType.canonical != BOOL) {
            diags += diag(TtrpDiagnosticId.TYP_001, "predicate must be bool, got ${rootType.canonical}", expr.location)
        }
        return TypedResult(rootType, diags, mdResolutions)
    }

    private class Ctx(
        val schema: Map<String, List<Column>>?,
        val variables: Set<String>,
        val md: MdContext?,
        val mdResolutions: MutableList<MdResolution>,
    )

    private fun type(
        e: Expression,
        ctx: Ctx,
        aggAllowed: Boolean,
        diags: MutableList<TtrpDiagnostic>,
    ): TtrpType? =
        when (e) {
            is Literal -> literalType(e.value)
            is ColumnRef -> resolveColumn(e, ctx, diags)
            is IsNull -> {
                type(e.expr, ctx, aggAllowed, diags)
                TtrpType.Bool
            }
            is InList -> {
                type(e.expr, ctx, aggAllowed, diags)
                e.items.forEach { type(it, ctx, aggAllowed, diags) }
                TtrpType.Bool
            }
            is Cast -> checkCast(e, ctx, aggAllowed, diags)
            is CaseWhen -> checkCase(e, ctx, aggAllowed, diags)
            is AggregateCall -> checkAggregate(e, ctx, aggAllowed, diags)
            is FunctionCall -> checkFunction(e, ctx, aggAllowed, diags)
            is MdPath -> resolveMdPath(e, ctx, diags)
        }

    /**
     * Resolve an [MdPath] against the injected [MdContext] (S3-A). Precedence (R23): if the leading
     * bare component is an in-scope input column, the **column wins** — MD resolution is suppressed
     * and, when the chain *also* resolves as an MD path, a `TTRP-MD-012` **warning** is emitted
     * (qualify the chain to force MD). Otherwise a `Resolved` outcome records an [MdResolution]
     * marker; `Ambiguous`/`Failed` surface `TTRP-MD-*` at the path's range.
     *
     * The result **type** stays null here — shape/measure typing (R15/R18) is S3-B. A null MD path
     * defers like a NULL operand, so a host expression such as `path * 1.1` still typechecks.
     */
    private fun resolveMdPath(
        e: MdPath,
        ctx: Ctx,
        diags: MutableList<TtrpDiagnostic>,
    ): TtrpType? {
        val model = ctx.md?.model ?: return null // MD resolution deferred (no context / no model)
        val md = ctx.md
        val outcome = md.resolver.resolve(e.components.toResolverComponents(), model, md.members, md.asof)

        val first = e.components.firstOrNull()
        val shadowed = first is MdPathComponent.Name && isInScopeColumn(first.text, ctx)
        if (shadowed) {
            // R23: the column wins. Warn only when the chain genuinely also resolves as an MD path
            // (a non-resolving chain led by a column name is just a column access, no MD-012).
            if (outcome is ResolutionOutcome.Resolved) {
                diags +=
                    diag(
                        TtrpDiagnosticId.MD_012,
                        "path shadowed by input column `${(first as MdPathComponent.Name).text}` — " +
                            "column wins; qualify (`dim.member`) to force MD",
                        e.location,
                        severity = Severity.WARNING,
                    )
            }
            return null
        }

        return when (outcome) {
            is ResolutionOutcome.Resolved -> {
                ctx.mdResolutions +=
                    MdResolution(
                        location = e.location,
                        canonical = CanonicalRenderer.render(outcome.path),
                        path = outcome.path,
                        shape = outcome.shape,
                        explanation = outcome.explanation,
                    )
                null // shape/measure typing is S3-B (R15/R18)
            }
            is ResolutionOutcome.Ambiguous -> {
                val alts = outcome.alternatives.joinToString("  |  ") { CanonicalRenderer.render(it.path) }
                diags += diag(TtrpDiagnosticId.MD_003, "${MdDiagId.AMBIGUOUS.text}: $alts", e.location)
                null
            }
            is ResolutionOutcome.Failed -> {
                for (d in outcome.diagnostics) diags += diag(d.id.toFrontendId(), d.frontendMessage(), e.location)
                null
            }
        }
    }

    /** True iff [name] is an in-scope input column (unqualified/default port, or any known port). */
    private fun isInScopeColumn(
        name: String,
        ctx: Ctx,
    ): Boolean {
        val schema = ctx.schema ?: return false
        return schema[""]?.any { it.name == name } == true || schema.values.any { cols -> cols.any { it.name == name } }
    }

    private fun literalType(v: LiteralValue): TtrpType? =
        when (v) {
            is LiteralValue.Str -> TtrpType.Str
            is LiteralValue.Bool -> TtrpType.Bool
            is LiteralValue.Num -> if (v.raw.contains('.')) TtrpType.Decimal() else TtrpType.Integer
            is LiteralValue.Null -> null // untyped NULL — unifies with anything (3VL)
        }

    private fun resolveColumn(
        ref: ColumnRef,
        ctx: Ctx,
        diags: MutableList<TtrpDiagnostic>,
    ): TtrpType? {
        // Scope rule (C3-a-iv-3): variables NEVER resolve inside an op expression.
        if (ref.port == null && ref.column in ctx.variables) {
            diags +=
                diag(
                    TtrpDiagnosticId.EXP_001,
                    "`${ref.column}` is a variable — not in scope in an op expression",
                    ref.location,
                )
            return null
        }
        val schema = ctx.schema ?: return null // resolution deferred (Stage 1.3)
        // A qualified ref whose port is absent from the schema map = an UNKNOWN input
        // port (e.g. a join side fed by a fragment container whose interior schema is
        // deferred to P6). Defer it — do NOT raise EXP-001 (which is for a column that
        // is genuinely out-of-scope on a KNOWN port).
        if (ref.port != null && !schema.containsKey(ref.port)) return null
        val col =
            if (ref.port != null) {
                schema[ref.port]?.firstOrNull { it.name == ref.column }
            } else {
                schema[""]?.firstOrNull { it.name == ref.column }
                    ?: schema.values.flatten().firstOrNull { it.name == ref.column }
            }
        if (col == null) {
            val what = if (ref.port != null) "${ref.port}.${ref.column}" else ref.column
            diags += diag(TtrpDiagnosticId.EXP_001, "column `$what` is not an input column in scope", ref.location)
            return null
        }
        return col.type
    }

    private fun checkFunction(
        e: FunctionCall,
        ctx: Ctx,
        aggAllowed: Boolean,
        diags: MutableList<TtrpDiagnostic>,
    ): TtrpType? =
        when (e.function) {
            CatalogId.AND, CatalogId.OR -> {
                e.args.forEach { requireBool(it, ctx, aggAllowed, diags) }
                TtrpType.Bool
            }
            CatalogId.NOT -> {
                e.args.forEach { requireBool(it, ctx, aggAllowed, diags) }
                TtrpType.Bool
            }
            CatalogId.EQ, CatalogId.NEQ, CatalogId.LT, CatalogId.LTE, CatalogId.GT, CatalogId.GTE -> {
                e.args.forEach { type(it, ctx, aggAllowed, diags) } // typed for nested diagnostics; no kind-check
                TtrpType.Bool
            }
            CatalogId.ADD, CatalogId.SUB, CatalogId.MUL, CatalogId.DIV -> {
                val a = type(e.args[0], ctx, aggAllowed, diags)
                val b = type(e.args[1], ctx, aggAllowed, diags)
                arithmetic(a, b, e.location, diags)
            }
            CatalogId.NEG -> {
                val a = type(e.args[0], ctx, aggAllowed, diags)
                if (a != null && a.kind != TtrpType.Kind.NUMERIC) {
                    diags +=
                        diag(
                            TtrpDiagnosticId.TYP_001,
                            "unary minus needs a numeric operand, got ${a.canonical}",
                            e.location,
                        )
                    null
                } else {
                    a
                }
            }
            else -> checkNamedCall(e.function.name, e.args, aggregate = false, ctx, aggAllowed, e.location, diags)
        }

    private fun checkAggregate(
        e: AggregateCall,
        ctx: Ctx,
        aggAllowed: Boolean,
        diags: MutableList<TtrpDiagnostic>,
    ): TtrpType? {
        if (!aggAllowed) {
            diags += diag(TtrpDiagnosticId.AGG_001, "aggregate `${e.function.name}` is not allowed here", e.location)
        }
        // Nested aggregates inside an aggregate's arguments are illegal (SQL) — aggregatesAllowed = false below.
        return checkNamedCall(e.function.name, e.args, aggregate = true, ctx, aggAllowed = false, e.location, diags)
    }

    /** Resolves a named function/aggregate call against the catalogue and types it (FN-001/FN-002 on failure). */
    private fun checkNamedCall(
        name: String,
        args: List<Expression>,
        aggregate: Boolean,
        ctx: Ctx,
        aggAllowed: Boolean,
        location: SourceLocation,
        diags: MutableList<TtrpDiagnostic>,
    ): TtrpType? {
        val argTypes = args.map { type(it, ctx, aggAllowed, diags) }
        val wantKind = if (aggregate) FunctionKind.AGGREGATE else FunctionKind.SCALAR
        val ofKind = catalog.resolve(name).filter { it.kind == wantKind }
        // Overload resolution is by arity: pick the entry whose parameter count matches
        // the call, else fall back to the canonical (first) entry so a wrong-arity call
        // still reports FN_002 against a signature. Functions with a single entry (every
        // v1 builtin except the grounding `period_start`/`period_end` overloads) are
        // unaffected — the arity match, when it exists, is that one entry.
        val entry = ofKind.firstOrNull { it.params.size == args.size } ?: ofKind.firstOrNull()
        if (entry == null) {
            // A wrong-kind hit (e.g. DISTINCT on a scalar, or an aggregate spelled as scalar) is an arity/kind reject.
            if (catalog.resolve(name).isNotEmpty()) {
                diags +=
                    diag(
                        TtrpDiagnosticId.FN_002,
                        "`$name` is not ${if (aggregate) "an aggregate" else "a scalar function"}",
                        location,
                    )
            } else {
                val canonical = BuiltinCatalog.aliases[name]
                val suggestion = if (canonical != null) "use $canonical" else null
                diags += diag(TtrpDiagnosticId.FN_001, "unknown function `$name`", location, suggestion)
            }
            return null
        }
        if (args.size != entry.params.size) {
            diags +=
                diag(
                    TtrpDiagnosticId.FN_002,
                    "`$name` expects ${entry.params.size} argument(s), got ${args.size}",
                    location,
                )
        }
        return returnType(entry, argTypes)
    }

    private fun returnType(
        entry: CatalogEntry,
        argTypes: List<TtrpType?>,
    ): TtrpType? =
        when (val rule = entry.returnType) {
            is ReturnTypeRule.Fixed -> rule.type
            is ReturnTypeRule.SameAsArg -> argTypes.getOrNull(rule.index)
            is ReturnTypeRule.Promoted -> argTypes.filterNotNull().reduceOrNull { a, b -> unifyNumeric(a, b) ?: a }
        }

    private fun checkCast(
        e: Cast,
        ctx: Ctx,
        aggAllowed: Boolean,
        diags: MutableList<TtrpDiagnostic>,
    ): TtrpType {
        val from = type(e.expr, ctx, aggAllowed, diags)
        if (from != null && !castLegal(from, e.target)) {
            diags +=
                diag(
                    TtrpDiagnosticId.TYP_002,
                    "no cast rule ${from.canonical} → ${e.target.canonical}",
                    e.location,
                )
        }
        return e.target
    }

    private fun checkCase(
        e: CaseWhen,
        ctx: Ctx,
        aggAllowed: Boolean,
        diags: MutableList<TtrpDiagnostic>,
    ): TtrpType? {
        val results = mutableListOf<TtrpType>()
        for ((cond, result) in e.branches) {
            requireBool(cond, ctx, aggAllowed, diags)
            type(result, ctx, aggAllowed, diags)?.let { results += it }
        }
        e.elseExpr?.let { type(it, ctx, aggAllowed, diags)?.let { t -> results += t } }
        if (results.isEmpty()) return null
        val unified = results.reduce { a, b -> unifyResult(a, b) ?: a }
        val incompatible = results.any { unifyResult(it, unified) == null }
        if (incompatible) {
            diags += diag(TtrpDiagnosticId.TYP_001, "case branches have incompatible types", e.location)
        }
        return unified
    }

    private fun requireBool(
        e: Expression,
        ctx: Ctx,
        aggAllowed: Boolean,
        diags: MutableList<TtrpDiagnostic>,
    ) {
        val t = type(e, ctx, aggAllowed, diags)
        if (t != null && t.canonical != BOOL) {
            diags += diag(TtrpDiagnosticId.TYP_001, "expected bool, got ${t.canonical}", e.location)
        }
    }

    private fun arithmetic(
        a: TtrpType?,
        b: TtrpType?,
        location: SourceLocation,
        diags: MutableList<TtrpDiagnostic>,
    ): TtrpType? {
        if (a == null || b == null) return a ?: b // NULL/unknown operand — defer
        val unified = unifyNumeric(a, b)
        if (unified == null) {
            diags +=
                diag(
                    TtrpDiagnosticId.TYP_001,
                    "no implicit coercion for ${a.canonical} and ${b.canonical}",
                    location,
                )
        }
        return unified
    }

    /**
     * Numeric promotion (Q9-4 decimal-exact, review-001 1.2-E). Implicit `integer`
     * widening is confined to `decimal`/`number` — NOT `float`/`double`, whose &gt;2^53
     * precision loss is the very reason implicit `decimal → double` is forbidden. Every
     * other cross-numeric pair (incl. `integer + double`) requires an explicit [Cast].
     */
    private fun unifyNumeric(
        a: TtrpType,
        b: TtrpType,
    ): TtrpType? {
        if (a.kind != TtrpType.Kind.NUMERIC || b.kind != TtrpType.Kind.NUMERIC) return null
        if (a.canonical == b.canonical) return a
        if (a.canonical == INTEGER && b.canonical in INT_WIDENS_TO) return b
        if (b.canonical == INTEGER && a.canonical in INT_WIDENS_TO) return a
        return null // e.g. integer + double, decimal + double — precision loss, needs explicit Cast
    }

    /**
     * Widens `date → timestamp`/`datetime` (review-001 1.2-B). `date` is a strict prefix
     * of both instants, so widening is lossless; `timestamp`/`datetime` do NOT widen into
     * each other implicitly.
     */
    private fun unifyTemporal(
        a: TtrpType,
        b: TtrpType,
    ): TtrpType? {
        if (a.kind != TtrpType.Kind.TEMPORAL || b.kind != TtrpType.Kind.TEMPORAL) return null
        if (a.canonical == b.canonical) return a
        if (a.canonical == DATE && b.canonical in DATE_WIDENS_TO) return b
        if (b.canonical == DATE && a.canonical in DATE_WIDENS_TO) return a
        return null
    }

    /** Unifies two result types (case branches / coalesce): same canonical, or numeric/temporal widening. */
    private fun unifyResult(
        a: TtrpType,
        b: TtrpType,
    ): TtrpType? =
        when {
            a.canonical == b.canonical -> a
            a.kind == TtrpType.Kind.NUMERIC && b.kind == TtrpType.Kind.NUMERIC -> unifyNumeric(a, b)
            a.kind == TtrpType.Kind.TEMPORAL && b.kind == TtrpType.Kind.TEMPORAL -> unifyTemporal(a, b)
            else -> null
        }

    /** The explicit-cast legality table (B-T5): within a kind, or numeric↔string / temporal↔string. */
    private fun castLegal(
        from: TtrpType,
        to: TtrpType,
    ): Boolean {
        if (from.kind == to.kind) return true
        val pair = setOf(from.kind, to.kind)
        return pair == setOf(TtrpType.Kind.NUMERIC, TtrpType.Kind.STRING) ||
            pair == setOf(TtrpType.Kind.TEMPORAL, TtrpType.Kind.STRING)
    }

    private fun diag(
        id: TtrpDiagnosticId,
        message: String,
        location: SourceLocation,
        suggestion: String? = id.suggestedAlternative,
        severity: Severity = Severity.ERROR,
    ) = TtrpDiagnostic(
        id = id,
        severity = severity,
        message = message,
        location = location,
        suggestedAlternative = suggestion,
    )

    private companion object {
        const val BOOL = "bool"
        const val INTEGER = "integer"
        const val DATE = "date"

        /** Canonicals `integer` may implicitly widen to (Q9-4: NOT float/double). */
        val INT_WIDENS_TO = setOf("decimal", "number")

        /** Canonicals `date` may implicitly widen to. */
        val DATE_WIDENS_TO = setOf("timestamp", "datetime")
    }
}
