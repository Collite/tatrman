// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.polars

import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.expr.catalog.ValidityCatalog
import org.tatrman.ttrp.expr.catalog.ValiditySpec
import org.tatrman.ttrp.graph.capability.RejectDomain
import org.tatrman.ttrp.graph.capability.RejectsSupport

/**
 * Renders the `internal.*` validity calls a reject guard carries to a **Polars boolean expression**
 * (RJ-P4) — the Polars counterpart of [org.tatrman.ttrp.emit.sql.RejectGuardSql]. The canonical
 * validity domain (contracts §2 / RJ-P0 YAMLs) is realized here as native Polars string/cast
 * expressions so the mask column is engine-agnostic in *meaning* (same accept/reject set as the SQL
 * guard) while idiomatic in *form*:
 *
 *  - `trim: ascii-ws` → `.str.strip_chars(" \t\n\r\x0c\x0b")` (the same six ASCII whitespace chars
 *    Postgres `btrim` uses),
 *  - `regex` → `.str.contains(r"<re>")` (Polars regex, not literal),
 *  - `bounds` → a non-strict cast whose `is_not_null()` proves in-range (overflow ⇒ null ⇒ reject),
 *  - `null_is_success` → `x.is_null() | (…)`.
 *
 * A Polars engine that proves `domain: canonical` for the pair (contracts §3, e.g. `text->date`)
 * uses the **bare non-strict op + mask** instead (contracts §6): `~(x.cast(T, strict=False).is_null()
 * & x.is_not_null())`. Every shipped Polars cast entry is `narrower`/`divergent`, so this is
 * normally the canonical guard.
 */
object RejectGuardPolars {
    /** Python string literal of the ASCII-whitespace trim set (space, tab, LF, CR, FF, VT). */
    private const val ASCII_WS = "\" \\t\\n\\r\\x0c\\x0b\""

    /** True if [e] is an `internal.*` validity call this renderer handles. */
    fun isInternal(e: Expression): Boolean = e is FunctionCall && e.function.value.startsWith("internal.")

    /**
     * True for a synthesized `_ttrp_v<n>` validity flag — internal to the guard/branch, never part
     * of any OUT or `rejects` port schema (contracts §1). Dropped from every passthrough projection.
     */
    fun isValidityFlag(name: String): Boolean = name.startsWith("_ttrp_v")

    /**
     * The Polars boolean expression for an `internal.*` validity call, or null if unrecognized.
     * [arg] renders a sub-expression operand (a column / literal) to a Polars expression.
     */
    fun render(
        call: FunctionCall,
        arg: (Expression) -> String,
        rejects: RejectsSupport? = null,
    ): String? =
        when (call.function.value) {
            "internal.is_castable" -> {
                val x = arg(call.args[0])
                val suffix = strLit(call.args.getOrNull(1))
                val pair = canonicalPair(suffix)
                val entry = rejects?.entry("cast", pair)
                // A `domain: canonical` engine proves its native non-strict cast IS the canonical
                // acceptance set — use the bare mask; otherwise emit the canonical enforcing guard.
                if (entry?.domain == RejectDomain.CANONICAL) {
                    bareCastMask(x, suffix)
                } else {
                    ValidityCatalog.rejectCapability("cast", pair)?.let { castGuard(it, x, suffix) }
                }
            }
            "internal.is_nonzero" -> {
                val x = arg(call.args[0])
                val spec = ValidityCatalog.rejectCapability("op.div", "numeric,numeric->numeric")
                nullSafe(x, "($x != 0)", spec?.nullIsSuccess ?: true)
            }
            "internal.is_parseable_dt" -> {
                val x = arg(call.args[0])
                // Portable floor: the spec's format regex when present, else accept-non-null (refined
                // per-format in a later pass; the v1 fixture roster is cast-based).
                ValidityCatalog.all
                    .firstOrNull { it.function == "fn.to_date" || it.function == "fn.to_timestamp" }
                    ?.let { spec ->
                        spec.domain.regex?.let { nullSafe(x, "$x.str.contains(r\"${it}\")", spec.nullIsSuccess) }
                    }
                    ?: nullSafe(x, "$x.is_not_null()", true)
            }
            else -> null
        }

    /**
     * The clean-side rendering of a reject-capable [Cast] inside a **guarded** island: `strict=False`
     * so a residual the guard is `narrower` on (e.g. whitespace-padded int64 on Polars — the native
     * cast does not trim) yields null instead of aborting the whole frame. Null for a non-reject-
     * capable cast (rendered strictly by the caller, correct fail-fast on bad data).
     *
     * It does **not** re-normalize (no strip): canonical clean *values* for narrower domains ride the
     * op-canonicalization in RJ-P5; here it only guarantees the clean frame does not crash on a value
     * the guard accepted. Fail-fast (guard-free) islands never reach this — their strict cast stands.
     */
    fun renderCleanCast(
        cast: Cast,
        arg: (Expression) -> String,
    ): String? {
        val suffix = typeSuffix(cast.target) ?: return null
        ValidityCatalog.rejectCapability("cast", canonicalPair(suffix)) ?: return null
        return "${arg(cast.expr)}.cast(${polarsType(suffix)}, strict=False)"
    }

    /** A cast-target [TtrpType] → its canonical §2 suffix, or null if not a reject-capable target. */
    private fun typeSuffix(t: TtrpType): String? =
        when (t) {
            TtrpType.Integer -> "int64"
            is TtrpType.Decimal -> "decimal18_4"
            TtrpType.Float, TtrpType.Double, TtrpType.Number -> "float64"
            TtrpType.Date -> "date"
            TtrpType.Timestamp, TtrpType.Datetime -> "timestamp"
            TtrpType.Bool -> "bool"
            else -> null
        }

    /** Bare non-strict cast + mask (for a `domain: canonical` pair): raw is null OR the cast succeeds. */
    private fun bareCastMask(
        x: String,
        suffix: String?,
    ): String = "(~($x.cast(${polarsType(suffix)}, strict=False).is_null() & $x.is_not_null()))"

    /** cast text→T canonical guard: ASCII-ws strip, canonical regex, then the post-regex bounds. */
    private fun castGuard(
        spec: ValiditySpec,
        x: String,
        suffix: String?,
    ): String {
        val d = spec.domain
        val trimmed = if (d.trim == "ascii-ws") "$x.str.strip_chars($ASCII_WS)" else x
        val checks = mutableListOf<String>()
        d.regex?.let { checks += "$trimmed.str.contains(r\"${it}\")" }
        d.bounds?.let { checks += boundsCheck(trimmed, suffix) }
        val body = if (checks.size > 1) "(${checks.joinToString(" & ")})" else checks.firstOrNull() ?: "True"
        return nullSafe(x, body, spec.nullIsSuccess)
    }

    /** Post-regex range check: the string is digits(+sign), so a non-strict cast nulls only on overflow. */
    private fun boundsCheck(
        trimmed: String,
        suffix: String?,
    ): String = "$trimmed.cast(${polarsType(suffix)}, strict=False).is_not_null()"

    private fun nullSafe(
        x: String,
        body: String,
        nullSuccess: Boolean,
    ): String = if (nullSuccess) "($x.is_null() | $body)" else body

    /** cast-suffix (`internal.is_castable`'s 2nd arg) → the Polars dtype the mask casts to. */
    private fun polarsType(suffix: String?): String =
        when (suffix) {
            "int64" -> "pl.Int64"
            "decimal18_4", "decimal(18,4)" -> "pl.Decimal(18, 4)"
            "float64" -> "pl.Float64"
            "date" -> "pl.Date"
            "timestamp" -> "pl.Datetime(\"us\", \"UTC\")"
            "bool" -> "pl.Boolean"
            else -> "pl.String"
        }

    /** cast-suffix → canonical §2 type-pair spelling. */
    private fun canonicalPair(suffix: String?): String =
        when (suffix) {
            null -> "text->?"
            "decimal18_4" -> "text->decimal(18,4)"
            else -> "text->$suffix"
        }

    private fun strLit(e: Expression?): String? = ((e as? Literal)?.value as? LiteralValue.Str)?.value
}
