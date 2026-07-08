package org.tatrman.ttrp.dialect.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.parser.TtrpParser

/**
 * The reject table (`ttr-sql.rejects.toml`) is a reviewable, versioned fixture
 * (contracts §8): (a) ids unique + monotone under `TTRP-SQL-`, (b) every entry has a
 * non-empty suggestion, (c) table ↔ corpus ↔ parser agree — every id has a `reject/`
 * fixture that produces it, and every fixture's id is in the table.
 */
class TtrSqlRejectTableSpec :
    StringSpec({
        val table = TtrSql.rejectTable

        "ids are unique, TTRP-SQL-prefixed, and monotone" {
            val ids = table.entries.map { it.id }
            ids.distinct() shouldBe ids
            ids.all { it.startsWith("TTRP-SQL-") } shouldBe true
            ids shouldBe ids.sorted()
        }

        "every entry has a non-empty suggestion" {
            table.entries.all { it.suggest.isNotBlank() } shouldBe true
        }

        "table ↔ corpus ↔ parser: every id has a fixture that produces it" {
            for (entry in table.entries) {
                val src = SqlCorpus.read("reject/${entry.id}.ttrp")
                val produced = TtrpParser.parseString(src, entry.id).diagnostics.map { it.id.id }
                (entry.id in produced) shouldBe true
            }
        }
    })
