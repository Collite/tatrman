// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.codec.sql

import org.tatrman.translate.v1.SqlDialect as SqlDialectProto
import org.tatrman.plan.v1.MergeMode
import org.tatrman.plan.v1.StoreNode
import org.tatrman.plan.v1.WriteMode

/**
 * Assemble SQL DML for a [StoreNode] (MD writeback, plan S5-B) around its already-unparsed RHS SELECT.
 *
 * A StoreNode is a write plan with no query-RelNode form, so it can't go through Calcite's
 * `RelToSqlConverter` (which only emits SELECT, and can't emit MERGE at all). The caller
 * ([org.tatrman.translator.orchestrator.Translator] `unparseStoreDml`) decodes only the child
 * (`store.input`, the RHS read plan) through the SAME read path — decode → optimize →
 * [RelToSqlUnparser] — and hands the resulting [RelToSqlUnparser.UnparsedSql] plus its output column
 * names here. Those columns (the decoded RelNode's row type) ARE the physical target columns, in order:
 * the producer (`MdWriteLowering`) projects the write-shaped row (grain keys + measure [+ long-shape
 * code constant] [+ valid flag]) named to match the target table. This object is pure string assembly —
 * no Calcite, no framework — so the DML shapes are unit-testable in isolation.
 *
 * The inner SELECT is evaluated **once** via a data-modifying CTE (`WITH _src AS (<select>) …`), so a
 * parametrised RHS binds its `?`s once and in one order (carried through unchanged). Each mode is a
 * single statement:
 *   - DIFF       → `INSERT INTO t (cols) <select>` — append a delta row; the read view SUMs per key.
 *   - OVERWRITE  → assign: delete the matching grain keys, then insert (current-state replace);
 *                  accumulate (`+=`): read-modify-write (UPDATE existing + insert the fresh keys).
 *   - INVALIDATE → flip prior live rows (`valid_column` = false) for the written keys, then insert the
 *                  new rows live (assign only; `+=` under invalidate is deferred to S5C).
 *
 * **Postgres only.** The data-modifying-CTE / row-value-`IN` shapes are Postgres-specific (MSSQL has no
 * DELETE-in-CTE); this is the S5-B round-trip dialect. Other dialects throw — write DML for
 * MSSQL/MySQL/DuckDB is a recorded follow-up, not silently mis-emitted.
 */
object StoreDmlUnparser {
    fun assemble(
        store: StoreNode,
        innerSelect: RelToSqlUnparser.UnparsedSql,
        columns: List<String>,
        dialect: SqlDialectProto,
    ): RelToSqlUnparser.UnparsedSql {
        if (dialect != SqlDialectProto.POSTGRESQL) {
            throw UnsupportedOperationException(
                "StoreNode DML is Postgres-only in S5-B (dialect=$dialect); other engines are a follow-up",
            )
        }
        val target = store.target.name
        val keys = store.grainKeyColumnsList.toList()
        require(keys.isNotEmpty()) { "StoreNode.grain_key_columns must be non-empty (the match/conflict key)" }
        require(columns.containsAll(keys)) {
            "grain_key_columns $keys must all be projected by the RHS SELECT (columns=$columns)"
        }
        val merge = if (store.merge == MergeMode.ACCUMULATE) MergeMode.ACCUMULATE else MergeMode.ASSIGN
        val select = innerSelect.sql

        val dml =
            when (store.mode) {
                WriteMode.DIFF -> insertSelect(target, columns, select)
                WriteMode.OVERWRITE ->
                    when (merge) {
                        MergeMode.ACCUMULATE -> overwriteAccumulate(store, target, columns, keys, select)
                        else -> overwriteAssign(target, columns, keys, select)
                    }
                WriteMode.INVALIDATE -> {
                    if (merge == MergeMode.ACCUMULATE) {
                        throw UnsupportedOperationException("`+=` under invalidate journaling is deferred (S5C)")
                    }
                    invalidateAssign(store, target, columns, keys, select)
                }
                else -> throw UnsupportedOperationException("StoreNode.mode is unset (WRITE_MODE_UNSPECIFIED)")
            }
        return RelToSqlUnparser.UnparsedSql(sql = dml, dynamicParamOrder = innerSelect.dynamicParamOrder)
    }

    /** DIFF: `INSERT INTO t (cols) <select>` — a pure append; the read view SUMs per grain key. */
    private fun insertSelect(
        target: String,
        columns: List<String>,
        select: String,
    ): String = "INSERT INTO $target (${columns.joinToString(", ")}) $select"

    /**
     * OVERWRITE assign: evaluate the RHS once, delete the rows it targets by grain key, then insert
     * the fresh rows — a constraint-free current-state replace (no unique index required on the target).
     */
    private fun overwriteAssign(
        target: String,
        columns: List<String>,
        keys: List<String>,
        select: String,
    ): String {
        val cols = columns.joinToString(", ")
        return "WITH _src AS ($select), " +
            "_del AS (DELETE FROM $target WHERE ${rowValueIn(keys)}) " +
            "INSERT INTO $target ($cols) SELECT $cols FROM _src"
    }

    /**
     * OVERWRITE accumulate (`+=`): add the delta to existing cells (read-modify-write) and insert the
     * grain keys that don't exist yet. Needs the measure column to know which cell accumulates.
     */
    private fun overwriteAccumulate(
        store: StoreNode,
        target: String,
        columns: List<String>,
        keys: List<String>,
        select: String,
    ): String {
        val measure = store.measureColumn
        require(measure.isNotBlank()) { "OVERWRITE `+=` needs StoreNode.measure_column" }
        val cols = columns.joinToString(", ")
        val keyEq = keys.joinToString(" AND ") { "$target.$it = _src.$it" }
        return "WITH _src AS ($select), " +
            "_upd AS (UPDATE $target SET $measure = $target.$measure + _src.$measure FROM _src " +
            "WHERE $keyEq RETURNING ${keys.joinToString(", ") { "$target.$it" }}) " +
            "INSERT INTO $target ($cols) SELECT $cols FROM _src WHERE ${rowValueIn(keys, negate = true, from = "_upd")}"
    }

    /**
     * INVALIDATE assign: flip prior live rows to superseded (`valid_column` = false) for the written
     * keys, then insert the new rows (the RHS projects the live-flag = true). Temporal / SCD-2 style.
     */
    private fun invalidateAssign(
        store: StoreNode,
        target: String,
        columns: List<String>,
        keys: List<String>,
        select: String,
    ): String {
        val valid = store.validColumn
        require(valid.isNotBlank()) { "INVALIDATE journaling needs StoreNode.valid_column" }
        val cols = columns.joinToString(", ")
        return "WITH _src AS ($select), " +
            "_inv AS (UPDATE $target SET $valid = false WHERE ${rowValueIn(keys)} AND $valid = true) " +
            "INSERT INTO $target ($cols) SELECT $cols FROM _src"
    }

    /** `(k1, k2) IN (SELECT k1, k2 FROM <from>)` — row-value membership (Postgres). */
    private fun rowValueIn(
        keys: List<String>,
        negate: Boolean = false,
        from: String = "_src",
    ): String {
        val tuple = keys.joinToString(", ")
        val op = if (negate) "NOT IN" else "IN"
        val lhs = if (keys.size == 1) tuple else "($tuple)"
        return "$lhs $op (SELECT $tuple FROM $from)"
    }
}
