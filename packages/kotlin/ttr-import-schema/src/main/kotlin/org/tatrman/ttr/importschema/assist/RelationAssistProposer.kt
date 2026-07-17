// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.assist

import org.tatrman.ttr.importschema.conventions.ConventionsFile
import org.tatrman.ttr.importschema.introspect.IntrospectedCatalog
import org.tatrman.ttr.importschema.probe.ColumnRef

/**
 * The F1-δ language-assist seam (S4·T6) — the deliverable is the SEAM, not an implementation. A
 * proposer reads identifiers/comments and *proposes* relation candidates in human terms; the v1
 * shipped implementation ([NoopRelationAssistProposer]) returns nothing.
 *
 * GI-2 quarantine: a proposal is never trusted. It enters the pipeline only as an extra HEURISTIC
 * candidate that must pass the same probe gate as any other — clean ⇒ a graded relation;
 * contradicted / unprobed ⇒ advisory-only in the checklist. So the deterministic path never
 * consumes an unverified guess, and the `--assist` OFF path is byte-identical to no flag.
 */
fun interface RelationAssistProposer {
    fun propose(
        catalog: IntrospectedCatalog,
        conventions: ConventionsFile,
    ): List<AssistProposal>
}

/** A proposed relation candidate + why the assist thinks so (surfaced in the checklist). */
data class AssistProposal(
    val child: ColumnRef,
    val parent: ColumnRef,
    val rationale: String,
)

/** The v1 implementation: proposes nothing. The real assist is a named later arc. */
class NoopRelationAssistProposer : RelationAssistProposer {
    override fun propose(
        catalog: IntrospectedCatalog,
        conventions: ConventionsFile,
    ): List<AssistProposal> = emptyList()
}
