// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.ColumnRef

/**
 * C2-b-iii β: `SELECT *` expands against the statically-known input schema, in schema
 * order; the authored text keeps the `*` (expansion is internal — [FragmentBody.sourceText]
 * is untouched). With no schema the star stays as a sentinel (resolved later).
 */
class TtrSqlStarExpansionSpec :
    StringSpec({
        val interior = SourceLocation("<sql>", 1, 0, 6, 0, 0, 40)

        fun projectCols(
            sql: String,
            schemas: Map<String, List<String>>,
        ): List<ColumnRef> {
            val d = TtrSql.decompose(sql, interior, outPort = "result", schemas = schemas)
            val chain = (d.statements.filterIsInstance<Assignment>().single { it.target == "result" }).chain
            val project = chain.elements.filterIsInstance<OpCall>().single { it.name == "project" }
            return project.args.map { (it.value as ExprArg).expr as ColumnRef }
        }

        "`SELECT * FROM t` expands to the schema columns in order" {
            val cols = projectCols("SELECT * FROM t", mapOf("t" to listOf("a", "b", "c")))
            cols.map { it.column } shouldContainExactly listOf("a", "b", "c")
            cols.all { it.column != "*" } shouldBe true
        }

        "with no known schema the star is kept as a sentinel" {
            val cols = projectCols("SELECT * FROM t", emptyMap())
            cols.map { it.column } shouldContainExactly listOf("*")
        }
    })
