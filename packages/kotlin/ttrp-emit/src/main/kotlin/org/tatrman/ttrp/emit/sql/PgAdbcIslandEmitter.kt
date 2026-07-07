package org.tatrman.ttrp.emit.sql

/**
 * Emits the runtime script for a **decomposed Postgres island** as a Python
 * `adbc_driver_postgresql` program (S3.5 T3.5.4). A PG island that must both ingest a CSV and
 * export Arrow can't be a plain `psql -f` — Arrow export needs the query and its session-local
 * temp tables on a **single ADBC connection** (`fetch_arrow_table`). So the invocation binding for
 * such an island is `python3` (run.sh already dispatches python3 islands), and the compute still
 * runs server-side in Postgres — a genuine second engine for A4.
 *
 * Structure:
 *  1. one connection from `TTR_CONN_<ENGINE>`;
 *  2. [SqlTemp]s — `CREATE TEMP TABLE <t> AS <sql>` (e.g. the same-engine `accounts` handoff:
 *     acc_prep's fragment SQL inlined, no cross-session staging);
 *  3. [CsvTemp]s — `CREATE TEMP TABLE` + typed `pyarrow.csv.read_csv` + `adbc_ingest` (COPY);
 *  4. [Output]s — one `execute` + `fetch_arrow_table` + Arrow-IPC write per container OUT port
 *     (`out/…` display, `staging/…` store — the same sinks the Polars variant writes).
 *
 * SQL is embedded in Python triple-quoted strings (the SQL uses `"` for identifiers and `'` for
 * literals — never `\"\"\"`), so no escaping is needed.
 */
class PgAdbcIslandEmitter {
    /** A temp table materialized from a SQL query (`CREATE TEMP TABLE <table> AS <sql>`). */
    data class SqlTemp(
        val table: String,
        val sql: String,
    )

    /** A temp table populated from a CSV via typed read + `adbc_ingest` (COPY). */
    data class CsvTemp(
        val table: String,
        val csvPath: String,
        val columns: List<PgColumn>,
    )

    /** One column of a CSV temp: its Postgres DDL type + the pyarrow read type. */
    data class PgColumn(
        val name: String,
        val ddlType: String,
        val arrowType: String,
    )

    /** One island output: the terminal SQL and the Arrow sink path (`out/…` / `staging/…`). */
    data class Output(
        val sql: String,
        val sinkPath: String,
    )

    fun emit(
        connEnv: String,
        sqlTemps: List<SqlTemp>,
        csvTemps: List<CsvTemp>,
        outputs: List<Output>,
    ): String =
        buildString {
            appendLine("import os")
            appendLine("import adbc_driver_postgresql.dbapi as _dbapi")
            appendLine("import pyarrow as _pa")
            appendLine("import pyarrow.csv as _pacsv")
            appendLine("import pyarrow.ipc as _paipc")
            appendLine()
            appendLine("def _write_ipc(_table, _path):")
            appendLine("    with _pa.OSFile(_path, \"wb\") as _f:")
            appendLine("        with _paipc.new_file(_f, _table.schema) as _w:")
            appendLine("            _w.write_table(_table)")
            appendLine()
            appendLine("_conn = _dbapi.connect(os.environ[${pyStr(connEnv)}])")
            appendLine("try:")
            appendLine("    _cur = _conn.cursor()")
            sqlTemps.forEach { t ->
                appendLine("    # temp table: ${t.table} (from SQL)")
                appendLine("    _cur.execute(${pySql("CREATE TEMP TABLE \"${t.table}\" AS\n${t.sql}")})")
            }
            csvTemps.forEach { t ->
                val ddl = t.columns.joinToString(", ") { "\"${it.name}\" ${it.ddlType}" }
                val types = t.columns.joinToString(", ") { "${pyStr(it.name)}: ${it.arrowType}" }
                appendLine("    # temp table: ${t.table} (from CSV)")
                appendLine("    _cur.execute(${pySql("CREATE TEMP TABLE \"${t.table}\" ($ddl)")})")
                appendLine(
                    "    _t_${t.table} = _pacsv.read_csv(${pyStr(t.csvPath)}, " +
                        "convert_options=_pacsv.ConvertOptions(column_types={$types}))",
                )
                appendLine("    _cur.adbc_ingest(${pyStr(t.table)}, _t_${t.table}, mode=\"append\")")
            }
            outputs.forEachIndexed { i, o ->
                appendLine("    # output → ${o.sinkPath}")
                appendLine("    _cur.execute(${pySql(o.sql)})")
                appendLine("    _write_ipc(_cur.fetch_arrow_table(), ${pyStr(o.sinkPath)})")
            }
            appendLine("    _conn.commit()")
            appendLine("finally:")
            appendLine("    _conn.close()")
        }

    private fun pyStr(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** SQL in a Python triple-quoted string (SQL never contains `\"\"\"`). */
    private fun pySql(sql: String): String = "\"\"\"\n${sql.trimEnd()}\n\"\"\""

    companion object {
        /** db-schema type spelling → (Postgres DDL type, pyarrow read type) for a CSV temp column. */
        fun pgColumn(
            name: String,
            spelling: String,
        ): PgColumn =
            when (spelling.substringBefore('(').trim().lowercase()) {
                "int", "integer", "bigint", "smallint", "tinyint", "long" ->
                    PgColumn(name, "BIGINT", "_pa.int64()")
                "float", "double", "real", "number" -> PgColumn(name, "DOUBLE PRECISION", "_pa.float64()")
                "decimal", "numeric", "money" -> PgColumn(name, "NUMERIC", "_pa.decimal128(19, 2)")
                "bool", "boolean" -> PgColumn(name, "BOOLEAN", "_pa.bool_()")
                "date" -> PgColumn(name, "DATE", "_pa.date32()")
                "time", "timestamp", "datetime" -> PgColumn(name, "TIMESTAMPTZ", "_pa.timestamp(\"us\", \"UTC\")")
                else -> PgColumn(name, "TEXT", "_pa.string()")
            }
    }
}
