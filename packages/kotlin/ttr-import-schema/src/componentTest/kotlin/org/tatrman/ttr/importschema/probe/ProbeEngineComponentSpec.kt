// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.probe

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.importschema.conventions.ProbeConfig
import org.tatrman.ttr.importschema.introspect.Dialect
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager

/**
 * SV-P4·S3·T5 — the probe engine against a real Postgres: orphan / null / uniqueness evidence,
 * and the Q-2 determinism guarantee (re-probe an unchanged DB ⇒ identical numbers), plus the
 * keyed-sampling path (threshold forced to 0) proven deterministic.
 */
class ProbeEngineComponentSpec :
    StringSpec({
        var pg: PostgreSQLContainer? = null
        var conn: Connection? = null

        val childPks = mapOf("public.child" to listOf("id"))
        val candidate =
            ProbeCandidate(
                child = ColumnRef("public", "child", listOf("parent_id")),
                parent = ColumnRef("public", "parent", listOf("id")),
                origin = ProbeOrigin.DECLARED,
            )

        beforeSpec {
            val container = PostgreSQLContainer("postgres:16-alpine")
            container.start()
            pg = container
            val c = DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
            conn = c
            c.createStatement().use { st ->
                st.execute("CREATE TABLE parent (id integer PRIMARY KEY)")
                st.execute("INSERT INTO parent (id) VALUES (1),(2),(3)")
                st.execute("CREATE TABLE child (id integer PRIMARY KEY, parent_id integer, note varchar)")
                // 1,2 match; 99 is an orphan; NULL is a null-rate row.
                st.execute("INSERT INTO child (id, parent_id) VALUES (1,1),(2,2),(3,99),(4,NULL)")
            }
        }

        afterSpec {
            conn?.close()
            pg?.stop()
        }

        "full-scan probe finds the orphan, the null, and unique non-null values" {
            val engine = ProbeEngine(Dialect.POSTGRESQL, ProbeConfig(), childPks)
            val r = engine.run(conn!!, listOf(candidate)).single()
            r.provenance shouldBe Provenance.FULL
            r.orphanCount shouldBe 1
            r.hasOrphans.shouldBeTrue()
            r.nullCount shouldBe 1
            r.distinctChildValues shouldBe 3 // {1,2,99}
            r.childValuesUnique.shouldBeTrue()
        }

        "re-probing an unchanged DB yields identical numbers (GI-2 / Q-2)" {
            val engine = ProbeEngine(Dialect.POSTGRESQL, ProbeConfig(), childPks)
            val a = engine.run(conn!!, listOf(candidate)).single()
            val b = engine.run(conn!!, listOf(candidate)).single()
            a shouldBe b
        }

        "keyed sampling (threshold 0) is taken and is deterministic across re-runs" {
            val sampledConfig = ProbeConfig(fullScanThresholdRows = 0)
            val engine = ProbeEngine(Dialect.POSTGRESQL, sampledConfig, childPks)
            val a = engine.run(conn!!, listOf(candidate)).single()
            val b = engine.run(conn!!, listOf(candidate)).single()
            a.provenance shouldBe Provenance.SAMPLED
            a shouldBe b
        }

        "a clean declared FK reports no orphans" {
            // parent_id 99 is the only orphan; a candidate whose child has no orphans:
            conn!!.createStatement().use { st ->
                st.execute("CREATE TABLE child_clean (id integer PRIMARY KEY, parent_id integer)")
                st.execute("INSERT INTO child_clean (id, parent_id) VALUES (1,1),(2,2),(3,3)")
            }
            val cleanCandidate =
                ProbeCandidate(
                    ColumnRef("public", "child_clean", listOf("parent_id")),
                    ColumnRef("public", "parent", listOf("id")),
                    ProbeOrigin.DECLARED,
                )
            val engine = ProbeEngine(Dialect.POSTGRESQL, ProbeConfig(), mapOf("public.child_clean" to listOf("id")))
            val r = engine.run(conn!!, listOf(cleanCandidate)).single()
            r.orphanCount shouldBe 0
            r.hasOrphans.shouldBeFalse()
        }
    })
