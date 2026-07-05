package org.tatrman.ttrp.parser

import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.BinaryExpr
import org.tatrman.ttrp.ast.CallExpr
import org.tatrman.ttrp.ast.Chain
import org.tatrman.ttrp.ast.ChainStmt
import org.tatrman.ttrp.ast.ConfigBlock
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.ControlBlock
import org.tatrman.ttrp.ast.ControlDep
import org.tatrman.ttrp.ast.ControlKind
import org.tatrman.ttrp.ast.Expr
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.ast.IsNullExpr
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.ParenExpr
import org.tatrman.ttrp.ast.ProgramHeader
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.ast.UnaryExpr
import org.tatrman.ttrp.ast.AssignEntry
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * The Stage 1.1 named-reject pass (contracts §8). Runs over the built AST after the
 * ANTLR tree walk. Each rejected form carries its catalogue-supplied suggested
 * alternative. Stages 1.2/1.3 add their own passes; this one owns EQ/CTL/PRS/FRG.
 */
internal object TtrpChecks {
    private val RESERVED_PORTS = setOf("in", "out", "err", "rejects", "true", "false", "else")
    private val MULTI_IN_OPS = setOf("join", "intersect", "except")

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
        // `==` anywhere in an argument expression (S9 → EQ-001).
        for (arg in op.args) {
            if (arg.value is ExprArg) checkExpr(arg.value.expr, out)
        }
        op.config?.let { checkConfig(it, out) }
    }

    private fun checkConfig(
        config: ConfigBlock,
        out: MutableList<TtrpDiagnostic>,
    ) {
        for (entry in config.entries) if (entry is AssignEntry) checkExpr(entry.value, out)
    }

    private fun checkExpr(
        expr: Expr,
        out: MutableList<TtrpDiagnostic>,
    ) {
        when (expr) {
            is BinaryExpr -> {
                if (expr.op == "==") {
                    out += diag(TtrpDiagnosticId.EQ_001, "`==` is not the equality operator", expr.location)
                }
                checkExpr(expr.left, out)
                checkExpr(expr.right, out)
            }
            is UnaryExpr -> checkExpr(expr.operand, out)
            is IsNullExpr -> checkExpr(expr.operand, out)
            is ParenExpr -> checkExpr(expr.inner, out)
            is CallExpr -> expr.args.forEach { checkExpr(it, out) }
            else -> Unit
        }
    }

    private fun diag(
        id: TtrpDiagnosticId,
        message: String,
        location: org.tatrman.ttrp.ast.SourceLocation,
    ) = TtrpDiagnostic(id = id, severity = Severity.ERROR, message = message, location = location)
}
