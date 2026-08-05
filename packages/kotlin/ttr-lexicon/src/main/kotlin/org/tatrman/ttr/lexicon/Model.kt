// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//
// The typed lexicon-area model (RV-P1.1; contracts §2).
//
// These are the *authored* shapes — what a defining repo's `lexicon/` tree says. The
// compiled artifact (uniform entry table, normalized terms, layer tuple) is RV-P1.2's
// output and lives elsewhere: this library stops at "the files are well-formed and typed".
//
// Kinds are NOT modelled. An entry is an ALIAS or a VALUE by virtue of its target class,
// derived downstream (RV-38) — storing it here would let a file disagree with the model
// graph about what it is.
//

/** Where a definition came from. Carried on every def so a diagnostic can point at a line. */
data class Provenance(
    val file: String,
    val line: Int,
)

/** BCP-47-lite. `CS_EN` is the both-languages form, authored `cs|en`. */
enum class Lang(
    val wire: String,
) {
    CS("cs"),
    EN("en"),
    CS_EN("cs|en"),
    ;

    companion object {
        fun ofWire(wire: String): Lang? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * How a term is matched. `TYPOS` carries its edit budget in the type, so an unbudgeted
 * TYPOS cannot be represented — the schema rejects `TYPOS` and this rejects it again.
 */
sealed interface MatchMethod {
    val wire: String

    data object Exact : MatchMethod {
        override val wire = "EXACT"
    }

    data object Tokens : MatchMethod {
        override val wire = "TOKENS"
    }

    data class Typos(
        val maxDistance: Int,
    ) : MatchMethod {
        init {
            require(maxDistance in 1..3) { "TYPOS distance must be 1..3, was $maxDistance" }
        }

        override val wire = "TYPOS($maxDistance)"
    }

    companion object {
        private val TYPOS = Regex("""^TYPOS\((\d+)\)$""")

        /** Parses the wire form. Returns null for anything outside the closed set. */
        fun ofWire(wire: String): MatchMethod? =
            when {
                wire == Exact.wire -> Exact
                wire == Tokens.wire -> Tokens
                else ->
                    TYPOS.matchEntire(wire)?.groupValues?.get(1)?.toIntOrNull()?.let {
                        if (it in 1..3) Typos(it) else null
                    }
            }
    }
}

/**
 * RV-44 / ⚑M-1 — the normalization stratum a comparison happens on. **Closed**, and closed on
 * purpose: each name is a form the engine already computes, so a fourth would be a new invariant
 * rather than a new option. There is deliberately no case-sensitive `verbatim`: the whole TTR match
 * culture lowercases (category keys, both normalizers), and no use case asked for one.
 */
@Serializable
enum class Norm(
    val wire: String,
) {
    /** NFC + lowercase, **diacritics preserved** — [TermNormalizer], today's EXACT/TYPOS basis. */
    @SerialName("canonical")
    CANONICAL("canonical"),

    /** [CANONICAL] + diacritics stripped — the engine's recall fold. */
    @SerialName("folded")
    FOLDED("folded"),

    /** Per-token lemmatization, diacritics preserved. Backend rides RV-40's per-op binding. */
    @SerialName("lemma")
    LEMMA("lemma"),

    ;

    companion object {
        fun ofWire(wire: String): Norm? = entries.firstOrNull { it.wire == wire }

        /** The closed set, in declaration order — for a diagnostic that names what was allowed. */
        val WIRE_NAMES: List<String> get() = entries.map { it.wire }
    }
}

/**
 * RV-44 — bounded edit distance on one [Norm], costing [penalty] per edit off that norm's `exact`
 * score. Fires at `exact − d·penalty` for `1 ≤ d ≤ distance`.
 *
 * The penalty needs an anchor, which is why a `typos` without a sibling `exact` on the same norm is
 * a validator error rather than a rule with an implied 1.00.
 */
@Serializable
data class TyposRule(
    val distance: Int,
    val penalty: Double,
)

/**
 * One `(norm, algorithms, scores)` row of a [MatchProfile].
 *
 * [tokens] is a flag rather than a type because the authored `tokens: {}` has nothing in it yet
 * (contracts §2 addendum). When it gains keys this becomes a type — additive on both surfaces.
 */
@Serializable
data class NormRule(
    val norm: Norm,
    /** Equality on [norm]. `(0,1]`. Null ⇒ this rule declares no equality score. */
    val exact: Double? = null,
    val typos: TyposRule? = null,
    val tokens: Boolean = false,
)

/**
 * RV-44 — a declared matching profile: *which* normalized forms count for a term and *how strong*
 * each is (contracts §2 addendum; `design/06-M-matching-profiles-options.md`).
 *
 * Order is convention (strongest first, as Splink's comparison levels read); **combination is max**
 * over firings, so a mis-ordered list gives identical results.
 *
 * Every DECLARED row carries one — either authored as `match:` or [ofSugar]-resolved from `method:`
 * — so no consumer ever re-derives sugar. METADATA and member rows carry none (⚑M-2: profiles are
 * an authoring surface; the member index keeps engine scores).
 */
@Serializable
data class MatchProfile(
    val rules: List<NormRule>,
) {
    /**
     * The closest `method:` sugar to this profile, for the artifact's `method` column.
     *
     * A **projection, not an identity**: it round-trips exactly for the three [ofSugar] shapes, and
     * for anything richer it names the widest algorithm the profile admits. That keeps the column
     * meaningful for a reader that predates profiles — such a reader narrows (it gates on canonical
     * equality where the author also allowed a folded hit), which is the safe direction.
     */
    fun sugarMethod(): MatchMethod =
        when {
            rules.any { it.tokens } -> MatchMethod.Tokens
            // Clamped to the sugar's own 1..3 range: `TYPOS(4)` is unwritable, while
            // `typos: { distance: 4 }` is not (the addendum bounds it at `distance ≥ 1` only).
            rules.any { it.typos != null } ->
                MatchMethod.Typos(rules.mapNotNull { it.typos?.distance }.max().coerceIn(1, 3))

            else -> MatchMethod.Exact
        }

    companion object {
        /** ⚑M-4 — the canonical form of an authored term must be longer than this to fuzz-match. */
        const val SHORT_TERM_MAX_CHARS: Int = 3

        /** The per-edit penalty `TYPOS(d)` sugar expands to (contracts §2 addendum, M-T6). */
        const val SUGAR_TYPOS_PENALTY: Double = 0.05

        /** The equality score `EXACT`/`TYPOS(d)` sugar expands to. */
        const val SUGAR_EXACT_SCORE: Double = 1.00

        /**
         * M-T6, byte-exact: `method:` sugar → the profile it means. `method:` stays valid forever —
         * it is how the overwhelming majority of terms will keep being authored.
         */
        fun ofSugar(method: MatchMethod): MatchProfile =
            MatchProfile(
                listOf(
                    when (method) {
                        MatchMethod.Exact -> NormRule(Norm.CANONICAL, exact = SUGAR_EXACT_SCORE)
                        MatchMethod.Tokens -> NormRule(Norm.CANONICAL, tokens = true)
                        is MatchMethod.Typos ->
                            NormRule(
                                Norm.CANONICAL,
                                exact = SUGAR_EXACT_SCORE,
                                typos = TyposRule(method.maxDistance, SUGAR_TYPOS_PENALTY),
                            )
                    },
                ),
            )
    }
}

/**
 * One authored term: the text plus the resolved (own, else file-default) lang, method and profile.
 *
 * [matchProfile] is null when the author wrote no `match:` at either level — the compiler then
 * resolves one from [method] via [MatchProfile.ofSugar]. Keeping "authored" and "resolved" apart
 * here is what lets the ⚑M-4 guard and the `method:`-vs-`match:` conflict rule speak about what the
 * author actually wrote.
 */
data class TermDef(
    val text: String,
    val lang: Lang,
    val method: MatchMethod,
    val provenance: Provenance,
    val matchProfile: MatchProfile? = null,
)

/** One `entries:` item — n terms pointing at one target ref. */
data class LexiconEntryDef(
    val terms: List<TermDef>,
    val target: String,
    val provenance: Provenance,
)

/** A parsed `*.lex.yaml` file. */
data class LexiconDataFile(
    val entries: List<LexiconEntryDef>,
    val provenance: Provenance,
)

/** A parsed skill file — frontmatter as a def, body kept verbatim for the Golems. */
data class SkillDef(
    val opId: String,
    val triggers: List<TermDef>,
    val requires: List<String>,
    val version: Int,
    val body: String,
    val provenance: Provenance,
)

/**
 * One `lexicon/` tree, loaded. The compiler (RV-P1.2) consumes this; nothing here is
 * resolved against a model snapshot yet, so `target` refs may still be dangling.
 */
data class LexiconArea(
    val dataFiles: List<LexiconDataFile>,
    val skills: List<SkillDef>,
) {
    val entries: List<LexiconEntryDef> get() = dataFiles.flatMap { it.entries }
    val termCount: Int get() = entries.sumOf { it.terms.size }
}
