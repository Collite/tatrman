package org.tatrman.ttrp.dialect.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FragmentBody

/**
 * The accept corpus parses + decomposes with no TTR-SQL reject/syntax diagnostic
 * (`TTRP-SQL-*`). Structural coverage: WITH+SELECT, joins, DISTINCT, set-ops, VALUES,
 * `SELECT *`, semi/anti (IN-subquery), LIMIT/OFFSET after ORDER BY.
 */
class TtrSqlParserSpec :
    StringSpec({
        val accept =
            listOf(
                "hero-crunch.ttrp",
                "distinct.ttrp",
                "setops.ttrp",
                "values.ttrp",
                "star.ttrp",
                "semi-anti.ttrp",
                "limit-offset.ttrp",
            )

        for (f in accept) {
            "accept/$f parses and decomposes with no TTRP-SQL diagnostic" {
                val result = SqlCorpus.parseResult("accept/$f")
                val sqlDiags = result.diagnostics.filter { it.id.id.startsWith("TTRP-SQL-") }
                sqlDiags.map { it.id.id } shouldBe emptyList()
                // every """sql container was actually decomposed (non-null decomposition)
                result.document.statements
                    .filterIsInstance<ContainerDecl>()
                    .map { it.body }
                    .filterIsInstance<FragmentBody>()
                    .filter { it.tag == "sql" }
                    .all { it.decomposition != null } shouldBe true
            }
        }
    })
