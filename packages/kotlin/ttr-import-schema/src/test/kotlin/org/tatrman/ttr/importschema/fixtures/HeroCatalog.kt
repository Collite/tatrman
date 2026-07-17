// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.fixtures

import org.tatrman.ttr.importschema.introspect.IntrospectedCatalog
import org.tatrman.ttr.importschema.introspect.IntrospectedColumn
import org.tatrman.ttr.importschema.introspect.IntrospectedForeignKey
import org.tatrman.ttr.importschema.introspect.IntrospectedSchema
import org.tatrman.ttr.importschema.introspect.IntrospectedTable
import java.sql.Types

/**
 * The SV-P4 hero fixture — a Czech ERP (MS SQL shape) with the design's defining traits:
 * real Czech identifiers, **imperfect/partial FKs** (some relations declared, some only named),
 * `Ciselnik*` codebooks, a header/detail pair (Faktura / PolozkaFaktury), a pure M:N junction
 * (Artikl_Odberatel), and one deliberately-illegal identifier (`Sleva %`) to exercise the
 * mangle + checklist-rename path. Built in-code as an [IntrospectedCatalog] so the deterministic
 * core (catalog → bytes) is testable with no live DB; the Testcontainers component tier proves
 * JDBC introspection produces this same shape.
 *
 * Declared FKs:  Faktura→Odberatel, PolozkaFaktury→Faktura, Artikl_Odberatel→{Artikl,Odberatel}.
 * Named-only (no FK — the brownfield heuristic fodder for S4): Faktura.IDStav→Ciselnik_StavFaktury,
 *   Odberatel.IDStat→Ciselnik_Stat, PolozkaFaktury.IDArtikl→Artikl, Artikl.IDKategorie→(no table).
 */
fun heroCatalog(): IntrospectedCatalog = IntrospectedCatalog(schemas = listOf(heroDbo()))

private fun heroDbo(): IntrospectedSchema =
    IntrospectedSchema(
        name = "dbo",
        tables =
            listOf(
                table(
                    "Odberatel",
                    comment = "Odběratelé (zákazníci) evidovaní v systému.",
                    columns =
                        listOf(
                            col(
                                "IDOdberatel",
                                "int",
                                Types.INTEGER,
                                nullable = false,
                                ord = 1,
                                comment = "Identifikátor odběratele",
                            ),
                            col(
                                "Nazev",
                                "nvarchar",
                                Types.NVARCHAR,
                                nullable = false,
                                ord = 2,
                                comment = "Obchodní název",
                            ),
                            col("ICO", "varchar", Types.VARCHAR, nullable = true, ord = 3, comment = "IČO"),
                            col(
                                "IDStat",
                                "int",
                                Types.INTEGER,
                                nullable = true,
                                ord = 4,
                                comment = "Stát (číselník) — bez deklarovaného FK",
                            ),
                        ),
                    pk = listOf("IDOdberatel"),
                ),
                table(
                    "Ciselnik_Stat",
                    comment = "Číselník států.",
                    columns =
                        listOf(
                            col("IDStat", "int", Types.INTEGER, nullable = false, ord = 1),
                            col("Kod", "varchar", Types.VARCHAR, nullable = true, ord = 2, comment = "ISO kód"),
                            col("Nazev", "nvarchar", Types.NVARCHAR, nullable = false, ord = 3),
                        ),
                    pk = listOf("IDStat"),
                ),
                table(
                    "Ciselnik_StavFaktury",
                    comment = "Číselník stavů faktury.",
                    columns =
                        listOf(
                            col("IDStav", "int", Types.INTEGER, nullable = false, ord = 1),
                            col("Nazev", "nvarchar", Types.NVARCHAR, nullable = false, ord = 2),
                        ),
                    pk = listOf("IDStav"),
                ),
                table(
                    "Faktura",
                    comment = "Hlavička faktury.",
                    columns =
                        listOf(
                            col(
                                "IDFaktura",
                                "int",
                                Types.INTEGER,
                                nullable = false,
                                ord = 1,
                                comment = "Identifikátor faktury",
                            ),
                            col(
                                "CisloFaktury",
                                "varchar",
                                Types.VARCHAR,
                                nullable = false,
                                ord = 2,
                                comment = "Číslo faktury",
                            ),
                            col(
                                "IDOdberatel",
                                "int",
                                Types.INTEGER,
                                nullable = false,
                                ord = 3,
                                comment = "Odběratel (deklarovaný FK)",
                            ),
                            col(
                                "DatumVystaveni",
                                "date",
                                Types.DATE,
                                nullable = false,
                                ord = 4,
                                comment = "Datum vystavení",
                            ),
                            col(
                                "Castka",
                                "decimal",
                                Types.DECIMAL,
                                nullable = false,
                                ord = 5,
                                comment = "Částka celkem",
                            ),
                            col(
                                "IDStav",
                                "int",
                                Types.INTEGER,
                                nullable = true,
                                ord = 6,
                                comment = "Stav faktury (číselník) — bez FK",
                            ),
                            col("Poznamka", "nvarchar", Types.NVARCHAR, nullable = true, ord = 7),
                            // Deliberately illegal TTR-M identifier → mangled to `Sleva_`, original to the checklist.
                            col(
                                "Sleva %",
                                "decimal",
                                Types.DECIMAL,
                                nullable = true,
                                ord = 8,
                                comment = "Sleva v procentech",
                            ),
                        ),
                    pk = listOf("IDFaktura"),
                    fks =
                        listOf(
                            IntrospectedForeignKey(
                                "FK_Faktura_Odberatel",
                                listOf("IDOdberatel"),
                                "dbo",
                                "Odberatel",
                                listOf("IDOdberatel"),
                            ),
                        ),
                ),
                table(
                    "Artikl",
                    comment = "Artikly (zboží / SKU).",
                    columns =
                        listOf(
                            col(
                                "IDArtikl",
                                "int",
                                Types.INTEGER,
                                nullable = false,
                                ord = 1,
                                comment = "Identifikátor artiklu",
                            ),
                            col("KodArtiklu", "varchar", Types.VARCHAR, nullable = false, ord = 2),
                            col("Nazev", "nvarchar", Types.NVARCHAR, nullable = false, ord = 3),
                            col(
                                "IDKategorie",
                                "int",
                                Types.INTEGER,
                                nullable = true,
                                ord = 4,
                                comment = "Kategorie (číselník) — cíl neexistuje",
                            ),
                        ),
                    pk = listOf("IDArtikl"),
                ),
                table(
                    "PolozkaFaktury",
                    comment = "Položka faktury (detail).",
                    columns =
                        listOf(
                            col("IDPolozka", "int", Types.INTEGER, nullable = false, ord = 1),
                            col(
                                "IDFaktura",
                                "int",
                                Types.INTEGER,
                                nullable = false,
                                ord = 2,
                                comment = "Faktura (deklarovaný FK)",
                            ),
                            col(
                                "IDArtikl",
                                "int",
                                Types.INTEGER,
                                nullable = false,
                                ord = 3,
                                comment = "Artikl — bez deklarovaného FK",
                            ),
                            col("Mnozstvi", "decimal", Types.DECIMAL, nullable = false, ord = 4),
                            col("CenaZaKus", "decimal", Types.DECIMAL, nullable = false, ord = 5),
                        ),
                    pk = listOf("IDPolozka"),
                    fks =
                        listOf(
                            IntrospectedForeignKey(
                                "FK_PolozkaFaktury_Faktura",
                                listOf("IDFaktura"),
                                "dbo",
                                "Faktura",
                                listOf("IDFaktura"),
                            ),
                        ),
                ),
                table(
                    "Artikl_Odberatel",
                    comment = "Přiřazení artiklů odběratelům (M:N).",
                    columns =
                        listOf(
                            col("IDArtikl", "int", Types.INTEGER, nullable = false, ord = 1),
                            col("IDOdberatel", "int", Types.INTEGER, nullable = false, ord = 2),
                        ),
                    pk = listOf("IDArtikl", "IDOdberatel"),
                    fks =
                        listOf(
                            IntrospectedForeignKey(
                                "FK_ArtOdb_Artikl",
                                listOf("IDArtikl"),
                                "dbo",
                                "Artikl",
                                listOf("IDArtikl"),
                            ),
                            IntrospectedForeignKey(
                                "FK_ArtOdb_Odberatel",
                                listOf("IDOdberatel"),
                                "dbo",
                                "Odberatel",
                                listOf("IDOdberatel"),
                            ),
                        ),
                ),
            ),
    )

/**
 * The same estate returned by a differently-ordered driver: schemas/tables/columns/FKs all
 * reversed. Byte-identical output from this proves canonicalisation (GI-2 / S3·T6).
 */
fun heroCatalogShuffled(): IntrospectedCatalog {
    val base = heroCatalog()
    return IntrospectedCatalog(
        schemas =
            base.schemas.reversed().map { s ->
                s.copy(
                    tables =
                        s.tables.reversed().map { t ->
                            t.copy(
                                columns = t.columns.reversed(),
                                foreignKeys = t.foreignKeys.reversed(),
                            )
                        },
                )
            },
    )
}

private fun table(
    name: String,
    columns: List<IntrospectedColumn>,
    pk: List<String> = emptyList(),
    fks: List<IntrospectedForeignKey> = emptyList(),
    comment: String? = null,
): IntrospectedTable = IntrospectedTable(name, columns, pk, fks, comment = comment)

private fun col(
    name: String,
    sqlType: String,
    jdbcType: Int,
    nullable: Boolean,
    ord: Int,
    comment: String? = null,
): IntrospectedColumn =
    IntrospectedColumn(name, sqlType, jdbcType, nullable = nullable, comment = comment, ordinal = ord)
