package org.tatrman.ttrp.emit.polars

import io.kotest.core.spec.style.FunSpec
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.emit.core.SsaNames
import org.tatrman.ttrp.emit.sql.EmitFixtures.agg
import org.tatrman.ttrp.emit.sql.EmitFixtures.col
import org.tatrman.ttrp.emit.sql.EmitFixtures.fn
import org.tatrman.ttrp.emit.sql.EmitFixtures.isNotNull
import org.tatrman.ttrp.emit.sql.EmitFixtures.num
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Aggregation
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.Sort

/**
 * Golden Polars corpus (E-c straight-line, Q9-3 nulls_last, prelude minimality). Islands are
 * built from real graph nodes + the TTR-P expression IR; variable names via [SsaNames] (same
 * naming story as the SQL CTE names). Regenerate: `-DupdateGolden=true`.
 */
class PolarsGoldenTest :
    FunSpec({
        val loc = SourceLocation.UNKNOWN
        val emitter = PolarsIslandEmitter()

        fun load(
            id: String,
            label: String,
            source: String,
        ) = Load(id, label, loc, source)

        data class Spec(
            val inputs: List<String> = emptyList(),
            val source: PolarsSource? = null,
            val sink: String? = null,
        )

        fun steps(
            island: String,
            ordered: List<Node>,
            spec: Map<String, Spec>,
        ): String {
            val names = SsaNames.assign(ordered)
            val list =
                ordered.map { n ->
                    val s = spec[n.id] ?: Spec()
                    PolarsStep(
                        varName = names.getValue(n.id),
                        node = n,
                        inputVars = s.inputs.map { names.getValue(it) },
                        source = s.source,
                        sinkPath = s.sink,
                    )
                }
            return emitter.emit(island, list).text
        }

        test("straight_line_ssa — Load(csv)→Filter→Project, one statement per node") {
            val salesSchema = listOf("customer" to "string", "region" to "string", "amount" to "float")
            val l = load("l1", "sales", "files.sales")
            val f =
                Filter(
                    "f1",
                    "sales#2",
                    loc,
                    fn("op.and", fn("op.gt", col("amount"), num("0")), isNotNull(col("customer"))),
                )
            val p = Project("p1", "kept", loc, listOf(col("region"), col("amount")))
            val out =
                steps(
                    "prep",
                    listOf(l, f, p),
                    mapOf(
                        "l1" to Spec(source = PolarsSource.Csv("/data/files/sales.csv", salesSchema)),
                        "f1" to Spec(inputs = listOf("l1")),
                        "p1" to Spec(inputs = listOf("f1")),
                    ),
                )
            GoldenSupport.assertMatchesGolden(out, "polars/straight_line_ssa.py")
        }

        test("no_prelude_when_unneeded — no decimal/datetime cast ⇒ zero prelude lines") {
            val l = load("l1", "t", "files.t")
            val f = Filter("f1", "kept", loc, fn("op.gt", col("x"), num("0")))
            val out =
                steps(
                    "plain",
                    listOf(l, f),
                    mapOf(
                        "l1" to Spec(source = PolarsSource.Csv("/data/t.csv", listOf("x" to "int"))),
                        "f1" to Spec(inputs = listOf("l1")),
                    ),
                )
            GoldenSupport.assertMatchesGolden(out, "polars/no_prelude_when_unneeded.py")
        }

        test("prelude_decimal_only — a Cast to decimal ⇒ only the decimal helper") {
            val l = load("l1", "t", "files.t")
            val p = Project("p1", "cast", loc, listOf(Cast(col("amount"), TtrpType.Decimal(19, 2), loc)))
            val out =
                steps(
                    "dec",
                    listOf(l, p),
                    mapOf(
                        "l1" to Spec(source = PolarsSource.Csv("/data/t.csv", listOf("amount" to "float"))),
                        "p1" to Spec(inputs = listOf("l1")),
                    ),
                )
            GoldenSupport.assertMatchesGolden(out, "polars/prelude_decimal_only.py")
        }

        test("prelude_datetime_utc_us — a Cast to datetime ⇒ only the UTC-µs helper") {
            val l = load("l1", "t", "files.t")
            val p = Project("p1", "cast", loc, listOf(Cast(col("ts"), TtrpType.Datetime, loc)))
            val out =
                steps(
                    "dt",
                    listOf(l, p),
                    mapOf(
                        "l1" to Spec(source = PolarsSource.Csv("/data/t.csv", listOf("ts" to "string"))),
                        "p1" to Spec(inputs = listOf("l1")),
                    ),
                )
            GoldenSupport.assertMatchesGolden(out, "polars/prelude_datetime_utc_us.py")
        }

        test("sort_nulls_last — Sort emits nulls_last=True on every key") {
            val l = load("l1", "sums", "files.sums")
            val s = Sort("s1", "ranked", loc, listOf("total desc", "region"))
            val out =
                steps(
                    "srt",
                    listOf(l, s),
                    mapOf(
                        "l1" to Spec(source = PolarsSource.Staged("sums")),
                        "s1" to Spec(inputs = listOf("l1")),
                    ),
                )
            GoldenSupport.assertMatchesGolden(out, "polars/sort_nulls_last.py")
        }

        test("join_semi_anti — inner / semi / anti joins render natively") {
            val a = load("a1", "accounts", "files.accounts")
            val s = load("s1", "sales", "files.sales")
            val on = fn("op.eq", col("account_id", port = "left"), col("customer", port = "right"))
            val inner = Join("j1", "j", loc, JoinType.INNER, on = on)
            val semi = Join("j2", "js", loc, JoinType.SEMI, on = on)
            val anti = Join("j3", "ja", loc, JoinType.ANTI, on = on)
            val out =
                steps(
                    "joins",
                    listOf(a, s, inner, semi, anti),
                    mapOf(
                        "a1" to Spec(source = PolarsSource.Staged("accounts")),
                        "s1" to Spec(source = PolarsSource.Staged("sales")),
                        "j1" to Spec(inputs = listOf("a1", "s1")),
                        "j2" to Spec(inputs = listOf("a1", "s1")),
                        "j3" to Spec(inputs = listOf("a1", "s1")),
                    ),
                )
            GoldenSupport.assertMatchesGolden(out, "polars/join_semi_anti.py")
        }

        test("aggregate_group_by — group_by().agg() with sum/avg and a distinct count") {
            val l = load("l1", "sales", "files.sales")
            val a =
                Aggregate(
                    "a1",
                    "sums",
                    loc,
                    groupBy = listOf("region"),
                    aggregations =
                        listOf(
                            Aggregation("total", agg("agg.sum", col("amount"))),
                            Aggregation("avg_amt", agg("agg.avg", col("amount"))),
                            Aggregation("n_cust", agg("agg.count", col("customer"), distinct = true)),
                        ),
                )
            val out =
                steps(
                    "grp",
                    listOf(l, a),
                    mapOf(
                        "l1" to Spec(source = PolarsSource.Staged("sales")),
                        "a1" to Spec(inputs = listOf("l1")),
                    ),
                )
            GoldenSupport.assertMatchesGolden(out, "polars/aggregate_group_by.py")
        }
    })
