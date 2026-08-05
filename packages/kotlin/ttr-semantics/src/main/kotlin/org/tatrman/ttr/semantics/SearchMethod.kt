// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.diagnostics.DiagnosticCode
import org.tatrman.ttr.parser.model.MatchMethodValue
import org.tatrman.ttr.parser.model.SearchHintsValue

/**
 * RV-P1.5 (grammar 0.12, RV-31/RV-32) — the MEANING of `searchable` and its match
 * method. Kotlin port of the TS `@tatrman/semantics` `search-method.ts`; the two
 * must agree message-for-message, because the `fuzzy` deprecation rides the
 * portable cross-target conformance subset.
 *
 * The parser is mechanical (name as authored, raw argument). This file owns the
 * closed EXACT/TYPOS/TOKENS vocabulary, the arity rule, the RV-32 default of
 * `TYPOS(1)`, and the mapping of the deprecated `fuzzy` boolean.
 *
 * Resolution NEVER fails: a rejected method degrades to the default so consumers
 * keep working on a model whose diagnostics nobody has fixed yet.
 */
enum class MatchMethodName { EXACT, TYPOS, TOKENS }

/** RV-32: the default edit distance for an included carrier that declares none. */
const val DEFAULT_TYPOS_DISTANCE: Int = 1

/**
 * RV-32: the largest edit distance a `TYPOS` may ask for.
 *
 * The bound is the lexicon's, not this layer's: `ttr-lexicon`'s `MatchMethod.Typos` accepts `1..3`
 * and rejects the rest (`RG-LEX-002`), and a method authored in TTR-M compiles into that same
 * artifact. Accepting `TYPOS(7)` here would only defer the rejection to a place the model author is
 * no longer looking.
 */
const val MAX_TYPOS_DISTANCE: Int = 3

enum class MatchMethodOrigin { AUTHORED, LEGACY_FUZZY, DEFAULT }

data class EffectiveMatchMethod(
    val name: MatchMethodName,
    /** Maximum edit distance — TYPOS only. */
    val maxDistance: Int? = null,
    val origin: MatchMethodOrigin,
)

data class SearchMethodDiagnostic(
    val code: DiagnosticCode,
    val message: String,
    val isError: Boolean,
)

private fun nameOf(raw: String): MatchMethodName? = MatchMethodName.entries.firstOrNull { it.name == raw.uppercase() }

private fun isValidDistance(d: Double?): Boolean =
    d == null || (d >= 1.0 && d <= MAX_TYPOS_DISTANCE.toDouble() && d % 1.0 == 0.0)

private val DEFAULT_METHOD =
    EffectiveMatchMethod(MatchMethodName.TYPOS, DEFAULT_TYPOS_DISTANCE, MatchMethodOrigin.DEFAULT)

/**
 * The method a carrier's content is matched with, or `null` when the carrier is
 * not included in the lexicon.
 *
 * Precedence: an authored `method:` wins; then the deprecated `fuzzy` boolean
 * (`true` → `TYPOS(1)`, `false` → `EXACT` — the authored "no fuzzy" intent
 * survives the bump); then the RV-32 default.
 */
fun effectiveMatchMethod(search: SearchHintsValue?): EffectiveMatchMethod? {
    if (search == null || !search.searchable) return null

    val authored = search.method
    if (authored != null) {
        val name = nameOf(authored.name) ?: return DEFAULT_METHOD
        if (name == MatchMethodName.TYPOS) {
            if (!isValidDistance(authored.argument)) return DEFAULT_METHOD
            return EffectiveMatchMethod(
                MatchMethodName.TYPOS,
                authored.argument?.toInt() ?: DEFAULT_TYPOS_DISTANCE,
                MatchMethodOrigin.AUTHORED,
            )
        }
        // EXACT / TOKENS: a stray argument is diagnosed, not obeyed.
        return EffectiveMatchMethod(name, null, MatchMethodOrigin.AUTHORED)
    }

    if (search.fuzzyAuthored) {
        return if (search.fuzzy) {
            EffectiveMatchMethod(MatchMethodName.TYPOS, DEFAULT_TYPOS_DISTANCE, MatchMethodOrigin.LEGACY_FUZZY)
        } else {
            EffectiveMatchMethod(MatchMethodName.EXACT, null, MatchMethodOrigin.LEGACY_FUZZY)
        }
    }

    return DEFAULT_METHOD
}

/** The message text is a cross-target contract — keep TS/Python in step. */
fun fuzzyDeprecationMessage(fuzzy: Boolean): String {
    val replacement = if (fuzzy) "TYPOS($DEFAULT_TYPOS_DISTANCE)" else "EXACT"
    return "'fuzzy: $fuzzy' is deprecated (grammar 0.12) — replace it with " +
        "'searchable method: $replacement'"
}

/** Validate the `searchable`/`method`/`fuzzy` surface of one search block. */
fun validateSearchMethod(search: SearchHintsValue): List<SearchMethodDiagnostic> {
    val out = mutableListOf<SearchMethodDiagnostic>()
    val authored = search.method
    if (authored != null) {
        val name = nameOf(authored.name)
        if (name == null) {
            out +=
                SearchMethodDiagnostic(
                    DiagnosticCode.UnknownMatchMethod,
                    "unknown match method '${authored.name}' — expected EXACT, TYPOS(n) or TOKENS; " +
                        "falling back to TYPOS($DEFAULT_TYPOS_DISTANCE)",
                    isError = true,
                )
        } else if (name == MatchMethodName.TYPOS) {
            if (!isValidDistance(authored.argument)) {
                out +=
                    SearchMethodDiagnostic(
                        DiagnosticCode.InvalidMatchMethodArgument,
                        "match method 'TYPOS' takes a whole-number edit distance of 1..$MAX_TYPOS_DISTANCE; got " +
                            "'${formatArgument(authored.argument)}' — falling back to " +
                            "TYPOS($DEFAULT_TYPOS_DISTANCE)",
                        isError = true,
                    )
            }
        } else if (authored.argument != null) {
            out +=
                SearchMethodDiagnostic(
                    DiagnosticCode.InvalidMatchMethodArgument,
                    "match method '$name' takes no argument",
                    isError = true,
                )
        }
    }

    // The deprecation fires on the authored `fuzzy` whether or not a `method`
    // overrides it — the property is going away either way.
    if (search.fuzzyAuthored) {
        out +=
            SearchMethodDiagnostic(
                DiagnosticCode.SearchFuzzyDeprecated,
                fuzzyDeprecationMessage(search.fuzzy),
                isError = false,
            )
    }
    return out
}

/**
 * Render a raw numeric argument the way the TS/Python targets do — `2`, not
 * `2.0` — so diagnostic messages stay byte-identical across the conformance
 * harness. Delegates to the parser model, which renders the same number for the
 * writer and the conformance dump.
 */
fun formatArgument(argument: Double?): String = argument?.let { MatchMethodValue.formatNumber(it) } ?: "null"
