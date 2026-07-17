// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.er

/**
 * The evidence grade every derived relation carries (F1, S4·T1). The grade records *why* a
 * relation is believed — the checklist surfaces it, and only sufficiently-graded relations enter
 * the `er` model (a `contradicted` candidate never becomes a relation; it goes to the checklist).
 */
enum class EvidenceGrade {
    /** A declared foreign key (catalog constraint). Highest confidence. */
    DECLARED,

    /** A convention/name match, probe-confirmed by an exact full scan (no orphans). */
    VERIFIED_FULL,

    /** A convention/name match, probe-confirmed on a keyed sample (no orphans in the sample). */
    VERIFIED_SAMPLED,

    /** A convention/name match that was not (yet) probe-confirmed — probed but inconclusive, or unprobed. */
    NAMED_ONLY,

    /** A convention/name match left unprobed because the Q-5 budget was exhausted. */
    NAMED_ONLY_UNPROBED_BUDGET,

    /**
     * Name says yes, data says no — the probe found orphans (an exact counterexample, even from a
     * sample). NOT emitted as a relation; recorded in the checklist as a contradiction.
     */
    CONTRADICTED,
    ;

    /** Whether a relation with this grade is admissible into the `er` model (vs checklist-only). */
    val emittedAsRelation: Boolean
        get() = this != CONTRADICTED
}
