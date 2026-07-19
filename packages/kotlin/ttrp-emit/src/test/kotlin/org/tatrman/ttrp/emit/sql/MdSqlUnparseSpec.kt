// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainIgnoringCase
import org.tatrman.translate.v1.SqlDialect
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.Coordinate
import org.tatrman.ttr.md.resolve.MemberRef
import org.tatrman.ttr.md.resolve.PathShape
import org.tatrman.ttr.md.resolve.Selector
import org.tatrman.ttr.semantics.md.AggKind
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures

/**
 * MD dot-path S4-A4 (end-to-end) — a lowered MD read unparses to real SQL once its fact/hop tables are
 * registered in the island's [IslandModelHandle] (via [MdPathLowering.referencedTables]). This closes
 * the loop: canonical path → plan.v1 subtree → Postgres SQL through the published translator. Uses the
 * shared `sales-model` bindings + logical model (for hop join keys + column types).
 */
class MdSqlUnparseSpec :
    StringSpec({
        val lowering = MdPathLowering(MdFixtures.salesBindings(), MdFixtures.salesModel())

        fun scalar() = PathShape(freeDims = emptyList())

        fun pinned(
            attr: String,
            member: String,
        ) = Coordinate(attr.substringBefore('.'), attr, Selector.Pinned(MemberRef(member)))

        fun path(
            cubelet: String,
            coords: List<Coordinate>,
        ) = CanonicalPath(cubelet, coords, "net", AggKind.SUM)

        fun unparse(
            path: CanonicalPath,
            shape: PathShape,
        ): String {
            val subtree = lowering.lower(path, shape)
            val handle = IslandModelHandle(lowering.referencedTables(path, shape))
            return TranslatorFacade(handle, SqlDialect.POSTGRESQL).unparse(subtree, "md")
        }

        "a wide pinned read unparses to an aggregate SELECT over the registered fact table" {
            val sql = unparse(path("sales", listOf(pinned("Customer.name", "Kaufland"))), scalar())

            sql shouldContainIgnoringCase "select"
            sql shouldContainIgnoringCase "sum"
            sql shouldContain "f_sales"
            sql shouldContain "customer_name"
            sql shouldContain "Kaufland"
        }

        "a free star read unparses with a GROUP BY on the bound column" {
            val coord = Coordinate("Customer", "Customer.name", Selector.Star)
            val sql = unparse(path("sales", listOf(coord)), PathShape(listOf("Customer")))

            sql shouldContainIgnoringCase "group by"
            sql shouldContain "customer_name"
        }

        "a long-shape read unparses over the value column with a measure-code filter" {
            val sql = unparse(path("plan", listOf(pinned("Customer.name", "Kaufland"))), scalar())

            sql shouldContain "f_plan"
            sql shouldContain "amount"
            sql shouldContain "measure_code"
            sql shouldContain "NET"
        }

        "a hop read unparses with a JOIN to the backing table on the grain key" {
            val sql = unparse(path("sales", listOf(pinned("Customer.region", "West"))), scalar())

            sql shouldContain "f_sales"
            sql shouldContain "d_customer"
            sql shouldContainIgnoringCase "join"
            sql shouldContain "region"
        }
    })
