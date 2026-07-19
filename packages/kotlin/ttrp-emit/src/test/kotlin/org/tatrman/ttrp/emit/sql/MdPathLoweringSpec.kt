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
import org.tatrman.ttr.semantics.md.Journaling
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
            // `plan` is invalidate-journaled (is_current): a journal Filter wraps the Load, beneath the
            // coordinate filter — the §8 rows compose on top of the wrapped read view (R31, S4-A4b).
            val journal = node.aggregate.input.filter.input
            fn(journal.filter.condition).let {
                it.operation shouldBe "eq"
                it.operandsList[0].columnRef.name shouldBe "is_current"
                it.operandsList[1].literal.boolValue shouldBe true
            }
            journal.filter.input.tableScan.table.name shouldBe "f_plan"
            cols(journal.filter.input) shouldBe listOf("customer_name", "measure_code", "amount", "is_current")
        }

        // -- §8: journaling read view (R31, S4-A4b) — wrap the cubelet Load -------------------------

        "overwrite journaling leaves the Load unwrapped (sales)" {
            val node = lowering.lower(path("sales", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())
            // sales is overwrite → the coordinate Filter sits directly on the TableScan, no journal wrap.
            node.aggregate.input.filter.input.nodeCase shouldBe PlanNode.NodeCase.TABLE_SCAN
        }

        "invalidate journaling wraps the Load in a valid-flag Filter (plan/is_current)" {
            val node = lowering.lower(path("plan", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())
            val journal = node.aggregate.input.filter.input
            journal.nodeCase shouldBe PlanNode.NodeCase.FILTER
            fn(journal.filter.condition).let {
                it.operation shouldBe "eq"
                it.operandsList[0].columnRef.name shouldBe "is_current"
                it.operandsList[1].literal.boolValue shouldBe true
            }
            journal.filter.input.nodeCase shouldBe PlanNode.NodeCase.TABLE_SCAN
        }

        // -- §8: diff journaling → inner Aggregate SUM per grain (R31, S4-A4c) ----------------------

        // A diff-journaled `sales` (deltas per grain): copy the wide binding, flip journaling to Diff.
        val diffBindings =
            MdFixtures.salesBindings().let { b ->
                b.copy(
                    cubelets =
                        b.cubelets + ("sales" to b.cubelets.getValue("sales").copy(journaling = Journaling.Diff)),
                )
            }
        val diffLowering = MdPathLowering(diffBindings, MdFixtures.salesModel())

        "diff journaling wraps the Load in an inner Aggregate SUMming the measure per full grain" {
            val node = diffLowering.lower(path("sales", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())

            // outer §8 aggregate (scalar sum over the coordinate-filtered diff view).
            node.aggregate.aggregatesList
                .single()
                .function shouldBe "sum"
            val filter = node.aggregate.input
            filter.nodeCase shouldBe PlanNode.NodeCase.FILTER
            // inner diff view: SUM(net) grouped by the full grain [customer_name, sale_date].
            val diff = filter.filter.input
            diff.nodeCase shouldBe PlanNode.NodeCase.AGGREGATE
            diff.aggregate.groupKeysList.map { it.name } shouldBe listOf("customer_name", "sale_date")
            diff.aggregate.aggregatesList.single().let {
                it.function shouldBe "sum"
                it.argsList.single().name shouldBe "net"
                it.alias shouldBe "net" // aliased back so the outer Aggregate/Filter reference it unchanged
            }
            diff.aggregate.input.tableScan.table.name shouldBe "f_sales"
            cols(diff.aggregate.input) shouldBe listOf("customer_name", "sale_date", "net")
        }

        "a non-additive read over a diff cubelet is ill-defined — md/diff-nonadditive-unsupported" {
            shouldThrow<MdLoweringException> {
                diffLowering.lower(
                    path("sales", listOf(pinned("Customer.name", "Kaufland")), agg = AggKind.AVG),
                    scalarShape(),
                )
            }.code shouldBe "md/diff-nonadditive-unsupported"
        }

        "diff journaling needs the MdModel for the grain — without it, md/diff-unsupported" {
            shouldThrow<MdLoweringException> {
                MdPathLowering(
                    diffBindings,
                ).lower(path("sales", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())
            }.code shouldBe "md/diff-unsupported"
        }

        // -- §8: multi-source cubelet → UNION ALL of the source scans (contracts §4.1) --------------

        "a multi-source cubelet unions each source scan beneath the coordinate filter + aggregate" {
            // Two fact tables for `sales` (e.g. live + archive partitions) sharing column bindings.
            val msBindings =
                MdFixtures.salesBindings().let { b ->
                    val sales =
                        b.cubelets
                            .getValue(
                                "sales",
                            ).copy(sources = listOf("db.dbo.f_sales", "db.dbo.f_sales_archive"))
                    b.copy(cubelets = b.cubelets + ("sales" to sales))
                }
            val msLowering = MdPathLowering(msBindings, MdFixtures.salesModel())
            val node = msLowering.lower(path("sales", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())

            // Aggregate ← Filter(customer_name = Kaufland) ← Union(scan f_sales, scan f_sales_archive).
            val union = node.aggregate.input.filter.input
            union.nodeCase shouldBe PlanNode.NodeCase.UNION
            union.union.all shouldBe true // UNION ALL — partitioned fact rows must not be deduped
            union.union.inputsList.map { it.tableScan.table.name } shouldBe listOf("f_sales", "f_sales_archive")
            // both source scans project the same columns, so the union row types align.
            union.union.inputsList.forEach { cols(it) shouldBe listOf("customer_name", "net") }
        }

        "a single-source cubelet is not wrapped in a Union (scan sits directly under the filter)" {
            val node = lowering.lower(path("sales", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())
            // sales has one source → the coordinate Filter sits directly on the TableScan, no Union.
            node.aggregate.input.filter.input.nodeCase shouldBe PlanNode.NodeCase.TABLE_SCAN
        }

        // -- §8: scalar-position wrap (S3 canonical usage: MD path as a predicate operand) ---------

        "lowerScalar wraps the subtree as a scalar SubqueryExpression" {
            val expr = lowering.lowerScalar(path("sales", listOf(pinned("Customer.name", "Kaufland"))), scalarShape())

            expr.exprCase shouldBe Expression.ExprCase.SUBQUERY
            expr.subquery.kind shouldBe "scalar"
            expr.subquery.subquery.nodeCase shouldBe PlanNode.NodeCase.AGGREGATE
        }

        // -- deferred §8 cases surface as typed lowering errors, never silent drops -----------------

        "a hop attribute needs the MdModel — without it, md/hop-unsupported (not a silent drop)" {
            val coord = Coordinate("Customer", "Customer.region", Selector.Star)
            shouldThrow<MdLoweringException> {
                // `lowering` has bindings but no model → the join key can't be derived.
                lowering.lower(path("sales", listOf(coord)), PathShape(freeDims = listOf("Customer")))
            }.code shouldBe "md/hop-unsupported"
        }

        // -- §8: hop (map-mediated) attribute → inner Join on the grain key (S4-A4) -----------------

        "a pinned hop attribute joins the fact to the hop table on the grain key, then filters the joined column" {
            val withModel = MdPathLowering(MdFixtures.salesBindings(), MdFixtures.salesModel())
            val coord = Coordinate("Customer", "Customer.region", Selector.Pinned(MemberRef("West")))
            val node = withModel.lower(path("sales", listOf(coord)), scalarShape())

            // Aggregate ← Filter(region = 'West') ← Join(f_sales ⋈ d_customer).
            fn(node.aggregate.input.filter.condition).let {
                it.operation shouldBe "eq"
                it.operandsList[0].columnRef.name shouldBe "region"
                it.operandsList[1].literal.stringValue shouldBe "West"
            }
            val join = node.aggregate.input.filter.input
            join.nodeCase shouldBe PlanNode.NodeCase.JOIN
            join.join.left.tableScan.table.name shouldBe "f_sales"
            join.join.right.tableScan.table.name shouldBe "d_customer"
            // grain-key equijoin: f_sales.customer_name = d_customer.cust_name (the Name domain source).
            fn(join.join.condition).let {
                it.operation shouldBe "eq"
                it.operandsList[0].columnRef.name shouldBe "customer_name"
                it.operandsList[1].columnRef.name shouldBe "cust_name"
            }
            // the hop table projects its join key + the drilled column.
            join.join.right.tableScan.outputColumnsList
                .map { it.name } shouldBe listOf("cust_name", "region")
        }

        "a free hop attribute groups by the joined column" {
            val withModel = MdPathLowering(MdFixtures.salesBindings(), MdFixtures.salesModel())
            val coord = Coordinate("Customer", "Customer.region", Selector.Star)
            val node = withModel.lower(path("sales", listOf(coord)), PathShape(freeDims = listOf("Customer")))

            node.aggregate.groupKeysList.map { it.name } shouldBe listOf("region")
            // no filter (pure free dim) ⇒ the Aggregate sits directly on the Join.
            node.aggregate.input.nodeCase shouldBe PlanNode.NodeCase.JOIN
        }

        // -- §8: computed (viaCalc) coordinate → Join to the calc map's case table (S4-A5) ----------

        "a pinned viaCalc coordinate joins the fact to the calc case table, then filters the to-column" {
            val withModel = MdPathLowering(MdFixtures.salesBindings(), MdFixtures.salesModel())
            // sales[Time.month = 6] viaCalc monthOfDate — month is computed from the date grain (sale_date).
            val coord = Coordinate("Time", "Time.month", Selector.Pinned(MemberRef("6")), "monthOfDate")
            val node = withModel.lower(path("sales", listOf(coord)), scalarShape())

            // Aggregate ← Filter(cal_month = 6) ← Join(f_sales ⋈ d_calendar).
            fn(node.aggregate.input.filter.condition).let {
                it.operation shouldBe "eq"
                it.operandsList[0].columnRef.name shouldBe "cal_month"
                it.operandsList[1].literal.intValue shouldBe 6L
            }
            val join = node.aggregate.input.filter.input
            join.nodeCase shouldBe PlanNode.NodeCase.JOIN
            join.join.left.tableScan.table.name shouldBe "f_sales"
            join.join.right.tableScan.table.name shouldBe "d_calendar"
            // the join key is the calc's `from` domain (Date): f_sales.sale_date = d_calendar.cal_date.
            fn(join.join.condition).let {
                it.operation shouldBe "eq"
                it.operandsList[0].columnRef.name shouldBe "sale_date"
                it.operandsList[1].columnRef.name shouldBe "cal_date"
            }
            // the case table projects its join key + the drilled `to` column.
            join.join.right.tableScan.outputColumnsList
                .map { it.name } shouldBe listOf("cal_date", "cal_month")
        }

        "a free viaCalc coordinate groups by the case table's to-column" {
            val withModel = MdPathLowering(MdFixtures.salesBindings(), MdFixtures.salesModel())
            val coord = Coordinate("Time", "Time.month", Selector.Star, "monthOfDate")
            val node = withModel.lower(path("sales", listOf(coord)), PathShape(freeDims = listOf("Time")))

            node.aggregate.groupKeysList.map { it.name } shouldBe listOf("cal_month")
            // pure free dim (no filter) ⇒ the Aggregate sits directly on the Join.
            node.aggregate.input.nodeCase shouldBe PlanNode.NodeCase.JOIN
        }

        // -- §8: inline viaCalc (no case table) → a date-extraction predicate (S4-A5 inline) ---------

        "an inline viaCalc coordinate (no case table) filters on a date-extraction expression" {
            val withModel = MdPathLowering(MdFixtures.salesBindings(), MdFixtures.salesModel())
            // date_to_year (yearOfDate) has no md2db_map case table → inline: datepart(YEAR, sale_date) = 2025.
            val coord = Coordinate("Time", "Time.year", Selector.Pinned(MemberRef("2025")), "yearOfDate")
            val node = withModel.lower(path("sales", listOf(coord)), scalarShape())

            fn(node.aggregate.input.filter.condition).let { eq ->
                eq.operation shouldBe "eq"
                fn(eq.operandsList[0]).let {
                    it.operation shouldBe "extract"
                    it.operandsList[0].literal.stringValue shouldBe "YEAR" // TimeUnitRange symbol operand
                    it.operandsList[1].columnRef.name shouldBe "sale_date" // the bound date grain
                }
                eq.operandsList[1].literal.intValue shouldBe 2025L
            }
            // no case table ⇒ a plain scan (no join), the base date column projected for the extraction.
            node.aggregate.input.filter.input.tableScan.table.name shouldBe "f_sales"
            cols(node.aggregate.input.filter.input) shouldBe listOf("sale_date", "net")
        }

        "a chained inline calc (base not on the fact) is deferred — md/calc-no-base" {
            val withModel = MdPathLowering(MdFixtures.salesBindings(), MdFixtures.salesModel())
            // quarterOfMonth's `from` domain is Month; sales binds no month column (month is itself
            // computed from the date) → the base isn't fact-resident → chained calc, deferred.
            val coord = Coordinate("Time", "Time.quarter", Selector.Pinned(MemberRef("2")), "quarterOfMonth")
            shouldThrow<MdLoweringException> {
                withModel.lower(path("sales", listOf(coord)), scalarShape())
            }.code shouldBe "md/calc-no-base"
        }

        "a free (star) inline calc coordinate is deferred — md/calc-inline-star-unsupported" {
            val withModel = MdPathLowering(MdFixtures.salesBindings(), MdFixtures.salesModel())
            val coord = Coordinate("Time", "Time.year", Selector.Star, "yearOfDate")
            shouldThrow<MdLoweringException> {
                withModel.lower(path("sales", listOf(coord)), PathShape(freeDims = listOf("Time")))
            }.code shouldBe "md/calc-inline-star-unsupported"
        }

        "a viaCalc coordinate needs the MdModel — without it, md/calc-unsupported (not a silent drop)" {
            val coord = Coordinate("Time", "Time.month", Selector.Pinned(MemberRef("6")), "monthOfDate")
            shouldThrow<MdLoweringException> {
                lowering.lower(path("sales", listOf(coord)), scalarShape())
            }.code shouldBe "md/calc-unsupported"
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
