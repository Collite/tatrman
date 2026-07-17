// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.dbmodel

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.importschema.GoldenSupport
import org.tatrman.ttr.importschema.fixtures.heroCatalog
import org.tatrman.ttr.importschema.fixtures.heroCatalogShuffled
import org.tatrman.ttr.importschema.introspect.IntrospectedCatalog
import org.tatrman.ttr.importschema.introspect.IntrospectedColumn
import org.tatrman.ttr.importschema.introspect.IntrospectedSchema
import org.tatrman.ttr.importschema.introspect.IntrospectedTable
import org.tatrman.ttr.parser.loader.TtrLoader
import java.sql.Types

/**
 * SV-P4·S3·T1/T3 — the hero db-mirror golden + the GI-2 stage invariant.
 * The mirror is a pure function of (catalog, package); this tier needs no live DB.
 */
class ImportSchemaDbMirrorSpec :
    StringSpec({
        val pkg = "erp"

        "hero db mirror matches the golden bytes" {
            val result = DbMirror(pkg).render(heroCatalog())
            val db = result.files.single { it.path == "db.dbo.ttrm" }
            GoldenSupport.assertMatchesGolden(db.content, "db-mirror/db.dbo.ttrm")
        }

        "same catalog + same package ⇒ byte-identical output (GI-2, run twice)" {
            val a = DbMirror(pkg).render(heroCatalog())
            val b = DbMirror(pkg).render(heroCatalog())
            a.files.map { it.content } shouldBe b.files.map { it.content }
        }

        "a permuted catalog (shuffled driver order) yields byte-identical output (GI-2 / S3·T6)" {
            val ordered = DbMirror(pkg).render(heroCatalog())
            val shuffled = DbMirror(pkg).render(heroCatalogShuffled())
            shuffled.files.map { it.path to it.content } shouldBe ordered.files.map { it.path to it.content }
        }

        "the emitted db model parses back through ttr-parser with zero diagnostics" {
            val db = DbMirror(pkg).render(heroCatalog()).files.single { it.path == "db.dbo.ttrm" }
            val parsed = TtrLoader.parseString(db.content, "db.dbo.ttrm")
            withClue(parsed.errors.joinToString("\n") { "${it.line}:${it.column} ${it.message}" }) {
                parsed.ok.shouldBeTrue()
            }
        }

        "an illegal source identifier is mangled and the rename is recorded (source-name → checklist)" {
            val renames = DbMirror(pkg).render(heroCatalog()).renames
            renames shouldContain
                IdentifierRename(
                    IdentifierRename.Kind.COLUMN,
                    "dbo.Faktura",
                    "Sleva_",
                    "Sleva %",
                )
        }

        "two distinct identifiers colliding after mangling is a hard TTRP-IMP-001" {
            val colliding =
                IntrospectedCatalog(
                    listOf(
                        IntrospectedSchema(
                            "dbo",
                            listOf(
                                IntrospectedTable(
                                    "T",
                                    columns =
                                        listOf(
                                            IntrospectedColumn(
                                                "a b",
                                                "int",
                                                Types.INTEGER,
                                                nullable = false,
                                                ordinal = 1,
                                            ),
                                            IntrospectedColumn(
                                                "a-b",
                                                "int",
                                                Types.INTEGER,
                                                nullable = false,
                                                ordinal = 2,
                                            ),
                                        ),
                                ),
                            ),
                        ),
                    ),
                )
            val ex = shouldThrow<ImportSchemaException> { DbMirror(pkg).render(colliding) }
            ex.code shouldBe "TTRP-IMP-001"
            ex.message!! shouldContain "a_b"
        }
    })
