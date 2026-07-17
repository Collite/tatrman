// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.er

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import org.tatrman.ttr.importschema.fixtures.heroCatalog
import org.tatrman.ttr.importschema.introspect.IntrospectedCatalog
import org.tatrman.ttr.importschema.introspect.IntrospectedColumn
import org.tatrman.ttr.importschema.introspect.IntrospectedTable
import java.sql.Types

/** SV-P4·S4·T5 — F4-γ re-run: db proposes, er is flagged-only and never regenerated. */
class ReRunSpec :
    StringSpec({
        val rerun = ReRun("erp")

        fun mutate(block: (List<IntrospectedTable>) -> List<IntrospectedTable>): IntrospectedCatalog {
            val s = heroCatalog().schemas.single()
            return IntrospectedCatalog(listOf(s.copy(tables = block(s.tables))))
        }

        "an unchanged database is a no-op — no proposal, no flags, er untouched" {
            val r = rerun.rerun(heroCatalog(), heroCatalog())
            r.dbChanged.shouldBeFalse()
            r.flags.shouldBeEmpty()
            r.erRegenerated.shouldBeFalse()
        }

        "a new column proposes a db change but raises no er drift, and never regenerates er" {
            val newCatalog =
                mutate { tables ->
                    tables.map { t ->
                        if (t.name == "Faktura") {
                            t.copy(
                                columns =
                                    t.columns +
                                        IntrospectedColumn(
                                            "Splatnost",
                                            "date",
                                            Types.DATE,
                                            nullable = true,
                                            ordinal = 99,
                                        ),
                            )
                        } else {
                            t
                        }
                    }
                }
            val r = rerun.rerun(heroCatalog(), newCatalog)
            r.dbChanged.shouldBeTrue()
            r.flags.any { it.kind == ChecklistNote.Kind.ER_DRIFT }.shouldBeFalse()
            r.flags.any { it.kind == ChecklistNote.Kind.DB_DRIFT }.shouldBeTrue()
            r.erRegenerated.shouldBeFalse()
        }

        "a dropped FK proposes a db change AND flags er drift, er untouched" {
            val newCatalog =
                mutate { tables ->
                    tables.map { t -> if (t.name == "Faktura") t.copy(foreignKeys = emptyList()) else t }
                }
            val r = rerun.rerun(heroCatalog(), newCatalog)
            r.dbChanged.shouldBeTrue()
            r.flags
                .any {
                    it.kind == ChecklistNote.Kind.ER_DRIFT &&
                        it.subject.contains("FK_Faktura_Odberatel") &&
                        it.detail.contains("dropped")
                }.shouldBeTrue()
            r.erRegenerated.shouldBeFalse()
        }

        "a new table flags er drift (unmapped) and is proposed into db, er untouched" {
            val newCatalog =
                mutate { tables ->
                    tables +
                        IntrospectedTable(
                            "Dodavatel",
                            columns =
                                listOf(
                                    IntrospectedColumn(
                                        "IDDodavatel",
                                        "int",
                                        Types.INTEGER,
                                        nullable = false,
                                        ordinal = 1,
                                    ),
                                ),
                            primaryKey = listOf("IDDodavatel"),
                        )
                }
            val r = rerun.rerun(heroCatalog(), newCatalog)
            r.dbChanged.shouldBeTrue()
            r.flags
                .any {
                    it.kind == ChecklistNote.Kind.ER_DRIFT &&
                        it.subject == "dbo.Dodavatel" &&
                        it.detail.contains("new table")
                }.shouldBeTrue()
            r.erRegenerated.shouldBeFalse()
        }
    })
