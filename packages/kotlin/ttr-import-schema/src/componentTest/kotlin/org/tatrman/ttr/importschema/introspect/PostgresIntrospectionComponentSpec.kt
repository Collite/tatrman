// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.introspect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.importschema.ImportSchemaRunner
import org.tatrman.ttr.parser.loader.TtrLoader
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager

/**
 * SV-P4·S3·T2 — proves the JDBC introspection edge against a REAL Postgres (native multi-arch;
 * no CI gate needed). The deterministic core is unit-tested with a hand-built catalog
 * (ImportSchemaDbMirrorSpec); this asserts DatabaseMetaData → IntrospectedCatalog captures
 * tables / columns / PKs / declared FKs, and that the emitted db model parses clean.
 */
class PostgresIntrospectionComponentSpec :
    StringSpec({
        var pg: PostgreSQLContainer? = null
        var conn: Connection? = null

        beforeSpec {
            val container = PostgreSQLContainer("postgres:16-alpine")
            container.start()
            pg = container
            val c = DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
            conn = c
            c.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE odberatel (
                        id_odberatel integer PRIMARY KEY,
                        nazev        varchar(200) NOT NULL,
                        ico          varchar(20)
                    );
                    """.trimIndent(),
                )
                st.execute(
                    """
                    CREATE TABLE faktura (
                        id_faktura   integer PRIMARY KEY,
                        cislo        varchar(50) NOT NULL,
                        id_odberatel integer NOT NULL REFERENCES odberatel(id_odberatel),
                        castka       numeric(18,2) NOT NULL
                    );
                    """.trimIndent(),
                )
            }
        }

        afterSpec {
            conn?.close()
            pg?.stop()
        }

        "introspection captures tables (sorted), columns, PKs and declared FKs" {
            val catalog = MetaDataReader(Dialect.POSTGRESQL).read(conn!!)
            val schema = catalog.schemas.single { it.name == "public" }
            schema.tables.map { it.name } shouldContainExactly listOf("faktura", "odberatel")

            val faktura = schema.tables.single { it.name == "faktura" }
            faktura.primaryKey shouldContainExactly listOf("id_faktura")
            faktura.columns.map { it.name } shouldContainExactly listOf("id_faktura", "cislo", "id_odberatel", "castka")

            val fk = faktura.foreignKeys.single()
            fk.targetTable shouldBe "odberatel"
            fk.columns shouldContainExactly listOf("id_odberatel")
            fk.targetColumns shouldContainExactly listOf("id_odberatel")
        }

        "the end-to-end runner emits a db model that parses with zero diagnostics" {
            val result = ImportSchemaRunner(Dialect.POSTGRESQL, packageName = "erp").run(conn!!)
            val db = result.files.single { it.path == "db.public.ttrm" }
            val parsed = TtrLoader.parseString(db.content, "db.public.ttrm")
            parsed.ok.shouldBeTrue()
            // numeric→decimal, integer→int, varchar→text through the canonical mapper.
            db.content.shouldNotBeNull()
            Regex("def column castka \\{ type: decimal").containsMatchIn(db.content).shouldBeTrue()
        }
    })
