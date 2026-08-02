// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import org.tatrman.ttr.lexicon.EntryProvenance
import org.tatrman.ttr.lexicon.Lang
import org.tatrman.ttr.lexicon.LexiconArea
import org.tatrman.ttr.lexicon.MatchMethod
import org.tatrman.ttr.lexicon.SourceTag
import org.tatrman.ttr.lexicon.TargetClass
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.parser.loader.ParseResult

//
// RV-P1.2 — the compiler's inputs (contracts §2).
//
// Three extractors produce one row type; one merge stage classifies, validates, dedups and
// orders. The extractors are deliberately dumb: they do not know what a model contains, so
// they cannot silently drop a row for the wrong reason. Everything that can reject a row
// happens once, in `LexiconCompiler`, where the warning can be attributed.
//

/**
 * A pre-classification row. `targetRef` is still just a string here — classifying it is the
 * merge stage's job, because that is where a dangling ref becomes an attributable warning
 * (RV-20) rather than a silent `null`.
 */
data class SourceRow(
    val text: String,
    val lang: Lang,
    val method: MatchMethod,
    val targetRef: String,
    val sourceTag: SourceTag,
    val provenance: EntryProvenance,
)

/** One parsed `model lexicon [locale <id>]` unit — the TTR-M sugar side of the DECLARED layer. */
data class TtrmLexiconUnit(
    val file: String,
    val parsed: ParseResult,
)

/** Everything the compile reads. Any layer may be empty; an empty repo compiles to an empty table. */
data class LexiconSources(
    val area: LexiconArea = LexiconArea(emptyList(), emptyList()),
    val ttrm: List<TtrmLexiconUnit> = emptyList(),
    val model: Model? = null,
)

/**
 * The model snapshot a ref is validated against (RV-20). `op:` and `ground:` refs never reach
 * this — their class is in the prefix, and neither lives in the model graph.
 *
 * Returns null when the ref is not in the snapshot: that is a dangling ref, and the row is
 * dropped with a build warning rather than shipped as a binding that can never match.
 */
fun interface ModelRefIndex {
    fun classify(ref: String): TargetClass?

    companion object {
        /** Nothing resolves — every model-graph ref dangles. For tests of the warning path. */
        val EMPTY: ModelRefIndex = ModelRefIndex { null }
    }
}

/** A build warning. Never fatal: a lexicon with one bad row still compiles (RV-20 discipline). */
data class CompileWarning(
    val code: String,
    val message: String,
    val provenance: EntryProvenance,
) {
    companion object {
        /** The target ref is not in the model snapshot — row dropped. */
        const val DANGLING_REF: String = "RG-LEXC-001"

        /** Two rows agree on term+lang+target but disagree on method — the stronger one wins. */
        const val METHOD_CONFLICT: String = "RG-LEXC-002"

        /** An estate skill file overrides a stdlib op of the same id. */
        const val OPERATOR_OVERRIDE: String = "RG-LEXC-003"
    }
}
