// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rewrite

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.CaseWhen
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.InList
import org.tatrman.ttrp.expr.IsNull
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.expr.catalog.ValidityCatalog
import org.tatrman.ttrp.graph.model.Aggregation
import org.tatrman.ttrp.graph.model.Branch
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.Edge
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * RJ-P1 rejects elaboration (contracts §5, R-E1-α / R-B3-β). Two rules run in the
 * [Stratum.REJECT_ELABORATION] stratum (declared BEFORE sugar — the single-pass engine cannot
 * re-lower the name-bearing guard/reject Calcs this stratum synthesizes if it ran after sugar;
 * a conscious amendment to contracts §5's "after sugar", recorded in the design log). Rule order
 * within the stratum: join-ON decomposition first, then guard-and-branch.
 *
 * All the reject-capability knowledge (what casts/ops can reject, and their row codes) comes from
 * the RJ-P0 [ValidityCatalog] — the single source of truth shared with the measure and the
 * post-stratum diagnostics.
 */
object RejectElaboration {
    private const val PREFIX = "_ttrp_"

    // ---- reject-capability (shared with RewriteEngine.measure and the diagnostics pass) ----

    /** One reject-capable sub-expression found on a node: its validity call, row code, and stable id. */
    private data class RejectSite(
        val validityCall: Expression,
        val code: String,
        val exprId: String,
    )

    /** The validity type-pair suffix for a cast target (contracts §2 spellings). */
    private fun castSuffix(t: TtrpType): String =
        when (t.canonical) {
            "integer" -> "int64"
            "decimal" -> "decimal18_4"
            "float", "double", "number" -> "float64"
            "date" -> "date"
            "timestamp", "datetime" -> "timestamp"
            "bool" -> "bool"
            else -> t.canonical
        }

    /** The reject site for [e] if it is itself a reject-capable expr (cast / op.div / datetime parse), else null. */
    private fun siteOf(
        e: Expression,
        exprId: String,
    ): RejectSite? =
        when {
            e is Cast -> {
                val pair = "text->${castSuffix(e.target)}"
                ValidityCatalog.rejectCapability("cast", pair)?.let {
                    RejectSite(
                        internalCall(
                            "internal.is_castable",
                            listOf(e.expr, strLit(castSuffix(e.target), e.location)),
                            e.location,
                        ),
                        it.code,
                        exprId,
                    )
                }
            }
            e is FunctionCall && e.function.value == "op.div" ->
                ValidityCatalog.rejectCapability("op.div", "numeric,numeric->numeric")?.let {
                    RejectSite(internalCall("internal.is_nonzero", listOf(e.args[1]), e.location), it.code, exprId)
                }
            e is FunctionCall && e.function.value in setOf("fn.to_date", "fn.to_timestamp") ->
                ValidityCatalog.all.firstOrNull { it.function == e.function.value }?.let { spec ->
                    val fmt = e.args.getOrNull(1) ?: strLit("", e.location)
                    RejectSite(
                        internalCall("internal.is_parseable_dt", listOf(e.args[0], fmt), e.location),
                        spec.code,
                        exprId,
                    )
                }
            else -> null
        }

    /** Every reject site in [e], in pre-order (document order), tagged with [exprId]. */
    private fun sitesIn(
        e: Expression,
        exprId: String,
    ): List<RejectSite> {
        val here = siteOf(e, exprId)?.let { listOf(it) } ?: emptyList()
        val nested =
            when (e) {
                is Cast -> sitesIn(e.expr, exprId)
                is FunctionCall -> e.args.flatMap { sitesIn(it, exprId) }
                is AggregateCall -> e.args.flatMap { sitesIn(it, exprId) }
                is CaseWhen ->
                    e.branches.flatMap { sitesIn(it.first, exprId) + sitesIn(it.second, exprId) } +
                        (e.elseExpr?.let { sitesIn(it, exprId) } ?: emptyList())
                is InList -> sitesIn(e.expr, exprId) + e.items.flatMap { sitesIn(it, exprId) }
                is IsNull -> sitesIn(e.expr, exprId)
                is ColumnRef, is Literal -> emptyList()
            }
        // `here` first: a cast's own reject precedes any reject nested inside its operand (first-error).
        return here + nested
    }

    /** The ordered reject sites of a Calc's assignments (document order, each tagged with its column name). */
    private fun rejectSites(node: Node): List<RejectSite> =
        when (node) {
            is Calc -> node.assignments.flatMap { sitesIn(it.value, it.name) }
            else -> emptyList()
        }

    /** True if [node] carries ≥1 reject-capable expression (Calc assignments or a Join ON). */
    fun isRejectCapable(node: Node): Boolean =
        when (node) {
            is Calc -> node.assignments.any { sitesIn(it.value, it.name).isNotEmpty() }
            is Join -> node.on?.let { sitesIn(it, "on").isNotEmpty() } ?: false
            else -> false
        }

    /** True if [node]'s `rejects` out-port is consumed — directly, or via a consumed container port. */
    fun hasWiredRejects(
        g: TtrpGraph,
        nodeId: String,
    ): Boolean {
        if (g.edges.any { it.from == PortRef(nodeId, PortNames.REJECTS) }) return true
        for (c in g.containers.values) {
            for ((port, ref) in c.portMapping) {
                if (ref == PortRef(nodeId, PortNames.REJECTS) && g.edges.any { it.from == PortRef(c.id, port) }) {
                    return true
                }
            }
        }
        return false
    }

    /** The count of un-elaborated wired reject sites — the leading, strictly-decreasing measure term. */
    fun pendingSites(g: TtrpGraph): Int =
        g.nodes.values.count { n ->
            n.id !in g.synthProvenance && isRejectCapable(n) && hasWiredRejects(g, n.id) && !isBothSidesJoin(g, n)
        }

    /** A Join whose ON reject spans BOTH inputs cannot be decomposed — it is the RJ-105 fallback, not "pending". */
    private fun isBothSidesJoin(
        g: TtrpGraph,
        n: Node,
    ): Boolean = n is Join && n.on != null && bothSidesSite(n.on!!) != null

    // ---- the rules ----

    /** guard-and-branch elaboration (single-in reject-capable node with a wired rejects port). */
    val GuardAndBranch: RewriteRule =
        object : RewriteRule {
            override val name = "reject-guard-and-branch"
            override val stratum = Stratum.REJECT_ELABORATION

            override fun apply(
                node: Node,
                graph: TtrpGraph,
                ctx: RewriteContext,
            ): RewriteResult {
                if (node !is Calc) return RewriteResult.Unchanged
                if (node.id in graph.synthProvenance) return RewriteResult.Unchanged
                if (!hasWiredRejects(graph, node.id)) return RewriteResult.Unchanged
                val sites = rejectSites(node)
                if (sites.isEmpty()) return RewriteResult.Unchanged
                return elaborate(node, sites, graph)
            }
        }

    /** join-ON decomposition: pull a single-side reject-capable subexpr in the ON onto a per-side calc. */
    val JoinOnDecompose: RewriteRule =
        object : RewriteRule {
            override val name = "reject-join-on-decompose"
            override val stratum = Stratum.REJECT_ELABORATION

            override fun apply(
                node: Node,
                graph: TtrpGraph,
                ctx: RewriteContext,
            ): RewriteResult {
                if (node !is Join || node.on == null) return RewriteResult.Unchanged
                if (!hasWiredRejects(graph, node.id)) return RewriteResult.Unchanged
                // both-sides ON ⇒ not decomposable; the RJ-105 fallback is emitted by the post-pass.
                val single = singleSideSite(node.on!!) ?: return RewriteResult.Unchanged
                return decompose(node, single, graph)
            }
        }

    val RULES: List<RewriteRule> = listOf(JoinOnDecompose, GuardAndBranch)

    private fun elaborate(
        node: Calc,
        sites: List<RejectSite>,
        g: TtrpGraph,
    ): RewriteResult.Replaced {
        val loc = node.location
        val ssa = node.label.substringBefore('#')
        val guardId = "${node.id}_guard"
        val branchId = "${node.id}_branch"
        val rejectId = "${node.id}_reject"

        // guard calc: one _ttrp_v<n> per reject site (document order), computed by the internal fn.
        val validity = sites.mapIndexed { i, s -> Aggregation("${PREFIX}v${i + 1}", s.validityCall) }
        val guard = Calc(guardId, "${ssa}_guard", loc, assignments = validity)

        // branch predicate: conjunction of every validity flag.
        val predicate =
            validity
                .map { col(it.name, loc) }
                .reduce { a, b -> FunctionCall(CatalogId.AND, listOf(a, b), loc) }
        val branch = Branch(branchId, "${ssa}_branch", loc, predicate = predicate)

        // reject projection: first-error CASE ladders for the code and the failing-expr id.
        val codeLadder = ladder(validity.map { it.name }, sites.map { it.code }, loc)
        val exprLadder = ladder(validity.map { it.name }, sites.map { it.exprId }, loc)
        val reject =
            Calc(
                rejectId,
                "${ssa}_reject",
                loc,
                assignments =
                    listOf(
                        Aggregation("${PREFIX}reject_code", codeLadder),
                        Aggregation("${PREFIX}reject_expr", exprLadder),
                    ),
            )

        val containerId = GraphOps.containerIdOf(g, node.id)
        var ng = g
        ng = GraphOps.addNode(ng, guard, containerId)
        ng = GraphOps.addNode(ng, branch, containerId)
        ng = GraphOps.addNode(ng, reject, containerId)

        // rewire the input: X -> node.in  becomes  X -> guard.in -> branch -> {node.in | reject.in}.
        val inEdge = ng.edges.firstOrNull { it.to == PortRef(node.id, PortNames.IN) }
        ng = ng.copy(edges = ng.edges.filterNot { it.to == PortRef(node.id, PortNames.IN) })
        val newEdges = mutableListOf<Edge>()
        if (inEdge != null) newEdges += Edge(inEdge.from, PortRef(guardId, PortNames.IN), EdgeKind.DATA, loc)
        newEdges += Edge(PortRef(guardId, PortNames.OUT), PortRef(branchId, PortNames.IN), EdgeKind.DATA, loc)
        newEdges += Edge(PortRef(branchId, PortNames.TRUE), PortRef(node.id, PortNames.IN), EdgeKind.DATA, loc)
        newEdges += Edge(PortRef(branchId, PortNames.FALSE), PortRef(rejectId, PortNames.IN), EdgeKind.DATA, loc)
        ng = GraphOps.addEdges(ng, newEdges)

        // rewire the rejects consumer from node.rejects onto reject.out.
        ng = rewireRejects(ng, node.id, PortRef(rejectId, PortNames.OUT))

        // provenance: every synthesized node points back to the authored node.
        ng =
            ng.copy(
                synthProvenance =
                    ng.synthProvenance + mapOf(guardId to node.id, branchId to node.id, rejectId to node.id),
            )

        return RewriteResult.Replaced(
            ng,
            AppliedRewrite(
                "reject-guard-and-branch",
                Stratum.REJECT_ELABORATION,
                node.label,
                "${ssa}_guard/branch/reject",
                null,
                loc,
                "wired rejects → guard-and-branch elaboration (${sites.size} site(s))",
            ),
        )
    }

    private fun decompose(
        join: Join,
        site: SingleSide,
        g: TtrpGraph,
    ): RewriteResult.Replaced {
        val loc = join.location
        val ssa = join.label.substringBefore('#')
        val preId = "${join.id}_${site.side.first()}_pre" // e.g. j_l_pre
        val colName = "${PREFIX}on${site.side.first()}"
        // The pulled subexpr, with its own side qualifier stripped (columns are unqualified inside the side calc).
        val preCalc =
            Calc(
                preId,
                "${ssa}_${site.side.first()}_pre",
                loc,
                assignments = listOf(Aggregation(colName, stripPort(site.expr))),
            )

        val sidePort = if (site.side == PortNames.LEFT) PortNames.LEFT else PortNames.RIGHT
        val containerId = GraphOps.containerIdOf(g, join.id)
        var ng = GraphOps.addNode(g, preCalc, containerId)

        // splice the pre-calc between the side input and the join.
        val sideEdge = ng.edges.firstOrNull { it.to == PortRef(join.id, sidePort) }
        ng = ng.copy(edges = ng.edges.filterNot { it.to == PortRef(join.id, sidePort) })
        val spliced = mutableListOf<Edge>()
        if (sideEdge != null) spliced += Edge(sideEdge.from, PortRef(preId, PortNames.IN), EdgeKind.DATA, loc)
        spliced += Edge(PortRef(preId, PortNames.OUT), PortRef(join.id, sidePort), EdgeKind.DATA, loc)
        ng = GraphOps.addEdges(ng, spliced)

        // rewrite the ON to reference the computed side column instead of the pulled subexpr.
        val newOn = replaceExpr(join.on!!, site.expr, ColumnRef(site.side, colName, loc))
        ng = GraphOps.swapNode(ng, join.id, join.copy(on = newOn))
        ng = ng.copy(synthProvenance = ng.synthProvenance + (preId to join.id))

        return RewriteResult.Replaced(
            ng,
            AppliedRewrite(
                "reject-join-on-decompose",
                Stratum.REJECT_ELABORATION,
                join.label,
                "${ssa}_${site.side.first()}_pre",
                null,
                loc,
                "single-side reject-capable ON subexpr pulled to a per-side calc",
            ),
        )
    }

    // ---- post-stratum diagnostics (RJ-101 dead wire, RJ-105 pair-schema fallback) ----

    fun diagnostics(g: TtrpGraph): List<TtrpDiagnostic> {
        val out = mutableListOf<TtrpDiagnostic>()
        for (n in g.nodes.values) {
            if (n.id in g.synthProvenance) continue
            // A Container forwarding an inner producer's rejects (its `rejects` port maps to a
            // synth reject node's out) is legitimately wired — it is not a leaf that "can never
            // reject", so it must not draw the RJ-101 dead-wire warning (RJ-P3 fix).
            if (n is org.tatrman.ttrp.graph.model.Container) continue
            if (!hasWiredRejects(g, n.id)) continue
            when {
                n is Join && n.on != null && bothSidesSite(n.on!!) != null ->
                    out +=
                        TtrpDiagnostic(
                            TtrpDiagnosticId.RJ_105,
                            Severity.WARNING,
                            "both-sides ON expression on `${n.label}` — rejects use the pair schema",
                            n.location,
                        )
                n !is Join && !isRejectCapable(n) ->
                    out +=
                        TtrpDiagnostic(
                            TtrpDiagnosticId.RJ_101,
                            Severity.WARNING,
                            "`${n.label}` can never reject — the `rejects` wire is dead (empty stream)",
                            n.location,
                        )
            }
        }
        return out
    }

    // ---- single/both-side analysis of a join ON ----

    private data class SingleSide(
        val expr: Expression,
        val side: String,
    )

    /** The first single-side reject-capable subexpr in [on] (all its columns on one side), or null. */
    private fun singleSideSite(on: Expression): SingleSide? {
        for (e in rejectCapableSubexprs(on)) {
            val ports = columnPorts(e)
            val sides = ports.filter { it == PortNames.LEFT || it == PortNames.RIGHT }.toSet()
            if (sides.size == 1) return SingleSide(e, sides.first())
        }
        return null
    }

    /** The first both-sides reject-capable subexpr in [on] (columns on both sides), or null. */
    private fun bothSidesSite(on: Expression): Expression? =
        rejectCapableSubexprs(on).firstOrNull { e ->
            columnPorts(e).toSet().containsAll(listOf(PortNames.LEFT, PortNames.RIGHT))
        }

    private fun rejectCapableSubexprs(e: Expression): List<Expression> {
        val here = if (siteOf(e, "on") != null) listOf(e) else emptyList()
        val nested =
            when (e) {
                is Cast -> rejectCapableSubexprs(e.expr)
                is FunctionCall -> e.args.flatMap { rejectCapableSubexprs(it) }
                is AggregateCall -> e.args.flatMap { rejectCapableSubexprs(it) }
                is CaseWhen ->
                    e.branches.flatMap { rejectCapableSubexprs(it.first) + rejectCapableSubexprs(it.second) } +
                        (e.elseExpr?.let { rejectCapableSubexprs(it) } ?: emptyList())
                is InList -> rejectCapableSubexprs(e.expr) + e.items.flatMap { rejectCapableSubexprs(it) }
                is IsNull -> rejectCapableSubexprs(e.expr)
                is ColumnRef, is Literal -> emptyList()
            }
        return here + nested
    }

    private fun columnPorts(e: Expression): List<String> =
        when (e) {
            is ColumnRef -> listOfNotNull(e.port)
            is Cast -> columnPorts(e.expr)
            is FunctionCall -> e.args.flatMap { columnPorts(it) }
            is AggregateCall -> e.args.flatMap { columnPorts(it) }
            is CaseWhen ->
                e.branches.flatMap { columnPorts(it.first) + columnPorts(it.second) } +
                    (e.elseExpr?.let { columnPorts(it) } ?: emptyList())
            is InList -> columnPorts(e.expr) + e.items.flatMap { columnPorts(it) }
            is IsNull -> columnPorts(e.expr)
            is Literal -> emptyList()
        }

    // ---- small expression + graph builders ----

    private fun rewireRejects(
        g: TtrpGraph,
        fromNodeId: String,
        to: PortRef,
    ): TtrpGraph {
        val fromRef = PortRef(fromNodeId, PortNames.REJECTS)
        var ng = g.copy(edges = g.edges.map { if (it.from == fromRef) it.copy(from = to) else it })
        val containers = LinkedHashMap(ng.containers)
        val nodes = LinkedHashMap(ng.nodes)
        for ((cid, c) in ng.containers) {
            if (c.portMapping.values.none { it == fromRef }) continue
            val remapped = c.portMapping.mapValues { if (it.value == fromRef) to else it.value }
            val updated = c.copy(portMapping = remapped)
            containers[cid] = updated
            nodes[cid] = updated
        }
        return ng.copy(nodes = nodes, containers = containers)
    }

    private fun ladder(
        flags: List<String>,
        values: List<String>,
        loc: SourceLocation,
    ): CaseWhen =
        CaseWhen(
            branches =
                flags.zip(values).map { (flag, value) ->
                    FunctionCall(CatalogId.NOT, listOf(col(flag, loc)), loc) as Expression to strLit(value, loc)
                },
            elseExpr = null,
            location = loc,
        )

    private fun internalCall(
        id: String,
        args: List<Expression>,
        loc: SourceLocation,
    ): Expression = FunctionCall(CatalogId(id), args, loc)

    private fun col(
        name: String,
        loc: SourceLocation,
    ): Expression = ColumnRef(null, name, loc)

    private fun strLit(
        s: String,
        loc: SourceLocation,
    ): Expression = Literal(LiteralValue.Str(s), loc)

    /** Strip left/right port qualifiers so a pulled subexpr reads its columns bare inside a side calc. */
    private fun stripPort(e: Expression): Expression =
        when (e) {
            is ColumnRef -> e.copy(port = null)
            is Cast -> e.copy(expr = stripPort(e.expr))
            is FunctionCall -> e.copy(args = e.args.map { stripPort(it) })
            is AggregateCall -> e.copy(args = e.args.map { stripPort(it) })
            is CaseWhen ->
                e.copy(
                    branches = e.branches.map { stripPort(it.first) to stripPort(it.second) },
                    elseExpr = e.elseExpr?.let { stripPort(it) },
                )
            is InList -> e.copy(expr = stripPort(e.expr), items = e.items.map { stripPort(it) })
            is IsNull -> e.copy(expr = stripPort(e.expr))
            is Literal -> e
        }

    /** Replace every occurrence of [target] within [e] with [replacement] (structural equality). */
    private fun replaceExpr(
        e: Expression,
        target: Expression,
        replacement: Expression,
    ): Expression {
        if (e == target) return replacement
        return when (e) {
            is Cast -> e.copy(expr = replaceExpr(e.expr, target, replacement))
            is FunctionCall -> e.copy(args = e.args.map { replaceExpr(it, target, replacement) })
            is AggregateCall -> e.copy(args = e.args.map { replaceExpr(it, target, replacement) })
            is CaseWhen ->
                e.copy(
                    branches =
                        e.branches.map {
                            replaceExpr(it.first, target, replacement) to
                                replaceExpr(it.second, target, replacement)
                        },
                    elseExpr = e.elseExpr?.let { replaceExpr(it, target, replacement) },
                )
            is InList ->
                e.copy(
                    expr = replaceExpr(e.expr, target, replacement),
                    items =
                        e.items.map {
                            replaceExpr(it, target, replacement)
                        },
                )
            is IsNull -> e.copy(expr = replaceExpr(e.expr, target, replacement))
            is ColumnRef, is Literal -> e
        }
    }
}
