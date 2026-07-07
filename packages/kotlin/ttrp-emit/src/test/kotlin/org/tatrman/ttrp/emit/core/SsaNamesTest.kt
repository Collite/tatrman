package org.tatrman.ttrp.emit.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Node

class SsaNamesTest :
    FunSpec({
        fun node(
            id: String,
            label: String,
        ): Node = Filter(id, label, SourceLocation.UNKNOWN, predicate = null)

        test("bare label is preserved; `name#k` reassignment → name_k") {
            val ns = SsaNames.assign(listOf(node("a", "sales"), node("b", "sales#2")))
            ns.getValue("a") shouldBe "sales"
            ns.getValue("b") shouldBe "sales_2"
        }

        test("anonymous / illegal labels become legal identifiers") {
            val ns = SsaNames.assign(listOf(node("a", "~3"), node("b", ""), node("c", "1bad")))
            ns.getValue("a") shouldBe "_filter_0"
            ns.getValue("b") shouldBe "_filter_1"
            ns.getValue("c") shouldBe "_1bad"
        }

        test("collisions are broken deterministically (two labels → distinct identifiers)") {
            val ns = SsaNames.assign(listOf(node("a", "sales"), node("b", "sales")))
            ns.getValue("a") shouldBe "sales"
            ns.getValue("b") shouldBe "sales_1"
        }

        test("property: mangling is injective over any island's node set") {
            checkAll(Arb.list(Arb.string(0, 6), 0..20)) { labels ->
                val nodes = labels.mapIndexed { i, l -> node("n$i", l) }
                val names = SsaNames.assign(nodes)
                names.values.toSet().size shouldBe nodes.size
            }
        }
    })
