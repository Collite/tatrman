package org.tatrman.ttrp.graph.rewrite

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Branch
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.Distinct
import org.tatrman.ttrp.graph.model.Edge
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Except
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Intersect
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.TtrpGraph

/** The compiler-shipped rewrite-rule set (T6-d α: rules are compiler knowledge, not manifest data). */
object Rules {
    val ALL: List<RewriteRule> by lazy {
        listOf(
            SelectToProject,
            CalcToProject,
            DistinctToAggregate,
            HavingSplit,
            BranchToFilters,
            RightJoinSwap,
            IntersectToSemiJoin,
            ExceptToAntiJoin,
        )
    }

    private fun native(
        node: Node,
        graph: TtrpGraph,
        ctx: RewriteContext,
    ): Boolean {
        val kind = node::class.simpleName ?: return true
        val eng = ctx.engineOf(node.id, graph) ?: return true // program-level: nothing to lower against
        return eng.manifest.nodes.containsKey(kind)
    }

    // ---- Stratum: SUGAR (engine-independent, in-place swaps) ----

    val SelectToProject =
        rule("select->project", Stratum.SUGAR) { node, g, _ ->
            if (node !is org.tatrman.ttrp.graph.model.Select) {
                null
            } else {
                val p = Project(node.id, node.label, node.location, provenance = node.provenance)
                replaced(
                    GraphOps.swapNode(g, node.id, p),
                    "select->project",
                    Stratum.SUGAR,
                    node,
                    node.location,
                    null,
                    "Select is Project sugar",
                )
            }
        }

    val CalcToProject =
        rule("calc->project", Stratum.SUGAR) { node, g, _ ->
            if (node !is Calc) {
                null
            } else {
                val p =
                    Project(
                        node.id,
                        node.label,
                        node.location,
                        columns = node.assignments.map { it.value },
                        provenance = node.provenance,
                    )
                replaced(
                    GraphOps.swapNode(g, node.id, p),
                    "calc->project",
                    Stratum.SUGAR,
                    node,
                    node.location,
                    null,
                    "Calc is Project sugar",
                )
            }
        }

    val DistinctToAggregate =
        rule("distinct->aggregate", Stratum.SUGAR) { node, g, _ ->
            if (node !is Distinct) {
                null
            } else {
                // group-by-all-columns Aggregate with no aggregate calls (B-T10 sweep).
                val a = Aggregate(node.id, node.label, node.location, provenance = node.provenance)
                replaced(
                    GraphOps.swapNode(g, node.id, a),
                    "distinct->aggregate",
                    Stratum.SUGAR,
                    node,
                    node.location,
                    null,
                    "Distinct is group-by-all Aggregate",
                )
            }
        }

    val HavingSplit =
        rule("having->aggregate+filter", Stratum.SUGAR) { node, g, _ ->
            if (node !is Aggregate || node.having == null) {
                null
            } else {
                val filterId = "${node.id}~having"
                val containerId = GraphOps.containerIdOf(g, node.id)
                val filter = Filter(filterId, node.label, node.location, predicate = node.having)
                var ng = GraphOps.swapNode(g, node.id, node.copy(having = null))
                ng = GraphOps.addNode(ng, filter, containerId)
                // Insert the filter between the aggregate's out and its consumers.
                ng = GraphOps.redirectFrom(ng, PortRef(node.id, PortNames.OUT), PortRef(filterId, PortNames.OUT))
                ng =
                    GraphOps.addEdges(
                        ng,
                        listOf(Edge(PortRef(node.id, PortNames.OUT), PortRef(filterId, PortNames.IN), EdgeKind.DATA)),
                    )
                ng = remapContainerPort(ng, node.id, PortNames.OUT, PortRef(filterId, PortNames.OUT))
                replaced(
                    ng,
                    "having->aggregate+filter",
                    Stratum.SUGAR,
                    node,
                    node.location,
                    null,
                    "HAVING expressed as downstream Filter",
                )
            }
        }

    // ---- Stratum: NODE_LOWERING (engine-relative) ----

    val BranchToFilters =
        rule("branch->filter", Stratum.NODE_LOWERING) { node, g, ctx ->
            if (node !is Branch || native(node, g, ctx)) {
                null
            } else {
                val eng = ctx.engineOf(node.id, g)!!
                val containerId = GraphOps.containerIdOf(g, node.id)
                val pred = node.predicate
                val inEdge = g.edges.firstOrNull { it.to == PortRef(node.id, PortNames.IN) }
                val src = inEdge?.from
                val tId = "${node.id}~t"
                val fId = "${node.id}~f"
                val ft = Filter(tId, node.label, node.location, predicate = pred)
                // 3VL-correct complement: a NULL predicate row goes to the FALSE port.
                val ff = Filter(fId, node.label, node.location, predicate = pred?.let { notCoalesceFalse(it) })
                var ng = GraphOps.addNode(g, ft, containerId)
                ng = GraphOps.addNode(ng, ff, containerId)
                if (src != null) {
                    ng =
                        GraphOps.addEdges(
                            ng,
                            listOf(
                                Edge(src, PortRef(tId, PortNames.IN), EdgeKind.DATA),
                                Edge(src, PortRef(fId, PortNames.IN), EdgeKind.DATA),
                            ),
                        )
                }
                // Redirect true/false consumers + container port mappings.
                ng =
                    ng.copy(
                        edges =
                            ng.edges.map { e ->
                                when (e.from) {
                                    PortRef(node.id, PortNames.TRUE) -> e.copy(from = PortRef(tId, PortNames.OUT))
                                    PortRef(node.id, PortNames.FALSE) -> e.copy(from = PortRef(fId, PortNames.OUT))
                                    else -> e
                                }
                            },
                    )
                ng = remapContainerPort(ng, node.id, PortNames.TRUE, PortRef(tId, PortNames.OUT))
                ng = remapContainerPort(ng, node.id, PortNames.FALSE, PortRef(fId, PortNames.OUT))
                ng = GraphOps.removeNode(ng, node.id)
                replaced(
                    ng,
                    "branch->filter",
                    Stratum.NODE_LOWERING,
                    node,
                    node.location,
                    eng.engine.qname.name,
                    "Branch not native on ${eng.manifest.id}",
                )
            }
        }

    val RightJoinSwap =
        rule("right-join->left-join", Stratum.NODE_LOWERING) { node, g, ctx ->
            if (node !is Join || node.type != JoinType.RIGHT) {
                null
            } else {
                val eng = ctx.engineOf(node.id, g) ?: return@rule null
                if ("right" in (eng.manifest.nodes["Join"]?.types ?: emptyList())) {
                    return@rule null
                }
                // Swap inputs (left<->right) and flip to LEFT; a Project restoring column order is a P3 emit concern.
                var ng = GraphOps.swapNode(g, node.id, node.copy(type = JoinType.LEFT))
                ng =
                    ng.copy(
                        edges =
                            ng.edges.map { e ->
                                when (e.to) {
                                    PortRef(node.id, PortNames.LEFT) -> e.copy(to = PortRef(node.id, PortNames.RIGHT))
                                    PortRef(node.id, PortNames.RIGHT) -> e.copy(to = PortRef(node.id, PortNames.LEFT))
                                    else -> e
                                }
                            },
                    )
                replaced(
                    ng,
                    "right-join->left-join",
                    Stratum.NODE_LOWERING,
                    node,
                    node.location,
                    eng.engine.qname.name,
                    "right join lowered by input swap",
                )
            }
        }

    val IntersectToSemiJoin =
        setOpRule("intersect->semi-join", { it is Intersect }) { node -> JoinType.SEMI to node }

    val ExceptToAntiJoin =
        setOpRule("except->anti-join", { it is Except }) { node -> JoinType.ANTI to node }

    // ---- helpers ----

    private fun setOpRule(
        name: String,
        match: (Node) -> Boolean,
        toJoin: (Node) -> Pair<JoinType, Node>,
    ): RewriteRule =
        rule(name, Stratum.NODE_LOWERING) { node, g, ctx ->
            if (!match(node) || native(node, g, ctx)) {
                null
            } else {
                val eng = ctx.engineOf(node.id, g)!!
                val (type, _) = toJoin(node)
                // Set-semantics join on all columns (Distinct-wrapped inputs are a later concern);
                // in1/in2 become left/right of a semi/anti Join at the same id (edges preserved by port rename).
                val join = Join(node.id, node.label, node.location, type = type, provenance = node.provenance)
                var ng = GraphOps.swapNode(g, node.id, join)
                ng =
                    ng.copy(
                        edges =
                            ng.edges.map { e ->
                                when (e.to) {
                                    PortRef(node.id, "in1") -> e.copy(to = PortRef(node.id, PortNames.LEFT))
                                    PortRef(node.id, "in2") -> e.copy(to = PortRef(node.id, PortNames.RIGHT))
                                    else -> e
                                }
                            },
                    )
                replaced(
                    ng,
                    name,
                    Stratum.NODE_LOWERING,
                    node,
                    node.location,
                    eng.engine.qname.name,
                    "$name (dataframe set-op lowering)",
                )
            }
        }

    private fun remapContainerPort(
        g: TtrpGraph,
        oldNodeId: String,
        oldPort: String,
        newRef: PortRef,
    ): TtrpGraph {
        val target = PortRef(oldNodeId, oldPort)
        val containers = LinkedHashMap(g.containers)
        val nodes = LinkedHashMap(g.nodes)
        for ((cid, c) in g.containers) {
            if (c.portMapping.values.none { it == target }) continue
            val remapped = c.portMapping.mapValues { if (it.value == target) newRef else it.value }
            val updated = c.copy(portMapping = remapped)
            containers[cid] = updated
            nodes[cid] = updated
        }
        return g.copy(nodes = nodes, containers = containers)
    }

    /** `not(coalesce(pred, false))` — 3VL-correct FALSE-port complement (NULL ⇒ false). */
    private fun notCoalesceFalse(pred: Expression): Expression {
        val falseLit = Literal(LiteralValue.Bool(false), pred.location)
        val coalesced = FunctionCall(CatalogId("fn.coalesce"), listOf(pred, falseLit), pred.location)
        return FunctionCall(CatalogId.NOT, listOf(coalesced), pred.location)
    }

    private fun replaced(
        g: TtrpGraph,
        rule: String,
        stratum: Stratum,
        before: Node,
        loc: SourceLocation,
        engine: String?,
        reason: String,
    ): RewriteResult.Replaced =
        RewriteResult.Replaced(
            g,
            AppliedRewrite(rule, stratum, before.label, before::class.simpleName ?: "?", engine, loc, reason),
        )

    private fun rule(
        ruleName: String,
        s: Stratum,
        body: (Node, TtrpGraph, RewriteContext) -> RewriteResult.Replaced?,
    ): RewriteRule =
        object : RewriteRule {
            override val name = ruleName
            override val stratum = s

            override fun apply(
                node: Node,
                graph: TtrpGraph,
                ctx: RewriteContext,
            ): RewriteResult = body(node, graph, ctx) ?: RewriteResult.Unchanged
        }
}
