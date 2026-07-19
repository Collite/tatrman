// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.rejects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
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
 * RJ-P1 / 1.1.5, revised by the RJ-P5 review (B2) — join-ON reject sites are **fail-closed** in v1.
 * The former decomposition rule relocated a single-side ON cast onto an unguarded per-side calc but
 * never synthesized a guard/branch/reject, so a `rejects` wire off a join silently produced an empty
 * stream while invalid rows became NULL join keys and dropped (contracts §5 partition-invariant
 * violation). v1 now emits a `TTRP-RJ-108` ERROR for any reject-capable join `on:` with a wired
 * rejects port and leaves the graph unchanged — the author moves the cast into a `calc` before the
 * join. Built programmatically (like `RewriteSupport.chain`) so the ON expression is under exact control.
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

        "1.1.5 (B2) — a single-side cast in a wired join ON is fail-closed: graph unchanged + TTRP-RJ-108" {
            // ON: cast(left.account_id as int) = right.region  (cast is left-only)
            val on =
                fn(
                    "op.eq",
                    Cast(col(PortNames.LEFT, "account_id"), TtrpType.Integer, l),
                    col(PortNames.RIGHT, "region"),
                )
            val r = RewriteSupport.elaborationEngine().normalize(joinGraph(on))

            // no per-side calc is synthesized — the reject stratum does not touch the join...
            r.graph.nodes.values
                .filterIsInstance<Calc>() shouldBe emptyList()
            // ...the ON still carries the cast (unchanged)...
            val join =
                r.graph.nodes.values
                    .filterIsInstance<Join>()
                    .single()
            exprHasCast(join.on) shouldBe true
            // ...and a compile ERROR tells the author to move the cast into a calc before the join.
            r.diagnostics.map { it.id.id } shouldContain "TTRP-RJ-108"
            r.diagnostics.single { it.id.id == "TTRP-RJ-108" }.severity shouldBe Severity.ERROR
            // the old (silent) pair-schema warning is gone.
            r.diagnostics.map { it.id.id } shouldNotContain "TTRP-RJ-105"
        }

        "1.1.5 (B2) — a both-sides reject-capable ON is also fail-closed with TTRP-RJ-108" {
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
            exprHasCast(join.on) shouldBe false // no cast here, but the div ON is untouched
            join.on shouldBe on
            r.diagnostics.map { it.id.id } shouldContain "TTRP-RJ-108"
            r.diagnostics.single { it.id.id == "TTRP-RJ-108" }.severity shouldBe Severity.ERROR
        }
    })
