// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.expr.catalog

import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.TtrpType

/** Whether a catalogue entry is a scalar function or an aggregate (drives the [AggregateCall] arm — B-T5). */
enum class FunctionKind { SCALAR, AGGREGATE }

/**
 * How a call's result type is derived from its arguments.
 *  - [Fixed]      — always [type] (e.g. comparisons -> bool, `length` -> integer).
 *  - [SameAsArg]  — the type of argument [index] (e.g. `coalesce`, `abs`).
 *  - [Promoted]   — the numeric promotion of the argument types (e.g. `+`, `round`).
 */
sealed interface ReturnTypeRule {
    data class Fixed(
        val type: TtrpType,
    ) : ReturnTypeRule

    data class SameAsArg(
        val index: Int,
    ) : ReturnTypeRule

    data object Promoted : ReturnTypeRule
}

/**
 * NULL propagation (canonical SQL 3VL — B-T5 / Q9, forced by A4):
 *  - [STRICT] — any NULL argument makes the result NULL.
 *  - [CUSTOM] — the function defines its own null behaviour (`coalesce`, `is null`, Kleene `and/or`).
 */
enum class NullRule { STRICT, CUSTOM }

/** A typed catalogue signature — the T5-c-β contract both the builtin catalogue and (later) md-catalog implement. */
data class CatalogEntry(
    val id: CatalogId,
    val name: String,
    val kind: FunctionKind,
    val params: List<TtrpType>,
    val returnType: ReturnTypeRule,
    val nullPropagation: NullRule,
    /**
     * Purity (RJ-P1, R-C2-b): a `pure: false` (volatile) entry may not appear in a reject-capable
     * position — the elaboration would produce a guard whose result differs from the guarded op.
     * The v1 roster is all-pure; the check is `TTRP-RJ-104`.
     */
    val pure: Boolean = true,
    /**
     * Internal-only (RJ-P1, R-E1-α): the synthesized validity functions (`internal.*`) are not
     * reachable from surface syntax — [FunctionCatalog.resolve] hides them, so authoring
     * `is_castable(...)` is an unknown function (`TTRP-FN-001`), while the rewriter/emitter reach
     * them via [org.tatrman.ttrp.expr.catalog.BuiltinCatalog.internal].
     */
    val internalOnly: Boolean = false,
)

/**
 * T5-c-β: two catalogues, ONE interface. The general TTR-P function library
 * ([BuiltinCatalog]) and — later, D-h — the md-catalog both implement this so the
 * type-checker and rewriter treat them uniformly. Name -> overload list; empty means
 * unknown.
 */
interface FunctionCatalog {
    fun resolve(name: String): List<CatalogEntry>

    /** Stable catalogue id — `"ttrp.builtin"` here; `"md-catalog"` later (D-h). */
    val catalogId: String
}

/**
 * Resolves against an ordered list of catalogues (D-h reserves the md-catalog slot;
 * v1 wires only [BuiltinCatalog]). Precedence is list order — the first catalogue
 * with an entry for a name wins; a name-collision policy is a D-session concern.
 */
class CompositeCatalog(
    private val catalogs: List<FunctionCatalog>,
) : FunctionCatalog {
    override val catalogId: String = "ttrp.composite"

    override fun resolve(name: String): List<CatalogEntry> {
        for (c in catalogs) {
            val hits = c.resolve(name)
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }
}
