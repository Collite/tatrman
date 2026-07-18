// SPDX-License-Identifier: Apache-2.0
//
// RJ-P0 / Stage 0.1.2 — Postgres divergence runner (SPIKE, tag `Spike`, off by default).
//
// Drives the live ttrp-pg (Rancher-Desktop `postgres:16 @ localhost:55432`) over the divergence
// corpus (src/test/spike/corpus.yaml) and records, per (probe, type):
//   * pg_cast           — CAST(CAST(<probe> AS text) AS <T>)   → accept | error:<sqlstate> | null
//   * pg_input_is_valid — pg_input_is_valid(<probe>, '<T>')    → accept | reject | null
// into src/test/spike/results/pg.csv. Throwaway spike code, kept committed for reproducibility;
// NOT production and excluded from the default `test` task.
//
// Run:  ./gradlew :packages:kotlin:ttrp-conform:test --tests '*Spike*' -PincludeSpike=true
// Override the target with -Dspike.pg.url / -Dspike.pg.user / -Dspike.pg.password.
//
// The rows join to polars.csv on (type, probe_json). probe_json is encoded EXACTLY as Python
// json.dumps(raw, ensure_ascii=True) so the key is stable, not CSV-escape-sensitive. The corpus
// token "<<NULL>>" means a real SQL NULL (3VL pin, R-A3).

package org.tatrman.ttrp.conform.spike

import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Types

/** Tag that keeps live-DB spike specs out of the default test run (gated in build.gradle.kts). */
object Spike : Tag()

/** The corpus sentinel that both runners translate to a real NULL (3VL pin, R-A3). */
private const val NULL_TOKEN = "<<NULL>>"

/** JSON string encoding matching Python json.dumps(x, ensure_ascii=True) for our probe set. */
internal fun jsonEncode(s: String): String {
    val sb = StringBuilder(s.length + 2)
    sb.append('"')
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\b' -> sb.append("\\b")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\u000C' -> sb.append("\\f")
            else ->
                if (c.code < 0x20 || c.code > 0x7e) {
                    sb.append("\\u").append("%04x".format(c.code))
                } else {
                    sb.append(c)
                }
        }
    }
    sb.append('"')
    return sb.toString()
}

/** One CSV field, RFC-4180 quoted (matches Python csv.writer default dialect). */
private fun csv(field: String): String =
    if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else {
        field
    }

private data class TypeSpec(val name: String, val pgType: String, val probes: List<String>)

@Suppress("UNCHECKED_CAST")
private fun loadCorpus(path: File): List<TypeSpec> {
    val root = Yaml().load<Map<String, Any>>(path.inputStream())
    val types = root["types"] as Map<String, Map<String, Any>>
    return types.map { (name, spec) ->
        TypeSpec(
            name = name,
            pgType = spec["pg"] as String,
            probes = (spec["probes"] as List<Any?>).map { it?.toString() ?: NULL_TOKEN },
        )
    }
}

/** pg_cast verdict for one probe. Autocommit=true ⇒ a failed cast never poisons later probes. */
private fun pgCast(conn: Connection, pgType: String, rawProbe: String): Pair<String, String> {
    // CAST(CAST(? AS text) AS <T>): forces genuine text->T input-function semantics.
    conn.prepareStatement("SELECT CAST(CAST(? AS text) AS $pgType)").use { ps ->
        if (rawProbe == NULL_TOKEN) ps.setNull(1, Types.VARCHAR) else ps.setString(1, rawProbe)
        return try {
            ps.executeQuery().use { rs ->
                rs.next()
                val v = rs.getObject(1)
                if (v == null) "null" to "" else "accept" to v.toString()
            }
        } catch (e: SQLException) {
            "error:${e.sqlState ?: "?????"}" to (e.message ?: "").substringBefore('\n').trim()
        }
    }
}

/** pg_input_is_valid verdict for one probe (t=accept, f=reject, NULL=null-in). */
private fun pgInputIsValid(conn: Connection, pgType: String, rawProbe: String): String {
    conn.prepareStatement("SELECT pg_input_is_valid(CAST(? AS text), ?)").use { ps ->
        if (rawProbe == NULL_TOKEN) ps.setNull(1, Types.VARCHAR) else ps.setString(1, rawProbe)
        ps.setString(2, pgType)
        return try {
            ps.executeQuery().use { rs ->
                rs.next()
                when (rs.getObject(1) as Boolean?) {
                    true -> "accept"
                    false -> "reject"
                    null -> "null"
                }
            }
        } catch (e: SQLException) {
            "error:${e.sqlState ?: "?????"}"
        }
    }
}

class PgDivergenceSpike :
    StringSpec({
        tags(Spike)

        val corpusPath = File(System.getProperty("spike.corpus", "src/test/spike/corpus.yaml"))
        val outPath = File(System.getProperty("spike.pg.out", "src/test/spike/results/pg.csv"))
        val url = System.getProperty("spike.pg.url", "jdbc:postgresql://localhost:55432/postgres")
        val user = System.getProperty("spike.pg.user", "postgres")
        val password = System.getProperty("spike.pg.password", "ttrp")

        "PG divergence sweep -> results/pg.csv" {
            corpusPath.exists() shouldBe true
            val corpus = loadCorpus(corpusPath)
            outPath.parentFile.mkdirs()

            var rows = 0
            var pgVersion = "unknown"
            DriverManager.getConnection(url, user, password).use { conn ->
                conn.autoCommit = true
                conn.createStatement().use { st ->
                    st.executeQuery("SHOW server_version").use { rs ->
                        rs.next()
                        pgVersion = rs.getString(1)
                    }
                }
                outPath.printWriter(Charsets.UTF_8).use { out ->
                    out.println("type,probe_json,pg_cast,pg_input_is_valid,pg_cast_value")
                    for (t in corpus) {
                        for (raw in t.probes) {
                            val (castVerdict, castValue) = pgCast(conn, t.pgType, raw)
                            val isValid = pgInputIsValid(conn, t.pgType, raw)
                            out.println(
                                listOf(
                                    t.name,
                                    jsonEncode(raw),
                                    castVerdict,
                                    isValid,
                                    castValue,
                                ).joinToString(",") { csv(it) },
                            )
                            rows++
                        }
                    }
                }
            }
            println("[spike] PG server_version=$pgVersion; wrote $rows rows -> ${outPath.absolutePath}")
            rows shouldBe corpus.sumOf { it.probes.size }
        }
    })
