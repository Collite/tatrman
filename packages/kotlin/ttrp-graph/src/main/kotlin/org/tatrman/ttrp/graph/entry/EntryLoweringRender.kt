// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.entry

/**
 * A deterministic plain-text render of a lowered [EntryApplyPlan], for golden tests (the
 * `explain/NormalizedGraphJson` house style: sectioned, sorted, one item per line — NOT JSON). Proposal
 * and step order are semantic and preserved; value maps are sorted by key so the bytes are stable
 * regardless of `Map` iteration order (the EN-P3 determinism bar). Ids/source locations are excluded —
 * only the surgery shape is pinned.
 */
object EntryLoweringRender {
    fun write(plan: EntryApplyPlan): String {
        val sb = StringBuilder()
        sb.append("apply target=${plan.target} verb=${plan.verb} semantics=${plan.semantics}\n")
        for (pp in plan.proposals) {
            sb.append("proposal ${pp.row}:\n")
            if (pp.reads.isNotEmpty()) {
                sb.append("  reads:\n")
                pp.reads.forEach { sb.append("    ").append(read(it)).append('\n') }
            }
            sb.append("  steps:\n")
            pp.steps.forEach { sb.append("    ").append(step(it)).append('\n') }
        }
        return sb.toString()
    }

    private fun read(r: StateRead): String =
        when (r) {
            is StateRead.CurrentRowVersion ->
                "${r.name} = CurrentRowVersion(table=${r.table}, versionCol=${r.versionColumn}, key=${map(r.key)})"
            is StateRead.ReversalCount ->
                "${r.name} = ReversalCount(table=${r.table}, link=${r.reversalLinkColumn}, key=${map(r.originalKey)})"
        }

    private fun step(s: PlanStep): String =
        when (s) {
            is PlanStep.OptimisticGuard ->
                "OptimisticGuard(table=${s.table}, key=${map(s.key)}, base=${value(s.baseRowVersion)}, " +
                    "read=${s.currentVersionRead})"
            is PlanStep.InsertRow ->
                "InsertRow(table=${s.table}, cols=${map(s.columns)})"
            is PlanStep.UpdateRow ->
                "UpdateRow(table=${s.table}, key=${map(s.key)}, set=${map(s.set)})"
            is PlanStep.CloseValidity ->
                "CloseValidity(table=${s.table}, key=${map(s.key)}, ${s.validToColumn}=${value(s.validTo)})"
            is PlanStep.ReverseRow ->
                "ReverseRow(table=${s.table}, id=${value(s.id)}, ${s.reversalLinkColumn}=${value(s.original)})"
            is PlanStep.ReplaceRow ->
                "ReplaceRow(table=${s.table}, id=${value(s.id)}, ${s.reversalLinkColumn}=${value(s.original)}, " +
                    "cols=${map(s.columns)})"
            is PlanStep.PhysicalDelete ->
                "PhysicalDelete(table=${s.table}, key=${map(s.key)})"
            is PlanStep.RejectEnvelope ->
                "RejectEnvelope(table=${s.table}, key=${map(s.key)}, code=${value(s.code)}, detail=${value(s.detail)})"
        }

    private fun map(m: Map<String, PlanValue>): String =
        m.entries
            .sortedBy { it.key }
            .joinToString(", ", "{", "}") { "${it.key}=${value(it.value)}" }

    private fun value(v: PlanValue): String =
        when (v) {
            is PlanValue.BatchValue -> "batch.${v.column}"
            is PlanValue.BatchKey -> "key.${v.column}"
            is PlanValue.Const -> "'${v.text}'"
            is PlanValue.StateValue -> "state.${v.read}"
            is PlanValue.DerivedId -> "id(${v.role}, ${value(v.base)}, state.${v.counterRead}+1)"
        }
}
