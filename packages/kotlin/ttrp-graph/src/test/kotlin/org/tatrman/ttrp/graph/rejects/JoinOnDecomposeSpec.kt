// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rejects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.Edge
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Port
import org.tatrman.ttrp.graph.model.PortDirection
import org.tatrman.ttrp.graph.model.PortKind
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.graph.rewrite.RewriteSupport

/**
 * RJ-P1 / 1.1.5 — join-ON decomposition (contracts §5, R-B3-β), the earlier rule of the
 * reject stratum. A single-side reject-capable subexpr in the ON moves to a per-side `calc`;
 * a both-sides subexpr stays and produces the pair-schema fallback + `TTRP-RJ-105`. Built
 * programmatically (like `RewriteSupport.chain`) so the ON expression is under exact control.
 */
class JoinOnDecomposeSpec :
    StringSpec({
        val l = SourceLocation.UNKNOWN

        fun col(
            port: String,
            c: String,
        ): Expression = ColumnRef(port, c, l)

        fun fn(
            id: String,
            vararg a: Expression,
        ): Expression = FunctionCall(CatalogId(id), a.toList(), l)

        // A join in an erp_pg container with its `rejects` wired (so the stratum fires), plus the
        // given ON condition.
        fun joinGraph(on: Expression): TtrpGraph {
            val m0 = Load("m0", "m0#1", l, source = "files.x")
            val m1 = Load("m1", "m1#1", l, source = "files.y")
            val j = Join("j", "j#1", l, type = JoinType.INNER, on = on)
            val sink = Store("s", "s#1", l, target = "files.errs")
            val members = listOf<Node>(m0, m1, j)
            val container =
                Container(
                    id = "c0",
                    label = "c",
                    location = l,
                    target = "erp_pg",
                    memberIds = members.map { it.id },
                    declaredPorts =
                        listOf(
                            Port("o", PortKind.DATA, PortDirection.OUT),
                            Port(PortNames.REJECTS, PortKind.DATA, PortDirection.OUT),
                        ),
                    portMapping =
                        linkedMapOf(
                            "o" to PortRef("j", PortNames.OUT),
                            PortNames.REJECTS to PortRef("j", PortNames.REJECTS),
                        ),
                )
            val nodes = LinkedHashMap<String, Node>()
            members.forEach { nodes[it.id] = it }
            nodes["s"] = sink
            nodes["c0"] = container
            val edges =
                listOf(
                    Edge(PortRef("m0", PortNames.OUT), PortRef("j", PortNames.LEFT), EdgeKind.DATA),
                    Edge(PortRef("m1", PortNames.OUT), PortRef("j", PortNames.RIGHT), EdgeKind.DATA),
                    Edge(PortRef("c0", PortNames.REJECTS), PortRef("s", PortNames.IN), EdgeKind.DATA),
                )
            return TtrpGraph(nodes, edges, linkedMapOf("c0" to container))
        }

        fun exprHasCast(e: Expression?): Boolean =
            when (e) {
                is Cast -> true
                is FunctionCall -> e.args.any { exprHasCast(it) }
                else -> false
            }

        "1.1.5 — a single-side cast in the ON moves to a per-side calc; the ON loses the cast" {
            // ON: cast(left.account_id as int) = right.region  (cast is left-only)
            val on =
                fn(
                    "op.eq",
                    Cast(col(PortNames.LEFT, "account_id"), TtrpType.Integer, l),
                    col(PortNames.RIGHT, "region"),
                )
            val g = RewriteSupport.elaborationEngine().normalize(joinGraph(on)).graph

            // the cast now lives on a synthesized per-side calc...
            g.nodes.values
                .filterIsInstance<Calc>()
                .any { c -> c.assignments.any { exprHasCast(it.value) } } shouldBe
                true
            // ...and the join's ON no longer carries it.
            val join =
                g.nodes.values
                    .filterIsInstance<Join>()
                    .single()
            exprHasCast(join.on) shouldBe false
        }

        "1.1.5 — a both-sides reject-capable ON stays and warns TTRP-RJ-105 (pair-schema fallback)" {
            // ON: (left.amount / right.amount) > 0  — the div spans both inputs.
            val on =
                fn(
                    "op.gt",
                    fn("op.div", col(PortNames.LEFT, "amount"), col(PortNames.RIGHT, "amount")),
                    Literal(LiteralValue.Num("0"), l),
                )
            val r = RewriteSupport.elaborationEngine().normalize(joinGraph(on))

            val join =
                r.graph.nodes.values
                    .filterIsInstance<Join>()
                    .single()
            join.on.toString() shouldContain "op.div" // unchanged — cannot be pulled to one side
            r.diagnostics.map { it.id.id } shouldContain "TTRP-RJ-105"
        }
    })
