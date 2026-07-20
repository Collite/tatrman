// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.codec.sql

import org.tatrman.translate.v1.SqlDialect as SqlDialectProto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.plan.v1.MergeMode
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.SpreadStrategy
import org.tatrman.plan.v1.StoreNode
import org.tatrman.plan.v1.WriteMode

/**
 * DML-shape goldens for [StoreDmlUnparser] (MD writeback, S5-B). Pure string assembly over a canned
 * inner SELECT — the wire→DML executor for the write round-trip. One golden per journaling mode +
 * merge arm, plus the guard cases. The inner SELECT is fixed so the goldens read as templates.
 */
class StoreDmlUnparserSpec :
    StringSpec({

        val innerSql = "SELECT customer_name, month_num, 'NET' AS measure_code, amount, true AS is_current FROM _rhs"
        val columns = listOf("customer_name", "month_num", "measure_code", "amount", "is_current")
        val inner = RelToSqlUnparser.UnparsedSql(sql = innerSql, dynamicParamOrder = listOf(0))

        fun store(
            mode: WriteMode,
            keys: List<String>,
            measure: String = "",
            valid: String = "",
            merge: MergeMode = MergeMode.ASSIGN,
            spread: SpreadStrategy = SpreadStrategy.SPREAD_STRATEGY_UNSPECIFIED,
            spreadColumns: List<String> = emptyList(),
        ): StoreNode =
            StoreNode
                .newBuilder()
                .setTarget(
                    QualifiedName
                        .newBuilder()
                        .setSchemaCode(SchemaCode.DB)
                        .setNamespace("dbo")
                        .setName("f_plan"),
                ).setMode(mode)
                .addAllGrainKeyColumns(keys)
                .setMeasureColumn(measure)
                .setValidColumn(valid)
                .setMerge(merge)
                .setSpread(spread)
                .addAllSpreadColumns(spreadColumns)
                .build()

        fun assemble(s: StoreNode) = StoreDmlUnparser.assemble(s, inner, columns, SqlDialectProto.POSTGRESQL)

        "DIFF appends the RHS as a delta insert" {
            assemble(store(WriteMode.DIFF, keys = listOf("customer_name", "month_num", "measure_code"))).sql shouldBe
                "INSERT INTO f_plan (customer_name, month_num, measure_code, amount, is_current) $innerSql"
        }

        "OVERWRITE assign deletes matching keys then inserts (single data-modifying CTE)" {
            assemble(store(WriteMode.OVERWRITE, keys = listOf("customer_name", "month_num"))).sql shouldBe
                "WITH _src AS ($innerSql), " +
                "_del AS (DELETE FROM f_plan WHERE (customer_name, month_num) IN " +
                "(SELECT customer_name, month_num FROM _src)) " +
                "INSERT INTO f_plan (customer_name, month_num, measure_code, amount, is_current) " +
                "SELECT customer_name, month_num, measure_code, amount, is_current FROM _src"
        }

        "OVERWRITE accumulate (+=) is read-modify-write plus insert of fresh keys" {
            assemble(
                store(
                    WriteMode.OVERWRITE,
                    keys = listOf("customer_name", "month_num"),
                    measure = "amount",
                    merge = MergeMode.ACCUMULATE,
                ),
            ).sql shouldBe
                "WITH _src AS ($innerSql), " +
                "_upd AS (UPDATE f_plan SET amount = f_plan.amount + _src.amount FROM _src " +
                "WHERE f_plan.customer_name = _src.customer_name AND f_plan.month_num = _src.month_num " +
                "RETURNING f_plan.customer_name, f_plan.month_num) " +
                "INSERT INTO f_plan (customer_name, month_num, measure_code, amount, is_current) " +
                "SELECT customer_name, month_num, measure_code, amount, is_current FROM _src " +
                "WHERE (customer_name, month_num) NOT IN (SELECT customer_name, month_num FROM _upd)"
        }

        "INVALIDATE flips prior live rows then inserts the new live rows" {
            assemble(
                store(
                    WriteMode.INVALIDATE,
                    keys = listOf("customer_name", "month_num", "measure_code"),
                    valid = "is_current",
                ),
            ).sql shouldBe
                "WITH _src AS ($innerSql), " +
                "_inv AS (UPDATE f_plan SET is_current = false WHERE " +
                "(customer_name, month_num, measure_code) IN " +
                "(SELECT customer_name, month_num, measure_code FROM _src) AND is_current = true) " +
                "INSERT INTO f_plan (customer_name, month_num, measure_code, amount, is_current) " +
                "SELECT customer_name, month_num, measure_code, amount, is_current FROM _src"
        }

        "single-key membership omits the row-value parentheses" {
            assemble(store(WriteMode.OVERWRITE, keys = listOf("customer_name"))).sql shouldBe
                "WITH _src AS ($innerSql), " +
                "_del AS (DELETE FROM f_plan WHERE customer_name IN (SELECT customer_name FROM _src)) " +
                "INSERT INTO f_plan (customer_name, month_num, measure_code, amount, is_current) " +
                "SELECT customer_name, month_num, measure_code, amount, is_current FROM _src"
        }

        "the RHS parameter order is carried through unchanged" {
            assemble(store(WriteMode.DIFF, keys = listOf("customer_name"))).dynamicParamOrder shouldBe listOf(0)
        }

        "INVALIDATE without a valid_column is a hard error" {
            shouldThrow<IllegalArgumentException> {
                assemble(store(WriteMode.INVALIDATE, keys = listOf("customer_name")))
            }
        }

        "+= under invalidate is deferred (S5C)" {
            shouldThrow<UnsupportedOperationException> {
                assemble(
                    store(
                        WriteMode.INVALIDATE,
                        keys = listOf("customer_name"),
                        valid = "is_current",
                        merge = MergeMode.ACCUMULATE,
                    ),
                )
            }
        }

        "a non-Postgres dialect is refused (write DML is PG-only in S5-B)" {
            shouldThrow<UnsupportedOperationException> {
                StoreDmlUnparser.assemble(
                    store(WriteMode.DIFF, keys = listOf("customer_name")),
                    inner,
                    columns,
                    SqlDialectProto.MSSQL,
                )
            }
        }

        "a grain key the RHS does not project is a hard error" {
            shouldThrow<IllegalArgumentException> {
                assemble(store(WriteMode.OVERWRITE, keys = listOf("not_projected")))
            }
        }

        // ---- R21 spread (S5-B.2) -------------------------------------------------------------------

        "PROPORTIONAL spread scales existing rows by the coarse value over their current total" {
            assemble(
                store(
                    WriteMode.OVERWRITE,
                    keys = listOf("customer_name", "measure_code"),
                    measure = "amount",
                    spread = SpreadStrategy.SPREAD_PROPORTIONAL,
                    spreadColumns = listOf("month_num"),
                ),
            ).sql shouldBe
                "WITH _src AS ($innerSql) " +
                "UPDATE f_plan AS t " +
                "SET amount = _src.amount * t.amount / NULLIF(_tot.s, 0) " +
                "FROM _src JOIN (SELECT customer_name, measure_code, sum(amount) AS s FROM f_plan " +
                "GROUP BY customer_name, measure_code) _tot " +
                "ON _tot.customer_name = _src.customer_name AND _tot.measure_code = _src.measure_code " +
                "WHERE t.customer_name = _src.customer_name AND t.measure_code = _src.measure_code"
        }

        "PROPORTIONAL spread over a single pinned key omits the compound join parentheses" {
            assemble(
                store(
                    WriteMode.OVERWRITE,
                    keys = listOf("customer_name"),
                    measure = "amount",
                    spread = SpreadStrategy.SPREAD_PROPORTIONAL,
                    spreadColumns = listOf("month_num"),
                ),
            ).sql shouldBe
                "WITH _src AS ($innerSql) " +
                "UPDATE f_plan AS t " +
                "SET amount = _src.amount * t.amount / NULLIF(_tot.s, 0) " +
                "FROM _src JOIN (SELECT customer_name, sum(amount) AS s FROM f_plan " +
                "GROUP BY customer_name) _tot " +
                "ON _tot.customer_name = _src.customer_name " +
                "WHERE t.customer_name = _src.customer_name"
        }

        "PROPORTIONAL spread under invalidate journaling is a deferred follow-up" {
            shouldThrow<UnsupportedOperationException> {
                assemble(
                    store(
                        WriteMode.INVALIDATE,
                        keys = listOf("customer_name"),
                        measure = "amount",
                        valid = "is_current",
                        spread = SpreadStrategy.SPREAD_PROPORTIONAL,
                    ),
                )
            }
        }

        "PROPORTIONAL spread under `+=` is a deferred follow-up" {
            shouldThrow<UnsupportedOperationException> {
                assemble(
                    store(
                        WriteMode.OVERWRITE,
                        keys = listOf("customer_name"),
                        measure = "amount",
                        merge = MergeMode.ACCUMULATE,
                        spread = SpreadStrategy.SPREAD_PROPORTIONAL,
                    ),
                )
            }
        }
    })
