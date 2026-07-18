// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.expr.catalog.ValidityCatalog
import org.tatrman.ttrp.expr.catalog.ValiditySpec

/**
 * Renders the `internal.*` validity calls a reject guard carries directly to Postgres SQL
 * (Option E, RJ-P3). The canonical validity domains (contracts §2 / RJ-P0 YAMLs) are **regex**-
 * based, which the `plan.v1` wire + Calcite path cannot carry (no POSIX-regex operator, no raw-SQL
 * escape) — so the guard column is composed here as raw dialect SQL from the [ValidityCatalog]
 * domain, while every other column still routes through the translator ([CtePlanner]).
 *
 * This IS the guard emit contract (task 3.1.2): the SQL below realizes each YAML `domain`
 * (`trim` → ASCII-whitespace `btrim`, `regex` → `~`, `bounds` → a post-regex numeric range), NULL
 * as success per `null_is_success`.
 */
object RejectGuardSql {
    /** PG escape-string for the ASCII-whitespace trim set (space, tab, LF, CR, FF, VT). */
    private const val ASCII_WS = "E' \\t\\n\\r\\f\\x0B'"

    /** True if [e] is an `internal.*` validity call this renderer handles. */
    fun isInternal(e: Expression): Boolean = e is FunctionCall && e.function.value.startsWith("internal.")

    /**
     * True for a synthesized `_ttrp_v<n>` validity flag — internal to the guard/branch, never part
     * of any OUT or `rejects` port schema (contracts §1). Excluded from every passthrough so it
     * neither leaks into outputs nor appears in the reject rows (which carry only `_ttrp_reject_*`).
     */
    fun isValidityFlag(name: String): Boolean = name.startsWith("_ttrp_v")

    /**
     * The raw PG boolean expression for an `internal.*` validity call, or null if unrecognized.
     * [arg] renders a sub-expression operand (a column, cast, or literal) to PG SQL.
     */
    fun render(
        call: FunctionCall,
        arg: (Expression) -> String,
    ): String? =
        when (call.function.value) {
            "internal.is_castable" -> {
                val x = arg(call.args[0])
                val suffix = strLit(call.args.getOrNull(1))
                ValidityCatalog.rejectCapability("cast", canonicalPair(suffix))?.let { castGuard(it, x) }
            }
            "internal.is_nonzero" -> {
                val x = arg(call.args[0])
                val spec = ValidityCatalog.rejectCapability("op.div", "numeric,numeric->numeric")
                nullSafe(x, "($x) <> 0", spec?.nullIsSuccess ?: true)
            }
            "internal.is_parseable_dt" -> {
                val x = arg(call.args[0])
                // Portable floor: a format-anchored parse validity. Without a native TRY path we
                // fall back to the spec's regex when present, else accept-non-null (guard refined
                // per-format in a later pass; the fixture roster is cast-based).
                ValidityCatalog.all
                    .firstOrNull { it.function == "fn.to_date" || it.function == "fn.to_timestamp" }
                    ?.let { spec -> spec.domain.regex?.let { nullSafe(x, "$x ~ '${esc(it)}'", spec.nullIsSuccess) } }
                    ?: nullSafe(x, "$x IS NOT NULL", true)
            }
            else -> null
        }

    /** cast text->T guard: ASCII-ws trim, canonical regex, then the post-regex numeric bounds. */
    private fun castGuard(
        spec: ValiditySpec,
        x: String,
    ): String {
        val d = spec.domain
        val trimmed = if (d.trim == "ascii-ws") "btrim($x, $ASCII_WS)" else x
        val checks = mutableListOf<String>()
        d.regex?.let { checks += "$trimmed ~ '${esc(it)}'" }
        d.bounds?.let { checks += boundsCheck(trimmed, it) }
        val body = if (checks.size > 1) "(${checks.joinToString(" AND ")})" else checks.firstOrNull() ?: "true"
        return nullSafe(x, body, spec.nullIsSuccess)
    }

    /** Post-regex range check: the string is already digits(+sign), so a numeric cast is safe here. */
    private fun boundsCheck(
        trimmed: String,
        bounds: String,
    ): String =
        when (bounds) {
            "int64" -> "$trimmed::numeric BETWEEN -9223372036854775808 AND 9223372036854775807"
            "int32" -> "$trimmed::numeric BETWEEN -2147483648 AND 2147483647"
            else -> "true" // decimal/float bounds ride the regex; refined per-type in a later pass
        }

    private fun nullSafe(
        x: String,
        body: String,
        nullSuccess: Boolean,
    ): String = if (nullSuccess) "$x IS NULL OR $body" else body

    private fun strLit(e: Expression?): String? = ((e as? Literal)?.value as? LiteralValue.Str)?.value

    private fun esc(s: String): String = s.replace("'", "''")

    /** cast-suffix (`internal.is_castable`'s 2nd arg) → canonical §2 type-pair spelling. */
    private fun canonicalPair(suffix: String?): String =
        when (suffix) {
            null -> "text->?"
            "decimal18_4" -> "text->decimal(18,4)"
            else -> "text->$suffix"
        }

    // ---- a minimal PG renderer for the validity call's operand (column / cast / literal) ----

    fun renderArg(e: Expression): String =
        when (e) {
            is ColumnRef -> "\"${e.column}\""
            is Literal ->
                when (val v = e.value) {
                    is LiteralValue.Str -> "'${esc(v.value)}'"
                    is LiteralValue.Num -> v.raw
                    is LiteralValue.Bool -> v.value.toString()
                    LiteralValue.Null -> "NULL"
                }
            is Cast -> "CAST(${renderArg(e.expr)} AS ${e.target.canonical})"
            is FunctionCall -> "${e.function.name}(${e.args.joinToString(", ") { renderArg(it) }})"
            else -> throw IllegalArgumentException("reject guard operand not renderable to PG SQL: $e")
        }
}
