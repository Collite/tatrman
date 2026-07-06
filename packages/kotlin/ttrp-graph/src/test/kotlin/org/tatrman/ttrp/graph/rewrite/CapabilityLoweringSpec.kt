package org.tatrman.ttrp.graph.rewrite

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.Edge
import org.tatrman.ttrp.graph.model.EdgeKind
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Intersect
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Port
import org.tatrman.ttrp.graph.model.PortDirection
import org.tatrman.ttrp.graph.model.PortKind
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.PortRef
import org.tatrman.ttrp.graph.model.TtrpGraph

private val L = SourceLocation.UNKNOWN

/** A polars container with two Loads feeding [binary] (a Join/Intersect/Except at id "m2"). */
private fun binaryGraph(binary: Node): TtrpGraph {
    val a = Load("m0", "a#1", L, source = "files.x")
    val b = Load("m1", "b#1", L, source = "files.y")
    val members = listOf(a, b, binary)
    val edges =
        listOf(
            Edge(
                PortRef("m0", PortNames.OUT),
                PortRef("m2", "left").takeIf {
                    binary is Join
                } ?: PortRef("m2", "in1"),
                EdgeKind.DATA,
            ),
            Edge(
                PortRef("m1", PortNames.OUT),
                PortRef("m2", "right").takeIf {
                    binary is Join
                } ?: PortRef("m2", "in2"),
                EdgeKind.DATA,
            ),
        )
    val container =
        Container(
            "c0",
            "c",
            L,
            "polars",
            members.map { it.id },
            listOf(Port("o", PortKind.DATA, PortDirection.OUT)),
            linkedMapOf(
                "o" to PortRef("m2", PortNames.OUT),
            ),
        )
    val nodes = LinkedHashMap<String, Node>()
    members.forEach { nodes[it.id] = it }
    nodes["c0"] = container
    return TtrpGraph(nodes, edges, linkedMapOf("c0" to container))
}

/** T2.3a.5 — capability-lowering stratum (engine-relative). */
class CapabilityLoweringSpec :
    StringSpec({

        "a right join on polars lowers to a left join with swapped inputs" {
            val g = binaryGraph(Join("m2", "j#1", L, type = JoinType.RIGHT))
            val r = RewriteSupport.engine().normalize(g)
            val join = r.graph.nodes["m2"] as Join
            join.type shouldBe JoinType.LEFT
            // Inputs swapped: m0 now feeds the right port, m1 the left.
            r.graph.edges
                .first { it.from.nodeId == "m0" }
                .to.port shouldBe "right"
            r.graph.edges
                .first { it.from.nodeId == "m1" }
                .to.port shouldBe "left"
        }

        "intersect on polars lowers to a semi join (in1/in2 → left/right)" {
            val g = binaryGraph(Intersect("m2", "i#1", L))
            val r = RewriteSupport.engine().normalize(g)
            val join = r.graph.nodes["m2"] as Join
            join.type shouldBe JoinType.SEMI
            r.graph.edges
                .first { it.from.nodeId == "m0" }
                .to.port shouldBe "left"
        }

        "intersect stays native on erp_pg (no rewrite fires)" {
            val g = binaryGraph(Intersect("m2", "i#1", L))
            // Re-target the container to erp_pg where Intersect IS native.
            val c = g.containers.getValue("c0").copy(target = "erp_pg")
            val ng = g.copy(nodes = LinkedHashMap(g.nodes).apply { put("c0", c) }, containers = linkedMapOf("c0" to c))
            val r = RewriteSupport.engine().normalize(ng)
            r.log shouldBe emptyList()
        }

        "the Branch false-port filter is the 3VL-correct complement not(coalesce(pred, false))" {
            val g = GraphFixtures.graphOf(GraphFixtures.program("hero.ttrp"))
            val r = RewriteSupport.engine().normalize(g)
            val falseFilter =
                r.graph.nodes.values
                    .filterIsInstance<Filter>()
                    .single { it.id.endsWith("~f") }
            val not = falseFilter.predicate as FunctionCall
            not.function.value shouldBe "op.not"
            (not.args.single() as FunctionCall).function.value shouldBe "fn.coalesce"
        }

        "the rewrite log entry names the engine and the lowering reason" {
            val g = binaryGraph(Join("m2", "j#1", L, type = JoinType.RIGHT))
            val entry =
                RewriteSupport
                    .engine()
                    .normalize(g)
                    .log
                    .single()
            entry.engine shouldBe "polars"
            entry.reason shouldContain "swap"
        }
    })
