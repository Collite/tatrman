package org.tatrman.ttrp.emit.sql

import io.kotest.core.spec.style.FunSpec
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.emit.core.SsaNames
import org.tatrman.ttrp.emit.sql.EmitFixtures.agg
import org.tatrman.ttrp.emit.sql.EmitFixtures.base
import org.tatrman.ttrp.emit.sql.EmitFixtures.col
import org.tatrman.ttrp.emit.sql.EmitFixtures.cols
import org.tatrman.ttrp.emit.sql.EmitFixtures.cteInput
import org.tatrman.ttrp.emit.sql.EmitFixtures.filter
import org.tatrman.ttrp.emit.sql.EmitFixtures.fn
import org.tatrman.ttrp.emit.sql.EmitFixtures.isNotNull
import org.tatrman.ttrp.emit.sql.EmitFixtures.num
import org.tatrman.ttrp.emit.sql.EmitFixtures.pgPlanner
import org.tatrman.ttrp.emit.sql.EmitFixtures.project
import org.tatrman.ttrp.emit.sql.EmitFixtures.sort
import org.tatrman.ttrp.emit.sql.EmitFixtures.str
import org.tatrman.ttrp.graph.model.Aggregation
import org.tatrman.ttrp.graph.model.Node

/**
 * Golden SQL corpus (E-b CTE-per-node, Q9-3 NULLS LAST). Each case builds an island from
 * real Phase-2 graph nodes + the TTR-P expression IR, assigns CTE names via [SsaNames], and
 * drives [CtePlanner] → ttr-translator → Postgres SQL. Regenerate: `-DupdateGolden=true`.
 */
class SqlGoldenTest :
    FunSpec({
        val accountsCols = cols("account_id" to "int", "branch_code" to "text", "region" to "text", "status" to "text")
        val sumsCols = cols("region" to "text", "total" to "float", "avg_amt" to "float")

        /**
         * Assemble an [EmitNode] chain: [ordered] transform nodes get CTE names via [SsaNames];
         * each node's inputs and output columns are supplied by [spec] (keyed by node id).
         */
        fun plan(
            ordered: List<Node>,
            spec: Map<String, Pair<List<EmitInput>, List<EmitColumn>>>,
        ): List<EmitNode> {
            val names = SsaNames.assign(ordered)
            return ordered.map { n ->
                val (inputs, out) = spec.getValue(n.id)
                EmitNode(names.getValue(n.id), n, inputs, out)
            }
        }

        fun emit(plan: List<EmitNode>) = pgPlanner().emit(plan, islandName = "test")

        test("trivial_island_flat_select — single Filter over a Load emits a flat SELECT (no WITH)") {
            val f = filter("f1", "accounts", fn("op.eq", col("status"), str("ACTIVE")))
            val p = plan(listOf(f), mapOf("f1" to (listOf(base("erp", "accounts", accountsCols)) to accountsCols)))
            GoldenSupport.assertMatchesGolden(emit(p), "sql/postgres/trivial_island_flat_select.sql")
        }

        test("cte_chain_ssa_names — Load→Filter→Project emits WITH <ssa> AS (…) … SELECT") {
            val f =
                filter(
                    "f1",
                    "cleaned",
                    fn("op.and", isNotNull(col("region")), fn("op.eq", col("status"), str("ACTIVE"))),
                )
            val p = project("p1", "kept", listOf(col("account_id"), col("region")))
            val plan =
                plan(
                    listOf(f, p),
                    mapOf(
                        "f1" to (listOf(base("erp", "accounts", accountsCols)) to accountsCols),
                        "p1" to
                            (listOf(cteInput("f1", accountsCols)) to cols("account_id" to "int", "region" to "text")),
                    ),
                )
            GoldenSupport.assertMatchesGolden(emit(plan), "sql/postgres/cte_chain_ssa_names.sql")
        }

        test("ssa_reassignment_mangling — reassigned `accounts` → CTEs accounts, accounts_2") {
            val f1 = filter("f1", "accounts", fn("op.gt", col("account_id"), num("0")))
            val f2 = filter("f2", "accounts#2", fn("op.eq", col("status"), str("ACTIVE")))
            val p = project("p1", "kept", listOf(col("account_id"), col("region")))
            val plan =
                plan(
                    listOf(f1, f2, p),
                    mapOf(
                        "f1" to (listOf(base("erp", "accounts", accountsCols)) to accountsCols),
                        "f2" to (listOf(cteInput("f1", accountsCols)) to accountsCols),
                        "p1" to
                            (listOf(cteInput("f2", accountsCols)) to cols("account_id" to "int", "region" to "text")),
                    ),
                )
            GoldenSupport.assertMatchesGolden(emit(plan), "sql/postgres/ssa_reassignment_mangling.sql")
        }

        test("sort_nulls_last — terminal Sort emits explicit NULLS LAST (asc + desc)") {
            val s = sort("s1", "ranked", listOf("total desc", "region"))
            val p = plan(listOf(s), mapOf("s1" to (listOf(base("agg", "sums", sumsCols)) to sumsCols)))
            GoldenSupport.assertMatchesGolden(emit(p), "sql/postgres/sort_nulls_last.sql")
        }

        test("aggregate_group_by — GROUP BY with sum/avg (distinct arm on count)") {
            val salesCols = cols("region" to "text", "amount" to "float", "customer_id" to "int")
            val a =
                EmitFixtures.aggregate(
                    "a1",
                    "sums",
                    groupBy = listOf("region"),
                    aggregations =
                        listOf(
                            Aggregation("total", agg("agg.sum", col("amount"))),
                            Aggregation("avg_amt", agg("agg.avg", col("amount"))),
                            Aggregation("n_cust", agg("agg.count", col("customer_id"), distinct = true)),
                        ),
                )
            val p = plan(listOf(a), mapOf("a1" to (listOf(base("erp", "sales", salesCols)) to sumsCols)))
            GoldenSupport.assertMatchesGolden(emit(p), "sql/postgres/aggregate_group_by.sql")
        }

        test("aggregate_having — Aggregate CTE + post-Filter (HAVING already split by T8)") {
            val salesCols = cols("region" to "text", "amount" to "float")
            val a =
                EmitFixtures.aggregate(
                    "a1",
                    "sums",
                    groupBy = listOf("region"),
                    aggregations = listOf(Aggregation("total", agg("agg.sum", col("amount")))),
                )
            val f = filter("f1", "big", fn("op.gt", col("total"), num("100000")))
            val plan =
                plan(
                    listOf(a, f),
                    mapOf(
                        "a1" to
                            (listOf(base("erp", "sales", salesCols)) to cols("region" to "text", "total" to "float")),
                        "f1" to
                            (
                                listOf(cteInput("a1", cols("region" to "text", "total" to "float"))) to
                                    cols("region" to "text", "total" to "float")
                            ),
                    ),
                )
            GoldenSupport.assertMatchesGolden(emit(plan), "sql/postgres/aggregate_having.sql")
        }

        test("union_all — two-arity Union emits UNION ALL over both inputs") {
            val u =
                org.tatrman.ttrp.graph.model
                    .Union("u1", "combined", EmitFixtures.loc, arity = 2)
            val tCols = cols("id" to "int", "v" to "text")
            val plan =
                plan(
                    listOf(u),
                    mapOf("u1" to (listOf(base("erp", "t1", tCols), base("erp", "t2", tCols)) to tCols)),
                )
            GoldenSupport.assertMatchesGolden(emit(plan), "sql/postgres/union_all.sql")
        }

        // NOTE: SQL Join emit (even INNER) is DEFERRED — the translator's join-condition column
        // resolution needs input-qualified refs that the plan.v1 ColumnRef → RelBuilder.field path
        // does not yet thread; and SEMI/ANTI have no plan.v1 wire representation at all. No v1 hero
        // SQL island contains a join (the hero join is Polars-side), so this is off the A4 path.
        // Recorded in progress-phase-03.md; Polars joins are covered in Stage 3.2.

        test("limit_over_sort — Sort CTE + terminal Limit (ordered input enforced upstream, S15)") {
            val s = sort("s1", "ranked", listOf("total desc"))
            val l =
                org.tatrman.ttrp.graph.model
                    .Limit("l1", "top", EmitFixtures.loc, count = 10)
            val plan =
                plan(
                    listOf(s, l),
                    mapOf(
                        "s1" to (listOf(base("agg", "sums", sumsCols)) to sumsCols),
                        "l1" to (listOf(cteInput("s1", sumsCols)) to sumsCols),
                    ),
                )
            GoldenSupport.assertMatchesGolden(emit(plan), "sql/postgres/limit_over_sort.sql")
        }
    })
