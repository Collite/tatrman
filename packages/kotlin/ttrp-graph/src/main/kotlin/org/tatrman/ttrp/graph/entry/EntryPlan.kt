// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.entry

import org.tatrman.ttrp.diagnostics.TtrpDiagnostic

/*
 * EN-P3 ENTRY_LOWERING — the engine-free apply-plan step model (contracts §4/§5.1/§9). The typed
 * apply unit lowers to an ordered, deterministic list of steps whose value slots reference batch
 * columns + state reads, NEVER literal engine SQL (that representation is EN-P4). Modeled on the graph
 * `model/Nodes.kt` sealed-hierarchy idiom (data classes, `override`d shared fields, enums for closed
 * sets); the lowered result is an immutable value built functionally, like `TtrpGraph`.
 */

/** A value slot in a lowered step: a batch column, a literal, a state-read result, or an F3 derived id. */
sealed interface PlanValue {
    /** A value read from `proposal.values[column]`. */
    data class BatchValue(
        val column: String,
    ) : PlanValue

    /** A value read from `proposal.key[column]`. */
    data class BatchKey(
        val column: String,
    ) : PlanValue

    /** A compile-time literal (an effective date, a role marker). */
    data class Const(
        val text: String,
    ) : PlanValue

    /** The result of a named [StateRead] in this proposal's prefix. */
    data class StateValue(
        val read: String,
    ) : PlanValue

    /**
     * An F3 derived id (contracts §5, ⚑EN-2): `<base>-rev<n>` / `<base>-rep<n>` where `n` = 1 + the
     * [counterRead] state-read result. The concrete string is derived at run from the state read; the
     * shape is compile-time. [role] is `rev` (reversal) or `rep` (replacement). See [LedgerIds].
     */
    data class DerivedId(
        val role: String,
        val base: PlanValue,
        val counterRead: String,
    ) : PlanValue

    /**
     * ED — the result of a named [FunctionEval] in this proposal's prefix (the FO-8 derivation): a
     * column value COMPUTED door-side by a pinned pure `call-fn`, then bound like any other value. The
     * `StateValue` analogue for a function evaluation rather than a SQL state read (ED `contracts.md` §3).
     */
    data class CallFnValue(
        val read: String,
    ) : PlanValue
}

/**
 * ED — a prefix function evaluation (ED `contracts.md` §3): the pure `call-fn` [functionId]
 * (`versionConstraint` resolves to a pin at deploy) applied to [args] (batch/state values), its scalar
 * result named [name] and bound into a step via [PlanValue.CallFnValue]. Runs AFTER the [StateRead]
 * prefix (so [args] may reference [PlanValue.StateValue]) and before the DML — the deterministic order
 * state-reads → function-evals → DML. Pure by the EN-005 call-fn contract, so replay is byte-equal.
 */
data class FunctionEval(
    val name: String,
    val functionId: String,
    val versionConstraint: String,
    val args: List<PlanValue>,
)

/**
 * A state read that runs first, on the door's transaction connection (§5.1 prefix) — one snapshot per
 * apply. Its result value-binds later steps; the read shape is compile-time.
 */
sealed interface StateRead {
    val name: String
    val table: String

    /**
     * The current opaque row version for a key (§10 optimistic protocol) — binds the guard. The version
     * lives in an explicit md/convention column ([versionColumn], default `row_version`), NOT a
     * physical/engine pseudo-column: the door reads it portably and the emit quotes it in exact case.
     */
    data class CurrentRowVersion(
        override val name: String,
        override val table: String,
        val key: Map<String, PlanValue>,
        val versionColumn: String,
    ) : StateRead

    /** `count(reversal-link = <original>)` in committed state — the F3 `n` (contracts §5). */
    data class ReversalCount(
        override val name: String,
        override val table: String,
        val reversalLinkColumn: String,
        val originalKey: Map<String, PlanValue>,
    ) : StateRead
}

/** One lowered surgery step (contracts §9). Every value slot is a [PlanValue] — never engine SQL. */
sealed interface PlanStep {
    val table: String

    /** §10: reject the proposal per-row when the current row version differs from `baseRowVersion`. */
    data class OptimisticGuard(
        override val table: String,
        val key: Map<String, PlanValue>,
        val baseRowVersion: PlanValue,
        val currentVersionRead: String,
    ) : PlanStep

    /** A plain row insert (`insert-rows`; scd2 successor; ledger replacement uses [ReplaceRow]). */
    data class InsertRow(
        override val table: String,
        val columns: Map<String, PlanValue>,
    ) : PlanStep

    /**
     * An in-place update (`update-rows` on scd1/plain; guarded for undeclared targets). When
     * [versionColumn] is non-null (the §10 optimistic path), the update also advances that column to a
     * content hash of the post-update row (⚑EN-5, resolved 2026-07-23): `md5` over every non-key,
     * non-version column in md order, changed columns taking their new batch value and unchanged columns
     * their current value. This is a content-drift token (ABA-tolerant by design) — a same-content
     * rewrite re-derives the same version, exactly like the reference optimistic program.
     */
    data class UpdateRow(
        override val table: String,
        val key: Map<String, PlanValue>,
        val set: Map<String, PlanValue>,
        val versionColumn: String? = null,
    ) : PlanStep

    /** scd2 close: set `valid-to` on the current row, no successor when soft-deleting (§9). */
    data class CloseValidity(
        override val table: String,
        val key: Map<String, PlanValue>,
        val validToColumn: String,
        val validTo: PlanValue,
    ) : PlanStep

    /** ledger reversal row (Sysifos DNA): the reversal-linked negation of the original (contracts §9). */
    data class ReverseRow(
        override val table: String,
        val id: PlanValue,
        val reversalLinkColumn: String,
        val original: PlanValue,
    ) : PlanStep

    /** ledger replacement row: the corrected values, reversal-linked to the original (contracts §9). */
    data class ReplaceRow(
        override val table: String,
        val id: PlanValue,
        val reversalLinkColumn: String,
        val original: PlanValue,
        val columns: Map<String, PlanValue>,
    ) : PlanStep

    /** A physical delete by key — only for plain/optimistic/scd1 targets (never ledger/scd2, §9/EN-004). */
    data class PhysicalDelete(
        override val table: String,
        val key: Map<String, PlanValue>,
    ) : PlanStep

    /** `reject-row`: no surgery — feeds the §10 per-row reject envelope. */
    data class RejectEnvelope(
        override val table: String,
        val key: Map<String, PlanValue>,
        val code: PlanValue,
        val detail: PlanValue,
    ) : PlanStep
}

/**
 * One proposal's lowering: its state-read prefix, its function-eval prefix (ED — runs after the reads),
 * and its ordered steps (order is semantic — keep it). [evals] is empty for every non-derivation program.
 */
data class ProposalPlan(
    val row: Int,
    val reads: List<StateRead>,
    val steps: List<PlanStep>,
    val evals: List<FunctionEval> = emptyList(),
)

/** The lowered apply plan: the target, the verb, the derived semantics, and one plan per proposal. */
data class EntryApplyPlan(
    val target: String,
    val verb: String,
    val semantics: String,
    val proposals: List<ProposalPlan>,
)

/**
 * The result of lowering (the `PlanResult`/`NormalizeResult` idiom): the [plan] (null when lowering
 * could not proceed), the accumulated [diagnostics] (seeded from the unit's surface diagnostics), and
 * an [ok] flag.
 */
data class EntryLoweringResult(
    val plan: EntryApplyPlan?,
    val diagnostics: List<TtrpDiagnostic>,
) {
    val ok: Boolean get() = diagnostics.none { it.severity == org.tatrman.ttrp.diagnostics.Severity.ERROR }
}
