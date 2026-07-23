// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.entry

import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.entry.EntryApplyResolver.EntryApplyUnit
import org.tatrman.ttrp.entry.EntryVerbCatalog
import org.tatrman.ttrp.entry.RowBatch

/**
 * EN-P3 ENTRY_LOWERING (contracts §4/§9) — the compile-time, engine-free expansion of a typed
 * [EntryApplyUnit] into an ordered [EntryApplyPlan] of [PlanStep]s, driven by the target's md
 * `changeSemantics`. Its own pass over its own unit (architecture §2) — NOT a graph rewrite; the
 * `RejectElaboration` deterministic-construction discipline is the style precedent. Representation +
 * SQL emit are EN-P4; `call-fn` purity is EN-P5.
 *
 * By construction the lowering NEVER produces a physical delete on a ledger/scd2 target — deletes on
 * those lower to a soft close / pure reversal (§9). The [TtrpDiagnosticId.EN_004] structural guard
 * asserts that invariant (inert today, like `RejectPurityCheck`), independent of the server §12 gate.
 */
object EntryLowering {
    private const val VERSION_READ = "currentVersion"
    private const val REVERSAL_READ = "reversalCount"

    /** §10 optimistic version column — a convention column, overridable via the `rowVersion` role. */
    private const val DEFAULT_VERSION_COLUMN = "row_version"

    fun lower(unit: EntryApplyUnit): EntryLoweringResult {
        val diags = unit.diagnostics.toMutableList()
        val target = unit.target
        val verb = unit.verb
        val batch = unit.batch
        // Do not lower a unit that failed the surface (EN-001..007) or is missing an input — its
        // diagnostics already tell the story; a partial plan would be misleading.
        if (target == null || verb == null || batch == null || diags.any { it.severity == Severity.ERROR }) {
            return EntryLoweringResult(null, diags)
        }

        val mode = target.changeSemantics?.mode ?: "optimistic"
        val roles = target.changeSemantics?.roleColumns ?: emptyMap()
        val table = target.qname.name
        val loc = SourceLocation(unit.fileName, 1, 0, 1, 0, 0, 0)

        val proposals = batch.proposals.map { lowerProposal(verb, mode, roles, table, target, it) }

        // EN-004 structural guard — the lowering must not have produced forbidden surgery.
        proposals.forEach { pp ->
            if ((mode == "ledger" || mode == "scd2") && pp.steps.any { it is PlanStep.PhysicalDelete }) {
                diags +=
                    TtrpDiagnostic(
                        TtrpDiagnosticId.EN_004,
                        Severity.ERROR,
                        "lowering produced a physical delete on the `$mode` target `$table` (row ${pp.row})",
                        loc,
                    )
            }
        }

        val ok = diags.none { it.severity == Severity.ERROR }
        return EntryLoweringResult(if (ok) EntryApplyPlan(table, verb.id, mode, proposals) else null, diags)
    }

    private fun lowerProposal(
        verb: EntryVerbCatalog.VerbSig,
        mode: String,
        roles: Map<String, String>,
        table: String,
        target: DbTable,
        p: RowBatch.Proposal,
    ): ProposalPlan {
        val keyRefs = keyRefs(p)
        val valueRefs = p.values.keys.associateWith { PlanValue.BatchValue(it) }
        val versionCol = roles["rowVersion"] ?: DEFAULT_VERSION_COLUMN
        return when (verb.id) {
            "entry.insert-rows" ->
                ProposalPlan(p.row, emptyList(), listOf(PlanStep.InsertRow(table, valueRefs)))

            "entry.update-rows" ->
                if (mode == "optimistic") {
                    ProposalPlan(
                        p.row,
                        listOf(StateRead.CurrentRowVersion(VERSION_READ, table, keyRefs, versionCol)),
                        // §10: guard on the base version, then update in place AND advance the version
                        // column to a content hash of the post-update row (⚑EN-5).
                        listOf(
                            guard(table, keyRefs, p),
                            PlanStep.UpdateRow(table, keyRefs, valueRefs, versionColumn = versionCol),
                        ),
                    )
                } else {
                    // scd1/plain: overwrite in place. (update-rows is the "plain SCD1" verb, demand §2.)
                    ProposalPlan(p.row, emptyList(), listOf(PlanStep.UpdateRow(table, keyRefs, valueRefs)))
                }

            "entry.effective-date-change" -> {
                // scd2: close the current row (valid-to = effectiveDate) + insert the successor
                // (valid-from = effectiveDate); timeline contiguity holds by construction (§9).
                val validTo = roles["validTo"] ?: "valid_to"
                val validFrom = roles["validFrom"] ?: "valid_from"
                val eff = PlanValue.Const(p.effectiveDate.orEmpty())
                ProposalPlan(
                    p.row,
                    emptyList(),
                    listOf(
                        PlanStep.CloseValidity(table, keyRefs, validTo, eff),
                        PlanStep.InsertRow(table, keyRefs + valueRefs + (validFrom to eff)),
                    ),
                )
            }

            "entry.reverse-and-replace" -> {
                // ledger: reversal row THEN replacement row, both reversal-linked (§5.1 order + F3 ids).
                val link = roles["reversalLink"] ?: "reversal_of"
                val base = originalId(target, keyRefs)
                ProposalPlan(
                    p.row,
                    listOf(StateRead.ReversalCount(REVERSAL_READ, table, link, keyRefs)),
                    listOf(
                        PlanStep.ReverseRow(table, PlanValue.DerivedId("rev", base, REVERSAL_READ), link, base),
                        PlanStep.ReplaceRow(
                            table,
                            PlanValue.DerivedId("rep", base, REVERSAL_READ),
                            link,
                            base,
                            valueRefs,
                        ),
                    ),
                )
            }

            "entry.delete-rows" ->
                when (mode) {
                    // scd2 → soft close (valid-to = effectiveDate), no successor: history is never destroyed.
                    "scd2" -> {
                        val validTo = roles["validTo"] ?: "valid_to"
                        ProposalPlan(
                            p.row,
                            emptyList(),
                            listOf(
                                PlanStep.CloseValidity(
                                    table,
                                    keyRefs,
                                    validTo,
                                    PlanValue.Const(p.effectiveDate.orEmpty()),
                                ),
                            ),
                        )
                    }
                    // ledger → pure reversal (replacement omitted): the audit chain is preserved.
                    "ledger" -> {
                        val link = roles["reversalLink"] ?: "reversal_of"
                        val base = originalId(target, keyRefs)
                        ProposalPlan(
                            p.row,
                            listOf(StateRead.ReversalCount(REVERSAL_READ, table, link, keyRefs)),
                            listOf(
                                PlanStep.ReverseRow(table, PlanValue.DerivedId("rev", base, REVERSAL_READ), link, base),
                            ),
                        )
                    }
                    // optimistic (undeclared) → physical delete by key, guarded by baseRowVersion (§10).
                    "optimistic" ->
                        ProposalPlan(
                            p.row,
                            listOf(StateRead.CurrentRowVersion(VERSION_READ, table, keyRefs, versionCol)),
                            listOf(guard(table, keyRefs, p), PlanStep.PhysicalDelete(table, keyRefs)),
                        )
                    // scd1/plain → physical delete by key.
                    else ->
                        ProposalPlan(p.row, emptyList(), listOf(PlanStep.PhysicalDelete(table, keyRefs)))
                }

            "entry.reject-row" ->
                ProposalPlan(
                    p.row,
                    emptyList(),
                    listOf(
                        PlanStep.RejectEnvelope(
                            table,
                            keyRefs,
                            PlanValue.BatchValue("code"),
                            PlanValue.BatchValue("detail"),
                        ),
                    ),
                )

            else -> ProposalPlan(p.row, emptyList(), emptyList())
        }
    }

    private fun keyRefs(p: RowBatch.Proposal): Map<String, PlanValue> =
        p.key?.keys?.associateWith { PlanValue.BatchKey(it) } ?: emptyMap()

    private fun guard(
        table: String,
        keyRefs: Map<String, PlanValue>,
        p: RowBatch.Proposal,
    ) = PlanStep.OptimisticGuard(table, keyRefs, PlanValue.Const(p.baseRowVersion.orEmpty()), VERSION_READ)

    /** The original entry-row id a ledger reversal links to — the primary key value (contracts §5). */
    private fun originalId(
        target: DbTable,
        keyRefs: Map<String, PlanValue>,
    ): PlanValue =
        target.primaryKey
            .firstOrNull()
            ?.let { keyRefs[it] ?: PlanValue.BatchKey(it) }
            ?: keyRefs.values.firstOrNull()
            ?: PlanValue.Const("")
}
