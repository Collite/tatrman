// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.importschema.conventions.ConventionsLoader
import org.tatrman.ttr.importschema.conventions.ConventionsResolver
import org.tatrman.ttr.importschema.introspect.Dialect
import org.tatrman.ttr.importschema.write.ModelPackageWriter
import org.tatrman.ttr.parser.loader.TtrLoader
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

/**
 * SV-P4·S4·T7 — the hero end-to-end: a live Czech-ERP-shaped database → `ttr import-schema` →
 * db mirror + er first cut + review checklist → **all documents parse back with zero diagnostics**
 * (the quickstart step-3 seam; the live Veles `get_model` round-trip on the S2 kind cluster is the
 * documented step-3→4 path — see docs-site/get-running/import-schema.md). Runs on native Postgres;
 * the MSSQL hero is amd64/CI-gated.
 */
class HeroEndToEndComponentSpec :
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
                    CREATE TABLE "Odberatel" ("IDOdberatel" int PRIMARY KEY, "Nazev" varchar(200) NOT NULL, "ICO" varchar(20), "IDStat" int);
                    CREATE TABLE "Ciselnik_Stat" ("IDStat" int PRIMARY KEY, "Kod" varchar(3), "Nazev" varchar(200) NOT NULL);
                    CREATE TABLE "Ciselnik_StavFaktury" ("IDStav" int PRIMARY KEY, "Nazev" varchar(200) NOT NULL);
                    CREATE TABLE "Artikl" ("IDArtikl" int PRIMARY KEY, "KodArtiklu" varchar(50) NOT NULL, "Nazev" varchar(200) NOT NULL, "IDKategorie" int);
                    CREATE TABLE "Faktura" (
                        "IDFaktura" int PRIMARY KEY,
                        "CisloFaktury" varchar(50) NOT NULL,
                        "IDOdberatel" int NOT NULL REFERENCES "Odberatel"("IDOdberatel"),
                        "DatumVystaveni" date NOT NULL,
                        "Castka" numeric(18,2) NOT NULL,
                        "IDStav" int,
                        "Poznamka" varchar(400),
                        "Sleva %" numeric(5,2)
                    );
                    CREATE TABLE "PolozkaFaktury" (
                        "IDPolozka" int PRIMARY KEY,
                        "IDFaktura" int NOT NULL REFERENCES "Faktura"("IDFaktura"),
                        "IDArtikl" int NOT NULL,
                        "Mnozstvi" numeric(10,2) NOT NULL,
                        "CenaZaKus" numeric(18,2) NOT NULL
                    );
                    CREATE TABLE "Artikl_Odberatel" (
                        "IDArtikl" int NOT NULL REFERENCES "Artikl"("IDArtikl"),
                        "IDOdberatel" int NOT NULL REFERENCES "Odberatel"("IDOdberatel"),
                        PRIMARY KEY ("IDArtikl", "IDOdberatel")
                    );
                    """.trimIndent(),
                )
                // Clean referential data so the heuristic candidates probe VERIFIED_FULL (no orphans).
                st.execute("""INSERT INTO "Ciselnik_Stat" VALUES (1,'CZ','Česko'),(2,'SK','Slovensko')""")
                st.execute("""INSERT INTO "Ciselnik_StavFaktury" VALUES (1,'nová'),(2,'zaplacená')""")
                st.execute("""INSERT INTO "Odberatel" VALUES (10,'Firma A','111',1),(11,'Firma B','222',2)""")
                st.execute("""INSERT INTO "Artikl" VALUES (100,'A1','Artikl 1',null),(101,'A2','Artikl 2',null)""")
                st.execute(
                    """INSERT INTO "Faktura" VALUES (1000,'F1',10,'2026-01-01',100.00,1,null,null),(1001,'F2',11,'2026-02-01',200.00,2,'pozn',5.00)""",
                )
                st.execute("""INSERT INTO "PolozkaFaktury" VALUES (1,1000,100,2,50.00),(2,1001,101,1,200.00)""")
                st.execute("""INSERT INTO "Artikl_Odberatel" VALUES (100,10),(101,11)""")
            }
        }

        afterSpec {
            conn?.close()
            pg?.stop()
        }

        "the full hero import produces db + er + checklist that all parse with zero diagnostics" {
            val conventions = ConventionsLoader.parse(ConventionsResolver.loadProfileResource("czech-erp"))
            val result =
                ImportSchemaRunner(
                    Dialect.POSTGRESQL,
                    packageName = "erp",
                    conventions = conventions,
                ).run(conn!!)

            // db mirror: all seven tables.
            val db = result.dbFiles.single { it.path == "db.public.ttrm" }
            Regex("def table").findAll(db.content).count() shouldBe 7
            TtrLoader.parseString(db.content, "db.public.ttrm").ok.shouldBeTrue()

            // er first cut: junction collapsed → 6 entities + a M:N relation; heuristic + declared relations.
            val er = result.erFile.content
            Regex("def entity").findAll(er).count() shouldBe 6
            er shouldContain "cardinality: { from: \"0..*\", to: \"0..*\" }" // the collapsed M:N
            TtrLoader.parseString(er, "er.ttrm").ok.shouldBeTrue()

            // checklist: the mangled identifier, a codebook, the dangling id column.
            result.reviewJson shouldContain "Sleva %"
            result.reviewJson shouldContain "public.Ciselnik_Stat"
            result.reviewJson shouldContain "IDKategorie"

            // The whole package parses together (loadable — the servable model).
            val dir = Files.createTempDirectory("hero-import")
            ModelPackageWriter.write(dir, "erp", result)
            val docs = TtrLoader.parseDirectory(dir)
            docs.size shouldBeGreaterThanOrEqual 2
            docs.all { it.ok }.shouldBeTrue()
        }
    })
