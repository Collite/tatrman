// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.entry.RowBatch
import org.tatrman.ttrp.graph.entry.EntryApplyPlan
import org.tatrman.ttrp.graph.entry.FunctionEval
import org.tatrman.ttrp.graph.entry.PlanStep
import org.tatrman.ttrp.graph.entry.PlanValue
import org.tatrman.ttrp.graph.entry.ProposalPlan
import org.tatrman.ttrp.graph.entry.StateRead

/** The emit result: the [EmittedApplyPlan] (null on error) + accumulated typed-bind diagnostics. */
data class ApplyEmitResult(
    val plan: EmittedApplyPlan?,
    val diagnostics: List<TtrpDiagnostic>,
) {
    val ok: Boolean get() = diagnostics.none { it.severity == Severity.ERROR }
}

/**
 * EN-P4 apply-plan emitter (contracts §5.1, ⚑EN-1(a)) — turns an EN-P3 [EntryApplyPlan] + its concrete
 * §5 batch + the target md into an [EmittedApplyPlan]: ordered `?`-parameterized PG SQL-DML + a typed
 * positional bind manifest + a state-read prefix. Identifiers are quoted in exact md case with doubled-
 * quote escaping (F4, never trust engine folding); values are placeholders only (never inline). Each
 * batch value is typed from its md column AT MANIFEST BUILD — an incompatible value is a `TTRP-EN-001`
 * emit error, never deferred to execution (the F4 `stringtype=unspecified` lesson inverted).
 *
 * Ledger reversal basis (settling the §5.1 open item): the reversal row **reverses the original row**
 * via `INSERT … SELECT` — BIGINT measure columns negated, others copied, reversal-link → original; the
 * replacement is a fresh corrected row (link null, so the F3 count only sees reversals).
 */
object ApplyEmitter {
    private const val PROGRAM_VERSION = "0.0.0-dev"
    private val DATE_RE = Regex("""\d{4}-\d{2}-\d{2}""")

    fun emit(
        plan: EntryApplyPlan,
        batch: RowBatch,
        table: DbTable,
        version: String = PROGRAM_VERSION,
        pluginPins: List<PluginPin> = emptyList(),
    ): ApplyEmitResult {
        val diags = mutableListOf<TtrpDiagnostic>()
        val loc = SourceLocation("${plan.target}-entry-apply.ttrp", 1, 0, 1, 0, 0, 0)
        val types = table.columns.associate { colName(it.qname.name).lowercase() to sqlTypeOf(it.dataType) }
        val allCols = table.columns.map { colName(it.qname.name) }
        val pkCol = table.primaryKey.firstOrNull()
        val byRow = batch.proposals.associateBy { it.row }

        val proposals =
            plan.proposals.map { pp ->
                emitProposal(pp, byRow[pp.row], types, allCols, pkCol, pluginPins, diags, loc)
            }

        val emitted =
            EmittedApplyPlan(
                target = plan.target,
                verb = plan.verb,
                semantics = plan.semantics,
                applyProgram = ApplyProgramRef("${plan.target}-entry-apply", version),
                pluginPins = pluginPins,
                proposals = proposals,
            )
        return ApplyEmitResult(if (diags.any { it.severity == Severity.ERROR }) null else emitted, diags)
    }

    private fun emitProposal(
        pp: ProposalPlan,
        proposal: RowBatch.Proposal?,
        types: Map<String, SqlType>,
        allCols: List<String>,
        pkCol: String?,
        pluginPins: List<PluginPin>,
        diags: MutableList<TtrpDiagnostic>,
        loc: SourceLocation,
    ): EmittedProposal {
        val ctx = Ctx(pp.row, proposal, types, allCols, pkCol, diags, loc)
        val reads = pp.reads.map { emitRead(it, ctx) }
        // ED — the function-eval prefix: each eval's deploy pin + its arg binds (typed from the source
        // column). The door evaluates these after the reads and binds the results into the DML steps.
        val funcs =
            pp.evals.map { ev ->
                EmittedFuncEval(ev.name, pinFor(ev, pluginPins), ev.args.map { argBind(it, ctx) })
            }
        val guardStep = pp.steps.firstOrNull() as? PlanStep.OptimisticGuard
        val guard =
            guardStep?.let { EmittedGuard(it.currentVersionRead, bindOf(it.baseRowVersion, null, ctx)) }
        val steps = pp.steps.filterNot { it is PlanStep.OptimisticGuard }.map { emitStep(it, ctx) }
        return EmittedProposal(pp.row, reads, guard, steps, funcs)
    }

    /** The deploy-resolved pin for a [FunctionEval] (from `pluginPins`); falls back to the raw constraint. */
    private fun pinFor(
        ev: FunctionEval,
        pluginPins: List<PluginPin>,
    ): PluginPin =
        pluginPins.firstOrNull { it.id == ev.functionId }
            ?: PluginPin(ev.functionId, ev.versionConstraint.trimStart('^', '~'))

    /** An arg to a function-eval — a batch/state value typed from its own source column (never inline SQL). */
    private fun argBind(
        pv: PlanValue,
        ctx: Ctx,
    ): Bind =
        when (pv) {
            is PlanValue.BatchValue -> bindOf(pv, pv.column, ctx)
            is PlanValue.BatchKey -> bindOf(pv, pv.column, ctx)
            else -> bindOf(pv, null, ctx)
        }

    private fun emitRead(
        read: StateRead,
        ctx: Ctx,
    ): EmittedRead =
        when (read) {
            is StateRead.CurrentRowVersion ->
                EmittedRead(
                    read.name,
                    "SELECT ${q(read.versionColumn)} FROM ${q(read.table)} WHERE ${whereClause(read.key, ctx)}",
                    read.key.entries
                        .sortedBy { it.key }
                        .map { bindOf(it.value, it.key, ctx) },
                    ReadKind.ROW,
                )
            is StateRead.ReversalCount ->
                EmittedRead(
                    read.name,
                    "SELECT count(*) FROM ${q(read.table)} WHERE ${q(read.reversalLinkColumn)} = ?",
                    listOf(bindOf(originalKeyValue(read.originalKey, ctx), null, ctx)),
                    ReadKind.COUNT,
                )
        }

    private fun emitStep(
        step: PlanStep,
        ctx: Ctx,
    ): EmittedStep =
        when (step) {
            is PlanStep.InsertRow -> {
                val cols = step.columns.entries.sortedBy { it.key }
                EmittedStep(
                    "INSERT INTO ${q(step.table)} (${cols.joinToString(", ") { q(it.key) }}) " +
                        "VALUES (${cols.joinToString(", ") { "?" }})",
                    cols.map { bindOf(it.value, it.key, ctx) },
                    Effect.INSERTED,
                )
            }
            is PlanStep.UpdateRow -> {
                val set = step.set.entries.sortedBy { it.key }
                val setSql = set.joinToString(", ") { "${q(it.key)} = ?" }
                val setBinds = set.map { bindOf(it.value, it.key, ctx) }
                val bump = step.versionColumn?.let { versionBump(step, it, ctx) }
                EmittedStep(
                    "UPDATE ${q(step.table)} SET $setSql${bump?.let { ", ${it.sql}" } ?: ""} " +
                        "WHERE ${whereClause(step.key, ctx)}",
                    setBinds + (bump?.binds ?: emptyList()) + keyBinds(step.key, ctx),
                    Effect.UPDATED,
                )
            }
            is PlanStep.CloseValidity ->
                EmittedStep(
                    "UPDATE ${q(step.table)} SET ${q(step.validToColumn)} = ? " +
                        "WHERE ${whereClause(step.key, ctx)} AND ${q(step.validToColumn)} IS NULL",
                    listOf(bindOf(step.validTo, step.validToColumn, ctx)) + keyBinds(step.key, ctx),
                    Effect.CLOSED,
                )
            is PlanStep.PhysicalDelete ->
                EmittedStep(
                    "DELETE FROM ${q(step.table)} WHERE ${whereClause(step.key, ctx)}",
                    keyBinds(step.key, ctx),
                    Effect.NONE,
                )
            is PlanStep.ReverseRow -> emitReverse(step, ctx)
            is PlanStep.ReplaceRow -> emitReplace(step, ctx)
            is PlanStep.RejectEnvelope ->
                // No surgery. The §10 reject envelope (key/code/detail) is assembled door-side from the
                // batch proposal, not from these binds — this no-op statement only marks the reject step.
                EmittedStep(
                    "SELECT 1 WHERE ? IS NOT NULL",
                    listOf(bindOf(step.code, null, ctx)),
                    Effect.NONE,
                )
            is PlanStep.OptimisticGuard ->
                error("guard is lifted to EmittedProposal.guard before step emit")
        }

    /** Ledger reversal: `INSERT … SELECT` the original row with BIGINT measures negated, others copied. */
    private fun emitReverse(
        step: PlanStep.ReverseRow,
        ctx: Ctx,
    ): EmittedStep {
        val binds = mutableListOf<Bind>()
        val exprs =
            ctx.allCols.map { c ->
                when {
                    c == ctx.pkCol -> {
                        binds += bindOf(step.id, ctx.pkCol, ctx)
                        "?"
                    }
                    c.equals(step.reversalLinkColumn, ignoreCase = true) -> {
                        binds += bindOf(step.original, c, ctx)
                        "?"
                    }
                    ctx.types[c.lowercase()] == SqlType.BIGINT -> "-${q(c)}"
                    else -> q(c)
                }
            }
        binds += bindOf(step.original, ctx.pkCol, ctx) // WHERE pk = original
        return EmittedStep(
            "INSERT INTO ${q(step.table)} (${ctx.allCols.joinToString(", ") { q(it) }}) " +
                "SELECT ${exprs.joinToString(", ")} FROM ${q(step.table)} " +
                "WHERE ${q(ctx.pkCol ?: "")} = ?",
            binds,
            Effect.REVERSED,
        )
    }

    private data class VersionBumpSql(
        val sql: String,
        val binds: List<Bind>,
    )

    /**
     * ⚑EN-5 — the §10 optimistic version bump: `"<versionColumn>" = md5(<canonical row serialization>)`.
     * Hashes every non-key, non-version column in md order; a changed column contributes its new batch
     * value (a `?` bind), an unchanged column its current value (a column ref). Columns are `::text`-cast
     * and NULL-guarded with `chr(30)`, joined with `chr(31)` — control chars keep the plan quote-free (so
     * the placeholders-only hygiene holds) and the serialization canonical. A same-content rewrite
     * re-derives the same hash (ABA-tolerant content-drift token, matching the reference program).
     */
    private fun versionBump(
        step: PlanStep.UpdateRow,
        versionColumn: String,
        ctx: Ctx,
    ): VersionBumpSql {
        val keyCols = step.key.keys.mapTo(mutableSetOf()) { it.lowercase() }
        val hashed =
            ctx.allCols.filter { c ->
                c.lowercase() !in keyCols && !c.equals(versionColumn, ignoreCase = true)
            }
        val binds = mutableListOf<Bind>()
        val terms =
            hashed.map { c ->
                val setEntry = step.set.entries.firstOrNull { it.key.equals(c, ignoreCase = true) }
                if (setEntry != null) {
                    binds += bindOf(setEntry.value, c, ctx)
                    "coalesce(?::text, chr(30))"
                } else {
                    "coalesce(${q(c)}::text, chr(30))"
                }
            }
        return VersionBumpSql("${q(versionColumn)} = md5(${terms.joinToString(" || chr(31) || ")})", binds)
    }

    /**
     * Ledger replacement: a corrected row `INSERT … SELECT`-ed from the original so untouched columns
     * carry forward (e.g. `txn_ref`) — rep-id (null link) + the batch value overlays. Fresh id + null
     * link keep the F3 reversal count collision-free; the corrected measures come from the batch.
     */
    private fun emitReplace(
        step: PlanStep.ReplaceRow,
        ctx: Ctx,
    ): EmittedStep {
        val binds = mutableListOf<Bind>()
        val exprs =
            ctx.allCols.map { c ->
                // Overlay match is case-insensitive (consistent with versionBump) — the lowering already
                // normalizes the batch keys to md case, so this is defence-in-depth, never wrong-column.
                val overlay = step.columns.entries.firstOrNull { it.key.equals(c, ignoreCase = true) }
                when {
                    c == ctx.pkCol -> {
                        binds += bindOf(step.id, ctx.pkCol, ctx)
                        "?"
                    }
                    c.equals(step.reversalLinkColumn, ignoreCase = true) -> {
                        binds += Bind.Value(null, ctx.types[c.lowercase()] ?: SqlType.TEXT)
                        "?"
                    }
                    overlay != null -> {
                        binds += bindOf(overlay.value, c, ctx)
                        "?"
                    }
                    else -> q(c) // carry forward the original value untouched
                }
            }
        binds += bindOf(step.original, ctx.pkCol, ctx) // WHERE pk = original
        return EmittedStep(
            "INSERT INTO ${q(step.table)} (${ctx.allCols.joinToString(", ") { q(it) }}) " +
                "SELECT ${exprs.joinToString(", ")} FROM ${q(step.table)} " +
                "WHERE ${q(ctx.pkCol ?: "")} = ?",
            binds,
            Effect.INSERTED,
        )
    }

    // ---- binds ----

    private data class Ctx(
        val row: Int,
        val proposal: RowBatch.Proposal?,
        val types: Map<String, SqlType>,
        val allCols: List<String>,
        val pkCol: String?,
        val diags: MutableList<TtrpDiagnostic>,
        val loc: SourceLocation,
    )

    private fun bindOf(
        pv: PlanValue,
        targetCol: String?,
        ctx: Ctx,
    ): Bind {
        val type = targetCol?.let { ctx.types[it.lowercase()] } ?: SqlType.TEXT
        return when (pv) {
            is PlanValue.BatchValue -> valueBind(ctx.proposal?.values?.get(pv.column), type, pv.column, "values", ctx)
            is PlanValue.BatchKey -> valueBind(ctx.proposal?.key?.get(pv.column), type, pv.column, "key", ctx)
            is PlanValue.Const -> constBind(pv.text, type, targetCol ?: "const", ctx)
            is PlanValue.StateValue -> Bind.StateRef(pv.read, type)
            is PlanValue.DerivedId -> Bind.DerivedIdRef(pv.role, bindOf(pv.base, ctx.pkCol, ctx), pv.counterRead)
            is PlanValue.CallFnValue -> Bind.FuncRef(pv.read, type)
        }
    }

    /** Build a typed value bind, validating md-type coercibility at manifest build (F4 / TTRP-EN-001). */
    private fun valueBind(
        scalar: org.tatrman.ttrp.entry.BatchScalar?,
        type: SqlType,
        col: String,
        mapName: String,
        ctx: Ctx,
    ): Bind {
        if (scalar == null || scalar.kind == org.tatrman.ttrp.entry.BatchScalar.Kind.NULL) {
            return Bind.Value(null, type)
        }
        val raw = scalar.raw?.toString()
        val ok =
            when (type) {
                SqlType.TEXT -> true
                SqlType.BIGINT ->
                    scalar.kind == org.tatrman.ttrp.entry.BatchScalar.Kind.NUMBER || raw?.toLongOrNull() != null
                SqlType.DATE -> raw != null && DATE_RE.matches(raw)
            }
        if (!ok) {
            ctx.diags +=
                TtrpDiagnostic(
                    TtrpDiagnosticId.EN_001,
                    Severity.ERROR,
                    "value for `$col` in `$mapName` is not bindable as $type (row ${ctx.row})",
                    ctx.loc,
                )
        }
        return Bind.Value(raw, type)
    }

    /** A compile-time literal (e.g. `effectiveDate`) bound to [targetCol] — type-validated like a batch value. */
    private fun constBind(
        text: String,
        type: SqlType,
        targetCol: String,
        ctx: Ctx,
    ): Bind {
        val ok =
            when (type) {
                SqlType.TEXT -> true
                SqlType.BIGINT -> text.toLongOrNull() != null
                SqlType.DATE -> DATE_RE.matches(text)
            }
        if (!ok) {
            ctx.diags +=
                TtrpDiagnostic(
                    TtrpDiagnosticId.EN_001,
                    Severity.ERROR,
                    "value for `$targetCol` is not bindable as $type (row ${ctx.row})",
                    ctx.loc,
                )
        }
        return Bind.Value(text, type)
    }

    private fun keyBinds(
        key: Map<String, PlanValue>,
        ctx: Ctx,
    ): List<Bind> = key.entries.sortedBy { it.key }.map { bindOf(it.value, it.key, ctx) }

    private fun whereClause(
        key: Map<String, PlanValue>,
        ctx: Ctx,
    ): String = key.entries.sortedBy { it.key }.joinToString(" AND ") { "${q(it.key)} = ?" }

    private fun originalKeyValue(
        key: Map<String, PlanValue>,
        ctx: Ctx,
    ): PlanValue = ctx.pkCol?.let { key[it] } ?: key.values.first()

    // ---- helpers ----

    /** Exact-case identifier quoting with doubled-quote escaping (F4 — never trust engine folding). */
    private fun q(id: String): String = "\"" + id.replace("\"", "\"\"") + "\""

    private fun colName(qname: String): String = qname.substringAfterLast('.')

    private fun sqlTypeOf(dataType: String): SqlType =
        when (dataType.lowercase().substringBefore('(').trim()) {
            "int", "integer", "bigint", "smallint", "tinyint", "number", "numeric",
            "decimal", "float", "double", "real", "money",
            -> SqlType.BIGINT
            "date", "timestamp", "datetime", "timestamptz", "time" -> SqlType.DATE
            else -> SqlType.TEXT // text/varchar/char/string/bool → TEXT (the §5.1 wave set is TEXT/BIGINT/DATE)
        }
}
