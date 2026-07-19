// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.parser

import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.Chain
import org.tatrman.ttrp.ast.ChainStmt
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.ControlBlock
import org.tatrman.ttrp.ast.ControlDep
import org.tatrman.ttrp.ast.ControlKind
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.ProgramHeader
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * The Stage 1.1 STRUCTURAL named-reject pass (contracts §8): PRS/CTL/FRG. Runs in
 * every parse. `TTRP-EQ-001` now fires from [TtrpWalker] during expression folding
 * (S9). The Stage 1.2 EXPRESSION-semantic checks (catalogue FN/AGG, typing TYP,
 * scope EXP) live behind [org.tatrman.ttrp.TtrpFrontend].check, gated on the schema
 * seam — they are not part of the bare parse.
 */
internal object TtrpChecks {
    // S10 reserved port names. NOTE (review-001 1.1-B): only the identifier-lexable
    // members (`in`/`out`/`err`/`rejects`) can reach this walker check as an assignment
    // TARGET → PRS-005. `true`/`false`/`else` are keyword/literal tokens and are NOT in
    // the grammar's `identifier` rule, so `true = …` fails to parse as an assignment and
    // yields a generic syntax error (PRS-001) at parse time instead. Both paths reject
    // the binding; the difference is a conscious grammar-level split (pinned by a spec),
    // not a gap — a distinct `rejects` keyword etc. is a Stage 2.1 port-typing decision.
    private val RESERVED_PORTS = setOf("in", "out", "err", "rejects", "true", "false", "else")
    private val MULTI_IN_OPS = setOf("join", "intersect", "except")

    /** Reserved synthesized-rejects column prefix (contracts §1 / RS-5). */
    private const val TTRP_PREFIX = "_ttrp_"

    fun run(doc: TtrpDocument): List<TtrpDiagnostic> {
        val out = mutableListOf<TtrpDiagnostic>()
        checkStatements(doc.statements, enclosingPorts = emptySet(), out)
        return out
    }

    private fun checkStatements(
        statements: List<Statement>,
        enclosingPorts: Set<String>,
        out: MutableList<TtrpDiagnostic>,
    ) {
        for (stmt in statements) {
            when (stmt) {
                is ProgramHeader ->
                    out += diag(TtrpDiagnosticId.PRS_002, "`program` header is not part of TTR-P", stmt.location)

                is ControlDep -> checkControlDep(stmt, out)

                is ControlBlock -> stmt.deps.forEach { checkControlDep(it, out) }

                is Assignment -> {
                    if (stmt.target in RESERVED_PORTS && stmt.target !in enclosingPorts) {
                        out +=
                            diag(
                                TtrpDiagnosticId.PRS_005,
                                "`${stmt.target}` is a reserved port name and cannot be bound as a variable",
                                stmt.targetLocation,
                            )
                    }
                    checkChain(stmt.chain, out)
                }

                is ChainStmt -> checkChain(stmt.chain, out)

                is ContainerDecl -> {
                    when (val body = stmt.body) {
                        is FragmentBody ->
                            if (body.tag !in setOf("sql", "pandas", "ttrb")) {
                                out +=
                                    diag(
                                        TtrpDiagnosticId.FRG_001,
                                        "unknown fragment dialect `${body.tag}`",
                                        body.location,
                                    )
                            }
                        is FlowBody ->
                            checkStatements(body.statements, stmt.ports.map { it.name }.toSet(), out)
                    }
                }

                else -> Unit
            }
        }
    }

    private fun checkControlDep(
        dep: ControlDep,
        out: MutableList<TtrpDiagnostic>,
    ) {
        if (dep.kind == ControlKind.FF) {
            out +=
                diag(TtrpDiagnosticId.CTL_001, "`finishes with` (FF) is reserved and unavailable in v1", dep.location)
        }
    }

    private fun checkChain(
        chain: Chain,
        out: MutableList<TtrpDiagnostic>,
    ) {
        for (elem in chain.elements) {
            if (elem is OpCall) checkOpCall(elem, out)
        }
    }

    private fun checkOpCall(
        op: OpCall,
        out: MutableList<TtrpDiagnostic>,
    ) {
        // RJ-103 (RS-5): the `_ttrp_` prefix is reserved for synthesized rejects columns; a user
        // column declared (schema literal) or computed (calc/aggregate assignment) with it errors.
        op.config?.entries?.filterIsInstance<org.tatrman.ttrp.ast.AssignEntry>()?.forEach { e ->
            if (e.name.startsWith(TTRP_PREFIX)) {
                out += diag(TtrpDiagnosticId.RJ_103, "`${e.name}` uses the reserved `_ttrp_` prefix", e.location)
            }
        }
        op.args.forEach { a ->
            (a.value as? org.tatrman.ttrp.ast.SchemaLiteralArg)?.columns?.forEach { col ->
                if (col.name.startsWith(TTRP_PREFIX)) {
                    out +=
                        diag(TtrpDiagnosticId.RJ_103, "`${col.name}` uses the reserved `_ttrp_` prefix", col.location)
                }
            }
        }
        // C3-c: multi-input ops take named inputs only (bare positional beyond index 0 → PRS-003).
        if (op.name in MULTI_IN_OPS) {
            op.args.forEachIndexed { i, arg ->
                if (i >= 1 && arg.name == null) {
                    out +=
                        diag(
                            TtrpDiagnosticId.PRS_003,
                            "op `${op.name}` requires named inputs for multiple sources",
                            arg.location,
                        )
                }
            }
        }
        // S11: union takes the list form only — any named input is a reject (PRS-004,
        // one per op-call regardless of how many inputs were named).
        if (op.name == "union") {
            op.args.firstOrNull { it.name != null }?.let {
                out += diag(TtrpDiagnosticId.PRS_004, "`union` uses the list form, not named inputs", it.location)
            }
        }
    }

    private fun diag(
        id: TtrpDiagnosticId,
        message: String,
        location: org.tatrman.ttrp.ast.SourceLocation,
    ) = TtrpDiagnostic(id = id, severity = Severity.ERROR, message = message, location = location)
}
