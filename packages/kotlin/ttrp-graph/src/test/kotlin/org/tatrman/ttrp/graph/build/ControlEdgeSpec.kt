package org.tatrman.ttrp.graph.build

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.model.EdgeKind

private const val WORLD = "uses world \"acme.worlds.dev\"\n"

private fun twoPg(rest: String): String =
    WORLD +
        "container a target erp_pg \"\"\"sql\n    select 1\n\"\"\"\n" +
        "container b target erp_pg \"\"\"sql\n    select 2\n\"\"\"\n" +
        rest

/** T2.1.5 — control edges FS/SS build; FF + cross-container err are capability errors. */
class ControlEdgeSpec :
    StringSpec({

        "`after` builds an FS edge" {
            val g = GraphFixtures.build(twoPg("b after a\n")).graph
            g.edges.count { it.kind == EdgeKind.CONTROL_FS } shouldBe 1
        }

        "`with` builds an SS edge" {
            val g = GraphFixtures.build(twoPg("a with b\n")).graph
            g.edges.count { it.kind == EdgeKind.CONTROL_SS } shouldBe 1
        }

        "a control block is accepted and builds both edges" {
            val g = GraphFixtures.build(twoPg("control {\n    b after a\n    a with b\n}\n")).graph
            g.edges.count { it.kind == EdgeKind.CONTROL_FS } shouldBe 1
            g.edges.count { it.kind == EdgeKind.CONTROL_SS } shouldBe 1
        }

        "`finishes with` (FF) yields TTRP-CTL-001" {
            val r = GraphFixtures.build(twoPg("a finishes with b\n"))
            r.allErrorIds shouldBe setOf("TTRP-CTL-001")
        }

        "a cross-container `err` edge yields TTRP-CTL-004" {
            val src =
                WORLD +
                    "container a target erp_pg \"\"\"sql\n    select 1\n\"\"\"\n" +
                    "container b(in data, out o) target polars {\n    o = filter(data, 1 > 0)\n}\n" +
                    "a.err -> b.data\n"
            GraphFixtures.build(src).allErrorIds shouldBe setOf("TTRP-CTL-004")
        }

        "a cross-container `rejects` edge is legal (data-shaped, F-d-i)" {
            val src =
                WORLD +
                    "container a(in x, out o, err rejects) target polars {\n    o = filter(x, 1 > 0)\n}\n" +
                    "container b(in data, out o2) target polars {\n    o2 = filter(data, 1 > 0)\n}\n" +
                    "a.rejects -> b.data\n"
            val r = GraphFixtures.build(src)
            r.allErrorIds shouldBe emptySet()
            r.graph.edges.any { it.from.port == "rejects" && it.to.port == "data" } shouldBe true
        }

        "an unconnected err port is silent (elision is P2-legal)" {
            val src =
                WORLD + "container a(in x, out o, err rejects) target polars {\n    o = filter(x, 1 > 0)\n}\n"
            GraphFixtures.build(src).allErrorIds shouldBe emptySet()
        }
    })
