// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.semanticsblock

/**
 * MS (contracts §5) — **THE** derivation table: declared model facts → the `objectKind` a
 * resolver reads.
 *
 * There is exactly one implementation of this mapping anywhere in the ecosystem
 * (architecture §4.1), and it lives here, next to the vocabulary version that governs it — it
 * is versioned by [Vocabulary.SEMANTICS_VOCABULARY_VERSION]. The lexicon compiler calls it
 * per distinct model-object ref and writes the result into the archive's `targets` map;
 * everything downstream **surfaces that string verbatim**.
 *
 * ⛔ The rule that makes this table worth having: a kind is NEVER derived from a ref STRING,
 * here or anywhere. [ObjectFacts] carries [ObjectFacts.ownerRef] so the caller can record who
 * owns an attribute — [of] does not read it, and a test pins that it cannot. What decides
 * `isAttribute` is which model node the ref resolved to; what decides `listedAsMeasure` is the
 * owner's declared `measures:` list. Both are graph facts. `FrameRoles` was dead for exactly
 * as long as nothing supplied them (tatrman-server#69).
 *
 * The four values cross a wire, so consumers of the STRING tolerate unknowns (J-v2): a reader
 * that meets a kind it does not know treats the ref as unclassified rather than failing.
 */
object MentionKinds {
    const val MEASURE = "measure"
    const val ATTRIBUTE = "attribute"
    const val ENTITY = "entity"
    const val ENTITY_WITH_MEASURES = "entity_with_measures"

    /** Facts about one model object, as the model graph states them (NEVER from ref strings). */
    data class ObjectFacts(
        /** attribute/column (true) vs entity/table (false). */
        val isAttribute: Boolean,
        /** The owning entity's targetRef; null for entities. Carried for the archive, not consulted. */
        val ownerRef: String? = null,
        /** This attribute appears in its owner's `measures:` list. */
        val listedAsMeasure: Boolean = false,
        /** The entity's `measures:` list is non-empty. */
        val ownerHasMeasures: Boolean = false,
    )

    /** Total and closed — every [ObjectFacts] maps to one of the four kinds. */
    fun of(f: ObjectFacts): String =
        when {
            f.isAttribute && f.listedAsMeasure -> MEASURE
            // MS-R4: an attribute that is not listed IS an attribute. Absence is the answer,
            // not a missing declaration to be guessed around.
            f.isAttribute -> ATTRIBUTE
            f.ownerHasMeasures -> ENTITY_WITH_MEASURES
            else -> ENTITY
        }
}
