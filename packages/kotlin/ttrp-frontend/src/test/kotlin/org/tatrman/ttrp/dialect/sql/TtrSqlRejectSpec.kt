package org.tatrman.ttrp.dialect.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.parser.TtrpParser

/**
 * Every fixture under `reject/` produces its header-declared `TTRP-SQL-NNN` with the
 * reject table's suggested alternative (single source — parser, table, and test agree).
 * These are NAMED grammar rejects (C2-g), never bare syntax errors.
 */
class TtrSqlRejectSpec :
    StringSpec({
        val table = TtrSql.rejectTable
        val ids = (1..15).map { "TTRP-SQL-%03d".format(it) }

        for (id in ids) {
            "reject fixture $id → $id with the table suggestion" {
                val src = SqlCorpus.read("reject/$id.ttrp")
                val expect =
                    src
                        .lineSequence()
                        .first()
                        .substringAfter("expect:")
                        .trim()
                expect shouldBe id
                val diags = TtrpParser.parseString(src, "$id.ttrp").diagnostics
                val hit = diags.firstOrNull { it.id.id == id }
                (hit != null) shouldBe true
                hit!!.suggestedAlternative shouldBe table.entry(id).suggest
            }
        }
    })
