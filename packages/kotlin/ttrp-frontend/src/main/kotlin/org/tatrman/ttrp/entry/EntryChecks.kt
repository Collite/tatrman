// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.Chain
import org.tatrman.ttrp.ast.ChainStmt
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/*
 * EN-P2 apply-surface checks (contracts §4/§7): the typecheck half of the `entry` stdlib. Each object
 * is a pure function from structured inputs (a resolved target DbTable, a §5 RowBatch, a catalogue
 * verb) to `TTRP-EN-0xx` diagnostics — no lowering, no emit. Messages name the offending column/row at
 * the call site (the RejectPurityCheck house pattern); the enum's static text is the fix hint.
 */

/** Coarse md-type category, for `TTRP-EN-001` value compatibility (the door does full coercion at run). */
private enum class MdCategory { TEXT, NUMBER, BOOL, DATE, OTHER }

private fun categoryOf(dataType: String): MdCategory =
    when (dataType.lowercase().substringBefore('(').trim()) {
        "text", "varchar", "char", "string", "nvarchar", "nchar", "clob" -> MdCategory.TEXT
        "int", "integer", "bigint", "smallint", "tinyint", "number", "numeric",
        "decimal", "float", "double", "real", "money",
        -> MdCategory.NUMBER
        "bool", "boolean", "bit" -> MdCategory.BOOL
        "date", "timestamp", "datetime", "timestamptz", "time" -> MdCategory.DATE
        else -> MdCategory.OTHER
    }

/** True when a batch scalar is assignable to an md column of [category]. NULL is always allowed (§10). */
private fun compatible(
    scalar: BatchScalar,
    category: MdCategory,
): Boolean =
    when (scalar.kind) {
        BatchScalar.Kind.NULL -> true
        BatchScalar.Kind.TEXT ->
            category == MdCategory.TEXT ||
                category == MdCategory.DATE ||
                category == MdCategory.OTHER
        BatchScalar.Kind.NUMBER -> category == MdCategory.NUMBER || category == MdCategory.OTHER
        BatchScalar.Kind.BOOL -> category == MdCategory.BOOL || category == MdCategory.OTHER
    }

/**
 * `TTRP-EN-001` — a §5 [RowBatch] validated against the target's md shape: unknown/typed columns in
 * `values`/`key`, per-op key presence, `op` outside the §5 enum, and (when the verb requires it) a
 * missing `effectiveDate` on a dated change. [effectiveDateRequired] is derived by the resolver from
 * the verb (only `effective-date-change` demands it, contracts §4).
 */
object BatchShapeChecker {
    private val OPS = EntryOp.entries.map { it.wire }.toSet()

    fun check(
        batch: RowBatch,
        table: DbTable,
        effectiveDateRequired: Boolean,
        location: SourceLocation,
    ): List<TtrpDiagnostic> {
        val out = mutableListOf<TtrpDiagnostic>()
        val columns: Map<String, MdCategory> =
            table.columns.associate {
                it.qname.name
                    .substringAfterLast('.')
                    .lowercase() to categoryOf(it.dataType)
            }

        for (p in batch.proposals) {
            val where = "row ${p.row}"
            if (p.op != null && p.op !in OPS) {
                out += diag("`op` is `${p.op}`, not one of ${OPS.joinToString("/")} ($where)", location)
            }
            when (p.op) {
                EntryOp.INSERT.wire ->
                    if (!p.key.isNullOrEmpty()) {
                        out += diag("`insert` must not carry a `key` ($where)", location)
                    }
                EntryOp.UPDATE.wire, EntryOp.DELETE.wire ->
                    if (p.key.isNullOrEmpty()) {
                        out += diag("`${p.op}` requires a non-empty `key` ($where)", location)
                    }
            }
            checkColumnMap(p.values, columns, "values", where, out, location)
            p.key?.let { checkColumnMap(it, columns, "key", where, out, location) }
            if (effectiveDateRequired && p.effectiveDate == null) {
                out += diag("missing `effectiveDate` — an scd2 dated change requires it ($where)", location)
            }
        }
        return out
    }

    private fun checkColumnMap(
        map: Map<String, BatchScalar>,
        columns: Map<String, MdCategory>,
        mapName: String,
        where: String,
        out: MutableList<TtrpDiagnostic>,
        location: SourceLocation,
    ) {
        for ((col, scalar) in map) {
            val category = columns[col.lowercase()]
            if (category == null) {
                out += diag("unknown column `$col` in `$mapName` — not on the target table ($where)", location)
            } else if (!compatible(scalar, category)) {
                out += diag("value for `$col` in `$mapName` is not compatible with its md type ($where)", location)
            }
        }
    }

    private fun diag(
        message: String,
        location: SourceLocation,
    ) = TtrpDiagnostic(TtrpDiagnosticId.EN_001, Severity.ERROR, message, location)
}

/**
 * `TTRP-EN-002/003/004` — the verb against the target's declared change-semantics (contracts §4/§9):
 *  - EN-002: the verb requires a semantics the target does not declare (e.g. `effective-date-change`
 *    on a non-scd2 target).
 *  - EN-003: the target declares a mode whose required role columns are missing/unresolved.
 *  - EN-004: a hard physical delete is demanded of a ledger/scd2 target — the compiler refuses (route
 *    through `delete-rows`, which soft-closes / reverses; §12 remains the server gate). [physicalDelete]
 *    is set by the resolver when a delete is expressed as a raw physical delete rather than `delete-rows`.
 */
object VerbDeclarationChecker {
    fun check(
        verb: EntryVerbCatalog.VerbSig,
        table: DbTable,
        physicalDelete: Boolean,
        location: SourceLocation,
    ): List<TtrpDiagnostic> {
        val out = mutableListOf<TtrpDiagnostic>()
        val mode = table.changeSemantics?.mode
        val roles = table.changeSemantics?.roleColumns ?: emptyMap()
        val columnNames =
            table.columns
                .map {
                    it.qname.name
                        .substringAfterLast('.')
                        .lowercase()
                }.toSet()

        val required = verb.requiresSemantics
        if (required != null && (mode == null || mode !in required)) {
            out +=
                TtrpDiagnostic(
                    TtrpDiagnosticId.EN_002,
                    Severity.ERROR,
                    "verb `${verb.name}` requires a ${required.joinToString("/")} target, " +
                        "but `${table.qname.name}` is `${mode ?: "undeclared"}`",
                    location,
                )
        }

        // EN-003: role completeness for whatever mode the target declares (independent of the verb).
        val missingRoles = missingRolesFor(mode, roles, columnNames)
        for (role in missingRoles) {
            out +=
                TtrpDiagnostic(
                    TtrpDiagnosticId.EN_003,
                    Severity.ERROR,
                    "`$mode` target `${table.qname.name}` is missing the `$role` role column",
                    location,
                )
        }

        if (physicalDelete && (mode == "ledger" || mode == "scd2")) {
            out +=
                TtrpDiagnostic(
                    TtrpDiagnosticId.EN_004,
                    Severity.ERROR,
                    "a hard delete is not emittable for the `$mode` target `${table.qname.name}` — " +
                        "route deletes through `delete-rows`",
                    location,
                )
        }
        return out
    }

    /** Role columns a declared mode requires that are absent from the declaration or not on the table. */
    private fun missingRolesFor(
        mode: String?,
        roles: Map<String, String>,
        columnNames: Set<String>,
    ): List<String> {
        val needed =
            when (mode) {
                "scd2" -> listOf("validFrom", "validTo")
                "ledger" -> listOf("reversalLink")
                else -> emptyList()
            }
        return needed.filter { role ->
            val col = roles[role]
            col == null || col.substringAfterLast('.').lowercase() !in columnNames
        }
    }
}

/**
 * `TTRP-EN-005` (surface half) — an apply program is pure (contracts §6, P-3): no flow constructs
 * (`load`/`store`/`display`) and no foreign call except `call-fn(...)`. The full purity walk (impure
 * expression functions, deep call graphs) deepens in EN-P3; the surface rejection is defined now.
 */
object EntryPuritySurfaceCheck {
    private val FLOW_OPS = setOf("load", "store", "display")

    fun check(document: TtrpDocument): List<TtrpDiagnostic> {
        val out = mutableListOf<TtrpDiagnostic>()
        document.statements.forEach { walk(it, out) }
        return out
    }

    private fun walk(
        stmt: Statement,
        out: MutableList<TtrpDiagnostic>,
    ) {
        when (stmt) {
            is Assignment -> walkChain(stmt.chain, out)
            is ChainStmt -> walkChain(stmt.chain, out)
            is ContainerDecl -> (stmt.body as? FlowBody)?.statements?.forEach { walk(it, out) }
            else -> Unit
        }
    }

    private fun walkChain(
        chain: Chain,
        out: MutableList<TtrpDiagnostic>,
    ) {
        for (elem in chain.elements) {
            if (elem is OpCall && elem.name in FLOW_OPS) {
                out +=
                    TtrpDiagnostic(
                        TtrpDiagnosticId.EN_005,
                        Severity.ERROR,
                        "flow construct `${elem.name}` is not allowed in an apply program (it is pure)",
                        elem.location,
                    )
            }
        }
    }
}
