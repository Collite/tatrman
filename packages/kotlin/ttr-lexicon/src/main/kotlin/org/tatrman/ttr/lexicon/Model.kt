// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon

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

/** One authored term: the text plus the resolved (own, else file-default) lang and method. */
data class TermDef(
    val text: String,
    val lang: Lang,
    val method: MatchMethod,
    val provenance: Provenance,
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
