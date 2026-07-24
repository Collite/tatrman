// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp

import org.tatrman.ttr.semantics.md.MdCubelet
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.ChainStmt
import org.tatrman.ttrp.ast.ConfigBlock
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.CubeletStmt
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
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.MdContext
import org.tatrman.ttrp.expr.MdResolution
import org.tatrman.ttrp.materialize.MaterializeSpec
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
    private val cubeletChecker = CubeletStatementChecker(typechecker)

    data class TtrpCheckResult(
        val document: TtrpDocument,
        val diagnostics: List<TtrpDiagnostic>,
        val source: String,
        /** MD dot-paths that resolved in expression positions (S3-A): canonical form + shape + explanation. */
        val mdResolutions: List<MdResolution> = emptyList(),
        /** Fresh cubelets materialized by `C := e` (R27): the inferred defs to emit as generated `.ttrm` (S5C-B.2). */
        val materializations: List<MaterializeSpec> = emptyList(),
    )

    /** Diagnostics + resolved MD dot-paths from an expression-check pass (the Stage 1.3 seam return). */
    data class ExprCheckResult(
        val diagnostics: List<TtrpDiagnostic>,
        val mdResolutions: List<MdResolution> = emptyList(),
        /** Fresh cubelets materialized by `C := e` (R27) — the compile-side generated model (S5C-B.2). */
        val materializations: List<MaterializeSpec> = emptyList(),
    )

    fun check(
        source: String,
        schemas: SchemaSource = EmptySchemaSource,
        fileName: String = "<memory>",
        md: MdContext? = null,
    ): TtrpCheckResult {
        val parsed = TtrpParser.parseString(source, fileName)
        val variables = collectVariableNames(parsed.document.statements)
        val out = mutableListOf<TtrpDiagnostic>()
        val mdOut = mutableListOf<MdResolution>()
        val matOut = mutableListOf<MaterializeSpec>()
        out += parsed.diagnostics
        checkStatements(parsed.document.statements, variables, schemas, md, mutableMapOf(), out, mdOut, matOut)
        return TtrpCheckResult(parsed.document, out, source, mdOut, matOut)
    }

    /**
     * Runs the Stage 1.2 expression checks (FN/AGG/TYP/EXP) over an already-parsed
     * [document] using resolved [schemas], plus MD dot-path resolution (S3-A) when [md]
     * carries a model. This is the seam Stage 1.3's orchestrator uses — the parse has
     * already happened, and [schemas] now carries real column lists (a
     * `ResolvedSchemaSource`), not hand-fed maps.
     */
    fun checkExpressions(
        document: TtrpDocument,
        schemas: SchemaSource,
        md: MdContext? = null,
    ): ExprCheckResult {
        val variables = collectVariableNames(document.statements)
        val out = mutableListOf<TtrpDiagnostic>()
        val mdOut = mutableListOf<MdResolution>()
        val matOut = mutableListOf<MaterializeSpec>()
        checkStatements(document.statements, variables, schemas, md, mutableMapOf(), out, mdOut, matOut)
        return ExprCheckResult(out, mdOut, matOut)
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
        md: MdContext?,
        // R25: virtual cubelets bound by `C = e`, accumulated in statement order (SSA) — a binding is
        // visible only to statements after it. Shared across nested containers (like the variable set).
        sessionCubelets: MutableMap<String, MdCubelet>,
        out: MutableList<TtrpDiagnostic>,
        mdOut: MutableList<MdResolution>,
        matOut: MutableList<MaterializeSpec>,
    ) {
        for (stmt in statements) {
            when (stmt) {
                is Assignment ->
                    stmt.chain.elements.filterIsInstance<OpCall>().forEach {
                        checkOpCall(it, variables, schemas, md, sessionCubelets, out, mdOut)
                    }
                is ChainStmt ->
                    stmt.chain.elements.filterIsInstance<OpCall>().forEach {
                        checkOpCall(it, variables, schemas, md, sessionCubelets, out, mdOut)
                    }
                is ContainerDecl ->
                    (stmt.body as? FlowBody)?.let {
                        checkStatements(it.statements, variables, schemas, md, sessionCubelets, out, mdOut, matOut)
                    }
                is CubeletStmt -> cubeletChecker.check(stmt, md, variables, sessionCubelets, out, mdOut, matOut)
                else -> Unit
            }
        }
    }

    private fun checkOpCall(
        op: OpCall,
        variables: Set<String>,
        schemas: SchemaSource,
        md: MdContext?,
        sessionCubelets: Map<String, MdCubelet>,
        out: MutableList<TtrpDiagnostic>,
        mdOut: MutableList<MdResolution>,
    ) {
        val schema = inputSchemaFor(op, schemas)
        // Ops whose non-source arg is a boolean PREDICATE (must type `bool`). This is a
        // conservative subset of the op roster; Stage 2.1's node set (T10) supersedes it
        // with the authoritative per-port predicate metadata (review-001 1.2-A).
        val predicateExpected = op.name in PREDICATE_OPS
        // Compound (non-bare) args are predicates/formulas: aggregates are NOT legal here.
        for (arg in op.args) {
            val value = arg.value
            if (value !is ExprArg) continue
            // Bare refs/literals AND nested op-calls (`filter(load(x), …)`) are INPUTS,
            // not predicate/formula expressions — the graph resolves them, so they are
            // not scalar-typechecked here (review-001 1.2-C).
            if (isBareSource(value.expr) || isNestedOpCall(value.expr)) continue
            val result =
                typechecker.check(
                    value.expr,
                    inputSchema = schema,
                    aggregatesAllowed = false,
                    variableNames = variables,
                    predicateExpected = predicateExpected,
                    md = md,
                    sessionCubelets = sessionCubelets,
                )
            out += result.diagnostics
            mdOut += result.mdResolutions
        }
        // Config-block formulas: aggregates are legal ONLY for the aggregating ops
        // (`aggregate { total = sum(amount) }` / `pivot`), not e.g. `sort { … }`
        // (review-001 1.2-F). Stage 2.1's node roster supersedes this list.
        op.config?.let { checkConfig(op, it, schema, variables, md, sessionCubelets, out, mdOut) }
    }

    private fun checkConfig(
        op: OpCall,
        config: ConfigBlock,
        schema: Map<String, List<Column>>?,
        variables: Set<String>,
        md: MdContext?,
        sessionCubelets: Map<String, MdCubelet>,
        out: MutableList<TtrpDiagnostic>,
        mdOut: MutableList<MdResolution>,
    ) {
        val aggregatesAllowed = op.name in AGG_CONFIG_OPS
        for (entry in config.entries) {
            if (entry is AssignEntry) {
                val result =
                    typechecker.check(
                        entry.value,
                        inputSchema = schema,
                        aggregatesAllowed = aggregatesAllowed,
                        variableNames = variables,
                        md = md,
                        sessionCubelets = sessionCubelets,
                    )
                out += result.diagnostics
                mdOut += result.mdResolutions
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

    /** A nested op-call used as an input (`load(x)`), not a scalar expression (review-001 1.2-C). */
    private fun isNestedOpCall(expr: Expression): Boolean = expr is FunctionCall && expr.function.name in DATA_OPS

    /**
     * The data-op roster (T10 node set). A top-level arg spelled as one of these is a
     * nested INPUT op-call, not a scalar function. Stage 2.1's node set is the
     * authoritative source; this mirror exists only for the front-half's arg
     * classification (review-001 1.2-A/1.2-C/1.2-F).
     */
    private val DATA_OPS =
        setOf(
            "load",
            "store",
            "transfer",
            "display",
            "join",
            "aggregate",
            "union",
            "branch",
            "filter",
            "sort",
            "distinct",
            "limit",
            "sample",
            "head",
            "tail",
            "project",
            "select",
            "calc",
            "switch",
            "values",
            "intersect",
            "except",
            "pivot",
        )

    /** Ops whose non-source arg is a boolean predicate (must type `bool`). */
    private val PREDICATE_OPS = setOf("filter", "branch", "join")

    /** Ops whose config-block formulas may contain aggregate calls. */
    private val AGG_CONFIG_OPS = setOf("aggregate", "pivot")
}
