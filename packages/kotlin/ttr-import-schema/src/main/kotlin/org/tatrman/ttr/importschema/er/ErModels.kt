// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.er

import org.tatrman.ttr.importschema.probe.ColumnRef
import org.tatrman.ttr.importschema.probe.ProbeOrigin
import org.tatrman.ttr.importschema.probe.Provenance

/** Cardinality of a derived relation (from = child/FK side, to = parent/PK side). */
enum class RelationCardinality {
    ONE_TO_ONE,
    MANY_TO_ONE,
    MANY_TO_MANY,
}

/** The evidence trail a relation carries — grade + how it was found + the probe numbers. */
data class RelationEvidence(
    val grade: EvidenceGrade,
    val origin: ProbeOrigin,
    /** Which rule fired: `declared:<fkName>` or `pk-name-match` / a conventions pattern. */
    val rule: String,
    val provenance: Provenance? = null,
    /** Orphan count from the probe (estimate under SAMPLED); null when unprobed. */
    val orphanCount: Long? = null,
    val childRowCount: Long? = null,
)

/** A relation between two entities, admitted to the model only when [RelationEvidence.grade] allows. */
data class DerivedRelation(
    val name: String,
    val fromEntity: String,
    val toEntity: String,
    val fromColumns: List<String>,
    val toColumns: List<String>,
    val cardinality: RelationCardinality,
    val evidence: RelationEvidence,
)

/** An entity in the er first cut — a table shaped into logical form (junctions collapsed away). */
data class DerivedEntity(
    val name: String,
    /** `schema.table` this entity came from. */
    val sourceTable: String,
    /** Codebook (`Ciselnik*`) tables are proposed as enum-like entities. */
    val isCodebook: Boolean = false,
)

/** How a candidate relation was proposed — carried through shaping into the checklist. */
data class RelationCandidate(
    val child: ColumnRef,
    val parent: ColumnRef,
    val origin: ProbeOrigin,
    val rule: String,
)

/** The complete er derivation: the entities + relations that entered the model, and everything the
 * checklist (S4·T4) must surface (collapses, proposed folds, contradictions, unmatched columns). */
data class ErDerivationResult(
    val packageName: String,
    val entities: List<DerivedEntity>,
    val relations: List<DerivedRelation>,
    val notes: List<ChecklistNote>,
)

/** A single reviewable judgement for the checklist — the F5 confidence carrier. */
data class ChecklistNote(
    val kind: Kind,
    /** The subject: a `schema.table`, a relation name, or a column. */
    val subject: String,
    val detail: String,
) {
    enum class Kind {
        JUNCTION_COLLAPSED,
        HEADER_DETAIL_PROPOSED,
        CODEBOOK_PROPOSED,
        CONTRADICTED,
        UNMATCHED_COLUMN,
        RENAMED_IDENTIFIER,
        RELATION_EVIDENCE,
        UNPROBED_BUDGET,

        /** Re-run (F4-γ): the `db` mirror drifted — a PR-shaped proposal follows. */
        DB_DRIFT,

        /** Re-run (F4-γ): er-relevant drift (new table, dropped FK …) — flag only; er is not touched. */
        ER_DRIFT,
    }
}
