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

    /**
     * ED — a program-level derivation (ED `contracts.md` §1): the target [column] is bound to the pure
     * `call-fn` [functionId] (`versionConstraint` → a deploy pin) over [args] (batch/state values). The
     * frontend `DerivationDemand` is the typecheck form; this is the lowering form (args are `PlanValue`s).
     * Applies to the proposal-producing verbs (`insert-rows`/`update-rows`) — every proposal gets it.
     */
    data class PlanDerivation(
        val column: String,
        val functionId: String,
        val versionConstraint: String,
        val args: List<PlanValue>,
    )

    /**
     * ED-P4 — a program-level **derived row** (ED `contracts.md` §7): an extra row emitted beside the
     * proposed one (the cash counter-leg). Each column is a const, a copied batch value, or a `call-fn`.
     * Applies to `insert-rows` — every proposal emits the proposed row then the derived row(s).
     */
    data class PlanRowDerivation(
        val columns: Map<String, PlanRowSource>,
    )

    /** A source for one column of a derived row (contracts §7). */
    sealed interface PlanRowSource {
        data class Const(
            val text: String,
        ) : PlanRowSource

        data class Batch(
            val column: String,
        ) : PlanRowSource

        data class Call(
            val functionId: String,
            val versionConstraint: String,
            val args: List<PlanValue>,
        ) : PlanRowSource
    }

    fun lower(
        unit: EntryApplyUnit,
        derivations: List<PlanDerivation> = emptyList(),
        rowDerivations: List<PlanRowDerivation> = emptyList(),
    ): EntryLoweringResult {
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

        val proposals = batch.proposals.map { lowerProposal(verb, mode, roles, table, target, it, derivations, rowDerivations) }

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
        derivations: List<PlanDerivation>,
        rowDerivations: List<PlanRowDerivation>,
    ): ProposalPlan {
        val colIndex = mdColumnIndex(target)
        val keyRefs = keyRefs(p, colIndex)
        val valueRefs = p.values.keys.associate { mdName(colIndex, it) to PlanValue.BatchValue(it) }
        val versionCol = roles["rowVersion"] ?: DEFAULT_VERSION_COLUMN
        // ED — derived columns apply to the proposal-producing verbs only (contracts §1): a FunctionEval
        // per derived column, its result bound into the row via CallFnValue. Empty for every other verb.
        val evals = derivations.map { FunctionEval("fn_${it.column.lowercase()}", it.functionId, it.versionConstraint, it.args) }
        val derivedCols = derivations.associate { mdName(colIndex, it.column) to PlanValue.CallFnValue("fn_${it.column.lowercase()}") }
        return when (verb.id) {
            "entry.insert-rows" -> {
                // ED-P4 — the proposed (security) row, then the derived (cash counter-leg) row(s) + evals.
                val (rowEvals, rowInserts) = lowerRowDerivations(table, colIndex, rowDerivations)
                ProposalPlan(
                    p.row,
                    emptyList(),
                    listOf<PlanStep>(PlanStep.InsertRow(table, valueRefs + derivedCols)) + rowInserts,
                    evals + rowEvals,
                )
            }

            "entry.update-rows" ->
                if (mode == "optimistic") {
                    ProposalPlan(
                        p.row,
                        listOf(StateRead.CurrentRowVersion(VERSION_READ, table, keyRefs, versionCol)),
                        // §10: guard on the base version, then update in place AND advance the version
                        // column to a content hash of the post-update row (⚑EN-5).
                        listOf(
                            guard(table, keyRefs, p),
                            PlanStep.UpdateRow(table, keyRefs, valueRefs + derivedCols, versionColumn = versionCol),
                        ),
                        evals,
                    )
                } else {
                    // scd1/plain: overwrite in place. (update-rows is the "plain SCD1" verb, demand §2.)
                    ProposalPlan(p.row, emptyList(), listOf(PlanStep.UpdateRow(table, keyRefs, valueRefs + derivedCols)), evals)
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

    private fun keyRefs(
        p: RowBatch.Proposal,
        colIndex: Map<String, String>,
    ): Map<String, PlanValue> = p.key?.keys?.associate { mdName(colIndex, it) to PlanValue.BatchKey(it) } ?: emptyMap()

    /**
     * ED-P4 — lower each [PlanRowDerivation] to one extra [PlanStep.InsertRow] (columns bound to
     * `Const`/`BatchValue`/`CallFnValue`) + a [FunctionEval] per `Call` column (names `fnrow_<column>`,
     * disjoint from the §3 `fn_<column>` column-derivation names). Column names are md-normalized (F4).
     */
    private fun lowerRowDerivations(
        table: String,
        colIndex: Map<String, String>,
        rowDerivations: List<PlanRowDerivation>,
    ): Pair<List<FunctionEval>, List<PlanStep.InsertRow>> {
        val evals = mutableListOf<FunctionEval>()
        val inserts = mutableListOf<PlanStep.InsertRow>()
        for (rd in rowDerivations) {
            val cols = mutableMapOf<String, PlanValue>()
            for ((col, src) in rd.columns) {
                val mdCol = mdName(colIndex, col)
                cols[mdCol] =
                    when (src) {
                        is PlanRowSource.Const -> PlanValue.Const(src.text)
                        is PlanRowSource.Batch -> PlanValue.BatchValue(src.column)
                        is PlanRowSource.Call -> {
                            val name = "fnrow_${col.lowercase()}"
                            evals += FunctionEval(name, src.functionId, src.versionConstraint, src.args)
                            PlanValue.CallFnValue(name)
                        }
                    }
            }
            inserts += PlanStep.InsertRow(table, cols)
        }
        return evals to inserts
    }

    /**
     * F4 — a case-insensitive index from a wire column/key name to its md-declared exact-case identifier.
     * Batch column/key names are normalized to the md name before they become SQL identifiers, so the
     * emitted surgery quotes the real column regardless of the (client-supplied) batch casing; the
     * `BatchValue`/`BatchKey` keeps the original wire key so the run-time batch lookup still resolves.
     * Unknown columns never reach lowering (EN-001 fails the surface first), so `?: wireKey` is inert.
     */
    private fun mdColumnIndex(target: DbTable): Map<String, String> =
        target.columns.associate {
            val name = it.qname.name.substringAfterLast('.')
            name.lowercase() to name
        }

    private fun mdName(
        colIndex: Map<String, String>,
        wireKey: String,
    ): String = colIndex[wireKey.lowercase()] ?: wireKey

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
