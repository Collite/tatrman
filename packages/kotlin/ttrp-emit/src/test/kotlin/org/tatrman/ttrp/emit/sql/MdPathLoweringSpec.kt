// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.Coordinate
import org.tatrman.ttr.md.resolve.MemberRef
import org.tatrman.ttr.md.resolve.PathShape
import org.tatrman.ttr.md.resolve.Selector
import org.tatrman.ttr.semantics.md.AggKind
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures

/**
 * MD dot-path S4-A — lowering a resolved [CanonicalPath] to a `plan.v1` relational subtree
 * ([MdPathLowering]), one assertion group per contracts §8 row. Goldens are asserted at the plan.v1
 * PlanNode structure level (the wire form is the contract; SQL unparse of MD reads — registering the
 * fact table in the island's ModelHandle — is a later integration step). Bindings come from the shared
 * `sales-model` `binding.ttrm` fixture ([MdFixtures.salesBindings]): `sales` is wide (`f_sales`),
 * `plan` is long (`f_plan`, measure-code `NET` in `amount`).
 */
class MdPathLoweringSpec :
    StringSpec({
        val lowering = MdPathLowering(MdFixtures.salesBindings())

        // -- helpers: hand-build canonical paths (the resolver's S3 output shape, §3) -----------

        fun pinned(
            attr: String,
            member: String,
        ) = Coordinate(attr.substringBefore('.'), attr, Selector.Pinned(MemberRef(member)))

        fun path(
            cubelet: String,
            coords: List<Coordinate>,
            measure: String = "net",
            agg: AggKind = AggKind.SUM,
        ) = CanonicalPath(cubelet, coords, measure, agg)

        fun scalarShape() = PathShape(freeDims = emptyList())

        fun cols(node: PlanNode) = node.tableScan.outputColumnsList.map { it.name }

        fun fn(e: Expression) = e.function

        // -- §8: cubelet → TableScan; pinned → Filter EQ; measure+agg → Aggregate (wide, scalar) --

        "a pinned coordinate lowers to Aggregate ← Filter(col = literal) ← TableScan of the bound table" {
            val node = lowering.lower(path("sales", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())

            node.nodeCase shouldBe PlanNode.NodeCase.AGGREGATE
            // scalar shape ⇒ aggregate-all (no group keys), one sum over the wide measure column.
            node.aggregate.groupKeysList.map { it.name } shouldBe emptyList()
            node.aggregate.aggregatesList.single().let {
                it.function shouldBe "sum"
                it.argsList.single().name shouldBe "net"
                it.alias shouldBe "net"
            }

            val filter = node.aggregate.input
            filter.nodeCase shouldBe PlanNode.NodeCase.FILTER
            fn(filter.filter.condition).let {
                it.operation shouldBe "eq"
                it.operandsList[0].columnRef.name shouldBe "customer_name"
                it.operandsList[1].literal.stringValue shouldBe "Kaufland"
            }

            val scan = filter.filter.input
            scan.nodeCase shouldBe PlanNode.NodeCase.TABLE_SCAN
            scan.tableScan.table.let {
                it.schemaCode shouldBe SchemaCode.DB
                it.namespace shouldBe "dbo"
                it.name shouldBe "f_sales"
            }
            cols(scan) shouldBe listOf("customer_name", "net")
        }

        // -- §8: MemberSet → IN --------------------------------------------------------------------

        "a member set lowers to Filter col IN (…)" {
            val coord =
                Coordinate(
                    "Customer",
                    "Customer.name",
                    Selector.MemberSet(listOf(MemberRef("Kaufland"), MemberRef("Lidl"))),
                )
            val node = lowering.lower(path("sales", listOf(coord)), scalarShape())

            fn(node.aggregate.input.filter.condition).let {
                it.operation shouldBe "in"
                it.operandsList[0].columnRef.name shouldBe "customer_name"
                it.operandsList[1].literal.stringValue shouldBe "Kaufland"
                it.operandsList[2].literal.stringValue shouldBe "Lidl"
            }
        }

        // -- §8: Range → BETWEEN (ge AND le — the wire has no BETWEEN operator) ---------------------

        "a range lowers to Filter (col ge lo) and (col le hi)" {
            val coord = Coordinate("Time", "Time.day", Selector.Range(MemberRef("2025-01-01"), MemberRef("2025-12-31")))
            val node = lowering.lower(path("sales", listOf(coord)), scalarShape())

            fn(node.aggregate.input.filter.condition).let { and ->
                and.operation shouldBe "and"
                fn(and.operandsList[0]).let {
                    it.operation shouldBe "ge"
                    it.operandsList[0].columnRef.name shouldBe "sale_date"
                    it.operandsList[1].literal.stringValue shouldBe "2025-01-01"
                }
                fn(and.operandsList[1]).let {
                    it.operation shouldBe "le"
                    it.operandsList[1].literal.stringValue shouldBe "2025-12-31"
                }
            }
        }

        // -- §8: Star (free dim) → group-by key; vector shape --------------------------------------

        "a free star lowers to an Aggregate group-by on the bound column, with no filter" {
            val coord = Coordinate("Customer", "Customer.name", Selector.Star)
            val node = lowering.lower(path("sales", listOf(coord)), PathShape(freeDims = listOf("Customer")))

            node.nodeCase shouldBe PlanNode.NodeCase.AGGREGATE
            node.aggregate.groupKeysList.map { it.name } shouldBe listOf("customer_name")
            node.aggregate.aggregatesList
                .single()
                .argsList
                .single()
                .name shouldBe "net"
            // no coordinate filter ⇒ the Aggregate sits directly on the scan.
            node.aggregate.input.nodeCase shouldBe PlanNode.NodeCase.TABLE_SCAN
        }

        // -- §8: long shape → measure-code pre-Filter + value-column aggregate ---------------------

        "a long-shape cubelet pre-Filters the measure code and aggregates the value column" {
            val node = lowering.lower(path("plan", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())

            node.aggregate.aggregatesList.single().let {
                it.function shouldBe "sum"
                it.argsList.single().name shouldBe "amount" // the long value column, not a per-measure column
                it.alias shouldBe "net"
            }
            // conjunction: measure-code pre-filter first, then the coordinate filter.
            fn(node.aggregate.input.filter.condition).let { and ->
                and.operation shouldBe "and"
                fn(and.operandsList[0]).let {
                    it.operation shouldBe "eq"
                    it.operandsList[0].columnRef.name shouldBe "measure_code"
                    it.operandsList[1].literal.stringValue shouldBe "NET"
                }
                fn(and.operandsList[1]).operandsList[0].columnRef.name shouldBe "customer_name"
            }
            node.aggregate.input.filter.input.tableScan.table.name shouldBe "f_plan"
            cols(node.aggregate.input.filter.input) shouldBe listOf("customer_name", "measure_code", "amount")
        }

        // -- §8: scalar-position wrap (S3 canonical usage: MD path as a predicate operand) ---------

        "lowerScalar wraps the subtree as a scalar SubqueryExpression" {
            val expr = lowering.lowerScalar(path("sales", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())

            expr.exprCase shouldBe Expression.ExprCase.SUBQUERY
            expr.subquery.kind shouldBe "scalar"
            expr.subquery.subquery.nodeCase shouldBe PlanNode.NodeCase.AGGREGATE
        }

        // -- deferred §8 cases surface as typed lowering errors, never silent drops -----------------

        "a hop (map-mediated) attribute is a documented deferred case (S4-A4)" {
            val coord = Coordinate("Customer", "Customer.region", Selector.Star)
            shouldThrow<MdLoweringException> {
                lowering.lower(path("sales", listOf(coord)), PathShape(freeDims = listOf("Customer")))
            }.code shouldBe "md/hop-unsupported"
        }

        "an unbound cubelet fails with md/unbound-cubelet" {
            shouldThrow<MdLoweringException> {
                lowering.lower(path("nonexistent", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())
            }.let {
                it.code shouldBe "md/unbound-cubelet"
                it.message!! shouldContain "nonexistent"
            }
        }

        // -- the lowered subtree is standard plan.v1 — it survives wire round-trip (S4-A6, MDS5) ----

        "the lowered subtree round-trips through the plan.v1 wire form" {
            val node = lowering.lower(path("plan", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())
            PlanNode.parseFrom(node.toByteArray()) shouldBe node
        }

        "an unbound measure fails with md/unbound-measure" {
            shouldThrow<MdLoweringException> {
                lowering.lower(
                    path("sales", listOf(pinned("Customer.name", "Kaufland")), measure = "profit"),
                    scalarShape(),
                )
            }.code shouldBe "md/unbound-measure"
        }
    })
