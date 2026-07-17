// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.er

import org.tatrman.ttr.importschema.probe.ProbeOrigin
import org.tatrman.ttr.importschema.probe.ProbeResult
import org.tatrman.ttr.importschema.probe.Provenance

/**
 * The evidence-grade contract (S4·T1), as a PURE function of (origin, probe result). This is the
 * ratified F1 grade cascade in one place:
 *
 *  - A **declared** FK is `DECLARED` — unless the data contradicts it (a MSSQL `WITH NOCHECK`
 *    constraint can carry orphans): probed orphans ⇒ `CONTRADICTED`. A declared FK is never
 *    downgraded to `verified` just because it was probed.
 *  - A **heuristic** candidate is graded by its probe: no probe ⇒ `NAMED_ONLY`; unprobed for
 *    budget ⇒ `NAMED_ONLY_UNPROBED_BUDGET`; probed clean ⇒ `VERIFIED_FULL` / `VERIFIED_SAMPLED`
 *    by provenance; probed with orphans ⇒ `CONTRADICTED` (an orphan in a sample is exact).
 */
object GradeAssigner {
    fun grade(
        origin: ProbeOrigin,
        probe: ProbeResult?,
    ): EvidenceGrade {
        if (origin == ProbeOrigin.DECLARED) {
            return if (probe != null && probe.provenance != Provenance.UNPROBED_BUDGET && probe.hasOrphans) {
                EvidenceGrade.CONTRADICTED
            } else {
                EvidenceGrade.DECLARED
            }
        }
        // HEURISTIC
        return when (probe?.provenance) {
            null -> EvidenceGrade.NAMED_ONLY
            Provenance.UNPROBED_BUDGET -> EvidenceGrade.NAMED_ONLY_UNPROBED_BUDGET
            Provenance.UNPROBED_OVERRIDE -> EvidenceGrade.NAMED_ONLY
            Provenance.FULL -> if (probe.hasOrphans) EvidenceGrade.CONTRADICTED else EvidenceGrade.VERIFIED_FULL
            Provenance.SAMPLED -> if (probe.hasOrphans) EvidenceGrade.CONTRADICTED else EvidenceGrade.VERIFIED_SAMPLED
        }
    }

    /**
     * Cardinality from probe evidence: a FK child→parent is many:one by default; if the child's FK
     * values are unique (probe-confirmed), it is one:one. (Many:many only arises from junction
     * collapse, in the shaper.)
     */
    fun cardinality(probe: ProbeResult?): RelationCardinality =
        if (probe != null && probe.childValuesUnique && probe.provenance == Provenance.FULL) {
            RelationCardinality.ONE_TO_ONE
        } else {
            RelationCardinality.MANY_TO_ONE
        }
}
