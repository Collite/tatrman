package org.tatrman.ttrp

import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.ChainStmt
import org.tatrman.ttrp.ast.ConfigBlock
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.DottedRef
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.Statement
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.ast.AssignEntry
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.expr.Column
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.ExpressionTypechecker
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.parser.TtrpParser

/**
 * Resolves the input schema for a dotted reference (a chain source / op input). This
 * is the seam Stage 1.3 replaces with world/model resolution; Stage 1.2 ships only
 * [DeclaredSchemaSource] (hand-fed schemas) and [EmptySchemaSource].
 */
interface SchemaSource {
    fun schemaFor(ref: DottedRef): List<Column>?
}

/** A hand-declared schema map keyed by the dotted source name (`"sales"`, `"erp.accounts"`). */
class DeclaredSchemaSource(
    private val schemas: Map<String, List<Column>>,
) : SchemaSource {
    override fun schemaFor(ref: DottedRef): List<Column>? = schemas[ref.parts.joinToString(".")]
}

/** No declared schemas — every reference is unresolved (typing deferred to Stage 1.3). */
object EmptySchemaSource : SchemaSource {
    override fun schemaFor(ref: DottedRef): List<Column>? = null
}

/**
 * The TTR-P front-half check entry point (T1.2.7). Parses [source] (structural
 * rejects + `TTRP-EQ-001`), then runs the [ExpressionTypechecker] over every
 * predicate/formula position: catalogue resolution (`FN`), aggregate placement
 * (`AGG`), the variable-scope rule (`EXP`), and — where [schemas] supplies an input
 * schema — static typing/coercion (`TYP`) and out-of-scope columns (`EXP`).
 *
 * Stage 1.3 replaces hand-fed [schemas] with resolved ones; the [SchemaSource] seam
 * is deliberately explicit so that swap is local.
 */
object TtrpFrontend {
    private val typechecker = ExpressionTypechecker()

    data class TtrpCheckResult(
        val document: TtrpDocument,
        val diagnostics: List<TtrpDiagnostic>,
        val source: String,
    )

    fun check(
        source: String,
        schemas: SchemaSource = EmptySchemaSource,
        fileName: String = "<memory>",
    ): TtrpCheckResult {
        val parsed = TtrpParser.parseString(source, fileName)
        val variables = collectVariableNames(parsed.document.statements)
        val out = mutableListOf<TtrpDiagnostic>()
        out += parsed.diagnostics
        checkStatements(parsed.document.statements, variables, schemas, out)
        return TtrpCheckResult(parsed.document, out, source)
    }

    private fun collectVariableNames(statements: List<Statement>): Set<String> {
        val names = mutableSetOf<String>()

        fun walk(stmts: List<Statement>) {
            for (s in stmts) {
                when (s) {
                    is Assignment -> names += s.target
                    is ContainerDecl -> (s.body as? FlowBody)?.let { walk(it.statements) }
                    else -> Unit
                }
            }
        }
        walk(statements)
        return names
    }

    private fun checkStatements(
        statements: List<Statement>,
        variables: Set<String>,
        schemas: SchemaSource,
        out: MutableList<TtrpDiagnostic>,
    ) {
        for (stmt in statements) {
            when (stmt) {
                is Assignment ->
                    stmt.chain.elements.filterIsInstance<OpCall>().forEach {
                        checkOpCall(
                            it,
                            variables,
                            schemas,
                            out,
                        )
                    }
                is ChainStmt ->
                    stmt.chain.elements.filterIsInstance<OpCall>().forEach {
                        checkOpCall(
                            it,
                            variables,
                            schemas,
                            out,
                        )
                    }
                is ContainerDecl ->
                    (stmt.body as? FlowBody)?.let {
                        checkStatements(
                            it.statements,
                            variables,
                            schemas,
                            out,
                        )
                    }
                else -> Unit
            }
        }
    }

    private fun checkOpCall(
        op: OpCall,
        variables: Set<String>,
        schemas: SchemaSource,
        out: MutableList<TtrpDiagnostic>,
    ) {
        val schema = inputSchemaFor(op, schemas)
        // Compound (non-bare) args are predicates/formulas: aggregates are NOT legal here.
        for (arg in op.args) {
            val value = arg.value
            if (value is ExprArg && !isBareSource(value.expr)) {
                out +=
                    typechecker
                        .check(
                            value.expr,
                            inputSchema = schema,
                            aggregatesAllowed = false,
                            variableNames = variables,
                        ).diagnostics
            }
        }
        // Config-block formulas (`aggregate { total = sum(amount) }`): aggregates ARE legal.
        op.config?.let { checkConfig(it, schema, variables, out) }
    }

    private fun checkConfig(
        config: ConfigBlock,
        schema: Map<String, List<Column>>?,
        variables: Set<String>,
        out: MutableList<TtrpDiagnostic>,
    ) {
        for (entry in config.entries) {
            if (entry is AssignEntry) {
                out +=
                    typechecker
                        .check(
                            entry.value,
                            inputSchema = schema,
                            aggregatesAllowed = true,
                            variableNames = variables,
                        ).diagnostics
            }
        }
    }

    /**
     * Best-effort input schema for an op from its bare source arg(s) via [schemas]:
     * named `left`/`right` inputs map to those ports; a single positional source maps
     * to the default (`""`) port. Returns null when no source resolves (typing then
     * deferred — Stage 1.3).
     */
    private fun inputSchemaFor(
        op: OpCall,
        schemas: SchemaSource,
    ): Map<String, List<Column>>? {
        val schema = mutableMapOf<String, List<Column>>()
        for ((index, arg) in op.args.withIndex()) {
            val value = arg.value
            if (value !is ExprArg) continue
            val ref = value.expr as? ColumnRef ?: continue
            val cols = schemas.schemaFor(dottedRefOf(ref)) ?: continue
            val port = arg.name ?: if (index == 0) "" else continue
            schema[port] = cols
        }
        return schema.ifEmpty { null }
    }

    private fun dottedRefOf(ref: ColumnRef): DottedRef {
        val parts = (ref.port?.split('.') ?: emptyList()) + ref.column
        return DottedRef(parts = parts, location = ref.location)
    }

    private fun isBareSource(expr: Expression): Boolean = expr is ColumnRef || expr is Literal
}
